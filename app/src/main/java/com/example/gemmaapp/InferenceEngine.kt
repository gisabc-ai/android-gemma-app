package com.example.gemmaapp

import android.content.Context
import com.google.mediapipe.tasks.genai.llm.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Gemma LLM 推理引擎
 * 
 * 支持通过 MediaPipe LLM Inference 在 Android 设备上本地运行 GGUF 格式的 Gemma 模型。
 * 
 * 使用方法：
 * 1. 从 https://huggingface.co/bartowski/gemma-2-2b-it-GGUF 下载 gemma-2-2b-it-Q4_K_M.gguf
 *    (约 1.6GB)
 * 2. 在 App 内点击「📥 加载模型」选择该文件
 * 3. 模型会复制到 App 私有目录并加载
 */
object InferenceEngine {

    // MediaPipe LLM Inference 实例
    private var llmInference: LlmInference? = null
    
    // 当前加载的模型文件路径
    private var currentModelPath: String? = null
    
    // 模型是否已加载
    @Volatile
    private var isModelLoaded = false

    /**
     * 加载 GGUF 模型文件
     * @param context Android Context
     * @param modelFile 模型文件（已复制到 App 内部存储）
     * @return 加载结果
     */
    suspend fun loadModel(context: Context, modelFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        // 如果已加载相同文件，跳过
        if (isModelLoaded && currentModelPath == modelFile.absolutePath && llmInference != null) {
            return@withContext Result.success(Unit)
        }
        
        // 释放之前的模型
        close()
        
        try {
            val absolutePath = modelFile.absolutePath
            currentModelPath = absolutePath
            
            val options = LlmInference.Options.builder()
                .setModelPath(absolutePath)
                .setMaxTokens(512)        // 最大输出 token 数
                .setTopK(40)              // Top-K 采样
                .setTemperature(0.8f)     // 温度参数
                .build()
            
            llmInference = LlmInference.createFromModelFile(context, options)
            isModelLoaded = true
            
            Result.success(Unit)
        } catch (e: Exception) {
            isModelLoaded = false
            currentModelPath = null
            Result.failure(e)
        }
    }

    /**
     * 生成文本（流式）
     * 返回 Flow<String>，每个 emit 出一个片段
     */
    fun generateStream(prompt: String): Flow<String> = flow {
        if (llmInference == null) {
            emit("错误：模型未加载，请先加载 GGUF 模型文件")
            return@flow
        }

        try {
            llmInference?.generateStream(prompt)?.let { result ->
                result.get().let { emit(it) }
            }
        } catch (e: Exception) {
            emit("错误：${e.message}")
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 生成文本（完整返回）
     */
    suspend fun generate(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        if (llmInference == null) {
            return@withContext Result.failure(IllegalStateException("模型未加载"))
        }
        
        try {
            val result = llmInference?.generate(prompt)
            Result.success(result ?: "无输出")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 检查模型是否已加载
     */
    fun isLoaded(): Boolean = isModelLoaded && llmInference != null

    /**
     * 获取当前模型路径
     */
    fun getModelPath(): String? = currentModelPath

    /**
     * 释放模型资源
     */
    fun close() {
        try {
            llmInference?.close()
        } catch (_: Exception) { }
        llmInference = null
        isModelLoaded = false
        currentModelPath = null
    }
}
