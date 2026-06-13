package comkuaihuiai.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*

/**
 * 可绘AI v3.5.0 - 语音输入助手
 * 
 * 🎤 功能：
 * ✅ 语音转文字提示词
 * ✅ 多语言识别
 * ✅ 实时字幕
 * ✅ 语音命令控制
 * ✅ TTS 语音播报
 */
class VoiceInputHelper(private val context: Context) {

    companion object {
        private const val TAG = "VoiceInputHelper"
        
        // 支持的语言
        val SUPPORTED_LANGUAGES = mapOf(
            "zh-CN" to "中文",
            "en-US" to "English",
            "ja-JP" to "日本語",
            "ko-KR" to "한국어",
            "fr-FR" to "Français",
            "de-DE" to "Deutsch"
        )
    }
    
    // ==================== 数据模型 ====================
    
    /**
     * 识别状态
     */
    enum class RecognitionState {
        IDLE,           // 空闲
        READY,          // 就绪
        LISTENING,      // 监听中
        PROCESSING,     // 处理中
        ERROR,          // 错误
        NOT_AVAILABLE   // 不可用
    }
    
    /**
     * 识别结果
     */
    data class RecognitionResult(
        val text: String,
        val confidence: Float = 1f,
        val language: String = "zh-CN",
        val isFinal: Boolean = false,
        val alternatives: List<String> = emptyList()
    )
    
    /**
     * 语音命令
     */
    data class VoiceCommand(
        val action: CommandAction,
        val params: Map<String, Any> = emptyMap(),
        val confidence: Float = 1f
    )
    
    enum class CommandAction {
        GENERATE,       // 生成
        STOP,           // 停止
        SAVE,           // 保存
        SHARE,          // 分享
        UNDO,           // 撤销
        REDO,           // 重做
        CLEAR,          // 清除
        SET_STYLE,      // 设置风格
        SET_MODEL,      // 设置模型
        SET_STEPS,      // 设置步数
        CHANGE_LANGUAGE // 切换语言
    }
    
    /**
     * TTS 配置
     */
    data class TTSConfig(
        val language: String = "zh-CN",
        val speechRate: Float = 1.0f,
        val pitch: Float = 1.0f,
        val voiceName: String = ""
    )
    
