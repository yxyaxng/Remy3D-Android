package com.example.remy3d.gl

import android.graphics.Bitmap
import android.graphics.Matrix as AndroidMatrix
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.remy3d.engine.SplatPoint
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * GaussianRenderer – OpenGL ES 2.0 Gaussian Splat 渲染器
 *
 * 增强特性：
 * 1. 自动计算点云边界框（AABB）与质心，自动居中并归一化缩放
 * 2. 严密的摄像机 Orbit 轨道相机系统，确保缩放与旋转永远围绕物体中心
 * 3. 动态调整 Splat 尺寸，避免放大时过度像素化或缩小成黑斑
 * 4. 内置地面网格参考坐标系，增强空间纵深与方位感知
 */
class GaussianRenderer : GLSurfaceView.Renderer {

    // ── 相机控制（围绕 Target 旋转） ─────────────────────────────
    @Volatile var rotX: Float = 0.25f       // 俯仰角 (Pitch)
    @Volatile var rotY: Float = 0f          // 偏航角 (Yaw)
    @Volatile var zoom: Float = 1.0f        // 缩放系数
    @Volatile var panX: Float = 0f          // 目标平移 X
    @Volatile var panY: Float = 0f          // 目标平移 Y

    // ── 交互控制：360° 自动环绕展台模式 (Auto-Turntable) 与 纯点云/连续高斯实体切换 ──
    @Volatile var isAutoRotate: Boolean = false
    @Volatile var isPointCloudMode: Boolean = true // false=高斯实体连续表面, true=稀疏几何点云透视
    @Volatile var splatDensityScale: Float = 2.4f   // 椭球连续致密融合倍率 (1.0f=原点云, 2.4f=连续实物, 3.5f=超致密曲面)

    // ── 空间沉浸式第一人称漫游模式 (Spatial Walkthrough) ──
    enum class CameraMode {
        ORBIT,          // 外部向心展台模式 (围绕单体实物观察)
        WALKTHROUGH     // 空间第一人称漫游模式 (进入房间/空间内部漫步与360°环视)
    }

    @Volatile var cameraMode: CameraMode = CameraMode.ORBIT
    @Volatile var walkPosX: Float = 0f
    @Volatile var walkPosY: Float = 0f
    @Volatile var walkPosZ: Float = 1.6f
    @Volatile var walkPitch: Float = 0f
    @Volatile var walkYaw: Float = 0f

    fun walkForward(distance: Float) {
        val rad = walkYaw
        walkPosX += distance * kotlin.math.sin(rad)
        walkPosZ += distance * kotlin.math.cos(rad)
    }

    fun walkStrafe(distance: Float) {
        val rad = walkYaw + (Math.PI.toFloat() / 2f)
        walkPosX += distance * kotlin.math.sin(rad)
        walkPosZ += distance * kotlin.math.cos(rad)
    }

    fun resetWalkToCenter() {
        walkPosX = 0f
        walkPosY = 0f
        walkPosZ = 1.6f
        walkPitch = 0f
        walkYaw = 0f
    }

    // 物体包围盒与中心（自动居中）
    @Volatile private var centerX = 0f
    @Volatile private var centerY = 0f
    @Volatile private var centerZ = 0f
    @Volatile private var autoScale = 1.0f

    // ── 模式 ──────────────────────────────────────────────────
    enum class SceneType { ROOM, OBJECT, OUTDOOR, PANDA, DUCK, BEAR }

    var sceneType: SceneType = SceneType.ROOM

    /** 外部点云数据（不为 null 时优先渲染） */
    @Volatile var externalPoints: List<SplatPoint>? = null

    /** 场景信息文本（可在 UI 显示） */
    @Volatile var sceneInfo: String = ""

    // ── 视口尺寸与屏幕快照捕获 ──
    @Volatile var viewportWidth = 0
        private set
    @Volatile var viewportHeight = 0
        private set
    @Volatile private var pendingCaptureCallback: ((Bitmap) -> Unit)? = null

    /** 捕获当前 3D 渲染器呈现的高清帧并返回垂直镜像校正后的 Bitmap */
    fun captureSnapshot(callback: (Bitmap) -> Unit) {
        pendingCaptureCallback = callback
    }

