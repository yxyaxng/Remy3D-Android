package com.example.remy3d.plyio

import com.example.remy3d.engine.SplatPoint
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

/**
 * PlyExporter – 位于 plyio 包下的 PLY 格式模型导出工具
 */
object PlyExporter {

    fun exportAscii(points: List<SplatPoint>, file: File): Boolean {
        return try {
            file.parentFile?.mkdirs()
            BufferedWriter(FileWriter(file), 65536).use { writer ->
                // PLY 头部
                writer.write("ply\n")
                writer.write("format ascii 1.0\n")
                writer.write("comment Created by Remy3D Gaussian Splatting Engine\n")
                writer.write("element vertex ${points.size}\n")
                writer.write("property float x\n")
                writer.write("property float y\n")
                writer.write("property float z\n")
                writer.write("property uchar red\n")
                writer.write("property uchar green\n")
                writer.write("property uchar blue\n")
                writer.write("property uchar alpha\n")
                writer.write("property float size\n")
                writer.write("property float scale_0\n")
                writer.write("property float scale_1\n")
                writer.write("property float scale_2\n")
                writer.write("property float rot_0\n")
                writer.write("property float rot_1\n")
                writer.write("property float rot_2\n")
                writer.write("property float rot_3\n")
                writer.write("end_header\n")

                // 顶点数据
                for (p in points) {
                    val r = (p.r * 255f).toInt().coerceIn(0, 255)
                    val g = (p.g * 255f).toInt().coerceIn(0, 255)
                    val b = (p.b * 255f).toInt().coerceIn(0, 255)
                    val a = (p.alpha * 255f).toInt().coerceIn(0, 255)
                    writer.write(
                        String.format(
                            java.util.Locale.US,
                            "%.4f %.4f %.4f %d %d %d %d %.4f %.4f %.4f %.4f %.4f %.4f %.4f %.4f\n",
                            p.x, p.y, p.z,
                            r, g, b, a,
                            p.size,
                            p.scaleX, p.scaleY, p.scaleZ,
                            p.rotW, p.rotX, p.rotY, p.rotZ
                        )
                    )
                }
                writer.flush()
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
