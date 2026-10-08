package com.wechat.agent.data.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile

/**
 * 语音能力协调器（单例）：集中管理模型下载状态、ASR 录音识别、TTS 播放。
 * UI 层通过 state 驱动按钮/进度展示；语音识别结果通过回调交回聊天页。
 */
class SpeechManager private constructor(private val context: Context) {

    companion object {
        @Volatile private var instance: SpeechManager? = null
        fun get(context: Context): SpeechManager =
            instance ?: synchronized(this) {
                instance ?: SpeechManager(context.applicationContext).also { instance = it }
            }
    }

    data class SpeechState(
        val modelsReady: Boolean = false,
        val asrReady: Boolean = false,
        val ttsReady: Boolean = false,
        val listening: Boolean = false,
        val speaking: Boolean = false,
        val partialText: String = "",
        val lastError: String = "",
        val voicePlaying: Boolean = false
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val allModels: List<SpeechModel> by lazy { ModelCatalog.load(context) }
    private val requiredModels: List<SpeechModel> by lazy { ModelCatalog.requiredModels(allModels) }

    private val _state = MutableStateFlow(SpeechState())
    val state: StateFlow<SpeechState> = _state.asStateFlow()

    val downloadManager: ModelDownloadManager get() = ModelDownloadManager.get(context)

    private var asrEngine: VoskAsrEngine? = null
    private var ttsEngine: SherpaTtsEngine? = null
    private var listenJob: Job? = null
    private var speakJob: Job? = null
    private var voiceRecordJob: Job? = null
    private var voicePlayJob: Job? = null
    @Volatile private var recording = false
    @Volatile private var recordCancelled = false

    private fun modelsRoot(): File = ModelDownloadManager.modelsRoot(context)

    /** 检查/刷新模型就绪状态；尝试懒加载引擎（失败仅记录错误，不崩溃）。 */
    fun refresh() {
        val asrReady = requiredModels.filter { it.type == "asr" }.all { ModelCatalog.isReady(it, modelsRoot()) }
        val ttsReady = requiredModels.filter { it.type == "tts" }.all { ModelCatalog.isReady(it, modelsRoot()) }
        val ready = asrReady && ttsReady
        _state.value = _state.value.copy(modelsReady = ready, asrReady = asrReady, ttsReady = ttsReady)
        if (asrReady) {
            runCatching {
                if (asrEngine == null) {
                    val vosk = requiredModels.firstOrNull { it.type == "asr" }
                    if (vosk != null) asrEngine = VoskAsrEngine(File(modelsRoot(), vosk.targetDir))
                }
            }.onFailure {
                _state.value = _state.value.copy(lastError = "引擎加载失败：${it.message}")
            }
        }
        if (ttsReady) {
            runCatching {
                if (ttsEngine == null) {
                    val vits = allModels.firstOrNull { it.id == "vits-zh" }
                    if (vits != null) ttsEngine = SherpaTtsEngine(File(modelsRoot(), vits.targetDir))
                }
            }.onFailure {
                _state.value = _state.value.copy(lastError = "引擎加载失败：${it.message}")
            }
        }
    }

    /** 顺序下载必需模型（ASR + TTS），带进度/状态。 */
    fun downloadBaseModels(onFinished: (Boolean, String) -> Unit = { _, _ -> }) {
        scope.launch {
            try {
                downloadManager.downloadSequential(requiredModels)
                refresh()
                onFinished(true, "")
            } catch (e: Exception) {
                val msg = e.message ?: "下载失败"
                _state.value = _state.value.copy(lastError = "下载失败：$msg")
                onFinished(false, msg)
            }
        }
    }

    /** 开始语音输入。需外部已授予 RECORD_AUDIO 权限。 */
    fun startListening(onFinal: (String) -> Unit) {
        if (_state.value.listening) return
        if (!_state.value.asrReady) {
            _state.value = _state.value.copy(lastError = "语音识别模型未就绪，请先到设置下载")
            return
        }
        val engine = asrEngine ?: run {
            refresh()
            asrEngine
        }
        if (engine == null) {
            _state.value = _state.value.copy(lastError = "ASR 引擎未初始化")
            return
        }
        _state.value = _state.value.copy(listening = true, partialText = "", lastError = "")
        listenJob = scope.launch {
            try {
                engine.start(
                    onPartial = { p -> _state.value = _state.value.copy(partialText = p) },
                    onFinal = { f ->
                        _state.value = _state.value.copy(listening = false, partialText = "")
                        onFinal(f)
                    }
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(listening = false, lastError = "识别失败：${e.message}")
            }
        }
    }

    fun stopListening() {
        asrEngine?.stop()
        listenJob?.cancel()
        _state.value = _state.value.copy(listening = false, partialText = "")
    }

    /** TTS 播放文本（覆盖当前播放）。 */
    fun speak(text: String, onDone: () -> Unit = {}) {
        val engine = ttsEngine ?: run { refresh(); ttsEngine }
        if (engine == null) {
            _state.value = _state.value.copy(lastError = if (_state.value.ttsReady) "TTS 引擎未初始化" else "TTS 模型未就绪，请先到设置下载")
            return
        }
        speakJob?.cancel()
        _state.value = _state.value.copy(speaking = true)
        speakJob = scope.launch {
            try {
                engine.speak(text) {
                    _state.value = _state.value.copy(speaking = false)
                }
                onDone()
            } catch (e: Exception) {
                _state.value = _state.value.copy(speaking = false, lastError = "朗读失败：${e.message}")
            }
        }
    }

    fun stopSpeaking() {
        ttsEngine?.stopPlayback()
        speakJob?.cancel()
        _state.value = _state.value.copy(speaking = false)
    }

    /** 开始录制一条语音消息：录音写入 WAV，同时离线识别；松开后回调 (文件路径, 时长毫秒, 识别文本)。 */
    fun startVoiceRecording(onResult: (audioPath: String, durationMs: Int, transcript: String) -> Unit) {
        if (recording) return
        if (!_state.value.asrReady) {
            _state.value = _state.value.copy(lastError = "语音识别模型未就绪，请先到设置下载")
            return
        }
        val engine = asrEngine ?: run { refresh(); asrEngine }
        if (engine == null) {
            _state.value = _state.value.copy(lastError = "ASR 引擎未初始化")
            return
        }
        recording = true
        recordCancelled = false
        _state.value = _state.value.copy(listening = true, partialText = "", lastError = "")
        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.wav")
        voiceRecordJob = scope.launch {
            try {
                engine.recordToWav(
                    outputFile = file,
                    onPartial = { p -> _state.value = _state.value.copy(partialText = p) }
                ) { transcript, durationMs ->
                    recording = false
                    _state.value = _state.value.copy(listening = false, partialText = "")
                    if (recordCancelled) {
                        runCatching { file.delete() }
                    } else {
                        onResult(file.absolutePath, durationMs, transcript)
                    }
                }
            } catch (e: Exception) {
                recording = false
                _state.value = _state.value.copy(listening = false, lastError = "录音失败：${e.message}")
                runCatching { file.delete() }
            }
        }
    }

    /** 松开结束录音并发送。 */
    fun finishVoiceRecording() {
        asrEngine?.stop()
    }

    /** 取消本次录音（不发送，删除临时文件）。 */
    fun cancelVoiceRecording() {
        recordCancelled = true
        asrEngine?.stop()
    }

    /** 播放语音消息（WAV）。 */
    fun playVoiceMessage(filePath: String, onDone: () -> Unit = {}) {
        voicePlayJob?.cancel()
        _state.value = _state.value.copy(voicePlaying = true)
        voicePlayJob = scope.launch {
            try {
                playWav(File(filePath))
            } catch (e: Exception) {
                _state.value = _state.value.copy(lastError = "语音播放失败：${e.message}")
            } finally {
                _state.value = _state.value.copy(voicePlaying = false)
                onDone()
            }
        }
    }

    fun stopVoicePlayback() {
        voicePlayJob?.cancel()
        _state.value = _state.value.copy(voicePlaying = false)
    }

    private suspend fun playWav(file: File) {
        val raf = RandomAccessFile(file, "r")
        try {
            raf.seek(24); val sampleRate = readIntLe(raf)
            raf.seek(40); val dataSize = readIntLe(raf)
            if (dataSize <= 0) return
            raf.seek(44)
            val bytes = ByteArray(dataSize)
            raf.readFully(bytes)
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bytes.size)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            try {
                track.write(bytes, 0, bytes.size)
                track.play()
                val durationMs = bytes.size / (sampleRate * 2)
                delay(durationMs + 120L)
            } finally {
                try { track.stop() } catch (_: Exception) {}
                track.release()
            }
        } finally {
            raf.close()
        }
    }

    private fun readIntLe(raf: RandomAccessFile): Int {
        val b0 = raf.read().coerceAtLeast(0)
        val b1 = raf.read().coerceAtLeast(0)
        val b2 = raf.read().coerceAtLeast(0)
        val b3 = raf.read().coerceAtLeast(0)
        return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
    }

    fun release() {
        stopListening()
        stopSpeaking()
        stopVoicePlayback()
        asrEngine?.release()
        asrEngine = null
        ttsEngine?.release()
        ttsEngine = null
    }
}
