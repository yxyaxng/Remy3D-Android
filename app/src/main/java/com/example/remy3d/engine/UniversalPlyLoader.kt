package com.example.remy3d.engine

import android.content.Context
import android.net.Uri
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * UniversalPlyLoader – 通用工业级 PLY 文件解析器
 *
 * 全面支持：
 * 1. 标准 ASCII 格式 PLY
 * 2. 二进制小端序 binary_little_endian 格式 PLY (3DGS 官方训练输出的标准格式，如 Postshot, Inria, Nerfstudio)
 * 3. 灵活映射属性：x, y, z, red/diffuse_red, green/diffuse_green, blue/diffuse_blue, alpha, scale_0, opacity 等
 * 4. 支持从本地文件 File 以及 Android SAF Uri (通过 ContentResolver) 直接读取
 */
object UniversalPlyLoader {

    fun loadFromUri(context: Context, uri: Uri): List<SplatPoint> {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return emptyList()
        return loadFromStream(inputStream)
    }

    fun loadFromFile(file: File): List<SplatPoint> {
        if (!file.exists()) return emptyList()
        return loadFromStream(FileInputStream(file))
    }

    private fun loadFromStream(inputStream: InputStream): List<SplatPoint> {
        val bis = BufferedInputStream(inputStream, 65536)
        val headerLines = mutableListOf<String>()

        // 1. 读取 PLY Header（按行读取直到 end_header）
        var isBinary = false
        var vertexCount = 0
        val propertyList = mutableListOf<PlyProperty>()

        while (true) {
            val line = readLine(bis) ?: break
            headerLines.add(line)
            if (line.startsWith("format binary_little_endian")) {
                isBinary = true
            } else if (line.startsWith("element vertex")) {
                val parts = line.split(" ")
                vertexCount = parts.last().toIntOrNull() ?: 0
            } else if (line.startsWith("property")) {
                val parts = line.split(" ")
                if (parts.size >= 3) {
                    val type = parts[1]
                    val name = parts[2]
                    propertyList.add(PlyProperty(name, type))
                }
            } else if (line == "end_header") {
                break
            }
        }

        if (vertexCount <= 0) {
            bis.close()
            return emptyList()
        }

        // 2. 根据格式分发解析
        return if (isBinary) {
            parseBinary(bis, vertexCount, propertyList)
        } else {
            parseAscii(bis, vertexCount, propertyList)
        }.also {
            bis.close()
        }
    }

    private data class PlyProperty(val name: String, val type: String)

    /**
     * 解析二进制小端序 PLY (Binary Little Endian)
     */
    private fun parseBinary(
        bis: BufferedInputStream,
        vertexCount: Int,
        properties: MutableList<PlyProperty>
    ): List<SplatPoint> {
        val points = mutableListOf<SplatPoint>()

        // 计算每个顶点的字节跨度
        var stride = 0
        var xOffset = -1; var yOffset = -1; var zOffset = -1
        var rOffset = -1; var gOffset = -1; var bOffset = -1
        var aOffset = -1
        var aType = "uchar"
        var scale0Offset = -1; var scale1Offset = -1; var scale2Offset = -1
        var rot0Offset = -1; var rot1Offset = -1; var rot2Offset = -1; var rot3Offset = -1
        var rType = "uchar"; var gType = "uchar"; var bType = "uchar"
        var isSphericalHarmonics = false

        for (prop in properties) {
            val size = when (prop.type) {
                "char", "uchar", "int8", "uint8" -> 1
                "short", "ushort", "int16", "uint16" -> 2
                "int", "uint", "int32", "uint32", "float", "float32" -> 4
                "double", "float64" -> 8
                else -> 4
            }

            val pName = prop.name.lowercase()
            when {
                pName == "x" -> xOffset = stride
                pName == "y" -> yOffset = stride
                pName == "z" -> zOffset = stride
                // 3DGS 工业标准（Kiri Engine / 3D Gaussian Splatting / Postshot 输出）：f_dc_0, f_dc_1, f_dc_2 球谐基函数系数
                pName == "f_dc_0" -> { rOffset = stride; rType = prop.type; isSphericalHarmonics = true }
                pName == "f_dc_1" -> { gOffset = stride; gType = prop.type; isSphericalHarmonics = true }
                pName == "f_dc_2" -> { bOffset = stride; bType = prop.type; isSphericalHarmonics = true }
                // 经典 RGB 属性
                rOffset == -1 && pName in listOf("red", "diffuse_red", "r") -> { rOffset = stride; rType = prop.type }
                gOffset == -1 && pName in listOf("green", "diffuse_green", "g") -> { gOffset = stride; gType = prop.type }
                bOffset == -1 && pName in listOf("blue", "diffuse_blue", "b") -> { bOffset = stride; bType = prop.type }
                pName in listOf("alpha", "opacity", "a") -> { aOffset = stride; aType = prop.type }
                // 3DGS 协方差缩放与旋转属性 (Kiri / Inria 标准)
                pName == "scale_0" -> scale0Offset = stride
                pName == "scale_1" -> scale1Offset = stride
                pName == "scale_2" -> scale2Offset = stride
                pName == "rot_0" -> rot0Offset = stride
                pName == "rot_1" -> rot1Offset = stride
                pName == "rot_2" -> rot2Offset = stride
                pName == "rot_3" -> rot3Offset = stride
            }
            stride += size
        }

        if (xOffset == -1 || yOffset == -1 || zOffset == -1) return emptyList()

        val buffer = ByteArray(stride)
        val byteBuf = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)

