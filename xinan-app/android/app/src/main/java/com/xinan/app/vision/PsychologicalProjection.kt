// ============================================================
// 「心安」心理预期推断引擎 (核心心理学模块)
// ============================================================
// 目标: 通过视频聊天中持续采集的微表情时序变化,
//      推断对方当前的「心理预期 / 心理投射状态」(psychological expectation)。
//
// 输入: MicroExpressionAnalyzer.AnalysisResult 时序帧 + 头部偏航(视线回避) + 眨眼率
// 输出: 主要心理预期标签 + 置信度 + 自然语言一句话 + 疏导建议方向
//
// 理论依据:
//   • Paul Ekman FACS 微表情泄露理论 — 压抑情绪会以 0.2~0.5s 微表情峰值泄露
//   • Matsumoto 情绪抑制研究 — 表面平静但微表情突变 = 情绪压抑
//   • 认知负荷与眨眼率正相关 (Kramer, 1991; measures of cognitive workload)
//   • 视线回避 (gaze aversion) 与防御性回避/回避型应对相关
//   • 焦虑预期: 持续 AU4(皱眉)+AU23(唇收紧)+焦虑轨迹上升 → 预期负面结果
//
// 本模型为「规则 + 时序轨迹」评分 (可被 CNN+LSTM 端到端分类器替代, 见路线图)。
package com.xinan.app.vision

import kotlin.math.abs

class PsychologicalProjection {

    /** 心理预期分类 */
    enum class Kind(val label: String) {
        ANXIOUS_ANTICIPATION("焦虑预期"),
        SUPPRESSED_EMOTION("情绪压抑"),
        COGNITIVE_OVERLOAD("认知负荷过载"),
        POSITIVE_ANTICIPATION("期待/希望"),
        DOUBT("疑虑/不信任"),
        AVOIDANT_DEFENSE("防御性回避"),
        VOLATILITY("情绪波动"),
        CALM_GROUNDED("平静放松");

        companion object {
            fun fromKey(k: String) = values().firstOrNull { it.name == k } ?: CALM_GROUNDED
        }
    }

    data class Projection(
        val kind: Kind,
        val label: String,
        val confidence: Float,           // 0..1 原始得分
        val normalizedConfidence: Float, // 相对归一化
        val scores: Map<String, Float>,  // 全部分类得分
        val summary: String,             // 给对话用的一句话
        val advice: String,              // 疏导建议方向
        val trajectoryNote: String,      // 焦虑轨迹说明
    )

    /** 一帧观察 (由分析结果 + 头部姿态构成) */
    data class Frame(
        val emotion: String,
        val anxiety: Float,
        val au: Map<String, Float>,
        val microExprs: List<String>,
        val headYawAbsDeg: Float,        // 头部偏航绝对值(°); >12 视线回避
        val blinkRate: Float,            // 归一化眨眼率 0..1
    )

    private val history = ArrayDeque<Frame>()
    private val WINDOW = 12   // ~6 秒 (每 ~0.5s 一帧结果)

    /**
     * 喂入一帧分析结果 + 头部偏航 + 眨眼率, 返回推断或 null(数据不足)
     */
    fun feed(
        result: MicroExpressionAnalyzer.AnalysisResult,
        headYawAbsDeg: Float,
        blinkRate: Float,
    ): Projection? {
        history.addLast(
            Frame(
                emotion = result.emotion,
                anxiety = result.anxietyScore.toFloat(),
                au = result.auValues,
                microExprs = result.microExpressions,
                headYawAbsDeg = abs(headYawAbsDeg),
                blinkRate = blinkRate.coerceIn(0f, 1f),
            )
        )
        if (history.size > WINDOW) history.removeFirst()
        // 需要至少 ~3 秒(6 帧)才稳定推断
        if (history.size < 6) return null
        return infer()
    }

    fun reset() { history.clear() }

