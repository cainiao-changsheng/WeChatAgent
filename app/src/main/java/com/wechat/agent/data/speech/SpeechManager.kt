package com.wechat.agent.data.speech

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

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
        val listening: Boolean = false,
        val speaking: Boolean = false,
        val partialText: String = "",
        val lastError: String = ""
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

    private fun modelsRoot(): File = ModelDownloadManager.modelsRoot(context)

    /** 检查/刷新模型就绪状态；尝试懒加载引擎（失败仅记录错误，不崩溃）。 */
    fun refresh() {
        val ready = requiredModels.all { ModelCatalog.isReady(it, modelsRoot()) }
        _state.value = _state.value.copy(modelsReady = ready)
        if (ready) {
            runCatching {
                if (asrEngine == null) {
                    val vosk = requiredModels.firstOrNull { it.type == "asr" }
                    if (vosk != null) asrEngine = VoskAsrEngine(File(modelsRoot(), vosk.targetDir))
                }
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
                onFinished(false, e.message ?: "下载失败")
            }
        }
    }

    /** 开始语音输入。需外部已授予 RECORD_AUDIO 权限。 */
    fun startListening(onFinal: (String) -> Unit) {
        if (_state.value.listening) return
        if (!_state.value.modelsReady) {
            _state.value = _state.value.copy(lastError = "语音模型未就绪，请先到设置下载")
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
            _state.value = _state.value.copy(lastError = "TTS 引擎未初始化")
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

    fun release() {
        stopListening()
        stopSpeaking()
        asrEngine?.release()
        asrEngine = null
        ttsEngine?.release()
        ttsEngine = null
    }
}
