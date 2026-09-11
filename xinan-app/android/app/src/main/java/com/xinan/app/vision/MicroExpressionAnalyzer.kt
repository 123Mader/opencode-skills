// ============================================================
// 「心安」微表情分析引擎 (核心! 视觉微表情心理学)
// FaceMesh 468点 → FACS动作单元(AU) → 时序微表情检测 → 情绪/焦虑
// ============================================================
package com.xinan.app.vision

import android.graphics.RectF
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

/**
 * 微表情分析器
 * 流程: 每帧FaceMesh → 计算AU强度 → 滑窗检测微表情 → 情绪分类
 */
class MicroExpressionAnalyzer {

    // FACS 关键AU的 Landmark 索引 (FaceMesh 468点标准)
    companion object {
        // 眉毛关键点 (皱眉 AU4)
        const val LEFT_EYEBROW_INNER = 46
        const val LEFT_EYEBROW_OUTER = 105
        const val RIGHT_EYEBROW_INNER = 275
        const val RIGHT_EYEBROW_OUTER = 334
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
        const val LIP_CORNER_L = 61
        const val LIP_CORNER_R = 291

        // 焦虑微表情的 AU 权重 (心理学 FACS 依据)
        val ANXIETY_AU_WEIGHTS = mapOf(
            "AU4" to 1.0f,   // 皱眉 (紧张)
            "AU23" to 1.0f,  // 嘴唇收紧
            "AU45" to 0.8f,  // 眨眼加快
            "AU15" to 0.7f,  // 嘴角下压
            "AU17" to 0.6f,  // 下巴上提
        )
    }

    // 上帧关键点 (用于计算帧间变化)
    private var prevEyeOpen = 0f
    private var blinkCount = 0
    private var frameCount = 0
    private val auHistory = ArrayDeque<Map<String, Float>>() // 1秒滑窗(30帧)
    private val WINDOW_SIZE = 30

    data class AnalysisResult(
        val emotion: String,        // 焦虑/平静/快乐/悲伤/愤怒/惊讶/厌恶
        val anxietyScore: Int,      // 焦虑指数 0-100
        val microExpressions: List<String>, // 检测到的微表情
        val auValues: Map<String, Float>,
        val confidence: Float,
    )

    /**
     * 每帧调用 (30fps)
     * @param landmarks FaceMesh 468个关键点
     */
    fun analyzeFrame(landmarks: List<FloatArray>): AnalysisResult? {
        frameCount++

        // 1. 计算 AU 强度 (基于关键点几何)
        val au = calcAUs(landmarks)

        // 2. 眨眼检测 (AU45)
        val eyeOpen = (dist(landmarks[LEFT_EYE_TOP], landmarks[LEFT_EYE_BOTTOM]) +
                dist(landmarks[RIGHT_EYE_TOP], landmarks[RIGHT_EYE_BOTTOM])) / 2f
        if (prevEyeOpen > 0.04f && eyeOpen < 0.015f) blinkCount++
        prevEyeOpen = eyeOpen
        au["AU45"] = blinkRate()

        // 3. 1秒滑窗记录
        auHistory.addLast(au)
        if (auHistory.size > WINDOW_SIZE) auHistory.removeFirst()

        // 4. 每0.5秒(15帧)分析一次
        if (frameCount % 15 == 0 && auHistory.size >= 15) {
            return analyzeWindow(auHistory.toList())
        }
        return null
    }