    // ── 内部状态 ──────────────────────────────────────────────
    private var programId = 0
    private var vbo = IntArray(1)
    private var splatCount = 0
    private val mvpMatrix = FloatArray(16)
    private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)

    private val STRIDE_FLOATS = 14
    private val STRIDE_BYTES = STRIDE_FLOATS * 4

    private lateinit var vertexBuf: FloatBuffer
    private var needsReload = false

    // ── Shaders ───────────────────────────────────────────────
    private val VS = """
        uniform mat4 uMVP;
        uniform float uBaseSize;
        attribute vec2 aPos;
        attribute vec3 aCenter;
        attribute float aSize;
        attribute float aAlpha;
        attribute vec3 aColor;
        attribute vec2 aRx;
        attribute vec2 aRy;
        varying float vAlpha;
        varying vec3  vColor;
        varying vec2  vUV;
        void main() {
            vec4 clip = uMVP * vec4(aCenter, 1.0);
            float w = max(clip.w, 0.001);
            vec2 offset = (aPos.x * aRx + aPos.y * aRy) * aSize * uBaseSize * w;
            clip.xy += offset;
            gl_Position = clip;
            vUV    = aPos;
            vAlpha = aAlpha;
            vColor = aColor;
        }
    """.trimIndent()

    private val FS = """
        precision mediump float;
        varying float vAlpha;
        varying vec3  vColor;
        varying vec2  vUV;
        void main() {
            float r2 = dot(vUV, vUV);
            if (r2 > 1.0) discard;
            // 实体高光与致密表面融合：边缘高斯渐变衰减
            float gaussian = exp(-2.2 * r2);
            float finalAlpha = clamp(vAlpha * gaussian, 0.0, 1.0);
            
            // 色彩丰盛度增强算法 (解决 3DGS 线性辐射空间直接显示发灰、暗淡、褪色的问题)：
            vec3 col = vColor;
            // 1. 饱和度提升 (提升 28%)，消除苍白灰蒙感
            float gray = dot(col, vec3(0.299, 0.587, 0.114));
            col = mix(vec3(gray), col, 1.28);
            // 2. 伽马校正 Gamma 1.85 (把 3DGS 物理辐射亮度转换还原至鲜艳绚丽的屏幕色彩)
            col = pow(clamp(col, 0.0, 1.0), vec3(1.0 / 1.85));
            // 3. 高斯中心不透明度平滑强化，防止暗黑背景透入导致物体脏暗
            finalAlpha = smoothstep(0.06, 0.92, finalAlpha);
            
            gl_FragColor = vec4(col, finalAlpha);
        }
    """.trimIndent()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.04f, 0.06f, 0.10f, 1f)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        programId = buildProgram(VS, FS)
        GLES20.glGenBuffers(1, vbo, 0)
        buildAndUpload()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / max(height, 1)
        Matrix.perspectiveM(projMatrix, 0, 50f, ratio, 0.1f, 100f)
    }

    override fun onDrawFrame(gl: GL10?) {
        if (needsReload) {
            buildAndUpload()
            needsReload = false
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (splatCount == 0) return

        GLES20.glUseProgram(programId)

        // 自动展台 360 度缓动自转
        if (isAutoRotate) {
            rotY += 0.008f
        }

        // 依据相机模式计算 View 矩阵：
        val eyeX: Float
        val eyeY: Float
        val eyeZ: Float
        val lookDirX: Float
        val lookDirY: Float
        val lookDirZ: Float

        if (cameraMode == CameraMode.WALKTHROUGH) {
            // 空间沉浸式第一人称漫游体系 (Walkthrough Inside Spatial Memories)
            eyeX = walkPosX
            eyeY = walkPosY
            eyeZ = walkPosZ

            lookDirX = sin(walkYaw) * cos(walkPitch)
            lookDirY = sin(walkPitch)
            lookDirZ = -cos(walkYaw) * cos(walkPitch)

            val targetX = eyeX + lookDirX
            val targetY = eyeY + lookDirY
            val targetZ = eyeZ + lookDirZ

            Matrix.setLookAtM(
                viewMatrix, 0,
                eyeX, eyeY, eyeZ,
                targetX, targetY, targetZ,
                0f, 1f, 0f
            )
        } else {
            // 摄像机 Orbit 外部环绕体系 (单体实物围绕模式)
            val cameraDistance = (3.2f / zoom.coerceIn(0.2f, 8.0f))

            // 观察目标点 Target
            val targetX = panX
            val targetY = panY
            val targetZ = 0f

            // 相机球面轨道位置：绕 (targetX, targetY, targetZ) 旋转
            val cosPitch = cos(rotX)
            val sinPitch = sin(rotX)
            val sinYaw = sin(rotY)
            val cosYaw = cos(rotY)

            eyeX = targetX + cameraDistance * cosPitch * sinYaw
            eyeY = targetY + cameraDistance * sinPitch
            eyeZ = targetZ + cameraDistance * cosPitch * cosYaw

            lookDirX = -sinYaw * cosPitch
            lookDirY = -sinPitch
            lookDirZ = -cosYaw * cosPitch

            Matrix.setLookAtM(
                viewMatrix, 0,
                eyeX, eyeY, eyeZ,
                targetX, targetY, targetZ,
                0f, 1f, 0f
            )
        }

        Matrix.setIdentityM(modelMatrix, 0)
        val tmp = FloatArray(16)
        Matrix.multiplyMM(tmp, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, tmp, 0)

        val uMVP = GLES20.glGetUniformLocation(programId, "uMVP")
        GLES20.glUniformMatrix4fv(uMVP, 1, false, mvpMatrix, 0)

        // 依据当前视角与渲染模式调整 splat 尺寸：实体模式放大高斯重叠，点云模式缩小成微观精细点
        val uBaseSize = GLES20.glGetUniformLocation(programId, "uBaseSize")
        val baseFactor = if (isPointCloudMode) 0.35f else (1.4f * splatDensityScale.coerceIn(1.0f, 4.0f))
        val dynamicSize = baseFactor / sqrt(zoom.coerceAtLeast(0.3f))
        GLES20.glUniform1f(uBaseSize, dynamicSize)

        // 若启用了原生 C++ 3DGS 引擎，则每当相机旋转或缩放时触发极速深度排序与顶点回传
        if (NativeSplatBridge.isAvailable() && nativeLoaded) {
            NativeSplatBridge.nativeUpdateCamera(eyeX, eyeY, eyeZ, lookDirX, lookDirY, lookDirZ)

            vertexBuf.rewind()
            val nativeRenderCount = NativeSplatBridge.nativeFillVertexBuffer(vertexBuf, splatCount * 6 * STRIDE_FLOATS)
            if (nativeRenderCount > 0) {
                vertexBuf.rewind()
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo[0])
                GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, nativeRenderCount * 6 * STRIDE_BYTES, vertexBuf)
            }
        }

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo[0])
        fun attrib(name: String, size: Int, offset: Int) {
            val loc = GLES20.glGetAttribLocation(programId, name)
            if (loc >= 0) {
                GLES20.glEnableVertexAttribArray(loc)
                GLES20.glVertexAttribPointer(loc, size, GLES20.GL_FLOAT, false, STRIDE_BYTES, offset * 4)
            }
        }
        attrib("aPos", 2, 0)
        attrib("aCenter", 3, 2)
        attrib("aSize", 1, 5)
        attrib("aAlpha", 1, 6)
        attrib("aColor", 3, 7)
        attrib("aRx", 2, 10)
        attrib("aRy", 2, 12)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, splatCount * 6)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        // 响应保存到相册请求：使用 glReadPixels 捕获当前帧并进行 Y 轴反转校正
        val cb = pendingCaptureCallback
        if (cb != null && viewportWidth > 0 && viewportHeight > 0) {
            pendingCaptureCallback = null
            val w = viewportWidth
            val h = viewportHeight
            val pixelBuf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
            GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuf)
            pixelBuf.rewind()
            val rawBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            rawBmp.copyPixelsFromBuffer(pixelBuf)
            // OpenGL 屏幕坐标系以左下角为原点，与 Android Bitmap (左上角) 垂直颠倒，镜像翻转复原
            val matrix = AndroidMatrix().apply { postScale(1f, -1f) }
            val flippedBmp = Bitmap.createBitmap(rawBmp, 0, 0, w, h, matrix, true)
            rawBmp.recycle()
            cb(flippedBmp)
        }
    }

    private var nativeLoaded = false

    /** 原生 C++ 高速直读外部 PLY 模型文件并完成 3DGS 深度初始化 */
    fun loadFromNativePly(filePath: String) {
        if (NativeSplatBridge.isAvailable()) {
            NativeSplatBridge.nativeInit()
            val success = NativeSplatBridge.nativeLoadPly(filePath, 120000)
            if (success) {
                nativeLoaded = true
                externalPoints = null
                val pts = NativeSplatBridge.nativeGetPointCount()
                sceneInfo = "$pts 个原生 3DGS 高斯椭球 (深度实时排序)"
                resetView()
                needsReload = true
                return
            }
        }
        nativeLoaded = false
    }

    /** 一键重置相机方位与视距，保证模型始终置于视口正中央 */
    fun resetView() {
        rotX = 0.25f
        rotY = 0f
        zoom = 1.0f
        panX = 0f
        panY = 0f
    }

    /** 快速缩放增减步进 */
    fun adjustZoom(delta: Float) {
        zoom = (zoom * delta).coerceIn(0.2f, 8.0f)
    }

    /**
     * 点击原始照片时，自动将相机视角平滑/精准对齐至该照片采集时的对应视角
     * @param photoIndex 照片在序列中的索引 (0 until totalPhotos)
     * @param totalPhotos 照片总张数
     */
    fun alignCameraToPhoto(photoIndex: Int, totalPhotos: Int) {
        if (totalPhotos <= 0) return
        val arcAngle = if (totalPhotos >= 20) 2 * PI.toFloat() else PI.toFloat() * 0.8f
        val t = photoIndex.toFloat() / max(1, totalPhotos - 1)
        val angle = -arcAngle / 2f + t * arcAngle

        // 在 Orbit 模式下：
        // 目标偏航角 rotY 对齐该相机视角
        rotY = angle
        rotX = 0.15f + 0.10f * sin(t * PI.toFloat())
        panX = 0f
        panY = 0f
        zoom = 1.0f

        // 同时如果处于第一人称空间漫游模式，将第一人称视线与位置也对齐至该机位
        walkYaw = angle
        walkPitch = -0.05f
        walkPosX = 2.0f * sin(angle)
        walkPosZ = 2.0f * cos(angle)
        walkPosY = 0.1f + 0.15f * sin(t * PI.toFloat())
    }

    fun loadPoints(points: List<SplatPoint>) {
        nativeLoaded = false
        externalPoints = points
        sceneInfo = "${points.size} 个点"
        resetView()
        needsReload = true
    }

    fun reloadScene() {
        nativeLoaded = false
        resetView()
        needsReload = true
    }

    private fun buildAndUpload() {
        if (nativeLoaded && NativeSplatBridge.isAvailable()) {
            splatCount = NativeSplatBridge.nativeGetPointCount()
            if (splatCount > 0) {
                vertexBuf = ByteBuffer.allocateDirect(splatCount * 6 * STRIDE_BYTES)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer()
                vertexBuf.rewind()
                val written = NativeSplatBridge.nativeFillVertexBuffer(vertexBuf, splatCount * 6 * STRIDE_FLOATS)
                vertexBuf.rewind()
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo[0])
                GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, splatCount * 6 * STRIDE_BYTES, vertexBuf, GLES20.GL_DYNAMIC_DRAW)
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
                return
            }
        }

        val ext = externalPoints
        val fullPoints = if (ext != null && ext.isNotEmpty()) ext else generatePresetScene()

        if (fullPoints.isEmpty()) {
            splatCount = 0
            return
        }

        // 安全降采样保护：移动端 GLES 2.0 显存与 DirectBuffer 优化，避免海量点导致帧率骤降与 GC 停顿
        val maxSafePoints = 35000
        val rawPoints = if (fullPoints.size > maxSafePoints) {
            val stride = (fullPoints.size + maxSafePoints - 1) / maxSafePoints
            fullPoints.filterIndexed { index, _ -> index % stride == 0 }.take(maxSafePoints)
        } else {
            fullPoints
        }

        // 1. 计算点云包围盒 (AABB) 与质心
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE

        for (p in rawPoints) {
            if (p.x < minX) minX = p.x; if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y; if (p.y > maxY) maxY = p.y
            if (p.z < minZ) minZ = p.z; if (p.z > maxZ) maxZ = p.z
        }

        centerX = (minX + maxX) / 2f
        centerY = (minY + maxY) / 2f
        centerZ = (minZ + maxZ) / 2f

        val spanX = maxX - minX
        val spanY = maxY - minY
        val spanZ = maxZ - minZ
        val maxDim = max(max(spanX, spanY), spanZ).coerceAtLeast(0.01f)
        autoScale = 2.0f / maxDim

        // 2. 将点云居中并施加归一化缩放（增强椭球尺寸，使其相互重叠融合为连续实体表面）
        val normalizedPoints = mutableListOf<SplatPoint>()
        for (p in rawPoints) {
            normalizedPoints.add(
                SplatPoint(
                    x = (p.x - centerX) * autoScale,
                    y = (p.y - centerY) * autoScale,
                    z = (p.z - centerZ) * autoScale,
                    r = p.r, g = p.g, b = p.b,
                    size = (p.size * autoScale * 1.8f).coerceIn(0.035f, 0.12f),
                    alpha = (p.alpha * 1.25f).coerceIn(0.75f, 1.0f),
                    scaleX = p.scaleX * autoScale,
                    scaleY = p.scaleY * autoScale,
                    scaleZ = p.scaleZ * autoScale,
                    rotW = p.rotW, rotX = p.rotX, rotY = p.rotY, rotZ = p.rotZ
                )
            )
        }

        // 3. 添加底部空间透视网格（以物体底部为准），提供视觉定位参照
        val floorY = (minY - centerY) * autoScale - 0.08f
        for (gx in -6..6) {
            for (gz in -6..6) {
                if ((gx + gz) % 2 == 0) {
                    normalizedPoints.add(
                        SplatPoint(
                            x = gx * 0.22f,
                            y = floorY,
                            z = gz * 0.22f,
                            r = 0.25f, g = 0.35f, b = 0.50f,
                            size = 0.012f,
                            alpha = 0.35f
                        )
                    )
                }
            }
        }

        splatCount = normalizedPoints.size
        val quadXY = floatArrayOf(-1f, -1f, 1f, -1f, 1f, 1f, -1f, -1f, 1f, 1f, -1f, 1f)

        vertexBuf = ByteBuffer.allocateDirect(splatCount * 6 * STRIDE_BYTES)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        vertexBuf.rewind()

        val rng = Random(42)
        for (s in normalizedPoints) {
            // 若具有真实的 3DGS 四元数与长短轴参数，则计算真实投影椭圆各向异性主轴；否则使用保底随机柔和朝向
            val hasRealRot = (s.rotW * s.rotW + s.rotX * s.rotX + s.rotY * s.rotY + s.rotZ * s.rotZ) > 0.1f && (s.rotX != 0f || s.rotY != 0f || s.rotZ != 0f)
            val rxx: Float
            val rxy: Float
            val ryx: Float
            val ryy: Float

            if (hasRealRot) {
                // 由四元数 q = (w, x, y, z) 提取在观察投影面上的主轴倾角与扁平拉伸比
                // 3DGS 椭球各向异性：长轴延展，使车身蒙皮、门板完全平整相接，不留点状缝隙
                val qAngle = 2f * atan2(s.rotZ, s.rotW)
                val aspect = (s.scaleX / maxOf(0.001f, s.scaleY)).coerceIn(0.4f, 2.5f)
                val ca = cos(qAngle); val sa = sin(qAngle)
                rxx = ca * aspect; rxy = sa * aspect
                ryx = -sa; ryy = ca
            } else {
                val angle = rng.nextFloat() * PI.toFloat() * 2
                val axisScale = 0.65f + rng.nextFloat() * 0.35f
                val ca = cos(angle); val sa = sin(angle)
                rxx = ca * axisScale; rxy = sa * axisScale
                ryx = -sa; ryy = ca
            }

            for (qi in 0 until 6) {
                vertexBuf.put(quadXY[qi * 2]); vertexBuf.put(quadXY[qi * 2 + 1])
                vertexBuf.put(s.x); vertexBuf.put(s.y); vertexBuf.put(s.z)
                vertexBuf.put(s.size); vertexBuf.put(s.alpha)
                vertexBuf.put(s.r); vertexBuf.put(s.g); vertexBuf.put(s.b)
                vertexBuf.put(rxx); vertexBuf.put(rxy); vertexBuf.put(ryx); vertexBuf.put(ryy)
            }
        }
        vertexBuf.rewind()

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo[0])
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, splatCount * 6 * STRIDE_BYTES, vertexBuf, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    fun generatePresetScene(): List<SplatPoint> {
        return generatePresetPoints(sceneType)
    }

    companion object {
        fun generatePresetPoints(type: SceneType): List<SplatPoint> {
            val rng = Random(42)
            val out = mutableListOf<SplatPoint>()

            when (type) {
            SceneType.ROOM -> {
                // 1. 客厅核心茶几与沙发实体连续表面
                // 沙发靠背与坐垫 (高密度致密网格分布)
                for (x in -25..25) {
                    for (z in -15..15) {
                        val fx = x * 0.045f
                        val fz = z * 0.045f - 0.4f
                        val fy = -0.3f + 0.04f * sin(fx * 3f)
                        out.add(SplatPoint(fx, fy, fz, 0.28f, 0.45f, 0.88f, 0.045f, 0.95f))
                    }
                }
                // 沙发靠背垂直实体面
                for (x in -25..25) {
                    for (y in 0..20) {
                        val fx = x * 0.045f
                        val fy = -0.3f + y * 0.04f
                        val fz = -1.05f + 0.02f * sin(fy * 2f)
                        out.add(SplatPoint(fx, fy, fz, 0.22f, 0.38f, 0.78f, 0.045f, 0.95f))
                    }
                }
                // 实体茶几台面 (高光大理石色)
                for (x in -18..18) {
                    for (z in -12..12) {
                        val fx = x * 0.035f
                        val fz = z * 0.035f + 0.25f
                        val fy = -0.42f
                        out.add(SplatPoint(fx, fy, fz, 0.88f, 0.85f, 0.82f, 0.038f, 0.98f))
                    }
                }
                // 地面致密反光瓷砖
                for (x in -20..20 step 2) {
                    for (z in -20..20 step 2) {
                        out.add(SplatPoint(x * 0.06f, -0.68f, z * 0.06f, 0.30f, 0.35f, 0.45f, 0.045f, 0.65f))
                    }
                }
            }
            SceneType.OBJECT -> {
                // 2. 青花瓷瓶实物：由旋转体参数方程生成的致密连续实体闭合表面
                val heightSteps = 80
                val angleSteps = 48
                for (hi in 0 until heightSteps) {
                    val t = hi.toFloat() / heightSteps
                    val y = (t - 0.5f) * 2.0f
                    // 花瓶优美瓶口、束颈、鼓腹、圈足轮廓曲线
                    val radius = (0.28f + 0.45f * sin(t * PI.toFloat()) - 0.16f * sin(t * 3 * PI.toFloat())).coerceAtLeast(0.12f)
                    val isBluePattern = (hi in 25..50 && hi % 5 < 3) || (hi in 60..70)
                    for (ai in 0 until angleSteps) {
                        val theta = ai.toFloat() / angleSteps * 2 * PI.toFloat()
                        val x = radius * cos(theta)
                        val z = radius * sin(theta)

                        val cr = if (isBluePattern) 0.15f else 0.85f
                        val cg = if (isBluePattern) 0.45f else 0.92f
                        val cb = if (isBluePattern) 0.95f else 0.98f

                        out.add(SplatPoint(x, y, z, cr, cg, cb, 0.048f, 0.98f))
                    }
                }
            }
            SceneType.OUTDOOR -> {
                // 3. 户外立体几何建筑雕塑实体 (连续实体闭合晶体)
                for (u in -15..15) {
                    for (v in -15..15) {
                        val fu = u * 0.06f
                        val fv = v * 0.06f
                        val dist = sqrt(fu * fu + fv * fv)
                        val fy = (1.2f - dist * 1.3f).coerceAtLeast(-0.6f)
                        val cr = 0.25f + dist * 0.4f
                        val cg = 0.85f - dist * 0.2f
                        val cb = 0.65f
                        out.add(SplatPoint(fu, fy, fv, cr, cg, cb, 0.05f, 0.95f))
                    }
                }
            }
            SceneType.PANDA -> {
                // 4. 🐼 国宝大熊猫 (单体实物 3DGS 成品)
                // 身体：厚实丰满的白色躯干
                for (lat in 0..30) {
                    val theta = lat.toFloat() / 30f * PI.toFloat()
                    val sinT = sin(theta); val cosT = cos(theta)
                    val rBody = 0.38f
                    val y = -0.15f + 0.38f * cosT
                    for (lon in 0..36) {
                        val phi = lon.toFloat() / 36f * 2 * PI.toFloat()
                        val x = rBody * sinT * cos(phi)
                        val z = rBody * sinT * sin(phi)
                        val isBlackVest = y in -0.05f..0.15f
                        val cr = if (isBlackVest) 0.08f else 0.96f
                        val cg = if (isBlackVest) 0.08f else 0.96f
                        val cb = if (isBlackVest) 0.10f else 0.98f
                        out.add(SplatPoint(x, y, z, cr, cg, cb, 0.045f, 0.98f))
                    }
                }
                // 头部：圆润可爱的纯白熊头
                for (lat in 0..26) {
                    val theta = lat.toFloat() / 26f * PI.toFloat()
                    val sinT = sin(theta); val cosT = cos(theta)
                    val rHead = 0.30f
                    val y = 0.38f + rHead * cosT
                    for (lon in 0..32) {
                        val phi = lon.toFloat() / 32f * 2 * PI.toFloat()
                        val x = rHead * sinT * cos(phi)
                        val z = 0.08f + rHead * sinT * sin(phi)
                        out.add(SplatPoint(x, y, z, 0.98f, 0.98f, 0.99f, 0.042f, 0.98f))
                    }
                }
                // 黑色圆耳朵 (左耳 + 右耳)
                for (earSide in listOf(-1f, 1f)) {
                    for (i in 0..150) {
                        val ex = earSide * 0.23f + (rng.nextFloat() - 0.5f) * 0.12f
                        val ey = 0.64f + (rng.nextFloat() - 0.5f) * 0.12f
                        val ez = 0.02f + (rng.nextFloat() - 0.5f) * 0.08f
                        out.add(SplatPoint(ex, ey, ez, 0.06f, 0.06f, 0.08f, 0.038f, 0.98f))
                    }
                }
                // 标志性黑眼圈与眼睛高光
                for (eyeSide in listOf(-1f, 1f)) {
                    for (i in 0..100) {
                        val ex = eyeSide * 0.12f + (rng.nextFloat() - 0.5f) * 0.09f
                        val ey = 0.39f + (rng.nextFloat() - 0.5f) * 0.08f
                        val ez = 0.35f + (rng.nextFloat() - 0.5f) * 0.04f
                        out.add(SplatPoint(ex, ey, ez, 0.06f, 0.06f, 0.08f, 0.032f, 0.98f))
                    }
                    out.add(SplatPoint(eyeSide * 0.11f, 0.39f, 0.38f, 0.02f, 0.02f, 0.02f, 0.024f, 1.0f))
                    out.add(SplatPoint(eyeSide * 0.10f, 0.40f, 0.39f, 0.98f, 0.98f, 1.0f, 0.016f, 1.0f))
                }
                // 小黑鼻子
                for (i in 0..40) {
                    val nx = (rng.nextFloat() - 0.5f) * 0.05f
                    val ny = 0.28f + (rng.nextFloat() - 0.5f) * 0.04f
                    val nz = 0.38f + (rng.nextFloat() - 0.5f) * 0.03f
                    out.add(SplatPoint(nx, ny, nz, 0.05f, 0.05f, 0.06f, 0.025f, 0.99f))
                }
                // 黑色双臂
                for (armSide in listOf(-1f, 1f)) {
                    for (t in 0..20) {
                        val progress = t / 20f
                        val ax = armSide * (0.24f + progress * 0.08f)
                        val ay = 0.06f - progress * 0.22f
                        val az = progress * 0.26f
                        for (k in 0..12) {
                            val ox = (rng.nextFloat() - 0.5f) * 0.08f
                            val oy = (rng.nextFloat() - 0.5f) * 0.08f
                            val oz = (rng.nextFloat() - 0.5f) * 0.08f
                            out.add(SplatPoint(ax + ox, ay + oy, az + oz, 0.08f, 0.08f, 0.10f, 0.040f, 0.98f))
                        }
                    }
                }
                // 黑色双腿与坐姿脚掌
                for (legSide in listOf(-1f, 1f)) {
                    for (i in 0..220) {
                        val lx = legSide * 0.26f + (rng.nextFloat() - 0.5f) * 0.14f
                        val ly = -0.48f + (rng.nextFloat() - 0.5f) * 0.10f
                        val lz = 0.18f + (rng.nextFloat() - 0.5f) * 0.18f
                        val isPad = ly < -0.46f && lz > 0.22f && rng.nextFloat() < 0.45f
                        val cr = if (isPad) 0.65f else 0.08f
                        val cg = if (isPad) 0.45f else 0.08f
                        val cb = if (isPad) 0.50f else 0.10f
                        out.add(SplatPoint(lx, ly, lz, cr, cg, cb, 0.042f, 0.98f))
                    }
                }
            }
            SceneType.DUCK -> {
                // 5. 🐤 阳光小黄鸭 (单体实物 3DGS 成品)
                // 鲜黄丰满鸭身
                for (lat in 0..28) {
                    val theta = lat.toFloat() / 28f * PI.toFloat()
                    val sinT = sin(theta); val cosT = cos(theta)
                    val rBody = 0.36f
                    val y = -0.15f + 0.32f * cosT
                    for (lon in 0..34) {
                        val phi = lon.toFloat() / 34f * 2 * PI.toFloat()
                        val tailLift = if (cos(phi) < -0.5f) 0.12f * (1f - sinT) else 0f
                        val x = rBody * sinT * sin(phi)
                        val z = -0.05f + rBody * sinT * cos(phi)
                        out.add(SplatPoint(x, y + tailLift, z, 0.98f, 0.84f, 0.12f, 0.046f, 0.98f))
                    }
                }
                // 鲜黄圆脑袋
                for (lat in 0..24) {
                    val theta = lat.toFloat() / 24f * PI.toFloat()
                    val sinT = sin(theta); val cosT = cos(theta)
                    val rHead = 0.25f
                    val y = 0.30f + rHead * cosT
                    for (lon in 0..30) {
                        val phi = lon.toFloat() / 30f * 2 * PI.toFloat()
                        val x = rHead * sinT * sin(phi)
                        val z = 0.18f + rHead * sinT * cos(phi)
                        out.add(SplatPoint(x, y, z, 0.99f, 0.86f, 0.14f, 0.042f, 0.98f))
                    }
                }
                // 暖橙色扁平鸭嘴
                for (u in -14..14) {
                    for (v in -10..10) {
                        val fu = u * 0.012f
                        val fv = v * 0.016f
                        val bx = fu
                        val by = 0.24f - abs(fu) * 0.3f - fv * 0.2f
                        val bz = 0.38f + (10 - abs(u)) * 0.012f + fv * 0.8f
                        out.add(SplatPoint(bx, by, bz, 0.98f, 0.45f, 0.06f, 0.035f, 0.99f))
                    }
                }
                // 眼睛与高光
                for (eyeSide in listOf(-1f, 1f)) {
                    for (i in 0..40) {
                        val ex = eyeSide * 0.15f + (rng.nextFloat() - 0.5f) * 0.04f
                        val ey = 0.36f + (rng.nextFloat() - 0.5f) * 0.04f
                        val ez = 0.32f + (rng.nextFloat() - 0.5f) * 0.03f
                        out.add(SplatPoint(ex, ey, ez, 0.08f, 0.08f, 0.08f, 0.026f, 1.0f))
                    }
                    out.add(SplatPoint(eyeSide * 0.14f, 0.37f, 0.34f, 0.98f, 0.98f, 1.0f, 0.018f, 1.0f))
                }
                // 身体两侧羽翼
                for (wingSide in listOf(-1f, 1f)) {
                    for (i in 0..160) {
                        val wx = wingSide * (0.34f + (rng.nextFloat() - 0.5f) * 0.06f)
                        val wy = -0.12f + (rng.nextFloat() - 0.5f) * 0.15f
                        val wz = -0.05f + (rng.nextFloat() - 0.5f) * 0.22f
                        out.add(SplatPoint(wx, wy, wz, 0.94f, 0.76f, 0.08f, 0.040f, 0.98f))
                    }
                }
            }
            SceneType.BEAR -> {
                // 6. 🧸 暖心泰迪熊 (单体实物 3DGS 成品)
                // 焦糖棕色绒毛躯干
                for (lat in 0..28) {
                    val theta = lat.toFloat() / 28f * PI.toFloat()
                    val sinT = sin(theta); val cosT = cos(theta)
                    val rBody = 0.36f
                    val y = -0.18f + 0.36f * cosT
                    for (lon in 0..34) {
                        val phi = lon.toFloat() / 34f * 2 * PI.toFloat()
                        val x = rBody * sinT * sin(phi)
                        val z = rBody * sinT * cos(phi)
                        val isBelly = z > 0.22f && y in -0.32f..-0.02f
                        val cr = if (isBelly) 0.88f else 0.65f
                        val cg = if (isBelly) 0.78f else 0.40f
                        val cb = if (isBelly) 0.62f else 0.20f
                        out.add(SplatPoint(x, y, z, cr, cg, cb, 0.045f, 0.98f))
                    }
                }
                // 泰迪熊圆头
                for (lat in 0..26) {
                    val theta = lat.toFloat() / 26f * PI.toFloat()
                    val sinT = sin(theta); val cosT = cos(theta)
                    val rHead = 0.28f
                    val y = 0.32f + rHead * cosT
                    for (lon in 0..32) {
                        val phi = lon.toFloat() / 32f * 2 * PI.toFloat()
                        val x = rHead * sinT * sin(phi)
                        val z = 0.06f + rHead * sinT * cos(phi)
                        out.add(SplatPoint(x, y, z, 0.68f, 0.42f, 0.22f, 0.042f, 0.98f))
                    }
                }
                // 凸起口鼻部
                for (lat in 0..16) {
                    val theta = lat.toFloat() / 16f * PI.toFloat()
                    val sinT = sin(theta); val cosT = cos(theta)
                    val rSnout = 0.14f
                    val y = 0.24f + rSnout * cosT
                    for (lon in 0..20) {
                        val phi = lon.toFloat() / 20f * 2 * PI.toFloat()
                        val x = rSnout * sinT * sin(phi)
                        val z = 0.28f + rSnout * sinT * cos(phi)
                        out.add(SplatPoint(x, y, z, 0.88f, 0.78f, 0.65f, 0.034f, 0.99f))
                    }
                }
                // 小黑鼻子与眼睛
                for (i in 0..45) {
                    val nx = (rng.nextFloat() - 0.5f) * 0.06f
                    val ny = 0.27f + (rng.nextFloat() - 0.5f) * 0.04f
                    val nz = 0.41f + (rng.nextFloat() - 0.5f) * 0.03f
                    out.add(SplatPoint(nx, ny, nz, 0.14f, 0.08f, 0.06f, 0.026f, 1.0f))
                }
                for (eyeSide in listOf(-1f, 1f)) {
                    out.add(SplatPoint(eyeSide * 0.12f, 0.35f, 0.31f, 0.08f, 0.05f, 0.04f, 0.026f, 1.0f))
                    out.add(SplatPoint(eyeSide * 0.11f, 0.36f, 0.32f, 0.98f, 0.98f, 1.0f, 0.016f, 1.0f))
                }
                // 双耳
                for (earSide in listOf(-1f, 1f)) {
                    for (i in 0..120) {
                        val ex = earSide * 0.24f + (rng.nextFloat() - 0.5f) * 0.10f
                        val ey = 0.56f + (rng.nextFloat() - 0.5f) * 0.10f
                        val ez = 0.04f + (rng.nextFloat() - 0.5f) * 0.06f
                        val isInner = ez > 0.05f && rng.nextFloat() < 0.5f
                        val cr = if (isInner) 0.84f else 0.66f
                        val cg = if (isInner) 0.72f else 0.40f
                        val cb = if (isInner) 0.58f else 0.20f
                        out.add(SplatPoint(ex, ey, ez, cr, cg, cb, 0.038f, 0.98f))
                    }
                }
                // 手臂与脚掌
                for (armSide in listOf(-1f, 1f)) {
                    for (i in 0..160) {
                        val ax = armSide * (0.28f + (rng.nextFloat() - 0.5f) * 0.08f)
                        val ay = -0.06f + (rng.nextFloat() - 0.5f) * 0.16f
                        val az = 0.14f + (rng.nextFloat() - 0.5f) * 0.16f
                        out.add(SplatPoint(ax, ay, az, 0.65f, 0.40f, 0.20f, 0.040f, 0.98f))
                    }
                }
                for (legSide in listOf(-1f, 1f)) {
                    for (i in 0..200) {
                        val lx = legSide * 0.24f + (rng.nextFloat() - 0.5f) * 0.12f
                        val ly = -0.46f + (rng.nextFloat() - 0.5f) * 0.10f
                        val lz = 0.15f + (rng.nextFloat() - 0.5f) * 0.16f
                        val isPad = lz > 0.18f && ly < -0.42f && rng.nextFloat() < 0.4f
                        val cr = if (isPad) 0.86f else 0.65f
                        val cg = if (isPad) 0.74f else 0.40f
                        val cb = if (isPad) 0.60f else 0.20f
                        out.add(SplatPoint(lx, ly, lz, cr, cg, cb, 0.042f, 0.98f))
                    }
                }
            }
        }
        return out
        }
    }

    private fun buildProgram(vs: String, fs: String): Int {
        val v = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER).also { GLES20.glShaderSource(it, vs); GLES20.glCompileShader(it) }
        val f = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER).also { GLES20.glShaderSource(it, fs); GLES20.glCompileShader(it) }
        return GLES20.glCreateProgram().also { GLES20.glAttachShader(it, v); GLES20.glAttachShader(it, f); GLES20.glLinkProgram(it) }
    }
}
