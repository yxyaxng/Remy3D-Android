package com.example.remy3d.gl

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector

/**
 * SplatGLView – 专业 3D 交互查看器
 *
 * 支持手势：
 * 1. 单指拖动：以模型为中心围绕旋转（水平 360°，垂直限位防倒置）
 * 2. 双指拖动：平移视野 (Pan)，移动观察中心
 * 3. 双指捏合：平滑缩放 (Zoom)，带阻尼限制
 * 4. 双击屏幕：一键重置视角与缩放
 */
class SplatGLView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    val renderer = GaussianRenderer()

    // 缩放手势检测器
    private val scaleDetector: ScaleGestureDetector

    // 双击检测器
    private val gestureDetector: GestureDetector

    // 手势状态记录
    private var lastX = 0f
    private var lastY = 0f
    private var lastPointerX = 0f
    private var lastPointerY = 0f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var isMultiTouch = false

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY

        scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scaleFactor = detector.scaleFactor
                // 对缩放因子做适度平滑处理
                val delta = (scaleFactor - 1.0f) * 1.5f + 1.0f
                renderer.zoom = (renderer.zoom * delta).coerceIn(0.2f, 8.0f)
                return true
            }
        })

        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                renderer.resetView()
                return true
            }
        })
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 先给双击与缩放手势检测器处理
        gestureDetector.onTouchEvent(event)
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                lastX = event.x
                lastY = event.y
                isMultiTouch = false
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                isMultiTouch = true
                if (event.pointerCount >= 2) {
                    lastPointerX = (event.getX(0) + event.getX(1)) / 2f
                    lastPointerY = (event.getY(0) + event.getY(1)) / 2f
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (scaleDetector.isInProgress) {
                    // 缩放中，不进行单指旋转或双指平移干扰
                    return true
                }

                if (event.pointerCount >= 2 && isMultiTouch) {
                    // 双指平移 (Pan)
                    val currentMidX = (event.getX(0) + event.getX(1)) / 2f
                    val currentMidY = (event.getY(0) + event.getY(1)) / 2f
                    val dx = currentMidX - lastPointerX
                    val dy = currentMidY - lastPointerY

                    // 根据当前缩放比例换算屏幕像素到世界空间平移距离
                    val panSensitivity = 0.0035f / renderer.zoom.coerceAtLeast(0.5f)
                    renderer.panX -= dx * panSensitivity
                    renderer.panY += dy * panSensitivity

                    // 限制平移范围，防止物体完全移出屏幕视野
                    renderer.panX = renderer.panX.coerceIn(-5.0f, 5.0f)
                    renderer.panY = renderer.panY.coerceIn(-5.0f, 5.0f)

                    lastPointerX = currentMidX
                    lastPointerY = currentMidY
                } else if (event.pointerCount == 1 && !isMultiTouch) {
                    val pointerIndex = event.findPointerIndex(activePointerId)
                    if (pointerIndex != -1) {
                        val x = event.getX(pointerIndex)
                        val y = event.getY(pointerIndex)
                        val dx = x - lastX
                        val dy = y - lastY

                        if (renderer.cameraMode == GaussianRenderer.CameraMode.WALKTHROUGH) {
                            // 空间漫游第一人称视角环顾转头
                            val lookSensitivity = 0.005f
                            renderer.walkYaw -= dx * lookSensitivity
                            renderer.walkPitch += dy * lookSensitivity
                            renderer.walkPitch = renderer.walkPitch.coerceIn(-1.4f, 1.4f)
                        } else {
                            // 单指绕物体中心旋转 (Orbit)
                            val rotSensitivity = 0.006f
                            renderer.rotY += dx * rotSensitivity
                            renderer.rotX += dy * rotSensitivity
                            // 俯仰角限制：避免倒扣视角导致方向混乱
                            renderer.rotX = renderer.rotX.coerceIn(-1.45f, 1.45f)
                        }

                        lastX = x
                        lastY = y
                    }
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val actionIndex = event.actionIndex
                val pointerId = event.getPointerId(actionIndex)
                if (pointerId == activePointerId) {
                    val newPointerIndex = if (actionIndex == 0) 1 else 0
                    lastX = event.getX(newPointerIndex)
                    lastY = event.getY(newPointerIndex)
                    activePointerId = event.getPointerId(newPointerIndex)
                }
                if (event.pointerCount <= 2) {
                    isMultiTouch = false
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
                isMultiTouch = false
            }
        }
        return true
    }
}
