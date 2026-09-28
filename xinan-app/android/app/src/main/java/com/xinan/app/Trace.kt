package com.xinan.app

import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore

/**
 * 诊断打点: debug 构建写入 Download 目录辅助定位; release 构建仅输出 Logcat, 不落盘不污染存储
 */
object Trace {
    private var counter = 0

    /** release 构建为 false → 不写文件 */
    private val fileEnabled: Boolean = BuildConfig.DEBUG

    fun log(stage: String, extra: String = "") {
        android.util.Log.d("XinanTrace", "STEP $counter: $stage $extra")
        if (!fileEnabled) return
        try {
            val app = XinanApp.instance ?: return
            if (Build.VERSION.SDK_INT < 29) {
                // 旧版本写私有目录保底
                java.io.File(app.getExternalFilesDir(null), "xinan_trace.txt")
                    .appendText("$stage $extra\n")
                return
            }
            counter++
            val r = app.contentResolver
            val v = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "xinan_trace_${System.currentTimeMillis()}_${counter}.txt")
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download")
            }
            val uri = r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v) ?: return
            r.openOutputStream(uri)?.use { os ->
                os.write("STEP $counter: $stage $extra\n".toByteArray())
                os.flush()
            }
        } catch (_: Throwable) {}
    }
}
