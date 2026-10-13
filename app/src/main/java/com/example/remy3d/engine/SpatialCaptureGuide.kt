package com.example.remy3d.engine

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import kotlin.math.*

/**
 * 拍照环绕方向与姿态指引器 (集成原生音效、马达振动触觉反馈与 36 单元空间立体雷达)
 *
 * 核心目标：
 * 1. 监测用户手机当前的环绕角度（Yaw 360°）与俯仰角（Pitch 垂直角度）
 * 2. 检查 360° 全景环绕与多高度层（水平、俯角、仰角）的覆盖完成度
 * 3. 实时判断并提示用户："请向右移动 30 度"、"视角过低/过高"、"当前角度已拍，请换角度" 等
 * 4. 触觉与听觉闭环：成功锁定未拍新视角时产生短促声响与触觉震动，大幅增强实境沉浸感
 */
class SpatialCaptureGuide(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    private var toneGenerator: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 80)
    } catch (_: Exception) { null }

    // 步频与慢步位移检测回调（提供给 ARCoreManager 估算行走距离）
    var onStepDetected: ((meters: Float, yaw: Float) -> Unit)? = null
    // 姿态角实时变化回调（提供给 UI 层无延迟刷新）
    var onAttitudeChanged: ((yaw: Float, pitch: Float, roll: Float) -> Unit)? = null
    private var lastStepTimeMs: Long = 0
    private var lastAccelNorm: Float = 9.8f
    private val lastGravity = FloatArray(3)
    private val lastGeomagnetic = FloatArray(3)
    private var hasGravity = false
    private var hasGeomagnetic = false

    // 当前手机空间姿态 (度数)
    @Volatile var currentYawDeg: Float = 0f       // 0 ~ 360° 水平朝向
    @Volatile var currentPitchDeg: Float = 0f     // -90° ~ +90° 俯仰角 (正为向下俯看物体，0°~30°为正常竖屏平视，负为向上仰视)
    @Volatile var currentRollDeg: Float = 0f      // 横向左右倾斜角 (0°为水平端正握持)

    // 空间半球多视角网格架构：
    // 垂直 3 层：
    // [0] 顶部俯视角 (Top Pitch: 30° ~ 75°) —— 拍物体顶部特征与开口
    // [1] 中间平视角 (Mid Pitch: 0° ~ 30°)  —— 拍物体正面主立面纹理
    // [2] 底部仰视角 (Bottom Pitch: -30° ~ 0°) —— 拍物体底座与悬空细节
    // 水平 12 扇区 (每个扇区 30°)：
    // 总计 3 x 12 = 36 个空间观测单元格 (Hemispherical Coverage Dome)
    val sectorCoverage = Array(3) { BooleanArray(12) { false } }

    enum class ScanMode {
        OBJECT,          // 单体实物模式 (手办、物件向心环绕与主体锁定)
        SPATIAL_SCENE    // 三维空间场景记忆模式 (房间、客厅空间漫游与环境大场景)
    }

    var scanMode: ScanMode = ScanMode.SPATIAL_SCENE // 默认开启三维空间场景模式

    // 扫描目标主体空间与视觉一致性追踪引擎 (Target Lock，在 OBJECT 模式自动启动防止拍错物体)
    val targetTracker = TargetTrackerEngine()
    @Volatile var targetDeviationWarning: String? = null

    // 记录已经拍摄的拍摄点角度列表
    private val capturedPoses = mutableListOf<Pair<Float, Float>>() // Pair<Yaw, Pitch>

    fun start() {
        if (rotationVectorSensor != null) {
            sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_GAME)
        } else {
            gravitySensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
            magneticSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        }
        accelSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    /**
     * 重置所有方位与覆盖数据 (在模式切换或重新开始新扫描时调用)
     */
    fun resetCoverage() {
        for (l in 0 until 3) {
            for (s in 0 until 12) {
                sectorCoverage[l][s] = false
            }
        }
        capturedPoses.clear()
        targetTracker.resetTarget()
        targetDeviationWarning = null
    }

    private fun updateAttitudeFromRotationMatrix(rotationMatrix: FloatArray) {
        // 1. 消除竖屏万向节死锁 (Gimbal Lock) 计算水平朝向角 Yaw (0 ~ 360°)
        val orientationAngles = FloatArray(3)
        if (abs(rotationMatrix[7]) > 0.5f) {
            val remapped = FloatArray(9)
            SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped)
            SensorManager.getOrientation(remapped, orientationAngles)
        } else {
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
        }
        var yaw = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
        if (yaw < 0f) yaw += 360f

        // 2. 计算后置相机光轴 (-Z) 的真实俯仰角 Pitch：
        // rotationMatrix[8] 为设备 +Z 轴在世界坐标系 Up 方向的分量：
        // 竖屏平视前方时约为 0°~25° (平视层)；手机抬高向下俯拍时 >= 30° (俯视层)；镜头向上仰拍时 < 0° (仰视层)
        val pitch = Math.toDegrees(asin(rotationMatrix[8].toDouble().coerceIn(-1.0, 1.0))).toFloat()

        // 3. 计算手机左右横向倾斜角 Roll (rotationMatrix[6] 为设备 +X 轴在世界 Up 方向的分量，无竖屏死锁)
        val roll = Math.toDegrees(asin(rotationMatrix[6].toDouble().coerceIn(-1.0, 1.0))).toFloat()

        currentYawDeg = yaw
        currentPitchDeg = pitch
        currentRollDeg = roll
        onAttitudeChanged?.invoke(yaw, pitch, roll)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                val rotationMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                updateAttitudeFromRotationMatrix(rotationMatrix)
            }
            Sensor.TYPE_GRAVITY -> {
                System.arraycopy(event.values, 0, lastGravity, 0, 3)
                hasGravity = true
                if (hasGeomagnetic) {
                    val r = FloatArray(9)
                    if (SensorManager.getRotationMatrix(r, null, lastGravity, lastGeomagnetic)) {
                        updateAttitudeFromRotationMatrix(r)
                    }
                }
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, lastGeomagnetic, 0, 3)
                hasGeomagnetic = true
                if (hasGravity) {
                    val r = FloatArray(9)
                    if (SensorManager.getRotationMatrix(r, null, lastGravity, lastGeomagnetic)) {
                        updateAttitudeFromRotationMatrix(r)
                    }
                }
            }
        }

        if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION || event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            if (rotationVectorSensor == null && event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                System.arraycopy(event.values, 0, lastGravity, 0, 3)
                hasGravity = true
                if (hasGeomagnetic) {
                    val r = FloatArray(9)
                    if (SensorManager.getRotationMatrix(r, null, lastGravity, lastGeomagnetic)) {
                        updateAttitudeFromRotationMatrix(r)
                    }
                }
            }
            val ax = event.values[0]; val ay = event.values[1]; val az = event.values[2]
            val norm = sqrt(ax * ax + ay * ay + az * az)
            val now = System.currentTimeMillis()
            // 简单的步态峰值检测 (波峰大于阈值且间隔 > 320ms)
            if (norm > 1.2f && abs(norm - lastAccelNorm) > 0.8f && (now - lastStepTimeMs) > 320) {
                lastStepTimeMs = now
                onStepDetected?.invoke(0.6f, currentYawDeg) // 正常一步约 0.6 米
            }
            lastAccelNorm = norm
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * 用户拍照后记录当前拍摄位姿并给出物理触觉/音效反馈
     */
    @SuppressLint("MissingPermission")
    fun recordShot() {
        val yaw = currentYawDeg
        val pitch = currentPitchDeg
        capturedPoses.add(Pair(yaw, pitch))

        val sIdx = ((yaw / 30f).toInt()) % 12
        val layerIdx = when {
            pitch >= 30f -> 0  // 顶部俯视层
            pitch >= 0f  -> 1  // 中部平视层
            else         -> 2  // 底部低角仰视层
        }
        sectorCoverage[layerIdx][sIdx] = true

        // 触觉与音效反馈 (原生 Vibrator 与 ToneGenerator)
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(45)
            }
        } catch (_: Exception) {}
    }

    /**
     * 当前所处的高度层：0-俯视顶部, 1-平视主层, 2-低角仰视
     */
    fun getCurrentLayerIndex(): Int {
        return when {
            currentPitchDeg >= 30f -> 0
            currentPitchDeg >= 0f  -> 1
            else                   -> 2
        }
    }

    /**
     * 垂直三层的整体覆盖比例：返回 Triple(俯视层覆盖率, 平视层覆盖率, 仰视层覆盖率)
     */
    fun getLayerCoverages(): Triple<Float, Float, Float> {
        var top = 0; var mid = 0; var bot = 0
        for (i in 0 until 12) {
            if (sectorCoverage[0][i]) top++
            if (sectorCoverage[1][i]) mid++
            if (sectorCoverage[2][i]) bot++
        }
        return Triple(top / 12f, mid / 12f, bot / 12f)
    }

    /**
     * 计算总体 36 个空间立体网格的覆盖率 (0.0 ~ 1.0)
     */
    fun calculateCoverage(): Float {
        var filled = 0
        for (l in 0 until 3) {
            for (s in 0 until 12) {
                if (sectorCoverage[l][s]) filled++
            }
        }
        // 拍满 20 个不同立体方位格即可视为高质量实物扫描
        return (filled / 20f).coerceIn(0f, 1f)
    }

    /**
     * 获取指定高度层的水平扇区覆盖情况 (0:顶部, 1:中部, 2:底部)
     */
    fun getLayerRingCoverage(layer: Int): BooleanArray {
        return sectorCoverage[layer.coerceIn(0, 2)]
    }

    /**
     * 综合 12 个方向是否有任一高度层拍过
     */
    fun getHorizontalRingCoverage(): BooleanArray {
        val ring = BooleanArray(12)
        for (i in 0 until 12) {
            ring[i] = sectorCoverage[0][i] || sectorCoverage[1][i] || sectorCoverage[2][i]
        }
        return ring
    }

    /**
     * 智能引导算法：输出当前需要执行的导航建议与警告 (全面兼顾水平环绕与上下高度俯仰)
     */
    fun evaluateGuide(): GuidanceFeedback {
        // 1. 若为单体实物模式：执行扫描目标主体偏离检测 (防止拍向其他物体或杂乱背景)
        if (scanMode == ScanMode.OBJECT) {
            if (targetTracker.currentStatus == TargetTrackerEngine.TargetStatus.LOCKED ||
                targetTracker.currentStatus == TargetTrackerEngine.TargetStatus.NOT_SET
            ) {
                targetDeviationWarning = null
            }
            if (targetDeviationWarning != null) {
                return GuidanceFeedback(
                    prompt = targetDeviationWarning!!,
                    directionDeg = 0f,
                    isWarning = true,
                    targetLevel = "偏离扫描目标 ✕"
                )
            }
            if (targetTracker.currentStatus == TargetTrackerEngine.TargetStatus.DEVIATED_OBJECT) {
                return GuidanceFeedback(
                    prompt = "已偏离目标物体！检测到镜头拍向其他杂物，请重新对准扫描目标",
                    directionDeg = 0f,
                    isWarning = true,
                    targetLevel = "非目标物体 ✕"
                )
            }
            if (targetTracker.currentStatus == TargetTrackerEngine.TargetStatus.DEVIATED_DISTANCE) {
                return GuidanceFeedback(
                    prompt = "物距漂移！当前焦平面脱离了目标主体，请保持稳定距离",
                    directionDeg = 0f,
                    isWarning = true,
                    targetLevel = "物距偏离 ✕"
                )
            }
        }

        // 2. 姿态横向倾斜警告
        if (abs(currentRollDeg) > 40f) {
            return GuidanceFeedback(
                prompt = "手机横向倾斜，请平稳握持",
                directionDeg = 0f,
                isWarning = true,
                targetLevel = "保持机身水平"
            )
        }

        // 若为三维空间场景记忆模式 (Spatial Scene Mode)
        if (scanMode == ScanMode.SPATIAL_SCENE) {
            val (topCov, midCov, botCov) = getLayerCoverages()
            val totalCoverage = calculateCoverage()

            if (capturedPoses.size < 4) {
                return GuidanceFeedback(
                    prompt = "【空间构建】请缓慢在房间内向前行走，环顾四周墙壁与地面",
                    directionDeg = 0f,
                    isWarning = false,
                    targetLevel = "起步空间探索"
                )
            }

            if (topCov < 0.2f) {
                return GuidanceFeedback(
                    prompt = "【空间高度】请稍微抬高手机，扫描天花板与吊顶结构",
                    directionDeg = 0f,
                    isWarning = false,
                    targetLevel = "补扫上方空间"
                )
            }

            if (botCov < 0.2f) {
                return GuidanceFeedback(
                    prompt = "【空间地面】请稍微下倾镜头，捕捉地板与地毯纵深纹理",
                    directionDeg = 0f,
                    isWarning = false,
                    targetLevel = "补扫地面细节"
                )
            }

            return GuidanceFeedback(
                prompt = "【空间记忆良好】已连续捕捉 ${(totalCoverage * 100).toInt()}% 环境特征点，继续环视",
                directionDeg = 0f,
                isWarning = false,
                targetLevel = "三维空间扩展中 ✓"
            )
        }

        val curLayer = getCurrentLayerIndex()
        val curSector = ((currentYawDeg / 30f).toInt()) % 12
        val (topCov, midCov, botCov) = getLayerCoverages()

        // 2. 检查距离上次拍摄是否角度过于雷同
        if (capturedPoses.isNotEmpty()) {
            val last = capturedPoses.last()
            val diffYaw = min(abs(currentYawDeg - last.first), 360f - abs(currentYawDeg - last.first))
            val diffPitch = abs(currentPitchDeg - last.second)
            if (diffYaw < 12f && diffPitch < 10f) {
                return GuidanceFeedback(
                    prompt = "当前角度已拍摄！请移动或改变高低俯仰",
                    directionDeg = 30f,
                    isWarning = true,
                    targetLevel = "请换新视角"
                )
            }
        }

        // 3. 上下高度层缺失侦测引导（提醒用户拍摄物体顶部或底部）
        if (midCov >= 0.5f && topCov < 0.25f && curLayer != 0) {
            return GuidanceFeedback(
                prompt = "中层已充足！请将手机稍抬高，俯拍物体顶部 (40°~60°)",
                directionDeg = 0f,
                isWarning = false,
                targetLevel = "需补拍顶部俯角"
            )
        }
        if (midCov >= 0.5f && topCov >= 0.3f && botCov < 0.2f && curLayer != 2) {
            return GuidanceFeedback(
                prompt = "请稍蹲下或压低镜头，仰拍物体下沿与底座细节",
                directionDeg = 0f,
                isWarning = false,
                targetLevel = "需补拍底部仰角"
            )
        }

        // 4. 当前方位格判断
        if (!sectorCoverage[curLayer][curSector]) {
            val layerName = when(curLayer) { 0 -> "顶部俯视"; 1 -> "正侧平视"; else -> "底部仰视" }
            return GuidanceFeedback(
                prompt = "【$layerName】新视角捕捉有效，点击拍摄！",
                directionDeg = 0f,
                isWarning = false,
                targetLevel = "未采集角度 ✓"
            )
        }

        // 5. 当前高度方位已拍过，向用户指引离得最近的未拍扇区方向
        val ring = sectorCoverage[curLayer]
        var nextRight = (curSector + 1) % 12
        var stepsRight = 1
        while (stepsRight < 12 && ring[nextRight]) {
            nextRight = (nextRight + 1) % 12
            stepsRight++
        }

        return if (stepsRight <= 6) {
            GuidanceFeedback(
                prompt = "请顺时针绕物体向右移动约 ${stepsRight * 30}°",
                directionDeg = (stepsRight * 30).toFloat(),
                isWarning = false,
                targetLevel = "缺少侧后方视角"
            )
        } else {
            GuidanceFeedback(
                prompt = "请逆时针绕物体向左移动拍摄缺失角度",
                directionDeg = -30f,
                isWarning = false,
                targetLevel = "补齐左侧视角"
            )
        }
    }

    data class GuidanceFeedback(
        val prompt: String,
        val directionDeg: Float, // 建议移动方向相对当前角
        val isWarning: Boolean,
        val targetLevel: String
    )
}
