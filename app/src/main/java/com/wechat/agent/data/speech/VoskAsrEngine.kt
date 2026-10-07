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
}
