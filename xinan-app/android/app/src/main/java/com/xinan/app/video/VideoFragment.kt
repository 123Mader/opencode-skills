package com.xinan.app.video

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.xinan.app.vision.FaceLandmarkerHelper
import com.xinan.app.vision.MicroExpressionAnalyzer
import kotlin.concurrent.thread
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 视频陪伴模式 — 摄像头实时微表情分析
 *
 * 流程: CameraX 30fps → FaceLandmarker(468点) → MicroExpressionAnalyzer(AU计算)
 *     → 情绪/焦虑指数 → 覆盖层显示 + 触发AI对话
 */
class VideoFragment : Fragment() {

    private lateinit var previewView: PreviewView
    private lateinit var faceLandmarker: FaceLandmarkerHelper
    private lateinit var dashboard: EmotionDashboardView
    private lateinit var memory: com.xinan.app.data.MemoryRepository
    private val analyzer = MicroExpressionAnalyzer()

    // 最近一次分析结果 (用于UI更新)
    private var lastEmotion = "平静"
    private var lastAnxiety = 0
    private var lastMicroExpr = emptyList<String>()

    private var lastAlertTime = 0L  // 防重复触发

    // 焦虑指数变化回调 (供UI层显示仪表盘)
    var onEmotionUpdate: ((emotion: String, anxiety: Int, microExpr: List<String>) -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        previewView = PreviewView(requireContext())
        // 叠加情绪仪表盘 (右下角)
        val overlay = android.widget.FrameLayout(requireContext())
        overlay.addView(previewView)
        dashboard = EmotionDashboardView(requireContext())
        val lp = android.widget.FrameLayout.LayoutParams(500, 260, android.view.Gravity.END or android.view.Gravity.BOTTOM)
        lp.setMargins(16, 16, 16, 120)
        overlay.addView(dashboard, lp)
        return overlay
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        checkPermissions()
        memory = com.xinan.app.data.MemoryRepository(requireContext())
        setupFaceLandmarker()
        startCamera()
        onEmotionUpdate = { emotion, anxiety, micro ->
            activity?.runOnUiThread {
                dashboard.update(emotion, anxiety, micro)
            }
            // 后台记录情绪日志
            kotlinx.coroutines.GlobalScope.launch {
                memory.recordEmotion("video", emotion, anxiety)
            }
        }
    }

    private fun setupFaceLandmarker() {
        faceLandmarker = FaceLandmarkerHelper(requireContext()) { landmarks ->
            // 每帧468点 → 微表情分析 (30fps)
            val result = analyzer.analyzeFrame(landmarks)
            if (result != null) {
                lastEmotion = result.emotion
                lastAnxiety = result.anxietyScore
                lastMicroExpr = result.microExpressions
                onEmotionUpdate?.invoke(lastEmotion, lastAnxiety, lastMicroExpr)

                // 焦虑指数升高 → 触发疏导 (示例: 通知上层)
                if (result.anxietyScore >= 60) {
                    onHighAnxietyDetected(result)
                }
            }
        }
        faceLandmarker.setup()
    }

    /** 高焦虑触发疏导: 30秒防抖 + 启动正念呼吸 */
    private fun onHighAnxietyDetected(result: MicroExpressionAnalyzer.AnalysisResult) {
        val now = System.currentTimeMillis()
        if (now - lastAlertTime < 30000) return  // 30秒内不重复
        lastAlertTime = now

        // 启动正念呼吸引导
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

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA  // 前置摄像头(自拍)

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalysis
                )
            } catch (e: Exception) {
                android.util.Log.e("VideoFragment", "相机启动失败: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    /** 帧处理: YUV_420_888 → Bitmap → FaceLandmarker (前置镜像) */
    private fun processImageProxy(imageProxy: ImageProxy) {
        imageProxy.image?.let { img ->
            val bitmap = com.xinan.app.vision.YuvToBitmap.convert(img)
            val mirrored = com.xinan.app.vision.YuvToBitmap.mirror(bitmap)  // 前置摄像头镜像
            faceLandmarker.processFrame(mirrored, imageProxy.imageInfo.timestamp)
        }
        imageProxy.close()
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