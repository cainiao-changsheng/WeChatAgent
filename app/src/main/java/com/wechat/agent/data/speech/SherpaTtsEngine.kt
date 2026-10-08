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

    fun stopPlayback() {
        playing = false
    }

    fun release() {
        stopPlayback()
        tts = null
    }
}
