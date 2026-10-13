package com.example.remy3d.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.File
import kotlin.math.*

/**
 * 扫描目标主体空间与视觉一致性追踪引擎 (Target Consistency & Anchor Tracker)
 *
 * 核心机制：
 * 1. 目标主体视觉特征指纹 (Target Visual Signature)：
 *    提取中心 ROI 区域的 HSV 色相直方图分布 (16-bin Hue + 8-bin Saturation) 与平均亮度。
 * 2. 焦点/物距门限追踪 (Focus Distance Gating)：
 *    监测 Camera2 物距变化，判断对焦是否偏移至背景或前景其他物体。
 * 3. 实时一致性比对：
 *    实时判定当前取景画面是否与锁定目标一致。
 */
class TargetTrackerEngine {

    enum class TargetStatus {
        NOT_SET,              // 尚未锁定主体（拍摄首张照片或点击屏幕即可锁定）
        LOCKED,               // 锁定成功，画面中心正对目标物体，符合拍摄要求
        DEVIATED_OBJECT,      // 严重偏离：画面中心物体颜色/纹理特征不匹配，拍到了其他物体
        DEVIATED_DISTANCE     // 物距异常：对焦距离显著漂移，拍到了背景或前景杂物
    }

    data class TargetSignature(
        val hueHistogram: FloatArray,    // 16-bin HSV 色相归一化直方图
        val satHistogram: FloatArray,    // 8-bin 饱和度直方图
        val avgBrightness: Float,        // 0 ~ 255
        val baseFocusDistance: Float,    // 参考物距 (米或 Camera2 对焦度)
        val lockedTimestamp: Long = System.currentTimeMillis()
    )

    @Volatile
    private var activeSignature: TargetSignature? = null
    @Volatile
    var isTrackerActive: Boolean = true // 是否开启目标主体一致性锁定

    /**
     * 当前锁定的主体状态
     */
    @Volatile
    var currentStatus: TargetStatus = TargetStatus.NOT_SET
        private set

    /**
     * 相似度匹配得分 (0.0 ~ 1.0)
     */
    @Volatile
    var lastMatchScore: Float = 1.0f
        private set

