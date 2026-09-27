// ============================================================
// 「心安」Android 主界面 — 双模式入口 · 打开即用（无需注册登录）
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
    private var videoFragment: VideoFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Trace.log("2_main_onCreate_start")
        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            Trace.log("4_main_inflated")
            setContentView(binding.root)
            Trace.log("5_main_setcontent")
        } catch (e: Exception) {
            Trace.log("5_main_inflate_fail", e.message ?: "")
            throw e
        }

        binding.btnChatMode.setOnClickListener { switchMode("chat") }
        binding.btnVideoMode.setOnClickListener { switchMode("video") }

        Trace.log("6_main_before_switch")
        switchMode("chat")
        Trace.log("9_main_after_switch")
    }

    private fun switchMode(mode: String) {
        Trace.log("7_switch_mode", mode)
        val fragment: Fragment = when (mode) {
            "video" -> VideoFragment().also { videoFragment = it }
            else -> ChatFragment().also { videoFragment = null }
        }
        try {
            supportFragmentManager.beginTransaction()
                .replace(binding.fragmentContainer.id, fragment)
                .commitAllowingStateLoss()
        } catch (e: Exception) {
            Trace.log("7_switch_fail", e.message ?: "")
        }
        binding.btnChatMode.isSelected = mode == "chat"
        binding.btnVideoMode.isSelected = mode == "video"
    }

    fun currentVideoFragment(): VideoFragment? = videoFragment
}
