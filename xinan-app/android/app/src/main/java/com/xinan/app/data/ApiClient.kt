package com.xinan.app.data

import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * 后端 API 客户端 — 注册/登录/情绪上报
 * 后端: Node.js server.js (http://<服务器IP>:3000)
 */
class ApiClient(private val baseUrl: String = "http://10.0.0.1:3000") {

    companion object {
        // 手机号哈希 (与后端一致: SHA-256 + salt)
        fun hashPhone(phone: String): String {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            val digest = md.digest((phone + "xinan-salt").toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }
    }

    private fun post(path: String, body: JSONObject): JSONObject {
        val conn = URL(baseUrl + path).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
        val response = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        return JSONObject(response)
    }

    /** 手机号注册 */
    fun register(phone: String, code: String, nickname: String, onResult: (Boolean, String) -> Unit) {
        Thread {
            try {
                val body = JSONObject().apply {
                    put("phone", phone)
                    put("code", code)
                    put("nickname", nickname)
                }
                val resp = post("/api/register", body)
                onResult(resp.getBoolean("ok"), resp.optString("userId"))
            } catch (e: Exception) {
                onResult(false, e.message ?: "网络错误")
            }
        }.start()
    }

    /** 短信登录 */
    fun login(phone: String, code: String, onResult: (Boolean, String) -> Unit) {
        Thread {
            try {
                val body = JSONObject().apply {
                    put("phone", phone)
                    put("code", code)
                }
                val resp = post("/api/login", body)
                onResult(resp.getBoolean("ok"), resp.optString("userId"))
            } catch (e: Exception) {
                onResult(false, e.message ?: "网络错误")
            }
        }.start()
    }

    /**
     * 情绪事件上报 (核心! 微表情分析结果上报到云端数据库)
     */
    fun reportEmotion(
        userId: String,
        mode: String,          // "chat" 或 "video"
        emotionType: String,   // 焦虑/平静/快乐...
        anxietyScore: Int,     // 0-100
        microExpr: Map<String, Any>,  // 微表情信号 (AU值等)
        triggerKw: String = "",
        strategyUsed: String = "",
        strategyEffect: Int = 0,
        onResult: (Boolean) -> Unit,
    ) {
        Thread {
            try {
                val body = JSONObject().apply {
                    put("userId", userId)
                    put("mode", mode)
                    put("emotionType", emotionType)
                    put("anxietyScore", anxietyScore)
                    put("microExpr", JSONObject(microExpr))
                    put("triggerKw", triggerKw)
                    put("strategyUsed", strategyUsed)
                    put("strategyEffect", strategyEffect)
                }
                val resp = post("/api/emotion", body)
                onResult(resp.getBoolean("ok"))
            } catch (e: Exception) {
                onResult(false)
            }
        }.start()
    }

    /** 查询个人30天情绪趋势 */
    fun getProgress(userId: String, onResult: (String) -> Unit) {
        Thread {
            try {
                val conn = URL("$baseUrl/api/user/$userId/progress").openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 10000
                val resp = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                onResult(resp)
            } catch (e: Exception) {
                onResult("[]")
            }
        }.start()
    }
}