    /**
     * 锁定指定图像文件（或首张拍照）中心物体作为全局扫描基准
     */
    fun lockTargetFromImage(file: File, focusDist: Float = 0.5f): Boolean {
        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
            val maxDim = max(boundsOpts.outWidth, boundsOpts.outHeight)
            val sampleSize = if (maxDim > 320) (maxDim / 320).coerceAtLeast(2) else 1

            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bmp = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return false
            lockTargetFromBitmap(bmp, focusDist)
            bmp.recycle()
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 锁定指定 Bitmap (例如实时分析帧或单次下采样解码图)
     */
    fun lockTargetFromBitmap(bitmap: Bitmap, focusDist: Float = 0.5f) {
        activeSignature = extractSignature(bitmap, focusDist)
        currentStatus = TargetStatus.LOCKED
        lastMatchScore = 1.0f
    }

    /**
     * 重置清除目标锁定，重新寻找新物体
     */
    fun resetTarget() {
        activeSignature = null
        currentStatus = TargetStatus.NOT_SET
        lastMatchScore = 1.0f
    }

    /**
     * 当用户放弃偏离照片或调整参数后，立即清除阻塞性的单次偏离状态，恢复实时追踪
     */
    fun clearDeviationState() {
        if (activeSignature != null) {
            if (currentStatus == TargetStatus.DEVIATED_OBJECT || currentStatus == TargetStatus.DEVIATED_DISTANCE) {
                currentStatus = TargetStatus.LOCKED
            }
        } else {
            currentStatus = TargetStatus.NOT_SET
        }
    }

    /**
     * 实时预览帧与物距监测更新：当用户移动镜头或更正角度/距离时，无需拍照即可实时更新锁定/偏移状态
     */
    fun verifyRealtimePreview(bmp: Bitmap, currentFocusDist: Float = 0f): TargetStatus {
        val target = activeSignature
        if (!isTrackerActive || target == null) {
            currentStatus = TargetStatus.NOT_SET
            return currentStatus
        }

        return try {
            val candidate = extractSignature(bmp, currentFocusDist)
            val hueSim = calculateHistogramIntersection(target.hueHistogram, candidate.hueHistogram)
            val satSim = calculateHistogramIntersection(target.satHistogram, candidate.satHistogram)
            val rawSim = (hueSim * 0.7f + satSim * 0.3f)

            // 平滑得分，但在从偏离恢复到正常时立即响应无延迟
            val smoothedSim = if (rawSim >= 0.42f) {
                max(rawSim, lastMatchScore * 0.3f + rawSim * 0.7f)
            } else {
                lastMatchScore * 0.35f + rawSim * 0.65f
            }
            lastMatchScore = smoothedSim

            if (target.baseFocusDistance > 0.05f && currentFocusDist > 0.05f) {
                val distRatio = abs(target.baseFocusDistance - currentFocusDist) / target.baseFocusDistance
                if (distRatio > 0.65f) {
                    currentStatus = TargetStatus.DEVIATED_DISTANCE
                    return currentStatus
                }
            }

            currentStatus = if (smoothedSim < 0.40f && rawSim < 0.42f) {
                TargetStatus.DEVIATED_OBJECT
            } else {
                TargetStatus.LOCKED
            }
            currentStatus
        } catch (_: Exception) {
            currentStatus
        }
    }

    /**
     * 仅根据物距变化实时更新距离偏离状态
     */
    fun updateFocusDistanceRealtime(currentFocusDist: Float) {
        val target = activeSignature ?: return
        if (!isTrackerActive) return
        if (target.baseFocusDistance > 0.05f && currentFocusDist > 0.05f) {
            val distRatio = abs(target.baseFocusDistance - currentFocusDist) / target.baseFocusDistance
            if (distRatio > 0.65f) {
                currentStatus = TargetStatus.DEVIATED_DISTANCE
            } else if (currentStatus == TargetStatus.DEVIATED_DISTANCE) {
                currentStatus = TargetStatus.LOCKED
            }
        } else if (currentStatus == TargetStatus.DEVIATED_DISTANCE) {
            currentStatus = TargetStatus.LOCKED
        }
    }

    /**
     * 校验待录入或当前帧的图像文件是否属于锁定的目标物体
     * @param imageFile 照片文件
     * @param currentFocusDist 当前 Camera2 对焦距离
     * @return Pair<是否符合拍摄, 详细判断信息>
     */
    fun verifyConsistency(imageFile: File, currentFocusDist: Float = 0f): Pair<Boolean, String> {
        val target = activeSignature
        if (!isTrackerActive || target == null) {
            currentStatus = TargetStatus.NOT_SET
            return Pair(true, "未启用目标锁定")
        }

        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(imageFile.absolutePath, boundsOpts)
            val maxDim = max(boundsOpts.outWidth, boundsOpts.outHeight)
            val sampleSize = if (maxDim > 320) (maxDim / 320).coerceAtLeast(2) else 1

            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bmp = BitmapFactory.decodeFile(imageFile.absolutePath, opts) ?: return Pair(true, "无法解析图片")
            try {
                verifyConsistency(bmp, currentFocusDist)
            } finally {
                bmp.recycle()
            }
        } catch (e: Exception) {
            Pair(true, "检测异常跳过")
        }
    }

