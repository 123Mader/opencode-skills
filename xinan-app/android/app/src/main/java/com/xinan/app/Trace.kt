package com.xinan.app

import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore

/**
 * 诊断打点: 每次调用立即写一个文件到 /sdcard/Download/xinan_trace_<时间>.txt
 * 崩溃(含 native SIGSEGV)前最后一个文件即崩点。外部可读 → 定位。
 */
object Trace {
    private var counter = 0
    fun log(stage: String, extra: String = "") {
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
