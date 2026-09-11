package com.xinan.app.llm

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 模型下载器 — 从国内可达源下载 GGUF 模型
 * 支持断点续传 + 进度回调
 */
class ModelDownloader(private val context: Context) {

    /**
     * 下载 GGUF 模型到 models 目录
     * @param url 模型 URL (建议国内可达镜像)
     * @param fileName 保存的文件名
     * @param onProgress 进度 0-100
     * @param onDone 完成回调 (成功/失败)
     */
    fun download(url: String, fileName: String, onProgress: (Int) -> Unit, onDone: (Boolean, String?) -> Unit) {
        Thread {
            val dir = File(context.getExternalFilesDir(null), "models").apply { mkdirs() }
            val target = File(dir, fileName)
            val tmp = File(dir, fileName + ".tmp")

            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.setRequestProperty("User-Agent", "xinan-app")
                conn.connect()

                if (conn.responseCode != 200) {
                    onDone(false, "HTTP ${conn.responseCode}")
                    return@Thread
                }

                // 断点续传: 已有临时文件则续传
                val existing = if (tmp.exists()) tmp.length() else 0L
                val total = conn.contentLength.toLong()

                conn.inputStream.use { input ->
                    FileOutputStream(tmp, existing > 0).use { output ->
                        if (existing > 0) input.skip(existing)
                        val buffer = ByteArray(64 * 1024)
                        var downloaded = existing
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (total > 0) {
                                onProgress((downloaded * 100 / total).toInt())
                            }
                        }
                    }
                }

                // 校验大小后重命名
                if (total > 0 && tmp.length() != total) {
                    onDone(false, "文件不完整: ${tmp.length()}/$total")
                } else {
                    tmp.renameTo(target)
                    onDone(true, target.absolutePath)
                }
            } catch (e: Exception) {
                onDone(false, e.message)
            }
        }.start()
    }

    companion object {
        // 国内可达的 GGUF 模型镜像源 (示例, 需确认可用)
        val MIRRORS = mapOf(
            "qwen3-3b-q4.gguf" to "https://hf-mirror.com/Qwen/Qwen3-3B-GGUF/resolve/main/qwen3-3b-q4.gguf",
            "qwen3-1.5b-q4.gguf" to "https://hf-mirror.com/Qwen/Qwen3-1.5B-GGUF/resolve/main/qwen3-1.5b-q4.gguf",
            "glm4-3b-q4.gguf" to "https://hf-mirror.com/THUDM/glm-4-9b-chat-gguf/resolve/main/glm-4-9b-chat-q4.gguf",
        )
    }
}