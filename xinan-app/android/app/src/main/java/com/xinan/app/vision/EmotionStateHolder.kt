package com.xinan.app.vision

/**
 * 全局情绪/心理预期状态持有者
 *
 * 视频模式(VideoFragment)实时写入, 聊天模式(ChatFragment)读取,
 * 实现"通过视频聊天采集微表情 → 在对话中给出心理预期"的跨模式联动。
 *
 * 最近一次写入为准; 切换模式不丢失(VideoFragment 被 replace 后状态仍在此处)。
 */
object EmotionStateHolder {

    var projection: PsychologicalProjection.Projection? = null
        private set
    var emotion: String = "平静"
        private set
    var anxiety: Int = 0
        private set
    var updatedAt: Long = 0
        private set

    fun update(emotion: String, anxiety: Int, projection: PsychologicalProjection.Projection?) {
        this.emotion = emotion
        this.anxiety = anxiety
        this.projection = projection
        this.updatedAt = System.currentTimeMillis()
    }

    /** 是否有足够"新鲜"的观察 (5 分钟内) */
    fun isFresh(): Boolean =
        updatedAt > 0 && System.currentTimeMillis() - updatedAt < 5 * 60_000

    /** 生成喂给 LLM 的视觉上下文字符串 (含心理预期) */
    fun formatVisualContext(): String? {
        if (!isFresh()) return null
        val p = projection
        return buildString {
            append("情绪:$emotion; 焦虑指数:$anxiety")
            if (p != null) {
                append("; 心理预期:${p.label}(${(p.normalizedConfidence * 100).toInt()}%)")
                append("; 观察:${p.summary}")
                append("; 焦虑轨迹:${p.trajectoryNote}")
                append("; 疏导方向:${p.advice}")
            }
        }
    }

    fun reset() {
        projection = null; emotion = "平静"; anxiety = 0; updatedAt = 0
    }
}
