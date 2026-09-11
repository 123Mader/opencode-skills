// ============================================================
// 「心安」Android 主界面 — 双模式入口 (聊天 + 视频陪伴)
// 位置: android/app/src/main/java/com/xinan/app/MainActivity.kt
// ============================================================
package com.xinan.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.xinan.app.chat.ChatFragment
import com.xinan.app.video.VideoFragment
import com.xinan.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentMode: String = "chat"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnChatMode.setOnClickListener { switchMode("chat") }
        binding.btnVideoMode.setOnClickListener { switchMode("video") }

        switchMode("chat")
    }

    private fun switchMode(mode: String) {
        currentMode = mode
        val fragment: Fragment = when (mode) {
            "video" -> VideoFragment()
            else -> ChatFragment()
        }
        supportFragmentManager.beginTransaction()
            .replace(binding.fragmentContainer.id, fragment)
            .commit()
        // UI 高亮当前模式
        binding.btnChatMode.isSelected = mode == "chat"
        binding.btnVideoMode.isSelected = mode == "video"
    }
}