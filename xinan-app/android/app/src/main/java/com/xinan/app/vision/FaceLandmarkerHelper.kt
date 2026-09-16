package com.xinan.app.vision

import android.content.Context
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import java.util.concurrent.Executors

/**
 * MediaPipe FaceLandmarker 封装 (468点人脸关键点)
 * 配置: GPU delegate (天玑9000 Mali-G710 加速)
 * 模式: LIVE_STREAM 实时流式 (30fps)
 */
class FaceLandmarkerHelper(private val context: Context, private val listener: (List<FloatArray>) -> Unit) {

    companion object {
        private const val MODEL_PATH = "face_landmarker.task"  // 放在 assets/
        private const val NUM_FACES = 1
        private const val MIN_FACE_DETECTION_CONFIDENCE = 0.5f
        private const val MIN_TRACKING_CONFIDENCE = 0.5f
    }

    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private var landmarker: FaceLandmarker? = null
    private var isProcessing = false

    /** 初始化 FaceLandmarker (GPU加速) */
    fun setup() {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath(MODEL_PATH)
            .setDelegate(Delegate.GPU)  // 天玑9000 Mali-G710 GPU加速
            .build()

        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumFaces(NUM_FACES)
            .setMinFaceDetectionConfidence(MIN_FACE_DETECTION_CONFIDENCE)
            .setMinTrackingConfidence(MIN_TRACKING_CONFIDENCE)
            .setResultListener { result: FaceLandmarkerResult, _: MPImage ->
                handleResult(result)
            }
            .setErrorListener { error ->
                android.util.Log.e("FaceLandmarker", "错误: ${error.message}")
            }
            .build()

        landmarker = FaceLandmarker.createFromOptions(context, options)
    }

    /** 处理实时帧 (由 CameraX Analyzer 调用) */
    fun processFrame(bitmap: android.graphics.Bitmap, frameTimestampMs: Long) {
        if (isProcessing) return  // 帧节流, 防积压
        isProcessing = true
        val mpImage = BitmapImageBuilder(bitmap).build()
        backgroundExecutor.execute {
            landmarker?.detectAsync(mpImage, frameTimestampMs)
        }
    }

    /** 解析结果: 提取468点坐标 → 传给微表情分析器 */
    private fun handleResult(result: FaceLandmarkerResult) {
        isProcessing = false
        if (result.faceLandmarks().isEmpty()) return
        val landmarks = result.faceLandmarks()[0]
        // 转为 FloatArray 列表 (归一化坐标 x,y,z)
        val points = landmarks.map { lm ->
            floatArrayOf(lm.x(), lm.y(), lm.z())
        }
        listener(points)
    }

    /** 释放资源 */
    fun close() {
        landmarker?.close()
        backgroundExecutor.shutdown()
    }
}