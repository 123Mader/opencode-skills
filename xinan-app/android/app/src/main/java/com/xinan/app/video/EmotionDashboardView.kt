package com.xinan.app.video

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import com.xinan.app.vision.PsychologicalProjection

/**
 * 情绪仪表盘覆盖层 — 实时显示情绪/焦虑指数/微表情/★心理预期
 * 叠加在摄像头预览上, 用户直观看到自己当前的心理状态预期。
 */
class EmotionDashboardView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs) {

    private val bgPaint = Paint().apply {
        color = Color.parseColor("#CC0E1718")   // 深薄荷半透明卡片
        isAntiAlias = true
    }
    private val titlePaint = Paint().apply {
        color = Color.WHITE; textSize = 38f; isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE; textSize = 30f; isAntiAlias = true
    }
    private val smallTextPaint = Paint().apply {
        color = Color.parseColor("#CFFFFFFF"); textSize = 24f; isAntiAlias = true
    }
    private val accentPaint = Paint().apply {
        color = Color.parseColor("#7BBDB8"); textSize = 26f; isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD
    }

    private val barBgPaint = Paint().apply { color = Color.argb(60, 255, 255, 255); isAntiAlias = true }
    private val barPaint = Paint().apply { color = Color.rgb(76, 175, 80); isAntiAlias = true }
    private val barFrame = RectF()

    private var anxiety = 0
    private var emotion = "平静"
    private var microExpr = emptyList<String>()
    private var projection: PsychologicalProjection.Projection? = null  // ★ 心理预期

    fun update(emotion: String, anxiety: Int, microExpr: List<String>) {
        this.emotion = emotion; this.anxiety = anxiety; this.microExpr = microExpr
        barPaint.color = when {
            anxiety < 30 -> Color.rgb(76, 175, 80)
            anxiety < 60 -> Color.rgb(255, 193, 7)
            else -> Color.rgb(244, 67, 54)
        }
        invalidate()
    }

    /** ★ 设置心理预期读数 */
    fun updateProjection(p: PsychologicalProjection.Projection?) {
        this.projection = p
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        canvas.drawRoundRect(RectF(12f, 12f, w - 12f, h - 12f), 28f, 28f, bgPaint)

        // 心情 emoji + 焦虑
        val emoji = when (emotion) {
            "快乐" -> "😊"; "焦虑" -> "😰"; "紧张" -> "😣"
            "悲伤" -> "😢"; "愤怒" -> "😠"; else -> "😌"
        }
        canvas.drawText("$emoji $emotion", 36f, 70f, titlePaint)

        // 焦虑指数条
        val barLeft = 36f; val barRight = w - 36f; val barTop = 96f; val barBottom = 120f
        barFrame.set(barLeft, barTop, barRight, barBottom)
        canvas.drawRoundRect(barFrame, 12f, 12f, barBgPaint)
        val fillW = (barRight - barLeft) * anxiety / 100f
        canvas.drawRoundRect(RectF(barLeft, barTop, barLeft + fillW, barBottom), 12f, 12f, barPaint)
        canvas.drawText("焦虑指数 $anxiety", barLeft, 152f, smallTextPaint)

        // ★ 心理预期区块
        var y = 190f
        accentPaint.color = Color.parseColor("#9AD0CB")
        canvas.drawText("🧠 心理预期", barLeft, y, accentPaint); y += 34f

        val p = projection
        if (p != null) {
            canvas.drawText("▸ ${p.label}  (${(p.normalizedConfidence * 100).toInt()}%)", barLeft, y, textPaint); y += 32f
            // 一句话(自动换行, 简单截断 2 行)
            y = drawWrapped(canvas, p.summary, barLeft, y, w - barLeft - 36f, smallTextPaint, 2)
            canvas.drawText("📈 ${p.trajectoryNote}", barLeft, y, smallTextPaint); y += 30f
        } else {
            canvas.drawText("采集微表情中…", barLeft, y, smallTextPaint); y += 30f
        }

        // 微表情提示 (顶部 2 条)
        if (microExpr.isNotEmpty()) {
            for (expr in microExpr.take(2)) {
                canvas.drawText("📌 $expr", barLeft, y, smallTextPaint); y += 28f
            }
        }
    }

    /** 简单中文换行(按字符宽度估算) */
    private fun drawWrapped(c: Canvas, text: String, x: Float, y: Float, maxW: Float, p: Paint, maxLines: Int): Float {
        var cy = y; var line = StringBuilder()
        var remain = maxLines
        for (ch in text) {
            line.append(ch)
            if (p.measureText(line.toString()) > maxW) {
                c.drawText(line.toString(), x, cy, p); cy += p.textSize + 2f
                line = StringBuilder(ch.toString())
                if (--remain <= 0) return cy
            }
        }
        if (line.isNotEmpty()) { c.drawText(line.toString(), x, cy, p); cy += p.textSize + 2f }
        return cy
    }
}
