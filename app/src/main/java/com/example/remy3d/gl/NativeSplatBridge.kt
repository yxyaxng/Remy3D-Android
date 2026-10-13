package com.example.remy3d.gl

import java.nio.FloatBuffer

/**
 * NativeSplatBridge – JNI 原生计算与深度排序桥接层
 *
 * 核心功能：
 * 1. 利用底层 C++ 原生解析百万级高斯二进制 PLY，绕过 JVM GC
 * 2. 针对相机位姿由远及近高速基数/快速排序 (Back-to-Front Depth Sorting)
 * 3. 内存直接映射 DirectBuffer，0 拷贝填充顶点数据
 */
object NativeSplatBridge {

    private var isLibraryLoaded = false

    init {
        try {
            System.loadLibrary("remy3d_vulkan")
            isLibraryLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            isLibraryLoaded = false
        }
    }

    fun isAvailable(): Boolean = isLibraryLoaded

    external fun nativeInit(): Boolean
    external fun nativeLoadPly(plyPath: String, maxPoints: Int): Boolean
    external fun nativeGetPointCount(): Int
    external fun nativeUpdateCamera(eyeX: Float, eyeY: Float, eyeZ: Float, lookX: Float, lookY: Float, lookZ: Float)
    external fun nativeFillVertexBuffer(floatBuffer: FloatBuffer, maxFloats: Int): Int
    external fun nativeDestroy()
}
