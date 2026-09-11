package com.xinan.app.vision

import android.graphics.Bitmap
import android.media.Image
import java.nio.ByteBuffer

/**
 * YUV_420_888 → Bitmap 转换器
 * CameraX ImageAnalysis 默认输出 YUV_420_888 格式, 需转为 Bitmap 供 MediaPipe 使用
 */
object YuvToBitmap {

    /**
     * 将 Image (YUV_420_888) 转为 ARGB Bitmap
     * 针对天玑9000 优化: 使用像素平面直读 + 快速颜色转换
     */
    fun convert(image: Image, rotationDegrees: Int = 0): Bitmap {
        val width = image.width
        val height = image.height

        // 获取 Y/U/V 平面
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val yRowStride = image.planes[0].rowStride
        val uvRowStride = image.planes[1].rowStride
        val uvPixelStride = image.planes[1].pixelStride

        val pixels = IntArray(width * height)

        // 逐像素 YUV → RGB 转换 (只读Y平面 + 交错UV)
        for (row in 0 until height) {
            val yRowStart = row * yRowStride
            val uvRowStart = (row / 2) * uvRowStride
            for (col in 0 until width) {
                val yIndex = yRowStart + col
                val uvIndex = uvRowStart + (col / 2) * uvPixelStride

                val y = (yBuffer.get(yIndex).toInt() and 0xFF)
                val u = (uBuffer.get(uvIndex).toInt() and 0xFF) - 128
                val v = (vBuffer.get(uvIndex).toInt() and 0xFF) - 128

                // YUV → RGB (BT.601 标准, 快速整数近似)
                val r = (y + 1.402f * v).toInt().coerceIn(0, 255)
                val g = (y - 0.344136f * u - 0.714136f * v).toInt().coerceIn(0, 255)
                val b = (y + 1.772f * u).toInt().coerceIn(0, 255)

                pixels[row * width + col] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

        // 旋转 (前置摄像头需翻转)
        return if (rotationDegrees != 0) rotate(bitmap, rotationDegrees) else bitmap
    }

    /** 旋转 Bitmap */
    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** 前置摄像头镜像翻转 */
    fun mirror(bitmap: Bitmap): Bitmap {
        val matrix = android.graphics.Matrix().apply {
            preScale(-1f, 1f)  // 水平镜像
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}