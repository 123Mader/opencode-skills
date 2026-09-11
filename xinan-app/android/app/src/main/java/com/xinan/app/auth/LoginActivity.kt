package com.xinan.app.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.xinan.app.MainActivity
import com.xinan.app.data.ApiClient

/**
 * 手机号注册/登录界面
 * 流程: 输入手机号 → 获取验证码 → 验证登录/注册 → 进入主界面
 */
class LoginActivity : AppCompatActivity() {

    private val api = ApiClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 已登录则直接进入
        if (isLoggedIn(this)) {
            goToMain()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 100, 48, 48)
        }

        val title = android.widget.TextView(this).apply {
            text = "心安"
            textSize = 36f
            gravity = android.view.Gravity.CENTER
        }

        val phoneInput = EditText(this).apply {
            hint = "请输入手机号"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }

        val codeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val codeInput = EditText(this).apply {
            hint = "验证码"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        val getCodeBtn = Button(this).apply {
            text = "获取验证码"
            setOnClickListener {
                val phone = phoneInput.text.toString().trim()
                if (phone.length == 11) {
                    // TODO: 调用后端发送短信验证码
                    // api.sendSmsCode(phone) { ok -> ... }
                    Toast.makeText(this@LoginActivity, "测试模式: 验证码 123456", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@LoginActivity, "请输入正确的11位手机号", Toast.LENGTH_SHORT).show()
                }
            }
        }
        codeRow.addView(codeInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        codeRow.addView(getCodeBtn)

        val loginBtn = Button(this).apply {
            text = "登录 / 注册"
            setOnClickListener { doLogin(phoneInput.text.toString().trim(), codeInput.text.toString().trim()) }
        }

        val privacy = android.widget.TextView(this).apply {
            text = "登录即同意《用户协议》和《隐私政策》\n你的情绪数据仅用于改善疏导效果, 严格保密"
            textSize = 13f
            gravity = android.view.Gravity.CENTER
            setTextColor(0xFF888888.toInt())
        }

        root.addView(title)
        root.addView(phoneInput)
        root.addView(codeRow)
        root.addView(loginBtn)
        root.addView(privacy)
        setContentView(root)
    }

    private fun doLogin(phone: String, code: String) {
        if (phone.length != 11) { Toast.makeText(this, "手机号格式错误", Toast.LENGTH_SHORT).show(); return }
        if (code.isEmpty()) { Toast.makeText(this, "请输入验证码", Toast.LENGTH_SHORT).show(); return }

        // 先尝试登录, 失败(用户不存在)则注册
        api.login(phone, code) { ok, userId ->
            runOnUiThread {
                if (ok) {
                    saveLogin(this, phone, userId)
                    Toast.makeText(this, "欢迎回来! 😊", Toast.LENGTH_SHORT).show()
                    goToMain()
                } else {
                    // 用户不存在 → 注册
                    api.register(phone, code, "心安用户") { regOk, newId ->
                        runOnUiThread {
                            if (regOk) {
                                saveLogin(this, phone, newId)
                                Toast.makeText(this, "注册成功, 欢迎使用心安! 🎉", Toast.LENGTH_SHORT).show()
                                goToMain()
                            } else {
                                Toast.makeText(this, "登录/注册失败: $newId", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    companion object {
        private const val PREF = "xinan_login"
        private const val KEY_USER = "user_id"
        private const val KEY_PHONE = "phone"

        fun saveLogin(ctx: Context, phone: String, userId: String) {
            ctx.getSharedPreferences(PREF, MODE_PRIVATE).edit()
                .putString(KEY_USER, userId)
                .putString(KEY_PHONE, phone)
                .apply()
        }

        fun isLoggedIn(ctx: Context): Boolean =
            ctx.getSharedPreferences(PREF, MODE_PRIVATE).getString(KEY_USER, null) != null

        fun currentUserId(ctx: Context): String? =
            ctx.getSharedPreferences(PREF, MODE_PRIVATE).getString(KEY_USER, null)

        fun logout(ctx: Context) {
            ctx.getSharedPreferences(PREF, MODE_PRIVATE).edit().clear().apply()
        }
    }
}