package com.example.remy3d.engine

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MediaStoreSaver – 将 3D 渲染画面无缝保存至 Android 系统相册
 *
 * 兼容 Android 10+ (Scoped Storage / 分区存储) 与历史版本，直接将 3DGS
 * 高清透视渲染帧以无损 PNG 格式写入系统相册目录 (Pictures/Remy3D)，
 * 在系统自带相册与微信发送图片中即刻可见。
 */
object MediaStoreSaver {

    fun saveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        titlePrefix: String = "Remy3D_Render"
    ): Uri? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val cleanPrefix = titlePrefix.replace("[^a-zA-Z0-9_\\u4e00-\\u9fa5]".toRegex(), "_")
        val filename = "${cleanPrefix}_${timestamp}.png"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Images.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Remy3D")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null

        return try {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        } catch (e: Exception) {
            try { resolver.delete(uri, null, null) } catch (_: Exception) {}
            null
        }
    }
}
