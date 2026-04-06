package com.example.gemmaapp

import android.content.Context
import com.google.mediapipe.tasks.genai.llm.LLmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * 推理引擎 - 使用 Google MediaPipe LLM Inference
 * 
 * 支持 Gemma 2B / 3B GGUF 量化模型（Q4_K_M 等）
 * 
 * 模型文件放置位置：
 * app/src/main/assets/gemma-2b-it-q4_k_m.gguf
 * 
 * 推荐模型下载：
 * https://huggingface.co/bartowski/gemma-2b-it-GGUF
 * (选择 gemma-2b-it-Q4_K_M.gguf，约1.3GB)
 */
object InferenceEngine {

    // ========== 配置 ==========
    // 设置为 false 启用真实推理
    private const val USE_MOCK = true
    
    // MediaPipe LLM Inference 实例
    private var llmInference: LLmInference? = null
    
    // 模型文件路径（相对于 assets）
    private const val MODEL_FILE = "gemma-2b-it-q4_k_m.gguf"
    
    // 加载状态
    @Volatile
    private var isModelLoaded = false

    /**
     * 加载 LLM 模型
     * 首次调用时自动加载，之后复用实例
     */
    suspend fun loadModel(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        if (USE_MOCK) {
            isModelLoaded = false
            return@withContext Result.success(Unit)
        }
        
        if (isModelLoaded && llmInference != null) {
            return@withContext Result.success(Unit)
        }
        
        try {
            val modelPath = "file:///android_asset/$MODEL_FILE"
            
            val options = LLmInference.Options.builder()
                .setModelPath(modelPath)
                .setMaxTokens(512)           // 最大输出 token 数
                .setTopK(40)                 // Top-K 采样
                .setTemperature(0.8f)       // 温度参数
                .setRandomSeed(0)            // 随机种子（0=每次不同）
                .build()
            
            llmInference = LLmInference.createFromModelFile(context, options)
            isModelLoaded = true
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 生成文本（流式）
     * 返回 Flow<String>，每个 emit 出一个 token 片段
     */
    fun generateStream(prompt: String): Flow<String> = flow {
        if (USE_MOCK) {
            // Mock 模式：模拟流式输出
            val mockResponses = listOf(
                "你好！我是基于 Gemma 的 AI 助手。",
                "我现在运行在您的 Android 设备上。",
                "MediaPipe LLM Inference 让我可以直接在本地处理您的请求，",
                "无需连接互联网，保护您的隐私。",
                "请问有什么可以帮助您的吗？"
            )
            for (response in mockResponses) {
                kotlinx.coroutines.delay(200)
                emit(response)
            }
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
        if (USE_MOCK) {
            kotlinx.coroutines.delay(1500) // 模拟推理延迟
            return@withContext Result.success(
                "这是来自 Gemma 模型的回复！\n" +
                "当前运行在 MediaPipe LLM Inference 上。\n" +
                "要启用真实推理，请：\n" +
                "1. 下载 Gemma 2B GGUF 模型文件\n" +
                "2. 放到 app/src/main/assets/ 目录\n" +
                "3. 将 USE_MOCK 设为 false\n" +
                "4. 重新构建 APK"
            )
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
    fun isLoaded(): Boolean = isModelLoaded && !USE_MOCK

    /**
     * 释放模型资源
     */
    fun close() {
        llmInference?.close()
        llmInference = null
        isModelLoaded = false
    }
}
