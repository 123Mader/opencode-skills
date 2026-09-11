package com.xinan.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 记忆仓储 — 情绪日志读写 + 成长分析
 */
class MemoryRepository(private val context: Context) {

    private val dao by lazy { XinanDatabase.get(context).emotionDao() }

    /** 记录情绪事件 (视频/聊天识别到情绪时调用) */
    suspend fun recordEmotion(
        mode: String,
        emotion: String,
        anxietyScore: Int,
        microExpr: Map<String, Float> = emptyMap(),
        trigger: String = "",
        strategy: String = "",
        strategyEffect: Int = 0,
    ) {
        withContext(Dispatchers.IO) {
            dao.insert(
                EmotionLog(
                    timestamp = System.currentTimeMillis(),
                    mode = mode,
                    emotion = emotion,
                    anxietyScore = anxietyScore,
                    microExpr = microExpr.toString(),
                    trigger = trigger,
                    strategy = strategy,
                    strategyEffect = strategyEffect,
                )
            )
        }
    }

    /** 近30天平均焦虑 */
    suspend fun avgAnxiety30d(): Float? = withContext(Dispatchers.IO) {
        dao.getAvgAnxiety(System.currentTimeMillis() - 30L * 24 * 3600 * 1000)
    }

    /** 情绪分布 (近30天) */
    suspend fun emotionDistribution(): List<EmotionCount> = withContext(Dispatchers.IO) {
        dao.getEmotionDistribution(System.currentTimeMillis() - 30L * 24 * 3600 * 1000)
    }

    /** 高焦虑次数 (近7天) */
    suspend fun highAnxietyCount7d(): Int = withContext(Dispatchers.IO) {
        dao.getHighAnxietyCount(System.currentTimeMillis() - 7L * 24 * 3600 * 1000)
    }

    /** 生成简短成长报告 */
    suspend fun growthReport(): String {
        val avg = avgAnxiety30d() ?: return "记录足够数据后, 我才能为你分析情绪趋势哦~"
        val dist = emotionDistribution()
        val high = highAnxietyCount7d()
        val dominant = dist.firstOrNull()?.emotion ?: "平静"
        return "📊 近30天平均焦虑: ${avg.toInt()}\n" +
            "  主要情绪: $dominant\n" +
            "  近7天高焦虑次数: $high\n" +
            "  ${if (avg < 30) "很棒! 你大部分时间都很平静 😊" else if (avg < 60) "有轻微焦虑, 记得用正念练习调节~" else "焦虑偏高, 建议多尝试CBT重构, 或咨询专业心理帮助"}"
    }
}