package com.xinan.app.llm

import android.content.Context
import java.io.File

/**
 * 大模型管理器 — 支持用户自由添加/切换 GGUF 模型
 *
 * 模型存放: getExternalFilesDir(null)/models/
 * 每个模型: {name, fileName, size, description, loaded}
 */
data class LlmModel(
    val name: String,          // 显示名 (如 "Qwen3-3B")
    val fileName: String,      // GGUF 文件名
    val description: String,
    val recommended: Boolean = false,
)

class ModelManager(private val context: Context) {

    companion object {
        // 内置推荐模型 (可扩展下载源)
        val RECOMMENDED_MODELS = listOf(
            LlmModel("Qwen3-3B (推荐)", "qwen3-3b-q4.gguf", "通义千问3B INT4, 中文心理对话强, 手机流畅", recommended = true),
            LlmModel("GLM-4-3B", "glm4-3b-q4.gguf", "智谱GLM 3B INT4, 共情能力强"),
            LlmModel("DeepSeek-R1-7B", "deepseek-r1-7b-q4.gguf", "深度思考7B INT4, 较慢但分析深"),
            LlmModel("Qwen3-1.5B", "qwen3-1.5b-q4.gguf", "轻量1.5B, 极快, 低端机可用"),
        )
    }

    val modelsDir: File
        get() = File(context.getExternalFilesDir(null), "models").apply { mkdirs() }

    /** 列出已下载的模型 */
    fun listDownloaded(): List<LlmModel> {
        return RECOMMENDED_MODELS.filter { File(modelsDir, it.fileName).exists() }
    }

    /** 模型是否已下载 */
    fun isDownloaded(model: LlmModel): Boolean =
        File(modelsDir, model.fileName).exists()

    /** 获取模型完整路径 */
    fun modelPath(model: LlmModel): String =
        File(modelsDir, model.fileName).absolutePath

    /** 添加本地导入的 GGUF 模型 (用户放入 modelsDir 后扫描) */
    fun scanLocalModels(): List<LlmModel> {
        return modelsDir.listFiles { f -> f.extension == "gguf" }
            ?.map { f ->
                LlmModel(
                    name = f.nameWithoutExtension,
                    fileName = f.name,
                    description = "本地导入模型 (${f.length() / 1024 / 1024}MB)",
                )
            } ?: emptyList()
    }

    /** 模型下载 (TODO: 接入下载源) */
    fun downloadModel(model: LlmModel, progress: (Int) -> Unit, done: (Boolean) -> Unit) {
        // TODO: 从可靠源下载 GGUF (国内可达镜像)
        // 示例 URL: https://huggingface.co/.../resolve/main/model-q4.gguf
        progress(0)
        done(false)
    }

    /** 释放模型占用的内存 */
    fun clearCache() {
        modelsDir.listFiles()?.filter { it.extension == "gguf.tmp" }?.forEach { it.delete() }
    }
}