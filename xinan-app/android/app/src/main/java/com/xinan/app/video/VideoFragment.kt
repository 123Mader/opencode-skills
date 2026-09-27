package com.xinan.app.video

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.xinan.app.vision.FaceLandmarkerHelper
import com.xinan.app.vision.MicroExpressionAnalyzer
import com.xinan.app.vision.PsychologicalProjection
import kotlinx.coroutines.launch

/**
 * 视频陪伴模式 — 摄像头实时微表情分析 + ★心理预期推断
 *
 * 流程: CameraX 30fps → FaceLandmarker(468点) → MicroExpressionAnalyzer
 *     → 情绪/焦虑 + 头部偏航 → PsychologicalProjection(心理预期)
 *     → 仪表盘显示 + 触发AI对话
 */
class VideoFragment : Fragment() {

    private lateinit var previewView: PreviewView
    private lateinit var faceLandmarker: FaceLandmarkerHelper
    private lateinit var dashboard: EmotionDashboardView
    private lateinit var memory: com.xinan.app.data.MemoryRepository
    private val analyzer = MicroExpressionAnalyzer()
    private val projection = PsychologicalProjection()   // ★ 心理预期引擎

    private var lastEmotion = "平静"
    private var lastAnxiety = 0
    private var lastMicroExpr = emptyList<String>()
    private var lastProjection: PsychologicalProjection.Projection? = null  // ★

    private var lastAlertTime = 0L

    /** 情绪+心理预期更新回调 (供外部读取, 如聊天模块) */
    var onEmotionUpdate: ((emotion: String, anxiety: Int, microExpr: List<String>, projection: PsychologicalProjection.Projection?) -> Unit)? = null

    /** 当前最新心理预期 (供聊天模块读取, 实现"给出他的心理预期") */
    fun currentProjection(): PsychologicalProjection.Projection? = lastProjection

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        previewView = PreviewView(requireContext())
        val overlay = android.widget.FrameLayout(requireContext())
        overlay.addView(previewView)
        dashboard = EmotionDashboardView(requireContext())
        // 心理预期区块需更大覆盖层
        val lp = android.widget.FrameLayout.LayoutParams(
            resources.getDimensionPixelSize(com.xinan.app.R.dimen.dashboard_overlay_w),
            resources.getDimensionPixelSize(com.xinan.app.R.dimen.dashboard_overlay_h),
            android.view.Gravity.END or android.view.Gravity.BOTTOM
        )
        lp.setMargins(12, 12, 12, 12)
        overlay.addView(dashboard, lp)
        return overlay
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        checkPermissions()
        memory = com.xinan.app.data.MemoryRepository(requireContext())
        setupFaceLandmarker()
        startCamera()
        onEmotionUpdate = { emotion, anxiety, micro, proj ->
            // ★ 写入全局状态, 供聊天模式读取心理预期
            com.xinan.app.vision.EmotionStateHolder.update(emotion, anxiety, proj)
            activity?.runOnUiThread {
                dashboard.update(emotion, anxiety, micro)
                dashboard.updateProjection(proj)
            }
            // 后台记录情绪日志 (用 lifecycleScope 替换泄漏的 GlobalScope)
            viewLifecycleOwner.lifecycleScope.launch {
                memory.recordEmotion("video", emotion, anxiety)
            }
        }
    }

    private fun setupFaceLandmarker() {
        faceLandmarker = FaceLandmarkerHelper(requireContext()) { landmarks ->
            val result = analyzer.analyzeFrame(landmarks)
            if (result != null) {
                lastEmotion = result.emotion
                lastAnxiety = result.anxietyScore
                lastMicroExpr = result.microExpressions

                // ★ 喂入心理预期引擎
                val proj = projection.feed(result, result.headYawDeg, result.blinkRate)
                if (proj != null) lastProjection = proj

                onEmotionUpdate?.invoke(lastEmotion, lastAnxiety, lastMicroExpr, lastProjection)

                if (result.anxietyScore >= 60) onHighAnxietyDetected(result)
            }
        }
        faceLandmarker.setup()
    }

    /** 高焦虑触发疏导: 30 秒防抖 + 启动正念呼吸 */
    private fun onHighAnxietyDetected(result: MicroExpressionAnalyzer.AnalysisResult) {
        val now = System.currentTimeMillis()
        if (now - lastAlertTime < 30000) return
        lastAlertTime = now
        try {
            val intent = android.content.Intent(requireContext(), com.xinan.app.relax.BreathingGuideActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("VideoFragment", "正念引导启动失败: ${e.message}")
        }
        onAnxietyAlert?.invoke(result)
    }

    var onAnxietyAlert: ((MicroExpressionAnalyzer.AnalysisResult) -> Unit)? = null

    /** CameraX 启动: 前置摄像头 30fps */
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder()
                .setTargetRotation(previewView.display.rotation)
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(android.util.Size(640, 480))
                .build()
            imageAnalysis.setAnalyzer(ExecutorsCompat.backgroundExecutor()) { imageProxy ->
                processImageProxy(imageProxy)
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalysis
                )
            } catch (e: Exception) {
                android.util.Log.e("VideoFragment", "相机启动失败: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    /** 帧处理: YUV_420_888 → Bitmap → FaceLandmarker (前置镜像). 异常不泄漏 imageProxy */
    private fun processImageProxy(imageProxy: ImageProxy) {
        try {
            imageProxy.image?.let { img ->
                val bitmap = com.xinan.app.vision.YuvToBitmap.convert(img)
                val mirrored = com.xinan.app.vision.YuvToBitmap.mirror(bitmap)
                faceLandmarker.processFrame(mirrored, imageProxy.imageInfo.timestamp)
            }
        } catch (e: Exception) {
            android.util.Log.e("VideoFragment", "帧处理异常: ${e.message}")
        } finally {
            imageProxy.close()
        }
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 100)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        faceLandmarker.close()
    }
}

/** 后台线程执行器 (CameraX Analyzer 用) */
object ExecutorsCompat {
    private val executors = java.util.concurrent.Executors.newSingleThreadExecutor()
    fun backgroundExecutor() = executors
}
