package com.wechat.agent.data.speech

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * sherpa-onnx 离线 TTS 引擎（阶段1：VITS-zh-ll 中文）。
 * 模型目录：models/sherpa-onnx-vits-zh-ll（需含 model.onnx / tokens.txt / lexicon.txt / espeak-ng-data）。
 */
class SherpaTtsEngine(private val modelDir: File) {

    private val tag = "SherpaTtsEngine"
    private var tts: OfflineTts? = null
    @Volatile private var playing = false

    fun ensureLoaded() {
        if (tts == null) {
            val modelFile = File(modelDir, "model.onnx")
            val tokens = File(modelDir, "tokens.txt")
            if (!modelFile.exists() || !tokens.exists()) {
                throw IllegalStateException("TTS 模型文件缺失：${modelDir.absolutePath}")
            }
            val lexicon = File(modelDir, "lexicon.txt")
            val vits = OfflineTtsVitsModelConfig(
                model = modelFile.absolutePath,
                tokens = tokens.absolutePath,
                lexicon = if (lexicon.exists()) lexicon.absolutePath else "",
            )
            val modelCfg = OfflineTtsModelConfig(
                vits = vits,
                numThreads = 1,
                debug = false,
                provider = "cpu",
            )
            // vits-zh-ll 使用 lexicon + 规则 FST 做中文文本归一化（日期/数字/电话号码），
            // 不使用 espeak-ng data（data_dir）。此前误设 dataDir 指向模型根目录，
            // 导致 native 校验因缺少 phontab/phonindex 等文件而失败（Invalid OfflineTtsConfig）。
            val ruleFsts = listOf("date.fst", "phone.fst", "number.fst")
                .joinToString(",") { File(modelDir, it).absolutePath }
            val cfg = OfflineTtsConfig(
                model = modelCfg,
                ruleFsts = ruleFsts,
                maxNumSentences = 2,
            )
            tts = OfflineTts(assetManager = null, config = cfg)
        }
    }

    /**
     * 合成并播放。onDone 在播放完成后回调（IO 线程）。
     */
    suspend fun speak(text: String, onDone: () -> Unit = {}) = withContext(Dispatchers.IO) {
        stopPlayback()
        ensureLoaded()
        val engine = tts ?: return@withContext
        if (text.isBlank()) return@withContext
        val audio: GeneratedAudio = engine.generate(text)
        if (audio.samples == null || audio.samples.isEmpty()) {
            Log.w(tag, "generate returned empty samples: $text")
            return@withContext
        }
        val sr = audio.sampleRate
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sr)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            // MODE_STATIC 必须显式指定缓冲区大小（float 每样本 4 字节），
            // 否则 write 时数据超过默认最小缓冲会抛 "Invalid audio buffer size"。
            .setBufferSizeInBytes(audio.samples.size * 4)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        playing = true
        try {
            track.write(audio.samples, 0, audio.samples.size, AudioTrack.WRITE_BLOCKING)
            track.play()
            while (playing && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                kotlinx.coroutines.delay(100)
            }
        } catch (e: Exception) {
            Log.e(tag, "play error", e)
        } finally {
            playing = false
            track.release()
        }
        onDone()
    }

    /**
     * 合成文本并写入 16-bit PCM 单声道 WAV 文件（用于语音气泡点击后即时合成 + 缓存）。
     * 返回音频时长（毫秒），失败返回 null。
     */
    suspend fun synthesizeToWav(text: String, outputFile: File): Int? = withContext(Dispatchers.IO) {
        ensureLoaded()
        val engine = tts ?: return@withContext null
        if (text.isBlank()) return@withContext null
        try {
            val audio: GeneratedAudio = engine.generate(text)
            val samples = audio.samples
            if (samples == null || samples.isEmpty()) {
                Log.w(tag, "synthesizeToWav empty samples: $text")
                return@withContext null
            }
            val sr = audio.sampleRate
            val dataSize = samples.size * 2
            val header = wavHeader(sr, dataSize)
            val bytes = ByteArray(dataSize)
            for (i in samples.indices) {
                val v = samples[i].toDouble().coerceIn(-1.0, 1.0)
                val s = (v * 32767.0).toInt()
                bytes[i * 2] = (s and 0xFF).toByte()
                bytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
            }
            FileOutputStream(outputFile).use { out ->
                out.write(header)
                out.write(bytes)
            }
            val durationMs = if (sr > 0) samples.size * 1000L / sr else 0L
            durationMs.toInt()
        } catch (e: Exception) {
            Log.e(tag, "synthesizeToWav error", e)
            null
        }
    }

    /** 构造 44 字节标准 WAV 头（RIFF + fmt + data，16-bit PCM 单声道）。 */
    private fun wavHeader(sampleRate: Int, dataSize: Int): ByteArray {
        val buf = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt(36 + dataSize)
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))
        buf.put("fmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(16)          // fmt 子块长度
        buf.putShort(1)         // PCM 编码
        buf.putShort(1)         // 单声道
        buf.putInt(sampleRate)  // 采样率
        buf.putInt(sampleRate * 2) // 字节率 = 采样率 * 声道数 * 每样本字节数
        buf.putShort(2)         // 块对齐
        buf.putShort(16)        // 位深
        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt(dataSize)
        return buf.array()
    }

    fun stopPlayback() {
        playing = false
    }

    fun release() {
        stopPlayback()
        tts = null
    }
}
