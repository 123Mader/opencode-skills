package com.xinan.app.llm

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceOptions
import com.google.mediapipe.tasks.genai.llminference.ResultListener
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * MediaPipe LLM 推理封装 (安卓本地大模型)
 * 支持 GGUF 模型 (Qwen/GLM/DeepSeek 量化)
 */
class LLMInference(private val context: Context) {

    companion object {
        // 内置 CBT 心理疏导系统提示词
        const val CBT_SYSTEM_PROMPT = """
            你是一个温暖、专业的 AI 情绪陪伴师，名叫「心安」。
            你的使命：帮助用户缓解焦虑情绪，回归自然快乐的生活。

            行为准则:
            1. 首先共情倾听，不急于给建议
            2. 识别用户认知偏差(灾难化/以偏概全/读心术/应该思维)
            3. 用认知行为疗法(CBT)温和引导用户重构思维
            4. 焦虑严重时引导正念呼吸(数息/腹式呼吸)
            5. 检测到自伤/严重危机信号时，立即停止对话并引导寻求专业帮助
            6. 回答简短温暖，每次1-3句话，像朋友一样

            你也要感知用户的状态:
            - 如果上层传入视觉情绪(如"焦虑指数75,皱眉,嘴唇紧抿")，要主动回应这个观察
        """.trimIndent()
    }

    private var llmInference: LlmInference? = null
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var conversationHistory = mutableListOf<String>()

    /**
     * 加载模型 (支持运行时切换不同 GGUF)
     * @param modelPath assets/或本地文件路径
     */
    fun loadModel(modelPath: String, onLoaded: (Boolean) -> Unit) {
        executor.execute {
            try {
                val options = LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(1024)
                    .setSystemPrompt(CBT_SYSTEM_PROMPT)
                    .build()
                llmInference = LlmInference.createFromOptions(context, options)
                onLoaded(true)
            } catch (e: Exception) {
                android.util.Log.e("LLMInference", "模型加载失败: ${e.message}")
                onLoaded(false)
            }
        }
    }

    /**
     * 对话 (带视觉情绪上下文)
     * @param userMessage 用户输入
     * @param visualContext 视觉情绪信息 (如 "焦虑指数75, 皱眉, 嘴唇紧抿") — 来自微表情分析
     * @param onResult 结果回调
     */
    fun chat(userMessage: String, visualContext: String? = null, onResult: (String) -> Unit) {
        val model = llmInference ?: run { onResult("模型未加载"); return }
        conversationHistory.add("用户: $userMessage")

        // 如果视觉检测到焦虑, 让AI感知
        var prompt = userMessage
        if (!visualContext.isNullOrBlank()) {
            prompt = "[视觉观察: $visualContext]\n$userMessage"
        }

        model.generateAsync(prompt, object : ResultListener {
            override fun onResult(result: String?, error: Throwable?) {
                val text = result ?: "抱歉, 我有点走神了, 能再说一遍吗?"
                conversationHistory.add("心安: $text")
                onResult(text)
            }
        })
    }

    /** 释放模型 */
    fun close() {
        llmInference?.close()
        executor.shutdown()
    }
}