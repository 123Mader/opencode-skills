// ============================================================
// 「心安」微表情分析引擎 (核心! 视觉微表情心理学)
// FaceMesh 468点 → FACS动作单元(AU) → 时序微表情检测 → 情绪/焦虑
// ============================================================
// v0.3 升级:
//   • 新增头部偏航(head yaw)检测 → 喂给心理预期引擎做"视线回避"判定
//   • AnalysisResult 扩展 headYawDeg / blinkRate / anxietyDelta
//   • 实现 blinkRate 滑窗(修除零) + 个人基线校准(原 TODO)
package com.xinan.app.vision

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.sqrt

/**
 * 微表情分析器
 * 流程: 每帧FaceMesh → 计算AU强度 → 滑窗检测微表情 → 情绪分类 + 头部姿态
 */
class MicroExpressionAnalyzer {

    companion object {
        // 眉毛 (皱眉 AU4)
        const val LEFT_EYEBROW_INNER = 46
        const val RIGHT_EYEBROW_INNER = 275
        // 眼睛
        const val LEFT_EYE_TOP = 159
        const val LEFT_EYE_BOTTOM = 145
        const val RIGHT_EYE_TOP = 386
        const val RIGHT_EYE_BOTTOM = 374
        // 嘴
        const val MOUTH_LEFT = 61
        const val MOUTH_RIGHT = 291
        const val MOUTH_TOP = 13
        const val MOUTH_BOTTOM = 14
        // 头部姿态 (鼻尖 + 左右脸颊 → 偏航)
        const val NOSE_TIP = 1
        const val LEFT_CHEEK = 234
        const val RIGHT_CHEEK = 454
        const val CHIN = 152

        // 焦虑微表情的 AU 权重 (FACS 心理学依据)
        val ANXIETY_AU_WEIGHTS = mapOf(
            "AU4" to 1.0f, "AU23" to 1.0f, "AU45" to 0.8f,
            "AU15" to 0.7f, "AU17" to 0.6f,
        )
    }

    // 帧间状态
    private var prevEyeOpen = 0f
    private var blinkCount = 0
    private var frameCount = 0
    private val auHistory = ArrayDeque<Map<String, Float>>()
    private val WINDOW_SIZE = 30            // 1 秒滑窗 (30fps)
    private val blinkWindow = ArrayDeque<Boolean>()  // 2 秒眨眼滑窗
    private val BLINK_WINDOW_SIZE = 60

    // 个人基线 (calibrateBaseline 填充, 默认经验值)
    private var baseBrow = 0.06f
    private var baseMouthGapRatio = 0.3f
    private var baseChinOffset = 0.06f
    private var calibrated = false

    data class AnalysisResult(
        val emotion: String,
        val anxietyScore: Int,
        val microExpressions: List<String>,
        val auValues: Map<String, Float>,
        val confidence: Float,
        val headYawDeg: Float = 0f,          // ★ 头部偏航 (°), 正=右转
        val blinkRate: Float = 0f,           // ★ 归一化眨眼率 0..1
        val anxietyDelta: Float = 0f,        // ★ 近窗焦虑变化 (-100..100)
    )

    /**
     * 每帧调用 (30fps)
     */
    fun analyzeFrame(landmarks: List<FloatArray>): AnalysisResult? {
        frameCount++

        val au = calcAUs(landmarks)
        au["headYaw"] = headYawDeg(landmarks)

        // 眨眼检测 (AU45): 眼睛开合骤降到极低
        val eyeOpen = (dist(landmarks[LEFT_EYE_TOP], landmarks[LEFT_EYE_BOTTOM]) +
                dist(landmarks[RIGHT_EYE_TOP], landmarks[RIGHT_EYE_BOTTOM])) / 2f
        if (prevEyeOpen > 0.04f && eyeOpen < 0.015f) blinkCount++
        prevEyeOpen = eyeOpen

        // 2 秒滑窗眨眼率 (修原版除零: 用固定窗而非 frameCount/30)
        blinkWindow.addLast(eyeOpen < 0.015f)
        if (blinkWindow.size > BLINK_WINDOW_SIZE) blinkWindow.removeFirst()
        val blinksIn2s = blinkWindow.count { it }
        val blinkPerMin = blinksIn2s * 30f / 2f          // 次/分钟
        val au45 = (blinkPerMin / 35f).coerceIn(0f, 1f)  // 35 次/分 饱和
        au["AU45"] = au45

        auHistory.addLast(au)
        if (auHistory.size > WINDOW_SIZE) auHistory.removeFirst()

        // 每 0.5 秒(15 帧)输出一次完整分析
        if (frameCount % 15 == 0 && auHistory.size >= 15) {
            return analyzeWindow(auHistory.toList())
        }
        return null
    }

    /** 计算 FACS 动作单元强度 (相对个人基线) */
    private fun calcAUs(lm: List<FloatArray>): MutableMap<String, Float> {
        val au = mutableMapOf<String, Float>()

        // AU4 皱眉: 眉毛内端到眼睛距离相对基线变小
        val browL = dist(lm[LEFT_EYEBROW_INNER], lm[LEFT_EYE_TOP])
        val browR = dist(lm[RIGHT_EYEBROW_INNER], lm[RIGHT_EYE_TOP])
        au["AU4"] = ((baseBrow - (browL + browR) / 2f) / baseBrow).coerceIn(0f, 1f)

        // AU23 嘴唇收紧: 唇间距/唇宽 相对基线缩小
        val mouthGap = dist(lm[MOUTH_TOP], lm[MOUTH_BOTTOM])
        val mouthWidth = (dist(lm[MOUTH_LEFT], lm[MOUTH_RIGHT])).coerceAtLeast(1e-6f)
        au["AU23"] = (1f - (mouthGap / mouthWidth / baseMouthGapRatio)).coerceIn(0f, 1f)

        // AU15 嘴角下压 / AU12 上扬 (相对鼻尖纵坐标)
        val cornerMidY = (lm[MOUTH_LEFT][1] + lm[MOUTH_RIGHT][1]) / 2f
        val noseY = lm[NOSE_TIP][1]
        au["AU15"] = ((cornerMidY - noseY) / 0.08f).coerceIn(0f, 1f)
        au["AU12"] = ((noseY - cornerMidY) / 0.08f).coerceIn(0f, 1f)

        // AU17 下巴上提 (紧张)
        val chinY = lm[CHIN][1]
        au["AU17"] = ((baseChinOffset - (noseY - chinY)) / 0.03f).coerceIn(0f, 1f)

        au["AU45"] = 0f // 由眨眼检测填充
        return au
    }

