package com.xinan.app.llm

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * 模型管理界面 — 查看/下载/切换大模型
 * 支持: 推荐模型列表 + 下载进度 + 本地导入
 */
class ModelManageActivity : AppCompatActivity() {

    private val manager by lazy { ModelManager(this) }
    private val downloader by lazy { ModelDownloader(this) }
    private lateinit var listContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 32, 24, 32)
        }

        val title = TextView(this).apply {
            text = "🧠 大模型管理"
            textSize = 26f
        }

        val subtitle = TextView(this).apply {
            text = "选择或下载模型, 当前手机: 一加 12GB (推荐3B模型)\n模型存放: Android/data/com.xinan.app/files/models/"
            textSize = 14f
            setTextColor(0xFF666666.toInt())
        }

        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val refreshBtn = Button(this).apply {
            text = "刷新模型列表"
            setOnClickListener { renderModels() }
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(listContainer)
        root.addView(refreshBtn)
        setContentView(root)

        renderModels()
    }

    /** 渲染模型列表 (已下载 + 可下载) */
    private fun renderModels() {
        listContainer.removeAllViews()
        val downloaded = manager.listDownloaded()
        val local = manager.scanLocalModels()
        val all = (downloaded + local + ModelManager.RECOMMENDED_MODELS)
            .distinctBy { it.fileName }

        for (model in all) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 12, 0, 12)
            }

            val info = TextView(this).apply {
                text = "${model.name}\n  ${model.description}"
                textSize = 15f
            }
            val btn = Button(this).apply {
                text = if (manager.isDownloaded(model)) "已下载 ✓" else "下载"
                setOnClickListener {
                    if (!manager.isDownloaded(model)) {
                        startDownload(model)
                    } else {
                        Toast.makeText(this@ModelManageActivity, "模型已在本地, 可加载使用", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            row.addView(info, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(btn)
            listContainer.addView(row)
        }
    }

    /** 下载模型 */
    private fun startDownload(model: LlmModel) {
        val mirrorUrl = ModelDownloader.MIRRORS[model.fileName]
        if (mirrorUrl == null) {
            Toast.makeText(this, "暂无该模型的下载源, 请将GGUF放入 models/ 目录", Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, "开始下载 ${model.name}...", Toast.LENGTH_SHORT).show()

        downloader.download(mirrorUrl, model.fileName,
            onProgress = { p ->
                runOnUiThread {
                    Toast.makeText(this, "下载中 $p%", Toast.LENGTH_SHORT).show()
                }
            },
            onDone = { ok, msg ->
                runOnUiThread {
                    if (ok) {
                        Toast.makeText(this, "✅ 下载完成: ${model.name}", Toast.LENGTH_LONG).show()
                        renderModels()
                    } else {
                        Toast.makeText(this, "❌ 下载失败: $msg", Toast.LENGTH_LONG).show()
                    }
                }
            })
    }
}