package com.example.gemmaapp

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 聊天消息数据结构
 */
data class ChatMessage(
    val id: Long = System.currentTimeMillis(),
    val role: MessageRole,
    val content: String,
    val isStreaming: Boolean = false
)

enum class MessageRole { User, Assistant }

/**
 * UI 状态
 */
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isModelLoaded: Boolean = false,
    val isLoadingModel: Boolean = false,
    val modelPath: String? = null,
    val error: String? = null,
    val currentStreamingContent: String = ""
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ChatViewModel"
        // 默认模型文件名
        const val DEFAULT_MODEL_NAME = "gemma-2-2b-it-Q4_K_M.gguf"
        // assets 中的模型文件名（打包进 APK 时使用）
        const val ASSET_MODEL_NAME = "models/$DEFAULT_MODEL_NAME"
    }

    // 当前输入框文本
    var inputText by mutableStateOf("")
        private set

    // UI 状态（StateFlow 供 Compose 观察）
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // 默认欢迎消息
    init {
        _uiState.update {
            it.copy(
                messages = listOf(
                    ChatMessage(
                        id = 0,
                        role = MessageRole.Assistant,
                        content = "👋 你好！我是 Gemma AI 助手。\n\n" +
                                "请先点击上方「📥 加载模型」按钮，选择 GGUF 模型文件（gemma-2-2b-it-Q4_K_M.gguf）。\n\n" +
                                "模型加载后，我就能真正回答你的问题啦！"
                    )
                )
            )
        }
    }

    // ============================================================
    // 文件选择 & 模型加载
    // ============================================================

    /**
     * 更新输入文本
     */
    fun onInputChange(text: String) {
        inputText = text
    }

    /**
     * 用户点击"加载模型"按钮后的回调
     * @param uri 用户通过文件选择器选中的 URI
     */
    fun onModelSelected(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingModel = true, error = null) }

            try {
                val context = getApplication<Application>()
                // 复制到 app 内部存储
                val destFile = copyModelToInternal(uri, context)

                if (destFile != null) {
                    val result = InferenceEngine.loadModel(context, destFile)
                    result.fold(
                        onSuccess = {
                            _uiState.update {
                                it.copy(
                                    isModelLoaded = true,
                                    isLoadingModel = false,
                                    modelPath = destFile.absolutePath
                                )
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    "✅ 模型加载成功！可以开始聊天了",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            // 更新欢迎消息
                            updateWelcomeMessage()
                        },
                        onFailure = { e ->
                            Log.e(TAG, "模型加载失败: ${e.message}", e)
                            _uiState.update {
                                it.copy(
                                    isLoadingModel = false,
                                    isModelLoaded = false,
                                    error = "模型加载失败: ${e.message}"
                                )
                            }
                        }
                    )
                } else {
                    _uiState.update {
                        it.copy(
                            isLoadingModel = false,
                            error = "无法复制模型文件：文件无效或不是有效的 GGUF 格式"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading model", e)
                _uiState.update {
                    it.copy(
                        isLoadingModel = false,
                        error = "加载错误: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 复制模型文件到 app 内部存储
     */
    private suspend fun copyModelToInternal(uri: Uri, context: android.content.Context): File? {
        return withContext(Dispatchers.IO) {
            try {
                val modelsDir = File(context.filesDir, "models")
                if (!modelsDir.exists()) modelsDir.mkdirs()

                val destFile = File(modelsDir, DEFAULT_MODEL_NAME)

                context.contentResolver.openInputStream(uri)?.use { input ->
                    // 先读取文件头验证 GGUF
                    val header = ByteArray(4)
                    val readBytes = input.read(header)
                    if (readBytes < 4) {
                        Log.e(TAG, "文件太短，无法读取 GGUF 头")
                        return@withContext null
                    }
                    val magic = String(header, Charsets.UTF_8)
                    if (magic != "GGUF") {
                        Log.e(TAG, "无效的 GGUF 文件头: '$magic'")
                        return@withContext null
                    }
                    Log.i(TAG, "GGUF 文件头验证通过: $magic")

                    // 重新打开流（已消耗4字节）
                    context.contentResolver.openInputStream(uri)?.use { freshInput ->
                        FileOutputStream(destFile).use { output ->
                            // 先写入文件头
                            output.write(header)
                            freshInput.copyTo(output, bufferSize = 8192)
                        }
                    }
                }

                Log.i(TAG, "Model copied to: ${destFile.absolutePath} (${destFile.length() / 1024 / 1024} MB)")
                destFile
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy model file", e)
                null
            }
        }
    }

    /**
     * 尝试从 assets 加载模型（打包进 APK 时）
     */
    fun tryLoadModelFromAssets() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingModel = true, error = null) }

            try {
                val context = getApplication<Application>()
                val modelsDir = File(context.filesDir, "models")
                if (!modelsDir.exists()) modelsDir.mkdirs()

                val destFile = File(modelsDir, DEFAULT_MODEL_NAME)

                // 如果文件已存在，跳过复制
                if (!destFile.exists()) {
                    withContext(Dispatchers.IO) {
                        context.assets.open(ASSET_MODEL_NAME).use { input ->
                            FileOutputStream(destFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }

                val result = InferenceEngine.loadModel(context, destFile)
                result.fold(
                    onSuccess = {
                        _uiState.update {
                            it.copy(
                                isModelLoaded = true,
                                isLoadingModel = false,
                                modelPath = destFile.absolutePath,
                                error = null
                            )
                        }
                    },
                    onFailure = { e ->
                        _uiState.update {
                            it.copy(
                                isModelLoaded = false,
                                isLoadingModel = false,
                                modelPath = null,
                                error = "Asset 模型加载失败: ${e.message}"
                            )
                        }
                    }
                )

                if (result.isSuccess) {
                    updateWelcomeMessage()
                    Toast.makeText(context, "✅ 模型加载成功", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load model from assets", e)
                _uiState.update {
                    it.copy(
                        isLoadingModel = false,
                        isModelLoaded = false,
                        error = "assets 中未找到模型文件，请手动选择 GGUF 文件"
                    )
                }
            }
        }
    }

    // ============================================================
    // 发送消息
    // ============================================================

    fun sendMessage() {
        val text = inputText.trim()
        if (text.isBlank() || _uiState.value.isLoading) return

        inputText = ""

        val userMsg = ChatMessage(
            id = System.currentTimeMillis(),
            role = MessageRole.User,
            content = text
        )

        // 流式响应用占位
        val assistantMsgId = System.currentTimeMillis() + 1
        val assistantMsg = ChatMessage(
            id = assistantMsgId,
            role = MessageRole.Assistant,
            content = "",
            isStreaming = true
        )

        _uiState.update { state ->
            state.copy(
                messages = state.messages + userMsg + assistantMsg,
                isLoading = true,
                currentStreamingContent = ""
            )
        }

        viewModelScope.launch {
            try {
                InferenceEngine.generateStream(text).collect { token ->
                    // 流式更新 UI
                    _uiState.update { state ->
                        val msgs = state.messages.toMutableList()
                        val lastIdx = msgs.indexOfLast { it.id == assistantMsgId }
                        if (lastIdx >= 0) {
                            msgs[lastIdx] = msgs[lastIdx].copy(
                                content = state.currentStreamingContent + token
                            )
                        }
                        state.copy(
                            messages = msgs,
                            currentStreamingContent = state.currentStreamingContent + token
                        )
                    }
                }

                // 标记完成
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        currentStreamingContent = ""
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Generation failed", e)
                _uiState.update { state ->
                    val msgs = state.messages.toMutableList()
                    val lastIdx = msgs.indexOfLast { it.id == assistantMsgId }
                    if (lastIdx >= 0) {
                        msgs[lastIdx] = msgs[lastIdx].copy(
                            content = "❌ 推理出错: ${e.message}",
                            isStreaming = false
                        )
                    }
                    state.copy(
                        messages = msgs,
                        isLoading = false,
                        error = e.message
                    )
                }
            }
        }
    }

    /**
     * 清空对话
     */
    fun clearChat() {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.filter { it.role == MessageRole.Assistant && it.id == 0L },
                error = null
            )
        }
    }

    /**
     * 更新欢迎消息（模型加载后）
     */
    private fun updateWelcomeMessage() {
        _uiState.update { state ->
            val updated = state.messages.map { msg ->
                if (msg.id == 0L && msg.role == MessageRole.Assistant) {
                    msg.copy(
                        content = "✅ 模型已加载！我现在可以回答你的问题了。\n\n" +
                                "设备: ${android.os.Build.MODEL}\n" +
                                "模型: Gemma 2-2B Q4_K_M\n" +
                                "推理: MediaPipe LLM\n\n" +
                                "请输入你的问题，我会尽力回答！"
                    )
                } else msg
            }
            state.copy(messages = updated)
        }
    }
}
