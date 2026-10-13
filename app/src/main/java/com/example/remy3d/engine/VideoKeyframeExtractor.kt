package com.example.remy3d.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * 外部视频流与多图导入预处理器 (支持 MP4/MOV 智能关键帧抽取与模糊度过滤)
 *
 * 核心功能管线：
 * 1. 视频抽帧：以自适应时间步长（400ms~600ms）提取关键帧并使用 ImageQualityEvaluator 过滤拖影废帧
 * 2. 多图导入：读取外部相册图片流并复制到沙盒规范化为高质量 JPEG 数据集
 */
object VideoKeyframeExtractor {

    interface ExtractCallback {
        fun onProgress(currentFrame: Int, totalEstimated: Int, status: String)
    }

    /**
     * 从视频文件或 Uri 中按时间步长抽取清晰关键帧图片
     * @param videoUri 视频文件的 Uri
     * @param targetDir 抽帧保存的目标文件夹
     * @param stepMs 抽帧时间间隔 (毫秒)，默认 500ms (一秒 2 帧)
     * @param maxFrames 允许抽取的最大帧数上限 (默认 45 帧，保障手机算力与内存)
     */
    fun extractFrames(
        context: Context,
        videoUri: Uri,
        targetDir: File,
        stepMs: Long = 500L,
        maxFrames: Int = 45,
        callback: ExtractCallback? = null
    ): List<File> {
        targetDir.mkdirs()
        val extractedFiles = mutableListOf<File>()
        val retriever = MediaMetadataRetriever()

        try {
            retriever.setDataSource(context, videoUri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 10_000L

            // 动态自适应抽帧间隔：若视频较长，适当增大步长保证帧数在合理范围
            val actualStepMs = max(stepMs, durationMs / maxFrames)
            val totalEstimated = (durationMs / actualStepMs).toInt().coerceIn(3, maxFrames)

            var timeMs = 0L
            var frameIndex = 0

            while (timeMs < durationMs && extractedFiles.size < maxFrames) {
                callback?.onProgress(
                    currentFrame = frameIndex + 1,
                    totalEstimated = totalEstimated,
                    status = "抽取视频关键帧 ${frameIndex + 1}/$totalEstimated (${timeMs / 1000}s)"
                )

                // 提取微秒级时刻的关键帧 (OPTION_CLOSEST_SYNC / OPTION_CLOSEST)
                val timeUs = timeMs * 1000L
                val frameBmp = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)

                if (frameBmp != null) {
                    val frameFile = File(targetDir, "vframe_${System.currentTimeMillis()}_$frameIndex.jpg")
                    val out = FileOutputStream(frameFile)
                    // 等比缩放至最大边长 960 以兼顾速度与质量
                    val scaled = scaleBitmap(frameBmp, 960)
                    scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    out.flush()
                    out.close()

                    // 进行画质清晰度校验：严重拖影模糊的废帧自动剔除
                    val quality = ImageQualityEvaluator.evaluate(frameFile)
                    if (!quality.isBlurry || extractedFiles.size < 6) {
                        extractedFiles.add(frameFile)
                    } else {
                        frameFile.delete() // 丢弃运动拖影帧
                    }

                    if (scaled != frameBmp) scaled.recycle()
                    frameBmp.recycle()
                    frameIndex++
                }

                timeMs += actualStepMs
            }
        } catch (_: Exception) {
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        return extractedFiles
    }

    /**
     * 将外部多图 Uri 集合批量复制到本地沙盒规范化目录
     */
    fun importImageUris(
        context: Context,
        uris: List<Uri>,
        targetDir: File,
        onProgress: ((Int, Int) -> Unit)? = null
    ): List<File> {
        targetDir.mkdirs()
        val imported = mutableListOf<File>()
        uris.forEachIndexed { index, uri ->
            onProgress?.invoke(index + 1, uris.size)
            try {
                val input = context.contentResolver.openInputStream(uri)
                if (input != null) {
                    val dest = File(targetDir, "import_${System.currentTimeMillis()}_$index.jpg")
                    val output = FileOutputStream(dest)
                    input.copyTo(output)
                    input.close()
                    output.flush()
                    output.close()
                    if (dest.exists() && dest.length() > 1024) {
                        // 检测并矫正外部图片可能存在的 EXIF 旋转角
                        try {
                            val exif = ExifInterface(dest.absolutePath)
                            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                            val deg = when (orientation) {
                                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                                else -> 0f
                            }
                            if (deg != 0f) {
                                val bmp = BitmapFactory.decodeFile(dest.absolutePath)
                                if (bmp != null) {
                                    val mat = Matrix().apply { postRotate(deg) }
                                    val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, mat, true)
                                    bmp.recycle()
                                    FileOutputStream(dest).use { fos ->
                                        rotated.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                                        fos.flush()
                                    }
                                    rotated.recycle()
                                    val newExif = ExifInterface(dest.absolutePath)
                                    newExif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                                    newExif.saveAttributes()
                                }
                            }
                        } catch (_: Exception) {}

                        imported.add(dest)
                    }
                }
            } catch (_: Exception) {}
        }
        return imported
    }

    private fun scaleBitmap(bmp: Bitmap, maxDim: Int): Bitmap {
        val w = bmp.width; val h = bmp.height
        val maxLen = max(w, h)
        if (maxLen <= maxDim) return bmp
        val factor = maxDim.toFloat() / maxLen
        return Bitmap.createScaledBitmap(bmp, (w * factor).toInt(), (h * factor).toInt(), true)
    }
}
