package com.xinan.app.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.xinan.app.chat.ChatFragment.Message

/**
 * 聊天消息适配器 (用户/AI 气泡)
 */
class MessageAdapter(private val messages: List<Message>) :
    RecyclerView.Adapter<MessageAdapter.VH>() {

    class VH(val textView: TextView) : RecyclerView.ViewHolder(textView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = TextView(parent.context).apply {
            textSize = 16f
            setPadding(24, 16, 24, 16)
        }
        return VH(tv)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val msg = messages[position]
        holder.textView.text = msg.content
        // 用户/AI 左右对齐 + 颜色区分
        val lp = holder.textView.layoutParams as? ViewGroup.MarginLayoutParams
        if (msg.role == "user") {
            holder.textView.setBackgroundColor(0xFF4CAF50.toInt())
            holder.textView.gravity = android.view.Gravity.END
            lp?.marginStart = 100
            lp?.marginEnd = 16
        } else {
            holder.textView.setBackgroundColor(0xFFE0E0E0.toInt())
            holder.textView.gravity = android.view.Gravity.START
            lp?.marginStart = 16
            lp?.marginEnd = 100
        }
    }

    override fun getItemCount() = messages.size
}