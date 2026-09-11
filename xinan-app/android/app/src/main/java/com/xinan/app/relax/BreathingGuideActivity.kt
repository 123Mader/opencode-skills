package com.xinan.app.relax

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

/**
 * 正念呼吸引导界面 — 4-7-8 呼吸法
 * 在检测到高焦虑时启动, 帮助用户平复情绪
 */
class BreathingGuideActivity : AppCompatActivity() {

    private lateinit var breathingView: BreathingCircleView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 48, 24, 48)
            setBackgroundColor(0xFF263238.toInt())
        }

        val title = android.widget.TextView(this).apply {
            text = "🌿 正念呼吸"
            textSize = 30f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = android.view.Gravity.CENTER
        }

        val subtitle = android.widget.TextView(this).apply {
            text = "跟着圆圈节奏: 吸气4秒 · 屏息7秒 · 呼气8秒\n让焦虑慢慢消散..."
            textSize = 16f
            setTextColor(0xFFB0BEC5.toInt())
            gravity = android.view.Gravity.CENTER
        }

        breathingView = BreathingCircleView(this)

        val closeBtn = Button(this).apply {
            text = "我放松一些了 ✨"
            setOnClickListener { finish() }
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(breathingView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(closeBtn)
        setContentView(root)

        // 自动开始呼吸引导
        breathingView.postDelayed({ breathingView.start() }, 500)
    }

    override fun onDestroy() {
        super.onDestroy()
        breathingView.stop()
    }
}