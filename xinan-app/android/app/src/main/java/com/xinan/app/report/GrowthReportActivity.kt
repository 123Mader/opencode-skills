package com.xinan.app.report

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.xinan.app.data.MemoryRepository
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * 成长报告界面 — 情绪趋势图表 + 分析建议
 * 入口: 主界面"成长报告"按钮
 */
class GrowthReportActivity : AppCompatActivity() {

    private lateinit var chartView: TrendChartView
    private lateinit var summaryText: TextView
    private lateinit var memory: MemoryRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        memory = MemoryRepository(this)

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 32, 24, 32)
            setBackgroundColor(0xFF263238.toInt())
        }

        val title = TextView(this).apply {
            text = "📈 我的成长报告"
            textSize = 28f
            setTextColor(0xFFFFFFFF.toInt())
        }

        chartView = TrendChartView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 480)
        }

        summaryText = TextView(this).apply {
            textSize = 16f
            setTextColor(0xFFB0BEC5.toInt())
            setPadding(0, 24, 0, 0)
        }

        val backBtn = android.widget.Button(this).apply {
            text = "返回"
            setOnClickListener { finish() }
        }

        root.addView(title)
        root.addView(chartView)
        root.addView(summaryText)
        root.addView(backBtn)
        scroll.addView(root)
        setContentView(scroll)

        loadReport()
    }

    /** 加载近30天情绪数据并显示 */
    private fun loadReport() {
        GlobalScope.launch {
            // 读取近30天日志 (简化: 从数据库按天聚合)
            val since = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
            val logs = com.xinan.app.data.XinanDatabase.get(this@GrowthReportActivity)
                .emotionDao().getRecent(since)

            // 按天聚合平均焦虑
            val dayMap = linkedMapOf<String, MutableList<Int>>()
            val fmt = java.text.SimpleDateFormat("MM-dd", java.util.Locale.getDefault())
            for (log in logs) {
                val day = fmt.format(java.util.Date(log.timestamp))
                dayMap.getOrPut(day) { mutableListOf() }.add(log.anxietyScore)
            }

            val days = dayMap.keys.toList()
            val avgValues = dayMap.values.map { list -> list.average().toInt() }

            val report = memory.growthReport()
            runOnUiThread {
                chartView.setData(avgValues, days)
                summaryText.text = report
            }
        }
    }
}