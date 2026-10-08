package com.wechat.agent.data.speech

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.RandomAccessFile

/**
 * Vosk 离线中文识别引擎。
 * 16kHz 单声道 PCM 流式识别，边录边出 partial 结果，停止时返回 final 文本。
 * 模型目录：models/vosk-model-small-cn-0.22
 */
class VoskAsrEngine(private val modelDir: File) {

    private val tag = "VoskAsrEngine"
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var recorder: AudioRecord? = null
    @Volatile private var running = false

    fun ensureLoaded() {
        if (model == null) {
            model = Model(modelDir.absolutePath)
        }
        if (recognizer == null) {
            recognizer = Recognizer(model, 16000f)
        }
    }

    /**
     * 开始录音识别。回调运行在 IO 线程；partial 实时回调，final 结束时回调一次并停止。
     */
    @SuppressLint("MissingPermission")
    suspend fun start(
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (running) return@withContext
        ensureLoaded()
        val rec = recognizer ?: return@withContext
        rec.reset()

        val sampleRate = 16000
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufSize = maxOf(minBuf, 8000)
        val audio = AudioRecord(
            MediaRecorder.AudioSource.MIC, sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize)
        if (audio.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("麦克风初始化失败，请检查录音权限")
        }
        recorder = audio
        running = true
        audio.startRecording()
        val buf = ShortArray(4000) // 250ms @16k
        try {
            while (running) {
                ensureActive()
                val n = audio.read(buf, 0, buf.size)
                if (n > 0 && rec.acceptWaveForm(buf, n)) {
                    val result = JSONObject(rec.result)
                    val text = result.optString("text", "")
                    if (text.isNotBlank()) onFinal(text)
                } else if (n > 0) {
                    val partial = JSONObject(rec.partialResult).optString("partial", "")
                    if (partial.isNotBlank()) onPartial(partial)
                }
            }
            // 停止时取一次 final，兜底未在循环内触发的情况
            val finalText = JSONObject(rec.finalResult).optString("text", "")
            if (finalText.isNotBlank()) onFinal(finalText)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "recognize error", e)
        } finally {
            running = false
            try { audio.stop() } catch (_: Exception) {}
            audio.release()
            recorder = null
        }
    }

    /**
     * 按住说话：边录音边识别，同时把 PCM 写入 16kHz 单声道 WAV 文件。
     * onPartial 实时回调识别文本；停止时回调 (最终文本, 时长毫秒)。
     */
    @SuppressLint("MissingPermission")
    suspend fun recordToWav(
        outputFile: File,
        onPartial: (String) -> Unit,
        onFinished: (transcript: String, durationMs: Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (running) return@withContext
        ensureLoaded()
        val rec = recognizer ?: return@withContext
        rec.reset()

        val sampleRate = 16000
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufSize = maxOf(minBuf, 8000)
        val audio = AudioRecord(
            MediaRecorder.AudioSource.MIC, sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize)
        if (audio.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("麦克风初始化失败，请检查录音权限")
        }
        recorder = audio
        running = true

        val raf = RandomAccessFile(outputFile, "rw")
        writeWavHeader(raf, sampleRate, 0)
        raf.seek(WAV_HEADER_SIZE.toLong())
        var dataSize = 0
        var lastPartial = ""
        try {
            audio.startRecording()
            val buf = ShortArray(4000) // 250ms @16k
            while (running) {
                ensureActive()
                val n = audio.read(buf, 0, buf.size)
                if (n > 0) {
                    val bytes = shortArrayToBytes(buf, n)
                    raf.write(bytes)
                    dataSize += bytes.size

                    if (rec.acceptWaveForm(buf, n)) {
                        val text = JSONObject(rec.result).optString("text", "").trim()
                        if (text.isNotBlank()) onPartial(text)
                    } else {
                        val partial = JSONObject(rec.partialResult).optString("partial", "").trim()
                        if (partial.isNotBlank()) {
                            lastPartial = partial
                            onPartial(partial)
                        }
                    }
                }
            }
            val finalText = JSONObject(rec.finalResult).optString("text", "").trim()
                .ifBlank { lastPartial }
            // 回填 WAV 头部实际的 chunk/data 大小
            raf.seek(4)
            raf.write(intToBytes(36 + dataSize))
            raf.seek(40)
            raf.write(intToBytes(dataSize))
            val durationMs = if (dataSize > 0) dataSize / (sampleRate * 2) else 0
            onFinished(finalText, durationMs)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(tag, "record error", e)
            onFinished("", 0)
        } finally {
            running = false
            try { audio.stop() } catch (_: Exception) {}
            audio.release()
            recorder = null
            try { raf.close() } catch (_: Exception) {}
        }
    }

    fun stop() {
        running = false
    }

    fun release() {
        stop()
        recognizer?.close()
        recognizer = null
        model?.close()
        model = null
    }

    companion object {
        private const val WAV_HEADER_SIZE = 44

        private fun writeWavHeader(raf: RandomAccessFile, sampleRate: Int, dataSize: Int) {
            fun writeAscii(s: String) = raf.write(s.toByteArray(Charsets.US_ASCII))
            writeAscii("RIFF")
            raf.write(intToBytes(36 + dataSize))
            writeAscii("WAVE")
            writeAscii("fmt ")
            raf.write(intToBytes(16))
            raf.write(shortToBytes(1)) // PCM
            raf.write(shortToBytes(1)) // 单声道
            raf.write(intToBytes(sampleRate))
            raf.write(intToBytes(sampleRate * 2)) // byteRate = sampleRate * channels * 2
            raf.write(shortToBytes(2)) // block align
            raf.write(shortToBytes(16)) // bits per sample
            writeAscii("data")
            raf.write(intToBytes(dataSize))
        }

        private fun shortArrayToBytes(data: ShortArray, count: Int): ByteArray {
            val out = ByteArray(count * 2)
            for (i in 0 until count) {
                val v = data[i].toInt()
                out[i * 2] = (v and 0xFF).toByte()
                out[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
            }
            return out
        }

        private fun shortToBytes(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())

        private fun intToBytes(v: Int) = byteArrayOf(
            (v and 0xFF).toByte(),
            ((v shr 8) and 0xFF).toByte(),
            ((v shr 16) and 0xFF).toByte(),
            ((v shr 24) and 0xFF).toByte()
        )
    }
}
