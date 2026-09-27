package com.xinan.app

/**
 * 心安 Application — 全局崩溃 handler + 诊断打点实例
 */
class XinanApp : android.app.Application() {
    companion object { @Volatile var instance: XinanApp? = null }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Trace.log("1_app_start")
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            val text = "=== 心安崩溃 ===\ntime: ${java.util.Date()}\nthread: ${t.name}\nexc: ${e.javaClass.name}: ${e.message}\n\n${android.util.Log.getStackTraceString(e)}\n"
            try { java.io.File(getExternalFilesDir(null), "xinan_crash.log").writeText(text) } catch (_: Throwable) {}
            try {
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    val r = applicationContext.contentResolver
                    val v = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.Downloads.DISPLAY_NAME, "xinan_crash_${System.currentTimeMillis()}.log")
                        put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/plain")
                        put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "Download")
                    }
                    val uri = r.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)
                    uri?.let { r.openOutputStream(it)?.use { os -> os.write(text.toByteArray()); os.flush() } }
                }
            } catch (_: Throwable) {}
            prev?.uncaughtException(t, e)
        }
    }
}
