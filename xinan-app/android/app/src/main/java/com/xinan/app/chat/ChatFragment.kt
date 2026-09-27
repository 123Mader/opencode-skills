package com.xinan.app.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xinan.app.Trace
import com.xinan.app.llm.LLMInference

/**
 * 聊天模式 — 文字对话疏导 (CBT)
 */
class ChatFragment : Fragment() {

    private lateinit var llm: LLMInference
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MessageAdapter
    private val messages = mutableListOf<Message>()

    data class Message(val role: String, val content: String)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Trace.log("8_chat_onCreateView")
        val root = LinearLayoutCompat(requireContext())
        recyclerView = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = MessageAdapter(messages)
        }
        val inputRow = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
        }
        val input = EditText(requireContext()).apply { hint = "和我聊聊你的心情..." }
        val send = ImageButton(requireContext()).apply {
            setOnClickListener {
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) { sendMessage(text); input.text.clear() }
            }
        }
        inputRow.addView(input, android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        inputRow.addView(send)
        root.addView(recyclerView)
        root.addView(inputRow)
        Trace.log("8b_chat_createview_end")
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Trace.log("10_chat_onViewCreated")
        llm = LLMInference(requireContext())
        Trace.log("11_chat_llm_constructed")
        // 不自动加载模型 (避免 MediaPipe genai native init 崩)
        val modelsDir = java.io.File(requireContext().getExternalFilesDir(null), "models")
        val modelFile = java.io.File(modelsDir, "qwen3-3b-q4.gguf")
        if (modelFile.exists()) {
            Trace.log("12_chat_model_exists")
            llm.loadModel(modelFile.absolutePath) { ok ->
                if (ok) messages.add(Message("ai", "你好呀，我是心安。今天感觉怎么样？"))
                else messages.add(Message("ai", "模型加载失败, 请到菜单→🧠模型管理"))
                adapter.notifyDataSetChanged()
            }
        } else {
            Trace.log("12_chat_no_model")
            messages.add(Message("ai", "你好, 我是心安 💙\n\n聊天对话需先装大模型(菜单→🧠模型管理)。\n📹 视频陪伴模式的微表情/🧠心理预期分析可直接用, 不需要模型。"))
            adapter.notifyDataSetChanged()
        }
        Trace.log("13_chat_done")
    }

    private fun sendMessage(text: String) {
        messages.add(Message("user", text))
        adapter.notifyDataSetChanged()
        val visualContext = com.xinan.app.vision.EmotionStateHolder.formatVisualContext()
        val projection = com.xinan.app.vision.EmotionStateHolder.projection
        llm.chat(text, visualContext, projection) { reply ->
            messages.add(Message("ai", reply))
            activity?.runOnUiThread { adapter.notifyDataSetChanged() }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try { llm.close() } catch (_: Throwable) {}
    }
}

class LinearLayoutCompat(context: android.content.Context) : android.widget.LinearLayout(context) {
    init { orientation = android.widget.LinearLayout.VERTICAL }
}
