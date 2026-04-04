package com.example.gemmaapp

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import kotlin.concurrent.thread

/**
 * InferenceEngine — Gemma GGUF 模型推理引擎封装
 *
 * 两种使用模式：
 * 1. Mock 模式（默认）：纯 Kotlin 模拟推理，用于无 native .so 时的编译验证
 * 2. JNI 模式：加载 libllama.so，调用真正的 llama.cpp 推理
 *
 * 集成真正的 native .so 步骤：
 * 1. 下载预编译 llama.cpp Android AAR（如 llm-bridge）
 *    或自己用 NDK 编译 llama.cpp 得到 libllama.so
 * 2. 将 .so / .aar 放到 app/libs/
 * 3. 取消 app/build.gradle.kts 中 implementation(files("libs/...")) 的注释
 * 4. 将下面 USE_MOCK = false
 */
object InferenceEngine {

    private const val TAG = "InferenceEngine"

    // ============================================================
    // ⚙️ 关键配置项：切换 Mock / JNI 模式
    // ============================================================
    private const val USE_MOCK = true  // 设为 false 启用真实 JNI

    // 模型文件路径（相对于 app 的 assets 或外部存储）
    private const val MODEL_FILE_NAME = "gemma-3b-it-Q4_K_M.gguf"

    // GGUF 幻数检查
    private val GGUF_MAGIC = listOf(0x47, 0x47, 0x55, 0x46) // "GGUF"

    // 上下文窗口大小
    private const val N_ctx = 2048

    // 推理线程数
    private const val N_threads = 4

    // ============================================================
    // 状态
    // ============================================================
    @Volatile
    private var isModelLoaded = false

    @Volatile
    private var isLoading = false

    private var mockModelPath: String? = null

    // 如果用 JNI，这里存放 native 指针
    // private var modelPtr: Long = 0

    // ============================================================
    // 对外 API
    // ============================================================

    /**
     * 检查模型是否已加载
     */
    fun isModelLoaded(): Boolean = isModelLoaded

    /**
     * 检查是否正在加载
     */
    fun isLoading(): Boolean = isLoading

    /**
     * 获取模型路径（Mock 模式返回模拟路径）
     */
    fun getModelPath(): String? = mockModelPath

    /**
     * 初始化并加载 GGUF 模型文件
     * @param context Android Context
     * @param modelFile 模型文件（File 对象）
     * @return true 加载成功
     */
    suspend fun loadModel(context: Context, modelFile: File): Boolean = withContext(Dispatchers.IO) {
        if (isModelLoaded) {
            Log.w(TAG, "Model already loaded")
            return@withContext true
        }
        if (isLoading) {
            Log.w(TAG, "Model is already being loaded")
            return@withContext false
        }

        isLoading = true
        try {
            if (USE_MOCK) {
                loadModelMock(context, modelFile)
            } else {
                loadModelJNI(context, modelFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model", e)
            isModelLoaded = false
            return@withContext false
        } finally {
            isLoading = false
        }
        true
    }

    /**
     * 卸载模型，释放内存
     */
    fun unloadModel() {
        if (!isModelLoaded) return
        if (USE_MOCK) {
            Log.i(TAG, "Unloading mock model")
        } else {
            // unloadModelJNI()
        }
        isModelLoaded = false
        mockModelPath = null
    }

    /**
     * 生成回复（流式）
     * @param prompt 输入提示词
     * @param onToken 每个 token 生成时的回调（用于流式 UI）
     * @return 完整回复文本
     */
    suspend fun generate(
        prompt: String,
        onToken: ((String) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        if (!isModelLoaded) {
            throw IllegalStateException("Model not loaded. Call loadModel() first.")
        }

        if (USE_MOCK) {
            generateMock(prompt, onToken)
        } else {
            generateJNI(prompt, onToken)
        }
    }

    // ============================================================
    // Mock 实现（纯 Kotlin，无 native 依赖）
    // ============================================================

    private fun loadModelMock(context: Context, modelFile: File): Boolean {
        Log.i(TAG, "=== MOCK MODE: Loading model (no real inference) ===")
        Log.i(TAG, "Model path: ${modelFile.absolutePath}")
        Log.i(TAG, "Model size: ${modelFile.length() / 1024 / 1024} MB")

        // GGUF 文件头验证（可选，检查魔数）
        try {
            RandomAccessFile(modelFile, "r").use { raf ->
                val magic = ByteArray(4)
                raf.read(magic)
                val m = magic.map { it.toInt() and 0xFF }
                Log.i(TAG, "File magic: $m | Expected: $GGUF_MAGIC")
                // 注意：有些 GGUF 文件魔数可能不同，这里只记录不断言失败
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not verify model file header: ${e.message}")
        }

        mockModelPath = modelFile.absolutePath
        isModelLoaded = true

        Log.i(TAG, "Mock model loaded successfully")
        return true
    }

    private fun generateMock(
        prompt: String,
        onToken: ((String) -> Unit)?
    ): String {
        Log.i(TAG, "=== MOCK MODE: Generating response ===")
        Log.i(TAG, "Prompt: $prompt")

        // 模拟流式输出
        val mockResponses = listOf(
            "你好！我是 Gemma AI 助手。",
            "我目前运行在 Mock 模式，因为还没有加载真实的 native .so 库。",
            "要启用真正的 Gemma 推理，请按以下步骤操作：",
            "1. 下载预编译的 llama.cpp Android AAR（llm-bridge）",
            "2. 放入 app/libs/ 并在 build.gradle.kts 中取消注释依赖",
            "3. 编译原生库或使用预编译版本",
            "4. 将 USE_MOCK 设为 false",
            "模型文件：gemma-3b-it-Q4_K_M.gguf（约 1.8GB）",
            "这是一个测试回复，代表 Gemma 3B 模型可能生成的输出。"
        )

        val fullResponse = StringBuilder()
        for ((i, sentence) in mockResponses.withIndex()) {
            Thread.sleep(80) // 模拟 token 延迟
            fullResponse.append(sentence)
            onToken?.invoke(sentence)
        }

        Log.i(TAG, "Mock generation complete: ${fullResponse.length} chars")
        return fullResponse.toString()
    }

    // ============================================================
    // JNI 实现（真实 llama.cpp 推理）
    // 当 USE_MOCK = false 时使用
    // ============================================================

    // 加载 native 库（示例）
    // init {
    //     System.loadLibrary("llama")
    // }

    // JNI 方法声明（llama.cpp 需要实现这些 native 方法）
    // private external fun nativeLoadModel(modelPath: String, nCtx: Int, nThreads: Int): Boolean
    // private external fun nativeGenerate(prompt: String, nPredict: Int): String
    // private external fun nativeFree()

    private fun loadModelJNI(context: Context, modelFile: File): Boolean {
        // 真实 JNI 实现示例：
        // val modelPath = modelFile.absolutePath
        // val ok = nativeLoadModel(modelPath, N_ctx, N_threads)
        // if (!ok) throw RuntimeException("Failed to load GGUF model via JNI")
        // isModelLoaded = true
        // return true

        // 目前 placeholder - 需要用户自己集成 native .so
        throw UnsupportedOperationException(
            "JNI mode requires integrating prebuilt llama.cpp .so/.aar. " +
            "See README.md for instructions."
        )
    }

    private fun generateJNI(
        prompt: String,
        onToken: ((String) -> Unit)?
    ): String {
        // 真实 JNI 实现：
        // return nativeGenerate(prompt, 512)
        throw UnsupportedOperationException("JNI mode not implemented in this build")
    }
}
