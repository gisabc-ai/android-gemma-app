package com.example.gemmaapp

import android.content.Context
import android.util.Log
import com.google.common.util.concurrent.ListenableFuture
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.guava.await
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

    private const val TAG = "InferenceEngine"

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
            Log.i(TAG, "模型已加载，跳过: ${modelFile.absolutePath}")
            return@withContext Result.success(Unit)
        }

        // 释放之前的模型
        close()

        try {
            // 验证文件存在且可读
            if (!modelFile.exists()) {
                val msg = "模型文件不存在: ${modelFile.absolutePath}"
                Log.e(TAG, msg)
                return@withContext Result.failure(Exception(msg))
            }
            if (!modelFile.canRead()) {
                val msg = "模型文件不可读，请检查权限: ${modelFile.absolutePath}"
                Log.e(TAG, msg)
                return@withContext Result.failure(Exception(msg))
            }

            // 验证 GGUF 文件头（魔数）
            val header = ByteArray(4)
            modelFile.inputStream().use { input ->
                val readBytes = input.read(header)
                if (readBytes < 4) {
                    val msg = "模型文件太短，无法读取 GGUF 文件头"
                    Log.e(TAG, msg)
                    return@withContext Result.failure(Exception(msg))
                }
            }
            val magic = String(header, Charsets.UTF_8)
            Log.i(TAG, "文件头: '$magic' (expected 'GGUF')")
            if (magic != "GGUF") {
                val msg = "不是有效的 GGUF 文件，文件头: '$magic'（应为 'GGUF'）"
                Log.e(TAG, msg)
                return@withContext Result.failure(Exception(msg))
            }

            val absolutePath = modelFile.absolutePath
            currentModelPath = absolutePath

            Log.i(TAG, "开始加载模型: $absolutePath (${modelFile.length() / 1024 / 1024} MB)")

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(absolutePath)
                .setMaxTokens(512)        // 最大输出 token 数
                .setMaxTopK(40)           // Top-K 采样
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            isModelLoaded = true

            Log.i(TAG, "模型加载成功！")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "模型加载失败", e)
            isModelLoaded = false
            currentModelPath = null
            Result.failure(e)
        }
    }

    /**
     * 生成文本（流式）
     * MediaPipe 的 generateResponseAsync 本身不支持真正的流式回调，
     * ProgressListener 的行为取决于具体实现。
     * 这里简化处理：等待完整结果后一次性 emit。
     */
    fun generateStream(prompt: String): Flow<String> = flow {
        if (llmInference == null) {
            emit("错误：模型未加载，请先加载 GGUF 模型文件")
            return@flow
        }

        try {
            Log.i(TAG, "开始生成，prompt 长度: ${prompt.length}")
            val future: ListenableFuture<String> = llmInference!!.generateResponseAsync(
                prompt,
                ProgressListener { partialResult, isDone ->
                    // 注意：MediaPipe 的 ProgressListener 可能不会按片段调用，
                    // 部分设备/版本可能只在 isDone=true 时才返回完整结果
                    Log.d(TAG, "ProgressListener: isDone=$isDone, partial.len=${partialResult?.length ?: 0}")
                }
            )
            // 等待完整结果
            val result = future.await()
            Log.i(TAG, "生成完成，结果长度: ${result.length}")
            emit(result)
        } catch (e: Exception) {
            Log.e(TAG, "生成失败", e)
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
            Log.i(TAG, "generate() prompt 长度: ${prompt.length}")
            val result = llmInference?.generateResponse(prompt) ?: ""
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "generate() 失败", e)
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
            Log.i(TAG, "模型资源已释放")
        } catch (e: Exception) {
            Log.e(TAG, "释放模型资源时出错", e)
        }
        llmInference = null
        isModelLoaded = false
        currentModelPath = null
    }
}
