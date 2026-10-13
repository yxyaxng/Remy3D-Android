#include <jni.h>
#include <string>
#include <vector>
#include "native_gaussian_engine.h"

static NativeGaussianEngine* g_engine = nullptr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_remy3d_gl_NativeSplatBridge_nativeInit(JNIEnv* env, jobject thiz) {
    if (!g_engine) {
        g_engine = new NativeGaussianEngine();
    }
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_remy3d_gl_NativeSplatBridge_nativeLoadPly(JNIEnv* env, jobject thiz, jstring plyPath, jint maxPoints) {
    if (!g_engine) g_engine = new NativeGaussianEngine();

    const char* pathStr = env->GetStringUTFChars(plyPath, nullptr);
    bool ok = g_engine->loadFromPlyFile(pathStr, maxPoints);
    env->ReleaseStringUTFChars(plyPath, pathStr);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_example_remy3d_gl_NativeSplatBridge_nativeGetPointCount(JNIEnv* env, jobject thiz) {
    return g_engine ? g_engine->getPointCount() : 0;
}

JNIEXPORT void JNICALL
Java_com_example_remy3d_gl_NativeSplatBridge_nativeUpdateCamera(JNIEnv* env, jobject thiz,
                                                               jfloat eyeX, jfloat eyeY, jfloat eyeZ,
                                                               jfloat lookX, jfloat lookY, jfloat lookZ) {
    if (g_engine) {
        g_engine->updateAndSort(eyeX, eyeY, eyeZ, lookX, lookY, lookZ);
    }
}

JNIEXPORT jint JNICALL
Java_com_example_remy3d_gl_NativeSplatBridge_nativeFillVertexBuffer(JNIEnv* env, jobject thiz,
                                                                    jobject floatBuffer, jint maxFloats) {
    if (!g_engine) return 0;

    auto* bufferPtr = static_cast<float*>(env->GetDirectBufferAddress(floatBuffer));
    if (!bufferPtr) return 0;

    const auto& points = g_engine->getPoints();
    int count = static_cast<int>(points.size());
    const float quadXY[12] = {-1.f, -1.f, 1.f, -1.f, 1.f, 1.f, -1.f, -1.f, 1.f, 1.f, -1.f, 1.f};

    int outIndex = 0;
    // 每点 6 个顶点，每个顶点 14 个 float:
    // aPos(2), aCenter(3), aSize(1), aAlpha(1), aColor(3), aRx(2), aRy(2) = 14 floats
    for (int i = 0; i < count; ++i) {
        if (outIndex + 84 > maxFloats) break;

        const auto& p = points[i];

        // 基于真实四元数解算切向主轴
        float qAngle = 2.0f * std::atan2(p.rot[3], p.rot[0]);
        float aspect = std::clamp(p.scale[0] / std::max(0.001f, p.scale[1]), 0.4f, 2.5f);
        float ca = std::cos(qAngle);
        float sa = std::sin(qAngle);
        float rxx = ca * aspect;
        float rxy = sa * aspect;
        float ryx = -sa;
        float ryy = ca;

        float sizeVal = std::max({p.scale[0], p.scale[1], p.scale[2], 0.035f});

        for (int q = 0; q < 6; ++q) {
            bufferPtr[outIndex++] = quadXY[q * 2];
            bufferPtr[outIndex++] = quadXY[q * 2 + 1];
            bufferPtr[outIndex++] = p.x;
            bufferPtr[outIndex++] = p.y;
            bufferPtr[outIndex++] = p.z;
            bufferPtr[outIndex++] = sizeVal;
            bufferPtr[outIndex++] = p.a;
            bufferPtr[outIndex++] = p.r;
            bufferPtr[outIndex++] = p.g;
            bufferPtr[outIndex++] = p.b;
            bufferPtr[outIndex++] = rxx;
            bufferPtr[outIndex++] = rxy;
            bufferPtr[outIndex++] = ryx;
            bufferPtr[outIndex++] = ryy;
        }
    }
    return outIndex / 14 / 6; // 返回实际填入的高斯图元数
}

JNIEXPORT void JNICALL
Java_com_example_remy3d_gl_NativeSplatBridge_nativeDestroy(JNIEnv* env, jobject thiz) {
    if (g_engine) {
        delete g_engine;
        g_engine = nullptr;
    }
}

}
