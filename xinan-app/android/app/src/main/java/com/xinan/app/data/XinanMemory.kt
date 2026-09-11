package com.xinan.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context

/**
 * 记忆系统 — Room 本地情绪日志
 * 记录每次情绪事件, 支持趋势分析/触发模式识别
 */

@Entity(tableName = "emotion_log")
data class EmotionLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,          // 时间戳
    val mode: String,             // "chat" / "video"
    val emotion: String,          // 焦虑/平静/快乐...
    val anxietyScore: Int,        // 0-100
    val microExpr: String,        // 微表情信号 (JSON字符串)
    val trigger: String,          // 触发场景
    val strategy: String,         // 采用的疏导策略
    val strategyEffect: Int,      // 效果 0-10
)

@Dao
interface EmotionDao {
    @Insert
    suspend fun insert(log: EmotionLog)

    @Query("SELECT * FROM emotion_log WHERE timestamp >= :since ORDER BY timestamp DESC")
    suspend fun getRecent(since: Long): List<EmotionLog>

    @Query("SELECT AVG(anxietyScore) FROM emotion_log WHERE timestamp >= :since")
    suspend fun getAvgAnxiety(since: Long): Float?

    @Query("SELECT emotion, COUNT(*) as cnt FROM emotion_log WHERE timestamp >= :since GROUP BY emotion ORDER BY cnt DESC")
    suspend fun getEmotionDistribution(since: Long): List<EmotionCount>

    @Query("SELECT COUNT(*) FROM emotion_log WHERE anxietyScore >= 60 AND timestamp >= :since")
    suspend fun getHighAnxietyCount(since: Long): Int
}

data class EmotionCount(val emotion: String, val cnt: Int)

@Database(entities = [EmotionLog::class], version = 1, exportSchema = false)
abstract class XinanDatabase : RoomDatabase() {
    abstract fun emotionDao(): EmotionDao

    companion object {
        @Volatile private var INSTANCE: XinanDatabase? = null
        fun get(context: Context): XinanDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, XinanDatabase::class.java, "xinan_memory.db")
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}