    /** 计算 FACS 动作单元强度 */
    private fun calcAUs(lm: List<FloatArray>): MutableMap<String, Float> {
        val au = mutableMapOf<String, Float>()

        // AU4 皱眉: 眉毛内端到眼睛距离变小 + 眉毛下压
        val browL = lm[LEFT_EYEBROW_INNER].let { dist(it, lm[LEFT_EYE_TOP]) }
        val browR = lm[RIGHT_EYEBROW_INNER].let { dist(it, lm[RIGHT_EYE_TOP]) }
        val baseBrow = 0.06f // 基线(可从个人校准)
        au["AU4"] = ((baseBrow - (browL + browR) / 2f) / baseBrow).coerceIn(0f, 1f)

        // AU23 嘴唇收紧: 唇间距缩小
        val mouthGap = dist(lm[MOUTH_TOP], lm[MOUTH_BOTTOM])
        val mouthWidth = dist(lm[MOUTH_LEFT], lm[MOUTH_RIGHT])
        au["AU23"] = (1f - (mouthGap / mouthWidth / 0.3f)).coerceIn(0f, 1f)

        // AU15 嘴角下压: 嘴角相对鼻梁下移
        val cornerMidY = (lm[MOUTH_LEFT][1] + lm[MOUTH_RIGHT][1]) / 2f
        val noseY = lm[1][1]
        au["AU15"] = ((cornerMidY - noseY) / 0.08f).coerceIn(0f, 1f)

        // AU12 微笑: 嘴角上扬
        au["AU12"] = ((noseY - cornerMidY) / 0.08f).coerceIn(0f, 1f)

        // AU17 下巴上提 (紧张)
        val chinY = lm[152][1]
        au["AU17"] = ((noseY - chinY - 0.06f) / 0.03f).coerceIn(0f, 1f)

        au["AU45"] = 0f // 由眨眼检测填充
        return au
    }

    /** 滑窗分析: 检测微表情 + 综合情绪 */
    private fun analyzeWindow(window: List<Map<String, Float>>): AnalysisResult {
        // 微表情: 检测帧间AU突变 (峰值幅度 × 持续时间)
        val microExprs = mutableListOf<String>()
        for (key in listOf("AU4", "AU23", "AU15")) {
            val vals = window.map { it[key] ?: 0f }
            val maxV = vals.max()
            val minV = vals.min()
            val surge = maxV - minV
            if (surge > 0.4f) microExprs.add("$key-突增(${(surge * 100).toInt()}%)")
        }

        // 综合 AU 均值
        val avgAu = mutableMapOf<String, Float>()
        for (key in listOf("AU4", "AU23", "AU45", "AU15", "AU12", "AU17")) {
            avgAu[key] = window.map { it[key] ?: 0f }.average().toFloat()
        }

        // 焦虑指数 (FACS 加权)
        var anxiety = 0f
        anxiety += avgAu["AU4"]!! * 25f
        anxiety += avgAu["AU23"]!! * 20f
        anxiety += avgAu["AU45"]!! * 15f
        anxiety += avgAu["AU15"]!! * 15f
        anxiety += avgAu["AU17"]!! * 10f
        // 微笑抵消
        anxiety -= avgAu["AU12"]!! * 30f
        val anxietyScore = anxiety.coerceIn(0f, 100f).toInt()

        // 情绪判定
        val emotion = when {
            avgAu["AU12"]!! > 0.35f && anxietyScore < 30 -> "快乐"
            anxietyScore >= 60 -> "焦虑"
            anxietyScore >= 40 -> "紧张"
            avgAu["AU15"]!! > 0.4f -> "悲伤"
            else -> "平静"
        }

        return AnalysisResult(
            emotion = emotion,
            anxietyScore = anxietyScore,
            microExpressions = microExprs,
            auValues = avgAu,
            confidence = 0.7f + (microExprs.size * 0.1f).coerceAtMost(0.25f),
        )
    }

    private fun blinkRate(): Float {
        val rate = blinkCount / (frameCount / 30f)
        // 正常 12-15次/分钟, 焦虑时翻倍
        return (rate / 30f).coerceIn(0f, 1f)
    }

    private fun dist(a: FloatArray, b: FloatArray): Float {
        val dx = a[0] - b[0]; val dy = a[1] - b[1]
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    /** 个人基线校准 (录30秒平静表情) */
    fun calibrateBaseline(landmarksList: List<List<FloatArray>>) {
        // TODO: 计算个人AU基线, 用于更精准的相对分析
    }
}