#pragma once

#include <cstdint>
#include <string>
#include <vector>
#include <cmath>
#include <android/log.h>

#define LOG_TAG "Remy3D-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 工业级标准 3DGS 高斯图元数据结构
struct GaussianPoint {
    float x, y, z;
    float r, g, b, a;
    float scale[3];
    float rot[4];       // 四元数 (w, x, y, z)
    float depth;        // 运行时相对于相机的投影距离 (用于基数/快速排序)
};

class NativeGaussianEngine {
public:
    NativeGaussianEngine();
    ~NativeGaussianEngine();

    // 高速直接内存 / 文件加载 (支持百万级高斯流式载入)
    bool loadFromPlyFile(const std::string& filePath, int maxPoints = 150000);

    // 运行时针对当前相机位姿的深度排序 (从后至前，满足透明高斯辐射度累加)
    void updateAndSort(float eyeX, float eyeY, float eyeZ,
                       float lookX, float lookY, float lookZ);

    int getPointCount() const { return static_cast<int>(m_points.size()); }
    const std::vector<GaussianPoint>& getPoints() const { return m_sortedPoints; }

    // 空间包围盒
    float getCenterX() const { return m_centerX; }
    float getCenterY() const { return m_centerY; }
    float getCenterZ() const { return m_centerZ; }
    float getAutoScale() const { return m_autoScale; }

private:
    std::vector<GaussianPoint> m_points;
    std::vector<GaussianPoint> m_sortedPoints;

    float m_centerX = 0.0f;
    float m_centerY = 0.0f;
    float m_centerZ = 0.0f;
    float m_autoScale = 1.0f;
};