    /** 头部偏航 (°): 鼻尖相对左右脸颊的水平偏移 → 反推转头角度 */
    private fun headYawDeg(lm: List<FloatArray>): Float {
        val dLeft = dist(lm[NOSE_TIP], lm[LEFT_CHEEK])
        val dRight = dist(lm[NOSE_TIP], lm[RIGHT_CHEEK])
        val sum = (dLeft + dRight).coerceAtLeast(1e-6f)
        val ratio = ((dRight - dLeft) / sum).coerceIn(-1f, 1f)
        // 经验放大系数: 归一化坐标差 → 实际角度近似
        return Math.toDegrees(asin(ratio.toDouble())).toFloat()
    }

    /** 滑窗分析: 微表情突变检测 + 综合情绪 */
    private fun analyzeWindow(window: List<Map<String, Float>>): AnalysisResult {
        val microExprs = mutableListOf<String>()
        for (key in listOf("AU4", "AU23", "AU15")) {
            val vals = window.map { it[key] ?: 0f }
            val surge = (vals.max() - vals.min())
            if (surge > 0.4f) microExprs.add("$key-突变(${(surge * 100).toInt()}%)")
        }

        val avgAu = mutableMapOf<String, Float>()
        for (key in listOf("AU4", "AU23", "AU45", "AU15", "AU12", "AU17", "headYaw")) {
            avgAu[key] = window.map { it[key] ?: 0f }.average().toFloat()
        }

        // 焦虑指数 (FACS 加权)
        var anxiety = 0f
        anxiety += avgAu.getValue("AU4") * 25f
        anxiety += avgAu.getValue("AU23") * 20f
        anxiety += avgAu.getValue("AU45") * 15f
        anxiety += avgAu.getValue("AU15") * 15f
        anxiety += avgAu.getValue("AU17") * 10f
        anxiety -= avgAu.getValue("AU12") * 30f   // 微笑抵消
        val anxietyScore = anxiety.coerceIn(0f, 100f).toInt()

        val emotion = when {
            avgAu.getValue("AU12") > 0.35f && anxietyScore < 30 -> "快乐"
            anxietyScore >= 60 -> "焦虑"
            anxietyScore >= 40 -> "紧张"
            avgAu.getValue("AU15") > 0.4f -> "悲伤"
            else -> "平静"
        }

        // 焦虑轨迹: 前 1/3 vs 后 1/3
        val third = (window.size / 3).coerceAtLeast(1)
        val earlyAvg = window.take(third).map { it["AU4"] ?: 0f }.average().toFloat()
        val lateAvg = window.takeLast(third).map { it["AU4"] ?: 0f }.average().toFloat()
        val anxietyDelta = ((lateAvg - earlyAvg) * 100f).coerceIn(-100f, 100f)

        return AnalysisResult(
            emotion = emotion,
            anxietyScore = anxietyScore,
            microExpressions = microExprs,
            auValues = avgAu,
            confidence = 0.7f + (microExprs.size * 0.1f).coerceAtMost(0.25f),
            headYawDeg = avgAu.getValue("headYaw"),
            blinkRate = avgAu.getValue("AU45"),
            anxietyDelta = anxietyDelta,
        )
    }

    /**
     * 个人基线校准 (录 ~30 秒平静表情)
     * 用样本均值替换经验常数, 使后续 AU 计算为"相对本人平静态"的偏移。
     */
    fun calibrateBaseline(landmarksList: List<List<FloatArray>>) {
        if (landmarksList.isEmpty()) return
        val brows = ArrayList<Float>()
        val mouthRatios = ArrayList<Float>()
        val chinOffsets = ArrayList<Float>()
        for (lm in landmarksList) {
            brows.add((dist(lm[LEFT_EYEBROW_INNER], lm[LEFT_EYE_TOP]) +
                    dist(lm[RIGHT_EYEBROW_INNER], lm[RIGHT_EYE_TOP])) / 2f)
            val mg = dist(lm[MOUTH_TOP], lm[MOUTH_BOTTOM])
            val mw = (dist(lm[MOUTH_LEFT], lm[MOUTH_RIGHT])).coerceAtLeast(1e-6f)
            mouthRatios.add(mg / mw)
            chinOffsets.add(lm[NOSE_TIP][1] - lm[CHIN][1])
        }
        baseBrow = brows.average().toFloat().coerceAtLeast(1e-3f)
        baseMouthGapRatio = mouthRatios.average().toFloat().coerceAtLeast(1e-3f)
        baseChinOffset = chinOffsets.average().toFloat().coerceAtLeast(1e-3f)
        calibrated = true
    }

    fun isCalibrated(): Boolean = calibrated

    private fun dist(a: FloatArray, b: FloatArray): Float {
        val dx = a[0] - b[0]; val dy = a[1] - b[1]
        return sqrt(dx * dx + dy * dy)
    }
}