    private fun infer(): Projection {
        val au4 = history.map { it.au["AU4"] ?: 0f }
        val au23 = history.map { it.au["AU23"] ?: 0f }
        val au15 = history.map { it.au["AU15"] ?: 0f }
        val au12 = history.map { it.au["AU12"] ?: 0f }
        val au17 = history.map { it.au["AU17"] ?: 0f }
        val au45 = history.map { it.au["AU45"] ?: 0f }
        val anx = history.map { it.anxiety }
        val blink = history.map { it.blinkRate }
        val yaw = history.map { it.headYawAbsDeg }

        val anxNow = anx.last()
        val anxFirst = anx.first()
        val anxDelta = (anxNow - anxFirst).coerceIn(-100f, 100f) / 100f   // -1..1
        val anxRising = anxDelta.coerceAtLeast(0f)
        val microSurgePerFrame = history.sumOf { it.microExprs.size }.toFloat() / history.size
        val emotionVariety = history.map { it.emotion }.toSet().size
        val blinkAvg = blink.average().toFloat().coerceIn(0f, 1f)
        val au4Avg = au4.average().toFloat()
        val au23Avg = au23.average().toFloat()
        val au15Avg = au15.average().toFloat()
        val au12Avg = au12.average().toFloat()
        val au17Avg = au17.average().toFloat()
        val yawAvg = yaw.average().toFloat()
        val calmFactor = (1f - anxNow / 100f).coerceIn(0f, 1f)

        val s = HashMap<String, Float>()

        // 1) 焦虑预期: 持续皱眉+唇收紧+焦虑轨迹上升 → 预期负面结果
        s[Kind.ANXIOUS_ANTICIPATION.name] =
            (au4Avg * 0.4f + au23Avg * 0.35f + anxRising * 0.25f).coerceIn(0f, 1f)

        // 2) 情绪压抑: 微表情频繁泄露但表面焦虑不高
        val leakScore = microSurgePerFrame.coerceIn(0f, 1f)
        s[Kind.SUPPRESSED_EMOTION.name] =
            (leakScore * 0.6f + calmFactor * 0.4f).coerceIn(0f, 1f)

        // 3) 认知负荷过载: 眨眼加快 + AU4 皱眉 + AU17 下巴紧
        s[Kind.COGNITIVE_OVERLOAD.name] =
            (blinkAvg * 0.45f + au4Avg * 0.3f + au17Avg * 0.25f).coerceIn(0f, 1f)

        // 4) 期待/希望: AU12 微笑 + 焦虑低
        s[Kind.POSITIVE_ANTICIPATION.name] =
            (au12Avg * 0.6f + calmFactor * 0.4f).coerceIn(0f, 1f)

        // 5) 疑虑/不信任: AU4 轻微 + AU15 嘴角微下压 (焦虑不高时才更像怀疑)
        s[Kind.DOUBT.name] =
            ((au4Avg * 0.5f + au15Avg * 0.5f) * (0.6f + calmFactor * 0.4f)).coerceIn(0f, 1f)

        // 6) 防御性回避: 头部偏航大 + AU4 + 焦虑中高
        val yawScore = (yawAvg / 18f).coerceIn(0f, 1f)   // 18° 视为强回避
        s[Kind.AVOIDANT_DEFENSE.name] =
            (yawScore * 0.5f + au4Avg * 0.25f + (anxNow / 100f) * 0.25f).coerceIn(0f, 1f)

        // 7) 情绪波动: 微表情多 + 情绪标签切换频繁
        s[Kind.VOLATILITY.name] =
            (leakScore * 0.5f + ((emotionVariety - 1).coerceAtLeast(0).toFloat() / 4f) * 0.5f)
                .coerceIn(0f, 1f)

        // 8) 平静放松: 各 AU 低 + 焦虑低 + 微表情少 + 眨眼正常
        s[Kind.CALM_GROUNDED.name] =
            ((1f - au4Avg) * 0.25f + calmFactor * 0.4f +
             (1f - blinkAvg) * 0.15f + (1f - leakScore) * 0.2f).coerceIn(0f, 1f)

        // 选最高 + 归一化
        val topEntry = s.maxByOrNull { it.value }
        val topKey = topEntry?.key ?: Kind.CALM_GROUNDED.name
        val topVal = topEntry?.value ?: 0f
        val sum = s.values.sum().coerceAtLeast(1e-6f)
        val kind = Kind.fromKey(topKey)
        val norm = (topVal / sum).coerceIn(0f, 1f)

        val traj = when {
            anxDelta > 0.2f -> "焦虑近 ${(anxDelta * 100f).toInt()}% 上升中"
            anxDelta < -0.2f -> "焦虑近 ${abs(anxDelta * 100f).toInt()}% 缓解中"
            else -> "焦虑趋于平稳"
        }

        return Projection(
            kind = kind,
            label = kind.label,
            confidence = topVal,
            normalizedConfidence = norm,
            scores = s,
            summary = summaryFor(kind, anxNow, microSurgePerFrame),
            advice = adviceFor(kind),
            trajectoryNote = traj,
        )
    }

    private fun summaryFor(kind: Kind, anxiety: Float, leak: Float): String = when (kind) {
        Kind.ANXIOUS_ANTICIPATION ->
            "你的微表情显示持续的紧张皱眉与唇部收紧, 像是在预期某种不太好的结果。"
        Kind.SUPPRESSED_EMOTION ->
            "我捕捉到几次短暂的微表情泄露, 但你表面看起来平静 — 也许有情绪还没表达出来。"
        Kind.COGNITIVE_OVERLOAD ->
            "眨眼加快、眉头紧锁, 像是脑子里在同时处理很多事, 信息有点过载。"
        Kind.POSITIVE_ANTICIPATION ->
            "嘴角轻微上扬、整体放松, 看起来你在期待一件还不错的事。"
        Kind.DOUBT ->
            "眉头轻蹙、嘴角微压, 像是对眼前的说法还有些不确定或保留。"
        Kind.AVOIDANT_DEFENSE ->
            "视线有些回避, 加上眉头收紧 — 像是下意识想躲开当前的话题或情境。"
        Kind.VOLATILITY ->
            "面部情绪切换比较频繁, 内心似乎正在几股情绪之间拉扯。"
        Kind.CALM_GROUNDED ->
            "整体面部放松、呼吸平稳, 现在的状态是稳的。"
    }

    private fun adviceFor(kind: Kind): String = when (kind) {
        Kind.ANXIOUS_ANTICIPATION -> "帮用户把'最坏结果'拆成可验证的小假设, 降低不确定性"
        Kind.SUPPRESSED_EMOTION -> "温和邀请表达: '有没有什么想说说看?' — 不逼问"
        Kind.COGNITIVE_OVERLOAD -> "建议先暂停输入, 做 4-7-8 呼吸 / 写下脑中盘旋的三件事"
        Kind.POSITIVE_ANTICIPATION -> "顺势强化积极预期, 把期待具体化为一个小行动"
        Kind.DOUBT -> "邀请用户说出疑点, 用证据检验而非否定"
        Kind.AVOIDANT_DEFENSE -> "降低话题压力, 先共情再决定是否继续, 不强行推进"
        Kind.VOLATILITY -> "命名情绪, 帮用户识别正在拉扯的几种感受分别是什么"
        Kind.CALM_GROUNDED -> "保持陪伴, 可进入成长回顾或轻量目标设定"
    }
}
