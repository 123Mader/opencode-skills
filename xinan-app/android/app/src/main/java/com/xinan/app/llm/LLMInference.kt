package com.xinan.app.llm

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.xinan.app.llm.CBTFlow

/**
 * MediaPipe LLM 推理封装 (安卓本地大模型)
 * 支持 GGUF 模型 (Qwen/GLM/DeepSeek 量化)
 */
class LLMInference(private val context: Context) {

    companion object {
        // 内置 CBT 心理疏导系统提示词 (注: 拼接到 prompt 中, 因为 MediaPipe LLM 不支持独立 system prompt)
        val CBT_SYSTEM_PROMPT = """
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
    private val cbtFlow = CBTFlow()
    private var currentStage = CBTFlow.Stage.LISTENING

    /**
     * 加载模型 (支持运行时切换不同 GGUF)
     * @param modelPath assets/或本地文件路径
     */
    fun loadModel(modelPath: String, onLoaded: (Boolean) -> Unit) {
        executor.execute {
            try {
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(1024)
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
     * 对话 (带视觉情绪上下文 + ★心理预期推断)
     * @param userMessage 用户输入
     * @param visualContext 视觉情绪信息 (如 "焦虑指数75, 皱眉, 嘴唇紧抿") — 来自微表情分析
     * @param projection 视频微表情推断出的心理预期 (可选) — 注入"镜像反馈"引导
     * @param onResult 结果回调
     */
    fun chat(
        userMessage: String,
        visualContext: String? = null,
        projection: com.xinan.app.vision.PsychologicalProjection.Projection? = null,
        onResult: (String) -> Unit,
    ) {
        val model = llmInference ?: run { onResult("模型未加载"); return }
        conversationHistory.add("用户: $userMessage")

        // 危机检测 (最高优先级)
        if (cbtFlow.isCrisis(userMessage)) {
            onResult("我很在意你刚才说的话。如果你现在有伤害自己的想法，请立即拨打心理援助热线 400-161-9995 或去医院寻求帮助。你的安全最重要。")
            return
        }
        // 认知偏差识别
        val (biasType, biasQuote) = cbtFlow.identifyBias(userMessage)
        if (biasType != null) currentStage = CBTFlow.Stage.IDENTIFY
        // 拼接 CBT 系统提示词 + 阶段引导 + 视觉观察 + ★心理预期镜像反馈 + 用户消息
        var prompt = CBT_SYSTEM_PROMPT + "\n\n"
        if (!visualContext.isNullOrBlank()) prompt += "[视觉观察: $visualContext]\n"
        if (projection != null) prompt += cbtFlow.buildProjectionReflection(projection) + "\n"
        prompt += cbtFlow.buildStagePrompt(CBTFlow.Context(currentStage, userMessage, biasType, biasQuote)) + "\n"
        prompt += userMessage

        // MediaPipe LLM: generateResponse (同步) 在后台线程执行
        executor.execute {
            try {
                val result = model.generateResponse(prompt) ?: "抱歉, 我有点走神了, 能再说一遍吗?"
                conversationHistory.add("心安: $result")
                onResult(result)
            } catch (e: Exception) {
                onResult("抱歉, 我现在有点卡, 稍后再聊~")
            }
        }
    }

    /** 释放模型 */
    fun close() {
        llmInference?.close()
        executor.shutdown()
    }
}