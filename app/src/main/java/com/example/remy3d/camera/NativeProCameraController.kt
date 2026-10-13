package com.example.remy3d.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.util.Range
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.SurfaceOrientedMeteringPointFactory

/**
 * 专业相机参数调节数据类
 */
data class ProCameraSettings(
    val exposureCompensationIndex: Int = 0,     // 曝光补偿 (-4 ~ +4 EV step)
    val isoValue: Int = 0,                       // 0 表示自动，否则固定 ISO (100, 200, 400, 800...)
    val shutterTimeNs: Long = 0L,                // 0 表示自动，否则快门时间纳秒 (如 1/100s = 10_000_000L)
    val focusDistance: Float = 0f,               // 0.0f 表示自动对焦，>0 表示手动微距对焦距离 (屈光度 1/m)
    val whiteBalanceMode: Int = CaptureRequest.CONTROL_AWB_MODE_AUTO, // 自动/日光/白炽灯/荧光灯
    val isTorchOn: Boolean = false,              // 恒定补光灯 (消除弱光阴影)
    val zoomRatio: Float = 1.0f                  // 变焦比
)

/**
 * NativeProCameraController – 基于原生 Android Camera2 互操作 API 的专业相机控制器
 *
 * 核心技术实现：
 * 1. 利用 Camera2CameraControl / CaptureRequestOptions 实现对底层 ISP 硬件的直接控制：
 *    - SENSOR_SENSITIVITY (ISO 感光度)
 *    - SENSOR_EXPOSURE_TIME (快门时间)
 *    - LENS_FOCUS_DISTANCE (对焦距离，用于近物高精度微距扫描)
 *    - CONTROL_AWB_MODE (白平衡锁定，避免 3DGS 训练因每张照片色温变化产生色差)
 * 2. 结合 CameraControl.setExposureCompensationIndex 进行硬件 EV 步进控制
 * 3. 针对 3D 扫描重构场景特设：AE/AF 锁定（Auto-Exposure / Auto-Focus Lock），确保环绕拍摄时光照一致性。
 */
@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
class NativeProCameraController {

    private var activeCamera: Camera? = null

    // 硬件能力范围
    var exposureRange: Range<Int> = Range(0, 0)
        private set
    var exposureStep: Float = 0.1666f
        private set
    var zoomRange: ClosedFloatingPointRange<Float> = 1.0f..5.0f
        private set

    /**
     * 绑定当前活跃相机并读取硬件特性
     */
    fun attachCamera(camera: Camera) {
        this.activeCamera = camera
        val cameraInfo = camera.cameraInfo
        val expState = cameraInfo.exposureState
        exposureRange = expState.exposureCompensationRange
        exposureStep = expState.exposureCompensationStep.toFloat()

        val zoomState = cameraInfo.zoomState.value
        if (zoomState != null) {
            zoomRange = zoomState.minZoomRatio..zoomState.maxZoomRatio
        }
    }

    /**
     * 调节曝光补偿 (EV 增益)
     */
    fun setExposureCompensation(index: Int) {
        val cam = activeCamera ?: return
        val clamped = index.coerceIn(exposureRange.lower, exposureRange.upper)
        cam.cameraControl.setExposureCompensationIndex(clamped)
    }

    /**
     * 调节变焦倍数
     */
    fun setZoom(ratio: Float) {
        val cam = activeCamera ?: return
        val clamped = ratio.coerceIn(zoomRange.start, zoomRange.endInclusive)
        cam.cameraControl.setZoomRatio(clamped)
    }

    /**
     * 开关恒定补光灯（扫描暗处物体缝隙时至关重要）
     */
    fun setTorch(enabled: Boolean) {
        activeCamera?.cameraControl?.enableTorch(enabled)
    }

    /**
     * 触屏单点对焦与测光 (Tap to Focus & Metering)
     */
    fun startFocusAndMetering(xNorm: Float, yNorm: Float) {
        val cam = activeCamera ?: return
        val factory = SurfaceOrientedMeteringPointFactory(1f, 1f)
        val point = factory.createPoint(xNorm, yNorm)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3000, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
        cam.cameraControl.startFocusAndMetering(action)
    }

    /**
     * 底层 Camera2 高级参数应用：ISO 感光度、白平衡与手动对焦距离
     */
    fun applyAdvancedSettings(settings: ProCameraSettings) {
        val cam = activeCamera ?: return
        val c2Control = Camera2CameraControl.from(cam.cameraControl)

        val builder = CaptureRequestOptions.Builder()

        // 1. 白平衡控制 (3DGS 扫描要求色温恒定，避免物体不同角度偏红或偏蓝)
        builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, settings.whiteBalanceMode)

        // 2. ISO 感光度与快门速度控制 (若指定手动参数)
        if (settings.isoValue > 0 || settings.shutterTimeNs > 0L) {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            if (settings.isoValue > 0) {
                builder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, settings.isoValue)
            }
            if (settings.shutterTimeNs > 0L) {
                builder.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, settings.shutterTimeNs)
            }
        } else {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        }

        // 3. 手动近距微距对焦 (Focus Distance: 屈光度值，数值越大离镜头越近，如 10.0f 代表 10cm 微距)
        if (settings.focusDistance > 0f) {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            builder.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, settings.focusDistance)
        } else {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        }

        c2Control.setCaptureRequestOptions(builder.build())
    }

    /**
     * 锁定自动曝光与自动对焦 (AE/AF Lock)
     * 3D 摄影测量通用规范：围绕物体旋转时锁定曝光，消除相邻照片接缝处的明暗阶梯跳变。
     */
    fun lockExposureAndFocus(locked: Boolean) {
        val cam = activeCamera ?: return
        val c2Control = Camera2CameraControl.from(cam.cameraControl)
        val builder = CaptureRequestOptions.Builder()
        builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, locked)
        c2Control.setCaptureRequestOptions(builder.build())
    }

    /**
     * 恢复全自动模式 (AUTO 模式)
     * 将曝光补偿归零、关闭固定 ISO、开启连续自动曝光 (AE_MODE_ON)、开启连续自动对焦 (AF_MODE_CONTINUOUS_PICTURE)、白平衡设为 AUTO
     */
    fun resetToAutoMode() {
        val cam = activeCamera ?: return
        cam.cameraControl.setExposureCompensationIndex(0)
        cam.cameraControl.cancelFocusAndMetering()

        val c2Control = Camera2CameraControl.from(cam.cameraControl)
        val builder = CaptureRequestOptions.Builder()
        builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
        builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, false)
        c2Control.setCaptureRequestOptions(builder.build())
    }
}
