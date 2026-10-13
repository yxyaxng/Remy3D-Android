package com.example.remy3d.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 图像质量与模糊度评估器 (基于拉普拉斯二阶梯度方差算法)
 *
 * 核心机制：
 * 1. 模糊检测 (Blur Detection)：计算图像灰度梯度的方差 (Laplacian Variance)，
 *    方差低于阈值判定为运动模糊或失焦照片；
 * 2. 曝光异常检测 (Illumination Check)：统计平均亮度，检测过暗或过曝区域。
 */
object ImageQualityEvaluator {

    data class QualityReport(
        val isBlurry: Boolean,
        val sharpnessScore: Float, // 锐度得分 (正常 > 35.0, 模糊 < 25.0)
        val isUnderExposed: Boolean,
        val isOverExposed: Boolean,
        val brightness: Float,     // 0 ~ 255
        val summaryPrompt: String
    )

    /**
     * 对保存的图片文件快速采样检测清晰度与质量 (自适应下采样加速，纯原生毫秒级无卡顿)
     */
    fun evaluate(file: File): QualityReport {
        if (!file.exists()) {
            return QualityReport(false, 100f, false, false, 128f, "文件就绪")
        }

        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
        val maxDim = kotlin.math.max(boundsOpts.outWidth, boundsOpts.outHeight)
        val sampleSize = if (maxDim > 320) (maxDim / 320).coerceAtLeast(2) else 1

        val opts = BitmapFactory.Options().apply {
            inJustDecodeBounds = false
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bmp = try {
            BitmapFactory.decodeFile(file.absolutePath, opts)
        } catch (_: Exception) {
            null
        } ?: return QualityReport(false, 100f, false, false, 128f, "读取失败")

        return try {
            evaluate(bmp)
        } finally {
            bmp.recycle()
        }
    }

    /**
     * 直接对内存中的下采样 Bitmap 进行毫秒级画质评估 (复用单次解码结果，避免重复磁盘 I/O 与 getPixel JNI 开销)
     */
    fun evaluate(bmp: Bitmap): QualityReport {
        val width = bmp.width
        val height = bmp.height
        val step = 2

        if (width <= step * 2 || height <= step * 2) {
            return QualityReport(false, 50f, false, false, 128f, "样本不足")
        }

        // 一次性批量读取全部像素至内存数组，彻底消除数十万次 getPixel() JNI 调用开销
        val pixels = IntArray(width * height)
        bmp.getPixels(pixels, 0, width, 0, 0, width, height)

        // 预计算灰度矩阵 Y = 0.299R + 0.587G + 0.114B
        val lum = FloatArray(width * height)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            lum[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }

        var totalLum = 0.0
        var sumGrad = 0.0
        var sumGradSq = 0.0
        var sampleCount = 0

        // 单趟在线计算拉普拉斯均值与二阶矩方差，避免构建大尺寸 MutableList<Float> 导致自动装箱与 GC
        for (y in step until height - step step step) {
            val rowOffset = y * width
            val upOffset = (y - step) * width
            val downOffset = (y + step) * width
            for (x in step until width - step step step) {
                val cLum = lum[rowOffset + x]
                val lumL = lum[rowOffset + x - step]
                val lumR = lum[rowOffset + x + step]
                val lumU = lum[upOffset + x]
                val lumD = lum[downOffset + x]

                totalLum += cLum
                val laplacian = abs(4f * cLum - (lumL + lumR + lumU + lumD)).toDouble()
                sumGrad += laplacian
                sumGradSq += laplacian * laplacian
                sampleCount++
            }
        }

        if (sampleCount == 0) {
            return QualityReport(false, 50f, false, false, 128f, "样本不足")
        }

        val avgBrightness = (totalLum / sampleCount).toFloat()
        val avgGrad = sumGrad / sampleCount
        val variance = ((sumGradSq / sampleCount) - (avgGrad * avgGrad)).coerceAtLeast(0.0).toFloat()

        // 判定阈值：通常平滑模糊图片方差极低 (< 22.0f)
        val isBlurry = variance < 22.0f
        val isUnderExposed = avgBrightness < 40f
        val isOverExposed = avgBrightness > 220f

        val prompt = when {
            isBlurry -> "检测到画面轻微模糊/运动拖影 (锐度 ${variance.toInt()})"
            isUnderExposed -> "环境光偏暗，特征识别可能受影响"
            isOverExposed -> "局部严重过曝，可能丢失高光纹理"
            else -> "画质优良清晰"
        }

        return QualityReport(
            isBlurry = isBlurry,
            sharpnessScore = variance,
            isUnderExposed = isUnderExposed,
            isOverExposed = isOverExposed,
            brightness = avgBrightness,
            summaryPrompt = prompt
        )
    }
}