    /**
     * 直接对内存中的下采样 Bitmap 进行目标一致性校验（复用单次解码结果，消除重复磁盘解码耗时）
     */
    fun verifyConsistency(bmp: Bitmap, currentFocusDist: Float = 0f): Pair<Boolean, String> {
        val target = activeSignature
        if (!isTrackerActive || target == null) {
            currentStatus = TargetStatus.NOT_SET
            return Pair(true, "未启用目标锁定")
        }

        return try {
            val candidate = extractSignature(bmp, currentFocusDist)

            // 1. 直方图巴氏距离/交集相似度计算
            val hueSim = calculateHistogramIntersection(target.hueHistogram, candidate.hueHistogram)
            val satSim = calculateHistogramIntersection(target.satHistogram, candidate.satHistogram)
            val overallSim = (hueSim * 0.7f + satSim * 0.3f)
            lastMatchScore = overallSim

            // 2. 物距漂移检测（若传入了有效对焦距离）
            if (target.baseFocusDistance > 0.05f && currentFocusDist > 0.05f) {
                val distRatio = abs(target.baseFocusDistance - currentFocusDist) / target.baseFocusDistance
                if (distRatio > 0.65f) {
                    currentStatus = TargetStatus.DEVIATED_DISTANCE
                    return Pair(false, "物距偏离 ${(distRatio * 100).toInt()}%：当前焦点对准了背景或其他物体！")
                }
            }

            // 3. 特征相似度门限判定 (低于 0.42 判定为偏离目标物体)
            if (overallSim < 0.42f) {
                currentStatus = TargetStatus.DEVIATED_OBJECT
                return Pair(false, "目标偏离 (相似度 ${(overallSim * 100).toInt()}%)：镜头已拍到其他物体，请重新对准扫描目标！")
            }

            currentStatus = TargetStatus.LOCKED
            Pair(true, "目标物体锁定匹配良好 (${(overallSim * 100).toInt()}%)")
        } catch (e: Exception) {
            Pair(true, "检测异常跳过")
        }
    }

    /**
     * 从图像中心 42% x 42% 的核心目标区域提取 HSV 颜色特征指纹（批量数组读取 + 内联 HSV 转换，耗时 < 1ms）
     */
    private fun extractSignature(bitmap: Bitmap, focusDist: Float): TargetSignature {
        val w = bitmap.width
        val h = bitmap.height
        val roiW = (w * 0.42f).toInt().coerceIn(1, w)
        val roiH = (h * 0.42f).toInt().coerceIn(1, h)
        val startX = (w - roiW) / 2
        val startY = (h - roiH) / 2

        val roiPixels = IntArray(roiW * roiH)
        bitmap.getPixels(roiPixels, 0, roiW, startX, startY, roiW, roiH)

        val hueBins = FloatArray(16)
        val satBins = FloatArray(8)
        var totalPixels = 0
        var totalBrightness = 0.0

        // 间隔 2 个像素均匀下采样，纯内存数组遍历无 JNI 阻塞
        for (y in 0 until roiH step 2) {
            val rowOffset = y * roiW
            for (x in 0 until roiW step 2) {
                val pixel = roiPixels[rowOffset + x]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                val maxC = maxOf(r, g, b)
                val minC = minOf(r, g, b)
                val delta = maxC - minC

                val sat = if (maxC == 0) 0f else delta.toFloat() / maxC.toFloat()
                var hue = when {
                    delta == 0 -> 0f
                    maxC == r -> 60f * (((g - b).toFloat() / delta) % 6f)
                    maxC == g -> 60f * (((b - r).toFloat() / delta) + 2f)
                    else -> 60f * (((r - g).toFloat() / delta) + 4f)
                }
                if (hue < 0f) hue += 360f

                val hueIdx = ((hue / 360f) * 16).toInt().coerceIn(0, 15)
                val satIdx = (sat * 8).toInt().coerceIn(0, 7)

                hueBins[hueIdx] += 1f
                satBins[satIdx] += 1f
                totalBrightness += maxC.toDouble()
                totalPixels++
            }
        }

        // 归一化直方图
        if (totalPixels > 0) {
            for (i in hueBins.indices) hueBins[i] /= totalPixels.toFloat()
            for (i in satBins.indices) satBins[i] /= totalPixels.toFloat()
        }

        return TargetSignature(
            hueHistogram = hueBins,
            satHistogram = satBins,
            avgBrightness = if (totalPixels > 0) (totalBrightness / totalPixels).toFloat() else 128f,
            baseFocusDistance = focusDist
        )
    }

    /**
     * 计算两个归一化直方图的交集相似度 (Histogram Intersection Similarity)
     * 返回 0.0 ~ 1.0 (1.0 为完全一致)
     */
    private fun calculateHistogramIntersection(histA: FloatArray, histB: FloatArray): Float {
        var intersection = 0f
        val len = min(histA.size, histB.size)
        for (i in 0 until len) {
            intersection += min(histA[i], histB[i])
        }
        return intersection.coerceIn(0f, 1f)
    }
}
