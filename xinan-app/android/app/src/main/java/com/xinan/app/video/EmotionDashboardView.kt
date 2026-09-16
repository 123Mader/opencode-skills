package com.xinan.app.video

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * 情绪仪表盘覆盖层 — 实时显示焦虑指数/心情/微表情
 * 叠加在摄像头预览上, 用户直观看到自己的情绪状态
 */
class EmotionDashboardView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs) {

    private val bgPaint = Paint().apply {
        color = Color.argb(120, 0, 0, 0)
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 42f
        isAntiAlias = true
    }
    private val smallTextPaint = Paint().apply {
        color = Color.WHITE
        textSize = 28f
        isAntiAlias = true
    }

    // 焦虑指数条
    private val barBgPaint = Paint().apply { color = Color.argb(80, 255, 255, 255) }
    private val barPaint = Paint().apply { color = Color.rgb(76, 175, 80) }  // 绿色(平静)
    private val barFrame = RectF()

    // 状态
    private var anxiety = 0          // 0-100
    private var emotion = "平静"
    private var microExpr = emptyList<String>()

    fun update(emotion: String, anxiety: Int, microExpr: List<String>) {
        this.emotion = emotion
        this.anxiety = anxiety
        this.microExpr = microExpr
        // 焦虑越高颜色越红
        barPaint.color = when {
            anxiety < 30 -> Color.rgb(76, 175, 80)    // 绿
            anxiety < 60 -> Color.rgb(255, 193, 7)    // 黄
            else -> Color.rgb(244, 67, 54)            // 红
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        // 背景圆角卡片
        canvas.drawRoundRect(RectF(16f, 16f, w - 16f, h - 16f), 24f, 24f, bgPaint)

        // 心情 emoji + 文字
        val emoji = when (emotion) {
            "快乐" -> "😊"; "焦虑" -> "😰"; "紧张" -> "😣"
            "悲伤" -> "😢"; "愤怒" -> "😠"; else -> "😌"
        }
        canvas.drawText("$emoji $emotion", 48f, 88f, textPaint)

        // 焦虑指数条
        val barLeft = 48f
        val barRight = w - 48f
        val barTop = 120f
        val barBottom = 144f
        barFrame.set(barLeft, barTop, barRight, barBottom)
        canvas.drawRoundRect(barFrame, 12f, 12f, barBgPaint)

        // 焦虑填充
        val fillW = (barRight - barLeft) * anxiety / 100f
        canvas.drawRoundRect(RectF(barLeft, barTop, barLeft + fillW, barBottom), 12f, 12f, barPaint)

        // 焦虑数值
        canvas.drawText("焦虑 $anxiety", barLeft, 178f, smallTextPaint)

        // 微表情提示
        var y = 220f
        for (expr in microExpr.take(2)) {
            canvas.drawText("📌 $expr", barLeft, y, smallTextPaint)
            y += 40f
        }
        if (microExpr.isEmpty()) {
            canvas.drawText("面部平静, 放松中...", barLeft, y, smallTextPaint)
        }
    }
}

