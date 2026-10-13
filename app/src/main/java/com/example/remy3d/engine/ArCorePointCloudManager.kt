package com.example.remy3d.engine

import android.content.Context
import com.google.ar.core.*
import java.nio.FloatBuffer
import kotlin.math.*

/**
 * Google ARCore 空间实境与特征点云管理器 (支持 6-DoF 空间轨迹与大尺度空间记忆构建)
 *
 * 核心技术实现：
 * 1. 硬件兼容性动态探测：检测设备是否具备 ARCore 原生运行环境；
 * 2. SLAM 空间追踪：启动 ARCore 空间定位会话，从真实世界提取具备物理绝对尺度的特征点云；
 * 3. 6-DoF 空间位姿走位轨迹追踪 (Camera 6-DoF Trajectory):
 *    记录用户在三维室内/场景中的行走距离、空间探索长宽范围 (如 3.2m x 2.4m)，指引大空间穿梭扫描；
 * 4. 融合增强：将物理激光/特征点注入离线反投影点云，大幅提高真实比例尺精度。
 */
class ArCorePointCloudManager(private val context: Context) {

    private var arSession: Session? = null
    var isArSupported: Boolean = false
        private set

    // 空间行走轨迹与空间尺寸记录 (空间大场景重构)
    val trajectoryPoints = mutableListOf<FloatArray>() // List of [x, y, z]
    var totalWalkDistance: Float = 0f
        private set
    var spatialBoundX: Float = 0f // 空间宽度 (米)
        private set
    var spatialBoundZ: Float = 0f // 空间进深 (米)
        private set

    init {
        checkSupport()
    }

    private fun checkSupport() {
        try {
            val availability = ArCoreApk.getInstance().checkAvailability(context)
            isArSupported = availability.isSupported
        } catch (_: Exception) {
            isArSupported = false
        }
    }

    /**
     * 启动或初始化 ARCore 会话（若硬件和环境支持）
     */
    fun createSession(): Boolean {
        if (!isArSupported) return false
        return try {
            if (arSession == null) {
                arSession = Session(context).apply {
                    val config = Config(this).apply {
                        focusMode = Config.FocusMode.AUTO
                        updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                        // 开启原生深度模式（若设备支持）
                        if (isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                            depthMode = Config.DepthMode.AUTOMATIC
                        }
                    }
                    configure(config)
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 重置空间轨迹记录
     */
    fun resetTrajectory() {
        trajectoryPoints.clear()
        totalWalkDistance = 0f
        spatialBoundX = 0f
        spatialBoundZ = 0f
    }

    /**
     * 仅更新相机位姿与走动距离 (无需抽取全量特征点云，用于预览实时轨迹计算)
     */
    fun updatePoseOnly() {
        val session = arSession ?: return
        try {
            val frame = session.update()
            val camPose = frame.camera.pose
            val curX = camPose.tx()
            val curY = camPose.ty()
            val curZ = camPose.tz()

            if (trajectoryPoints.isNotEmpty()) {
                val last = trajectoryPoints.last()
                val dx = curX - last[0]
                val dy = curY - last[1]
                val dz = curZ - last[2]
                val dist = sqrt(dx * dx + dy * dy + dz * dz)
                if (dist > 0.08f) { // 降低步进阈值至 8cm，使得平滑慢走也能实时灵敏累加米数
                    totalWalkDistance += dist
                    trajectoryPoints.add(floatArrayOf(curX, curY, curZ))
                    updateSpatialBounds()
                }
            } else {
                trajectoryPoints.add(floatArrayOf(curX, curY, curZ))
            }
        } catch (_: Exception) {}
    }

    /**
     * 手动追加位移（用于无 ARCore 硬件支持时的传感器航位推算/步频估算备选方案）
     */
    fun addEstimatedDistance(meters: Float, yawDeg: Float) {
        if (meters <= 0f) return
        totalWalkDistance += meters
        val rad = Math.toRadians(yawDeg.toDouble())
        val curX = if (trajectoryPoints.isNotEmpty()) trajectoryPoints.last()[0] + (meters * sin(rad)).toFloat() else (meters * sin(rad)).toFloat()
        val curZ = if (trajectoryPoints.isNotEmpty()) trajectoryPoints.last()[2] + (meters * cos(rad)).toFloat() else (meters * cos(rad)).toFloat()
        trajectoryPoints.add(floatArrayOf(curX, 0f, curZ))
        updateSpatialBounds()
    }

    /**
     * 从 ARCore 当前帧抽取真实世界 3D 特征点点云与 6-DoF 空间位姿
     * 返回点云格式：SplatPoint 集合
     */
    fun extractPointsFromCurrentFrame(): List<SplatPoint> {
        val session = arSession ?: return emptyList()
        val points = mutableListOf<SplatPoint>()

        try {
            val frame = session.update()

            // 1. 抽取当前相机空间 6-DoF 姿态并累加轨迹
            val camPose = frame.camera.pose
            val curX = camPose.tx()
            val curY = camPose.ty()
            val curZ = camPose.tz()

            if (trajectoryPoints.isNotEmpty()) {
                val last = trajectoryPoints.last()
                val dx = curX - last[0]
                val dy = curY - last[1]
                val dz = curZ - last[2]
                val dist = sqrt(dx * dx + dy * dy + dz * dz)
                if (dist > 0.08f) {
                    totalWalkDistance += dist
                    trajectoryPoints.add(floatArrayOf(curX, curY, curZ))
                    updateSpatialBounds()
                }
            } else {
                trajectoryPoints.add(floatArrayOf(curX, curY, curZ))
            }

            // 2. 抽取真实世界点云
            val pointCloud = frame.acquirePointCloud()
            val pointsBuffer: FloatBuffer = pointCloud.points // 每个点 [x, y, z, confidence]

            val count = pointsBuffer.remaining() / 4
            for (i in 0 until count) {
                val x = pointsBuffer.get()
                val y = pointsBuffer.get()
                val z = pointsBuffer.get()
                val confidence = pointsBuffer.get()

                // 提取置信度高的空间特征点
                if (confidence > 0.35f) {
                    points.add(
                        SplatPoint(
                            x = x,
                            y = y,
                            z = z,
                            r = 0.45f + confidence * 0.5f,
                            g = 0.75f,
                            b = 0.95f,
                            size = 0.025f,
                            alpha = confidence.coerceIn(0.6f, 0.95f)
                        )
                    )
                }
            }
            pointCloud.release()
        } catch (_: Exception) {}

        return points
    }

    private fun updateSpatialBounds() {
        if (trajectoryPoints.isEmpty()) return
        var minX = Float.MAX_VALUE; var maxX = Float.MIN_VALUE
        var minZ = Float.MAX_VALUE; var maxZ = Float.MIN_VALUE
        for (pt in trajectoryPoints) {
            minX = min(minX, pt[0])
            maxX = max(maxX, pt[0])
            minZ = min(minZ, pt[2])
            maxZ = max(maxZ, pt[2])
        }
        spatialBoundX = max(0.2f, maxX - minX)
        spatialBoundZ = max(0.2f, maxZ - minZ)
    }

    fun pause() {
        try { arSession?.pause() } catch (_: Exception) {}
    }

    fun resume() {
        try { arSession?.resume() } catch (_: Exception) {}
    }

    fun close() {
        try {
            arSession?.close()
            arSession = null
        } catch (_: Exception) {}
    }
}
