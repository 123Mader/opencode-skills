package com.xinan.app.llm

/**
 * CBT (认知行为疗法) 结构化疏导流程状态机
 *
 * 根据用户当前状态引导进入对应的疏导阶段:
 * 倾听共情 → 识别认知偏差 → 挑战非理性信念 → 重构积极认知 → 行动建议
 */
class CBTFlow {

    enum class Stage(val label: String) {
        LISTENING("倾听共情"),
        IDENTIFY("识别认知偏差"),
        CHALLENGE("挑战非理性信念"),
        RESTRUCTURE("重构积极认知"),
        ACTION("行动建议"),
    }

    data class Context(
        val stage: Stage,
        val userMessage: String,
        val biasType: String? = null,   // 识别到的认知偏差类型
        val biasQuote: String? = null,  // 用户原文
        val emotion: String = "平静",
        val anxietyScore: Int = 0,
    )

    /** 识别认知偏差 (关键词+语义) */
    fun identifyBias(text: String): Pair<String?, String?> {
        val biases = mapOf(
            "灾难化" to listOf("完了", "毁了", "全完了", "没救了", "这辈子毁了", "一切都完了"),
            "以偏概全" to listOf("总是", "从来", "一直都", "每次都", "从来没有人", "我永远"),
            "读心术" to listOf("肯定讨厌", "肯定觉得", "他肯定", "她一定觉得", "都在说我"),
            "应该思维" to listOf("必须", "一定要", "不得不", "应该", "本该", "理当"),
            "过滤消极" to listOf("没有一件好事", "全是坏事", "什么都不顺", "从来没好事"),
            "非黑即白" to listOf("要么完美要么失败", "不成功就是失败", "不进则退"),
        )
        for ((bias, keywords) in biases) {
            for (kw in keywords) {
                if (text.contains(kw)) {
                    return Pair(bias, kw)
                }
            }
        }
        return Pair(null, null)
    }

    /** 生成当前阶段的引导 prompt (拼接到 LLM 输入) */
    fun buildStagePrompt(ctx: Context): String {
        return when (ctx.stage) {
            Stage.LISTENING -> """
                [当前阶段: 倾听共情]
                请先认真倾听用户的话, 表达理解和接纳, 不要急于给建议。
                示例: "我能感受到你现在很[情绪], 这确实不容易。能多说一点吗?"
            """.trimIndent()

            Stage.IDENTIFY -> """
                [当前阶段: 识别认知偏差]
                用户的话里可能有认知偏差, 温和地指出来。
                已识别偏差: ${ctx.biasType ?: "未确定"}
                用户原文: "${ctx.biasQuote ?: ""}"
                引导用户: "你刚才说'${ctx.biasQuote}', 这个想法可能存在${ctx.biasType}的偏差..."
            """.trimIndent()

            Stage.CHALLENGE -> """
                [当前阶段: 挑战非理性信念]
                引导用户用证据检验这些想法:
                - 这个想法有100%的证据吗?
                - 最坏的情况发生的概率有多大?
                - 如果是朋友这样想, 你会怎么劝TA?
            """.trimIndent()

            Stage.RESTRUCTURE -> """
                [当前阶段: 重构积极认知]
                帮用户把扭曲的想法改写成更合理、更积极的说法。
                引导: "也许可以换个角度想: ..."
            """.trimIndent()

            Stage.ACTION -> """
                [当前阶段: 行动建议]
                给用户1-2个今天就能做的小事建议(不能太难):
                - 出门散步5分钟 / 深呼吸3次 / 喝口水
                - 给一个具体可执行的小行动
            """.trimIndent()
        }
    }

    /** 判断当前阶段 (根据对话内容) */
    fun detectStage(ctx: Context): Stage {
        // 高焦虑/危机 → 倾听共情优先
        if (ctx.anxietyScore >= 70) return Stage.LISTENING

        // 检测到认知偏差 → 进入识别阶段
        if (ctx.biasType != null) return Stage.IDENTIFY

        // 否则按顺序推进
        return ctx.stage
    }

    /** 危机检测 (必须最先判断) */
    fun isCrisis(text: String): Boolean {
        val crisis = listOf(
            "自杀", "不想活了", "想死", "死了算了", "轻生", "自残", "自伤",
            "活着没意思", "生不如死", "结束一切", "放弃一切", "活不下去",
        )
        return crisis.any { text.contains(it) }
    }

    /**
     * ★ 由微表情心理预期生成"镜像反馈"引导 prompt
     * 让 LLM 以试探性口吻把观察反馈给用户("我注意到你似乎在…是这样吗?"), 而非断言。
     */
    fun buildProjectionReflection(p: com.xinan.app.vision.PsychologicalProjection.Projection): String = """
        [当前心理预期推断 — 来自视频微表情]
        标签: ${p.label} (置信度 ${(p.normalizedConfidence * 100).toInt()}%)
        观察: ${p.summary}
        焦虑轨迹: ${p.trajectoryNote}
        疏导方向: ${p.advice}
        要求: 用温和、试探的方式把这一观察反馈给用户(例:"我注意到你似乎…, 是这样吗?"),
              不要断言, 让用户确认或修正; 若用户否认, 立即尊重其自我描述而非坚持推断。
    """.trimIndent()
}