    // ==================== 核心组件 ====================
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())
    
    // 状态
    private val _state = MutableStateFlow(RecognitionState.IDLE)
    val state: StateFlow<RecognitionState> = _state.asStateFlow()
    
    private val _interimText = MutableStateFlow("")
    val interimText: StateFlow<String> = _interimText.asStateFlow()
    
    private val _finalText = MutableStateFlow("")
    val finalText: StateFlow<String> = _finalText.asStateFlow()
    
    private val _recognitionResults = MutableSharedFlow<RecognitionResult>()
    val recognitionResults: SharedFlow<RecognitionResult> = _recognitionResults.asSharedFlow()
    
    private val _voiceCommands = MutableSharedFlow<VoiceCommand>()
    val voiceCommands: SharedFlow<VoiceCommand> = _voiceCommands.asSharedFlow()
    
    private var currentLanguage = "zh-CN"
    private var isTTSReady = false
    
    // 命令关键词映射
    private val commandKeywords = mapOf(
        CommandAction.GENERATE to listOf("生成", "create", "生成图片", "开始生成"),
        CommandAction.STOP to listOf("停止", "stop", "取消", "终止"),
        CommandAction.SAVE to listOf("保存", "save", "存储"),
        CommandAction.SHARE to listOf("分享", "share", "发送"),
        CommandAction.UNDO to listOf("撤销", "undo", "回退"),
        CommandAction.REDO to listOf("重做", "redo", "恢复"),
        CommandAction.CLEAR to listOf("清除", "clear", "清空", "删除"),
        CommandAction.SET_STYLE to listOf("风格", "style", "设置风格"),
        CommandAction.SET_MODEL to listOf("模型", "model", "设置模型"),
        CommandAction.SET_STEPS to listOf("步数", "steps", "设置步数"),
        CommandAction.CHANGE_LANGUAGE to listOf("语言", "language", "切换语言")
    )
    
    // ==================== 初始化 ====================
    
    init {
        initializeSpeechRecognizer()
        initializeTTS()
    }
    
    private fun initializeSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "语音识别不可用")
            _state.value = RecognitionState.NOT_AVAILABLE
            return
        }
        
        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(recognitionListener)
            _state.value = RecognitionState.READY
        } catch (e: Exception) {
            Log.e(TAG, "初始化语音识别失败: ${e.message}")
            _state.value = RecognitionState.NOT_AVAILABLE
        }
    }
    
    private fun initializeTTS() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTTSReady = true
                textToSpeech?.language = Locale.CHINESE
                
                textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {}
                    override fun onError(utteranceId: String?) {}
                })
            }
        }
    }
    
    // ==================== 公开 API ====================
    
    /**
     * 开始语音输入
     */
    fun startListening(language: String = currentLanguage) {
        if (_state.value == RecognitionState.LISTENING) {
            Log.d(TAG, "已经在监听中")
            return
        }
        
        currentLanguage = language
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }
        
        try {
            speechRecognizer?.startListening(intent)
            _state.value = RecognitionState.LISTENING
            _interimText.value = ""
            Log.i(TAG, "🎤 开始语音监听...")
        } catch (e: Exception) {
            Log.e(TAG, "启动语音监听失败: ${e.message}")
            _state.value = RecognitionState.ERROR
        }
    }
    
    /**
     * 停止语音输入
     */
    fun stopListening() {
        speechRecognizer?.stopListening()
        _state.value = RecognitionState.READY
        Log.i(TAG, "🛑 停止语音监听")
    }
    
    /**
     * 取消语音输入
     */
    fun cancelListening() {
        speechRecognizer?.cancel()
        _state.value = RecognitionState.READY
        _interimText.value = ""
    }
    
    /**
     * 文字转语音 (TTS)
     */
    fun speak(
        text: String,
        config: TTSConfig = TTSConfig(),
        onComplete: (() -> Unit)? = null
    ) {
        if (!isTTSReady) {
            Log.e(TAG, "TTS 还未就绪")
            return
        }
        
        textToSpeech?.apply {
            setSpeechRate(config.speechRate)
            setPitch(config.pitch)
            
            if (config.voiceName.isNotEmpty()) {
                val voice = voices?.find { it.name.contains(config.voiceName) }
                voice?.let { setVoice(it) }
            }
            
            val locale = when (config.language) {
                "zh-CN" -> Locale.CHINA
                "en-US" -> Locale.US
                "ja-JP" -> Locale.JAPAN
                "ko-KR" -> Locale.KOREA
                "fr-FR" -> Locale.FRANCE
                "de-DE" -> Locale.GERMANY
                else -> Locale.getDefault()
            }
            language = locale
            
            speak(text, TextToSpeech.QUEUE_FLUSH, null, "utterance_${System.currentTimeMillis()}")
        }
        
        onComplete?.let {
            handler.postDelayed(it, text.length * 100L) // 估算播报时间
        }
    }
    
    /**
     * 停止 TTS
     */
    fun stopSpeaking() {
        textToSpeech?.stop()
    }
    
    /**
     * 解析语音命令
     */
    fun parseCommand(text: String): VoiceCommand? {
        val lowerText = text.lowercase()
        
        for ((action, keywords) in commandKeywords) {
            for (keyword in keywords) {
                if (keyword.lowercase() in lowerText) {
                    // 提取参数
                    val params = extractParams(lowerText, action)
                    
                    return VoiceCommand(
                        action = action,
                        params = params,
                        confidence = 0.9f
                    )
                }
            }
        }
        
        return null
    }
    
    /**
     * 设置识别语言
     */
    fun setLanguage(language: String) {
        currentLanguage = language
    }
    
    /**
     * 获取支持的语言
     */
    fun getSupportedLanguages(): Map<String, String> = SUPPORTED_LANGUAGES
    
    /**
     * 释放资源
     */
    fun release() {
        scope.cancel()
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        Log.i(TAG, "🔴 VoiceInputHelper 已释放")
    }
    
    // ==================== 私有方法 ====================
    
    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.value = RecognitionState.READY
        }
        
        override fun onBeginningOfSpeech() {
            _state.value = RecognitionState.LISTENING
        }
        
        override fun onRmsChanged(rmsdB: Float) {
            // 可以用来显示音量指示
        }
        
        override fun onBufferReceived(buffer: ByteArray?) {}
        
        override fun onEndOfSpeech() {
            _state.value = RecognitionState.PROCESSING
        }
        
        override fun onError(error: Int) {
            val errorMessage = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "音频录制错误"
                SpeechRecognizer.ERROR_CLIENT -> "客户端错误"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "权限不足"
                SpeechRecognizer.ERROR_NETWORK -> "网络错误"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
                SpeechRecognizer.ERROR_NO_MATCH -> "没有匹配结果"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别服务忙"
                SpeechRecognizer.ERROR_SERVER -> "服务器错误"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有语音输入"
                else -> "未知错误"
            }
            
            Log.e(TAG, "语音识别错误: $errorMessage")
            _state.value = RecognitionState.ERROR
            
            // 自动重试
            if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                handler.postDelayed({
                    if (_state.value == RecognitionState.ERROR) {
                        startListening()
                    }
                }, 1000)
            }
        }
        
        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val text = matches[0]
                val alternatives = matches.drop(1)
                val confidence = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)?.firstOrNull() ?: 1f
                
                val result = RecognitionResult(
                    text = text,
                    confidence = confidence,
                    language = currentLanguage,
                    isFinal = true,
                    alternatives = alternatives
                )
                
                _finalText.value = text
                _recognitionResults.tryEmit(result)
                
                // 尝试解析为命令
                parseCommand(text)?.let { command ->
                    _voiceCommands.tryEmit(command)
                }
                
                Log.i(TAG, "✅ 识别结果: $text")
            }
            
            _state.value = RecognitionState.READY
            _interimText.value = ""
        }
        
        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                _interimText.value = matches[0]
            }
        }
        
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
    
    /**
     * 提取命令参数
     */
    private fun extractParams(text: String, action: CommandAction): Map<String, Any> {
        val params = mutableMapOf<String, Any>()
        
        when (action) {
            CommandAction.SET_STEPS -> {
                // 提取数字
                val numbers = Regex("\\d+").findAll(text).lastOrNull()?.value?.toIntOrNull()
                if (numbers != null) {
                    params["steps"] = numbers
                }
            }
            CommandAction.SET_STYLE -> {
                // 提取风格关键词
                val styles = listOf("动漫", "油画", "写实", "水彩", "素描")
                for (style in styles) {
                    if (style in text) {
                        params["style"] = style
                        break
                    }
                }
            }
            CommandAction.SET_MODEL -> {
                // 提取模型关键词
                val models = listOf("SDXL", "SD1.5", "Flux", "Z-Image")
                for (model in models) {
                    if (model in text) {
                        params["model"] = model
                        break
                    }
                }
            }
            CommandAction.CHANGE_LANGUAGE -> {
                // 提取语言
                SUPPORTED_LANGUAGES.forEach { (code, name) ->
                    if (name in text || code in text) {
                        params["language"] = code
                    }
                }
            }
            else -> {}
        }
        
        return params
    }
    
    // ==================== 扩展函数 ====================
    
    /**
     * 检查是否支持语音输入
     */
    fun isAvailable(): Boolean = _state.value != RecognitionState.NOT_AVAILABLE
    
    /**
     * 检查是否正在监听
     */
    fun isListening(): Boolean = _state.value == RecognitionState.LISTENING
    
    /**
     * 获取状态描述
     */
    fun getStateDescription(): String = when (_state.value) {
        RecognitionState.IDLE -> "空闲"
        RecognitionState.READY -> "就绪"
        RecognitionState.LISTENING -> "正在聆听..."
        RecognitionState.PROCESSING -> "处理中..."
        RecognitionState.ERROR -> "出错了"
        RecognitionState.NOT_AVAILABLE -> "不支持语音"
    }
}
