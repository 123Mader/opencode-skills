package com.xinan.app.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

/**
 * 情绪趋势折线图 — 显示近N天焦虑指数变化
 * 零依赖自定义 View (避免引入图表库)
 */
class TrendChartView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs) {

    private val gridPaint = Paint().apply {
        color = Color.argb(40, 255, 255, 255)
        strokeWidth = 1f
    }
    private val linePaint = Paint().apply {
        color = Color.rgb(100, 181, 246)
        strokeWidth = 5f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    private val fillPaint = Paint().apply {
        color = Color.argb(60, 100, 181, 246)
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val dotPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val labelPaint = Paint().apply {
        color = Color.WHITE
        textSize = 22f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    private var data = floatArrayOf()   // 焦虑指数序列 (0-100)
    private var labels = listOf<String>() // 日期标签

    /** 设置数据并重绘 */
    fun setData(values: List<Int>, dateLabels: List<String>) {
        data = values.map { it.coerceIn(0, 100).toFloat() }.toFloatArray()
        labels = dateLabels
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) {
            canvas.drawText("暂无足够数据, 坚持使用后会生成你的情绪曲线", width / 2f, height / 2f, labelPaint)
            return
        }

        val left = 40f
        val right = width - 40f
        val top = 40f
        val bottom = height - 60f
        val chartW = right - left
        val chartH = bottom - top

        // 网格 (3条水平线: 100/50/0)
        for (i in 0..2) {
            val y = top + chartH * i / 2f
            canvas.drawLine(left, y, right, y, gridPaint)
        }

        // 数据点坐标
        val stepX = if (data.size > 1) chartW / (data.size - 1) else 0f
        val points = data.mapIndexed { idx, v ->
            android.graphics.PointF(left + idx * stepX, bottom - chartH * v / 100f)
        }

        // 填充渐变区域
        if (points.size > 1) {
            val fillPath = Path().apply {
                moveTo(points[0].x, bottom)
                for (p in points) lineTo(p.x, p.y)
                lineTo(points.last().x, bottom)
                close()
            }
            canvas.drawPath(fillPath, fillPaint)
        }

        // 折线
        if (points.size > 1) {
            val linePath = Path().apply {
                moveTo(points[0].x, points[0].y)
                for (p in points.drop(1)) lineTo(p.x, p.y)
            }
            canvas.drawPath(linePath, linePaint)
        }

        // 数据点 + 关键标签
        points.forEachIndexed { idx, p ->
            canvas.drawCircle(p.x, p.y, 6f, dotPaint)
        }
        // 首尾日期标签
        if (labels.isNotEmpty()) {
            canvas.drawText(labels.first(), left, height - 20f, labelPaint)
            if (labels.size > 1) canvas.drawText(labels.last(), right, height - 20f, labelPaint)
        }

        // 焦虑参考线标注
        canvas.drawText("100", 12f, top + 12f, labelPaint)
        canvas.drawText("50", 12f, top + chartH / 2f + 12f, labelPaint)
        canvas.drawText("0", 12f, bottom + 12f, labelPaint)
    }
}