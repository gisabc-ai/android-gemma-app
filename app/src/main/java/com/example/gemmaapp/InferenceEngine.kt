package com.example.gemmaapp

import android.content.Context
import android.util.Log
import android.llama.cpp.LLamaAndroid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/**
 * InferenceEngine — llama.cpp GGUF 模型推理引擎
 *
 * 基于 android.llama.cpp.LLamaAndroid (来自 iris_android)
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
    private var loadedModelPath: String? = null

    // ============================================================
    // 对外 API
    // ============================================================

    /**
     * 加载 GGUF 模型文件
     * @param context Android Context
     * @param modelFile 模型文件（已复制到 App 内部存储）
     * @return Result.success(Unit) 加载成功
     */
    suspend fun loadModel(context: Context, modelFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        if (isModelLoaded && loadedModelPath == modelFile.absolutePath) {
            Log.i(TAG, "模型已加载，跳过: ${modelFile.absolutePath}")
            return@withContext Result.success(Unit)
        }

        // 释放之前的模型
        try { llm.unload() } catch (e: Exception) { /* ignore */ }

        try {
            Log.i(TAG, "加载 GGUF 模型: ${modelFile.absolutePath}")
            Log.i(TAG, "模型大小: ${modelFile.length() / 1024 / 1024} MB")

            llm.load(
                pathToModel = modelFile.absolutePath,
                userThreads = N_threads,
                topK = top_k,
                topP = top_p,
                temp = temp
            )

            loadedModelPath = modelFile.absolutePath
            isModelLoaded = true

            Log.i(TAG, "模型加载成功")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "模型加载失败", e)
            isModelLoaded = false
            loadedModelPath = null
            Result.failure(e)
        }
    }

    /**
     * 生成文本（流式）
     * @param prompt 输入提示词
     * @return Flow<String> 每个 token 作为一个 emit
     */
    fun generateStream(prompt: String): Flow<String> = flow {
        if (!isModelLoaded) {
            emit("错误：模型未加载，请先加载 GGUF 模型文件")
            return@flow
        }

        try {
            Log.i(TAG, "开始生成，prompt 长度: ${prompt.length}")
            llm.send(prompt)
                .catch { e ->
                    Log.e(TAG, "流式生成出错", e)
                    emit("错误：${e.message}")
                }
                .collect { token ->
                    emit(token)
                }
        } catch (e: Exception) {
            Log.e(TAG, "生成失败", e)
            emit("错误：${e.message}")
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 生成文本（完整返回）
     * @param prompt 输入提示词
     * @return Result.success(完整回复文本)
     */
    suspend fun generate(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        if (!isModelLoaded) {
            return@withContext Result.failure(IllegalStateException("模型未加载"))
        }

        try {
            val fullResponse = StringBuilder()
            llm.send(prompt)
                .catch { e -> emit("错误：${e.message}") }
                .collect { token -> fullResponse.append(token) }
            Result.success(fullResponse.toString())
        } catch (e: Exception) {
            Log.e(TAG, "generate() 失败", e)
            Result.failure(e)
        }
    }

    /**
     * 检查模型是否已加载
     */
    fun isLoaded(): Boolean = isModelLoaded

    /**
     * 获取当前模型路径
     */
    fun getModelPath(): String? = loadedModelPath

    /**
     * 释放模型资源
     */
    fun close() {
        try {
            kotlinx.coroutines.runBlocking {
                llm.unload()
            }
            Log.i(TAG, "模型资源已释放")
        } catch (e: Exception) {
            Log.e(TAG, "释放模型资源时出错", e)
        }
        isModelLoaded = false
        loadedModelPath = null
    }
}
