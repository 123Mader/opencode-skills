package com.xinan.app.relax

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import androidx.core.animation.doOnEnd
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * 正念呼吸引导圆 — 4-7-8 呼吸法
 * 吸气4秒(圆扩大) → 屏息7秒(保持) → 呼气8秒(圆缩小)
 * 引导用户放松, 降低焦虑
 */
class BreathingCircleView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs) {

    companion object {
        private const val INHALE_MS = 4000L   // 吸气4秒
        private const val HOLD_MS = 7000L      // 屏息7秒
        private const val EXHALE_MS = 8000L    // 呼气8秒
    }

    private val circlePaint = Paint().apply {
        color = Color.rgb(100, 181, 246)
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 44f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    private val hintPaint = Paint().apply {
        color = Color.WHITE
        textSize = 26f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    private var circleRadius = 100f
    private var minRadius = 120f
    private var maxRadius = 320f
    private var phaseText = "吸气"
    private var animator: ValueAnimator? = null

    var onPhaseChange: ((String) -> Unit)? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        minRadius = (width * 0.15f).coerceAtLeast(80f)
        maxRadius = (width * 0.4f).coerceAtLeast(200f)
    }

    /** 开始呼吸引导 */
    fun start() {
        animatePhase(INHALE_MS, "吸气", minRadius, maxRadius) {
            animatePhase(HOLD_MS, "屏息", maxRadius, maxRadius) {
                animatePhase(EXHALE_MS, "呼气", maxRadius, minRadius) {
                    start()  // 循环
                }
            }
        }
    }

    private fun animatePhase(duration: Long, text: String, from: Float, to: Float, done: () -> Unit) {
        phaseText = text
        onPhaseChange?.invoke(text)
        animator?.cancel()
        animator = ValueAnimator.ofFloat(from, to).apply {
            this.duration = duration
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                circleRadius = it.animatedValue as Float
                invalidate()
            }
            doOnEnd { done() }
            start()
        }
    }

    /** 停止引导 */
    fun stop() {
        animator?.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f

        // 呼吸圆
        canvas.drawCircle(cx, cy, circleRadius, circlePaint)

        // 阶段文字 (吸气/屏息/呼气)
        canvas.drawText(phaseText, cx, cy - 10f, textPaint)

        // 引导语
        val hint = when (phaseText) {
            "吸气" -> "慢慢吸气... 感受空气进入"
            "屏息" -> "轻轻屏住... 保持放松"
            else -> "缓缓呼气... 释放紧张"
        }
        canvas.drawText(hint, cx, cy + 60f, hintPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stop()
    }
}