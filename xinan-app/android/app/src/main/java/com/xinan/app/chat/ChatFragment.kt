package com.xinan.app.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xinan.app.llm.LLMInference

/**
 * 聊天模式 — 文字对话疏导 (CBT)
 */
class ChatFragment : Fragment() {

    private lateinit var llm: LLMInference
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MessageAdapter
    private val messages = mutableListOf<Message>()

    data class Message(val role: String, val content: String)  // role: user/ai

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        // 简化 UI 骨架 (实际用 XML/Compose)
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
                if (text.isNotEmpty()) {
                    sendMessage(text)
                    input.text.clear()
                }
            }
        }
        inputRow.addView(input, android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        inputRow.addView(send)
        root.addView(recyclerView)
        root.addView(inputRow)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        llm = LLMInference(requireContext())
        llm.loadModel("models/qwen3-3b-q4.gguf") { ok ->
            if (ok) {
                messages.add(Message("ai", "你好呀，我是心安。今天感觉怎么样？"))
                adapter.notifyDataSetChanged()
            } else {
                Toast.makeText(requireContext(), "模型加载失败, 请检查 models/ 目录", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun sendMessage(text: String) {
        messages.add(Message("user", text))
        adapter.notifyDataSetChanged()
        // 文本情绪分析 (TODO: 接入情绪分类模型)
        // 直接对话, 无视觉上下文 (聊天模式)
        llm.chat(text, null) { reply ->
            messages.add(Message("ai", reply))
            activity?.runOnUiThread { adapter.notifyDataSetChanged() }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        llm.close()
    }
}

/** 简化容器 (骨架用) */
class LinearLayoutCompat(context: android.content.Context) : android.widget.LinearLayout(context) {
    init {
        orientation = android.widget.LinearLayout.VERTICAL
    }
}