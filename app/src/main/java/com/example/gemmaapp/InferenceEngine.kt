package com.example.gemmaapp

import android.content.Context
import android.util.Log
import android.llama.cpp.LLamaAndroid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext
import java.io.File

/**
 * InferenceEngine — llama.cpp GGUF 模型推理引擎
 *
 * 基于 android.llama.cpp.LLamaAndroid (iris_android 项目)
 * 支持本地 GGUF 文件选择、流式推理
 */
object InferenceEngine {

    private const val TAG = "InferenceEngine"

    // 推理参数
    private const val N_ctx = 4096
    private const val N_threads = 4
    private const val top_p = 0.9f
    private const val top_k = 40
    private const val temp = 0.8f

    // llama.cpp 实例
    private val llm: LLamaAndroid by lazy { LLamaAndroid.instance() }

    // 状态
    @Volatile
    private var isModelLoaded = false

    @Volatile
    private var isLoading = false

    private var loadedModelPath: String? = null

    // ============================================================
    // 对外 API
    // ============================================================

    fun isModelLoaded(): Boolean = isModelLoaded

    fun isLoading(): Boolean = isLoading

    fun getModelPath(): String? = loadedModelPath

    /**
     * 加载 GGUF 模型
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
            Log.i(TAG, "Loading GGUF model: ${modelFile.absolutePath}")
            Log.i(TAG, "Model size: ${modelFile.length() / 1024 / 1024} MB")

            llm.load(
                pathToModel = modelFile.absolutePath,
                userThreads = N_threads,
                topK = top_k,
                topP = top_p,
                temp = temp
            )

            loadedModelPath = modelFile.absolutePath
            isModelLoaded = true

            Log.i(TAG, "Model loaded successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model", e)
            isModelLoaded = false
            loadedModelPath = null
            false
        } finally {
            isLoading = false
        }
    }

    /**
     * 卸载模型，释放内存
     */
    suspend fun unloadModel() {
        if (!isModelLoaded) return
        try {
            llm.unload()
        } catch (e: Exception) {
            Log.e(TAG, "Error unloading model", e)
        }
        isModelLoaded = false
        loadedModelPath = null
        Log.i(TAG, "Model unloaded")
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

        Log.i(TAG, "Generating response for prompt: $prompt")

        val fullResponse = StringBuilder()

        try {
            llm.send(prompt)
                .catch { e ->
                    Log.e(TAG, "Stream error", e)
                    emit("❌ 生成出错: ${e.message}")
                }
                .map { token ->
                    fullResponse.append(token)
                    onToken?.invoke(token)
                    token
                }
                .onCompletion { cause ->
                    if (cause != null) {
                        Log.e(TAG, "Stream completed with error", cause)
                    } else {
                        Log.i(TAG, "Stream completed. Total: ${fullResponse.length} chars")
                    }
                }
                .collect { /* token processed in map */ }
        } catch (e: Exception) {
            Log.e(TAG, "Generation failed", e)
            throw e
        }

        fullResponse.toString()
    }

    /**
     * 获取模型特殊 token（end-of-text）
     */
    fun getEotStr(): String {
        return try {
            llm.send_eot_str()
        } catch (e: Exception) {
            Log.w(TAG, "Could not get EOT str, using default", e)
            "<|im_end|>"
        }
    }

    /**
     * 获取设备信息
     */
    fun getSystemInfo(): String {
        return try {
            // System info is logged in LLamaAndroid init
            "llama.cpp GGUF inference"
        } catch (e: Exception) {
            "llama.cpp (init error: ${e.message})"
        }
    }
}