        // 采样控制：若外部 PLY 点数极大（如百万点），做自适应步进抽样防止手机 GPU OOM
        val maxTargetPoints = 50000
        val sampleStep = if (vertexCount > maxTargetPoints) vertexCount / maxTargetPoints else 1
        val SH_C0 = 0.28209479177387814f // 球谐零阶基函数常数 Y_0^0 = 1 / (2 * sqrt(pi))

        for (i in 0 until vertexCount) {
            var readBytes = 0
            while (readBytes < stride) {
                val r = bis.read(buffer, readBytes, stride - readBytes)
                if (r == -1) break
                readBytes += r
            }
            if (readBytes < stride) break

            if (i % sampleStep != 0) continue

            val x = byteBuf.getFloat(xOffset)
            val y = byteBuf.getFloat(yOffset)
            val z = byteBuf.getFloat(zOffset)

            var r = 0.85f; var g = 0.85f; var b = 0.85f; var a = 0.9f

            if (isSphericalHarmonics) {
                // Kiri Engine / 官方 3DGS 输出：色彩通过球谐函数 (SH) 表达：RGB = 0.5 + SH_C0 * f_dc
                if (rOffset != -1) {
                    val raw = byteBuf.getFloat(rOffset)
                    r = (0.5f + SH_C0 * raw).coerceIn(0f, 1f)
                }
                if (gOffset != -1) {
                    val raw = byteBuf.getFloat(gOffset)
                    g = (0.5f + SH_C0 * raw).coerceIn(0f, 1f)
                }
                if (bOffset != -1) {
                    val raw = byteBuf.getFloat(bOffset)
                    b = (0.5f + SH_C0 * raw).coerceIn(0f, 1f)
                }
            } else {
                if (rOffset != -1) {
                    r = if (rType == "float" || rType == "float32") {
                        val raw = byteBuf.getFloat(rOffset)
                        if (raw > 1.0f) raw / 255f else raw.coerceIn(0f, 1f)
                    } else {
                        (byteBuf.get(rOffset).toInt() and 0xFF) / 255f
                    }
                }
                if (gOffset != -1) {
                    g = if (gType == "float" || gType == "float32") {
                        val raw = byteBuf.getFloat(gOffset)
                        if (raw > 1.0f) raw / 255f else raw.coerceIn(0f, 1f)
                    } else {
                        (byteBuf.get(gOffset).toInt() and 0xFF) / 255f
                    }
                }
                if (bOffset != -1) {
                    b = if (bType == "float" || bType == "float32") {
                        val raw = byteBuf.getFloat(bOffset)
                        if (raw > 1.0f) raw / 255f else raw.coerceIn(0f, 1f)
                    } else {
                        (byteBuf.get(bOffset).toInt() and 0xFF) / 255f
                    }
                }
            }

            if (aOffset != -1) {
                if (aType == "float" || aType == "float32") {
                    val rawA = byteBuf.getFloat(aOffset)
                    // 3DGS 工业标准：opacity 存储为逆 Sigmoid logit
                    // 真实不透明度 alpha = 1 / (1 + exp(-rawA))
                    val sigmoidA = (1.0f / (1.0f + kotlin.math.exp(-rawA))).coerceIn(0f, 1f)
                    // 提升中心实体饱满度，避免背景透黑导致模型暗淡发灰
                    a = (sigmoidA * 1.35f).coerceIn(0.70f, 1.0f)
                } else {
                    val rawA = byteBuf.get(aOffset).toInt() and 0xFF
                    a = (rawA / 255f).coerceIn(0.60f, 1.0f)
                }
            } else {
                a = 0.95f
            }

            // 标准 3DGS 中 scale 存储为 log 尺度 (e^scale)，我们按真实对数底还原并控制合理物理半径
            var sx = 0.035f; var sy = 0.035f; var sz = 0.035f
            if (scale0Offset != -1) {
                val rawS0 = byteBuf.getFloat(scale0Offset)
                sx = kotlin.math.exp(rawS0).coerceIn(0.005f, 0.25f)
            }
            if (scale1Offset != -1) {
                val rawS1 = byteBuf.getFloat(scale1Offset)
                sy = kotlin.math.exp(rawS1).coerceIn(0.005f, 0.25f)
            }
            if (scale2Offset != -1) {
                val rawS2 = byteBuf.getFloat(scale2Offset)
                sz = kotlin.math.exp(rawS2).coerceIn(0.005f, 0.25f)
            }

            var rw = 1.0f; var rx = 0.0f; var ry = 0.0f; var rz = 0.0f
            if (rot0Offset != -1 && rot1Offset != -1 && rot2Offset != -1 && rot3Offset != -1) {
                rw = byteBuf.getFloat(rot0Offset)
                rx = byteBuf.getFloat(rot1Offset)
                ry = byteBuf.getFloat(rot2Offset)
                rz = byteBuf.getFloat(rot3Offset)
            }

            val avgSize = maxOf(sx, maxOf(sy, sz))

            points.add(
                SplatPoint(
                    x = x, y = y, z = z,
                    r = r, g = g, b = b,
                    size = avgSize,
                    alpha = a,
                    scaleX = sx, scaleY = sy, scaleZ = sz,
                    rotW = rw, rotX = rx, rotY = ry, rotZ = rz
                )
            )
        }
        return points
    }

    /**
     * 解析文本 ASCII PLY
     */
    private fun parseAscii(
        bis: BufferedInputStream,
        vertexCount: Int,
        properties: MutableList<PlyProperty>
    ): List<SplatPoint> {
        val reader = BufferedReader(InputStreamReader(bis))
        val points = mutableListOf<SplatPoint>()

        val xIdx = properties.indexOfFirst { it.name.equals("x", true) }
        val yIdx = properties.indexOfFirst { it.name.equals("y", true) }
        val zIdx = properties.indexOfFirst { it.name.equals("z", true) }
        val fdc0Idx = properties.indexOfFirst { it.name.equals("f_dc_0", true) }
        val fdc1Idx = properties.indexOfFirst { it.name.equals("f_dc_1", true) }
        val fdc2Idx = properties.indexOfFirst { it.name.equals("f_dc_2", true) }
        val isSH = fdc0Idx != -1 && fdc1Idx != -1 && fdc2Idx != -1

        val rIdx = if (isSH) fdc0Idx else properties.indexOfFirst { it.name in listOf("red", "diffuse_red", "r") }
        val gIdx = if (isSH) fdc1Idx else properties.indexOfFirst { it.name in listOf("green", "diffuse_green", "g") }
        val bIdx = if (isSH) fdc2Idx else properties.indexOfFirst { it.name in listOf("blue", "diffuse_blue", "b") }
        val aIdx = properties.indexOfFirst { it.name in listOf("alpha", "opacity", "a") }
        val sIdx = properties.indexOfFirst { it.name in listOf("size", "scale_0") }

        val maxTargetPoints = 50000
        val sampleStep = if (vertexCount > maxTargetPoints) vertexCount / maxTargetPoints else 1
        val SH_C0 = 0.28209479177387814f

        var count = 0
        while (count < vertexCount) {
            val line = reader.readLine() ?: break
            count++
            if (count % sampleStep != 0) continue

            val parts = line.trim().split("\\s+".toRegex())
            if (parts.size < 3) continue

            try {
                val x = parts.getOrNull(xIdx)?.toFloat() ?: continue
                val y = parts.getOrNull(yIdx)?.toFloat() ?: continue
                val z = parts.getOrNull(zIdx)?.toFloat() ?: continue

                val r: Float
                val g: Float
                val b: Float

                if (isSH) {
                    val rawF0 = parts.getOrNull(fdc0Idx)?.toFloat() ?: 0f
                    val rawF1 = parts.getOrNull(fdc1Idx)?.toFloat() ?: 0f
                    val rawF2 = parts.getOrNull(fdc2Idx)?.toFloat() ?: 0f
                    r = (0.5f + SH_C0 * rawF0).coerceIn(0f, 1f)
                    g = (0.5f + SH_C0 * rawF1).coerceIn(0f, 1f)
                    b = (0.5f + SH_C0 * rawF2).coerceIn(0f, 1f)
                } else {
                    val rawR = parts.getOrNull(rIdx)?.toFloat() ?: 220f
                    r = if (rawR > 1f) (rawR / 255f).coerceIn(0f, 1f) else rawR.coerceIn(0f, 1f)
                    val rawG = parts.getOrNull(gIdx)?.toFloat() ?: 220f
                    g = if (rawG > 1f) (rawG / 255f).coerceIn(0f, 1f) else rawG.coerceIn(0f, 1f)
                    val rawB = parts.getOrNull(bIdx)?.toFloat() ?: 220f
                    b = if (rawB > 1f) (rawB / 255f).coerceIn(0f, 1f) else rawB.coerceIn(0f, 1f)
                }

                val rawA = parts.getOrNull(aIdx)?.toFloat() ?: 230f
                val a = if (isSH) {
                    (1.0f / (1.0f + kotlin.math.exp(-rawA))).coerceIn(0.70f, 1.0f)
                } else {
                    (if (rawA > 1f) rawA / 255f else rawA).coerceIn(0.60f, 1.0f)
                }
                val s = parts.getOrNull(sIdx)?.toFloat() ?: 0.035f

                points.add(SplatPoint(x, y, z, r, g, b, s, a))
            } catch (_: Exception) {}
        }
        return points
    }

    private fun readLine(inputStream: InputStream): String? {
        val baos = ByteArrayOutputStream()
        var c = inputStream.read()
        if (c == -1) return null
        while (c != -1 && c != '\n'.code) {
            if (c != '\r'.code) {
                baos.write(c)
            }
            c = inputStream.read()
        }
        return baos.toString("UTF-8").trim()
    }
}
