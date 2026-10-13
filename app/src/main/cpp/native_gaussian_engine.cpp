#include "native_gaussian_engine.h"
#include <fstream>
#include <sstream>
#include <algorithm>
#include <cstring>

static constexpr float SH_C0 = 0.28209479177387814f;

NativeGaussianEngine::NativeGaussianEngine() = default;
NativeGaussianEngine::~NativeGaussianEngine() = default;

bool NativeGaussianEngine::loadFromPlyFile(const std::string& filePath, int maxPoints) {
    m_points.clear();
    m_sortedPoints.clear();

    std::ifstream file(filePath, std::ios::binary);
    if (!file.is_open()) {
        LOGE("Cannot open PLY file: %s", filePath.c_str());
        return false;
    }

    std::string line;
    bool isBinary = false;
    int vertexCount = 0;

    struct Property {
        std::string name;
        std::string type;
        int size = 4;
        int offset = 0;
    };
    std::vector<Property> properties;
    int totalStride = 0;

    while (std::getline(file, line)) {
        if (!line.empty() && line.back() == '\r') line.pop_back();
        if (line.find("format binary_little_endian") != std::string::npos) {
            isBinary = true;
        } else if (line.find("element vertex") != std::string::npos) {
            std::stringstream ss(line);
            std::string dummy1, dummy2;
            ss >> dummy1 >> dummy2 >> vertexCount;
        } else if (line.find("property") != std::string::npos) {
            std::stringstream ss(line);
            std::string dummy, type, name;
            ss >> dummy >> type >> name;
            int sz = 4;
            if (type == "char" || type == "uchar" || type == "uint8" || type == "int8") sz = 1;
            else if (type == "short" || type == "ushort" || type == "int16" || type == "uint16") sz = 2;
            else if (type == "double" || type == "float64") sz = 8;

            properties.push_back({name, type, sz, totalStride});
            totalStride += sz;
        } else if (line == "end_header") {
            break;
        }
    }

    if (vertexCount <= 0 || totalStride <= 0) {
        LOGE("Invalid PLY header or zero vertices");
        return false;
    }

    LOGI("Parsed PLY: %d vertices, stride: %d bytes, binary: %d", vertexCount, totalStride, isBinary);

    int xOff = -1, yOff = -1, zOff = -1;
    int fdc0Off = -1, fdc1Off = -1, fdc2Off = -1;
    int rOff = -1, gOff = -1, bOff = -1, aOff = -1;
    int s0Off = -1, s1Off = -1, s2Off = -1;
    int r0Off = -1, r1Off = -1, r2Off = -1, r3Off = -1;

    for (const auto& p : properties) {
        if (p.name == "x") xOff = p.offset;
        else if (p.name == "y") yOff = p.offset;
        else if (p.name == "z") zOff = p.offset;
        else if (p.name == "f_dc_0") fdc0Off = p.offset;
        else if (p.name == "f_dc_1") fdc1Off = p.offset;
        else if (p.name == "f_dc_2") fdc2Off = p.offset;
        else if (p.name == "red" || p.name == "diffuse_red" || p.name == "r") rOff = p.offset;
        else if (p.name == "green" || p.name == "diffuse_green" || p.name == "g") gOff = p.offset;
        else if (p.name == "blue" || p.name == "diffuse_blue" || p.name == "b") bOff = p.offset;
        else if (p.name == "alpha" || p.name == "opacity") aOff = p.offset;
        else if (p.name == "scale_0") s0Off = p.offset;
        else if (p.name == "scale_1") s1Off = p.offset;
        else if (p.name == "scale_2") s2Off = p.offset;
        else if (p.name == "rot_0") r0Off = p.offset;
        else if (p.name == "rot_1") r1Off = p.offset;
        else if (p.name == "rot_2") r2Off = p.offset;
        else if (p.name == "rot_3") r3Off = p.offset;
    }

    int step = 1;
    if (vertexCount > maxPoints) {
        step = (vertexCount + maxPoints - 1) / maxPoints;
    }

    m_points.reserve(std::min(vertexCount, maxPoints));

    float minX = 1e9f, maxX = -1e9f;
    float minY = 1e9f, maxY = -1e9f;
    float minZ = 1e9f, maxZ = -1e9f;

    std::vector<char> buffer(totalStride);

    for (int i = 0; i < vertexCount; ++i) {
        if (isBinary) {
            file.read(buffer.data(), totalStride);
            if (!file) break;

            if (i % step != 0) continue;

            GaussianPoint pt{};
            if (xOff >= 0) std::memcpy(&pt.x, buffer.data() + xOff, 4);
            if (yOff >= 0) std::memcpy(&pt.y, buffer.data() + yOff, 4);
            if (zOff >= 0) std::memcpy(&pt.z, buffer.data() + zOff, 4);

            // 颜色还原：球谐系数 f_dc_0/1/2 优先
            if (fdc0Off >= 0 && fdc1Off >= 0 && fdc2Off >= 0) {
                float f0 = 0.f, f1 = 0.f, f2 = 0.f;
                std::memcpy(&f0, buffer.data() + fdc0Off, 4);
                std::memcpy(&f1, buffer.data() + fdc1Off, 4);
                std::memcpy(&f2, buffer.data() + fdc2Off, 4);
                pt.r = std::clamp(0.5f + SH_C0 * f0, 0.0f, 1.0f);
                pt.g = std::clamp(0.5f + SH_C0 * f1, 0.0f, 1.0f);
                pt.b = std::clamp(0.5f + SH_C0 * f2, 0.0f, 1.0f);
            } else if (rOff >= 0 && gOff >= 0 && bOff >= 0) {
                uint8_t ur = static_cast<uint8_t>(buffer[rOff]);
                uint8_t ug = static_cast<uint8_t>(buffer[gOff]);
                uint8_t ub = static_cast<uint8_t>(buffer[bOff]);
                pt.r = ur / 255.0f;
                pt.g = ug / 255.0f;
                pt.b = ub / 255.0f;
            } else {
                pt.r = 0.85f; pt.g = 0.85f; pt.b = 0.85f;
            }

            if (aOff >= 0) {
                uint8_t ua = static_cast<uint8_t>(buffer[aOff]);
                pt.a = std::clamp(ua / 255.0f, 0.5f, 1.0f);
            } else {
                pt.a = 0.9f;
            }

            // 3D 缩放与四元数旋转
            if (s0Off >= 0) {
                float s0 = 0.f, s1 = 0.f, s2 = 0.f;
                std::memcpy(&s0, buffer.data() + s0Off, 4);
                if (s1Off >= 0) std::memcpy(&s1, buffer.data() + s1Off, 4);
                if (s2Off >= 0) std::memcpy(&s2, buffer.data() + s2Off, 4);
                pt.scale[0] = std::clamp(std::exp(s0), 0.005f, 0.25f);
                pt.scale[1] = std::clamp(std::exp(s1), 0.005f, 0.25f);
                pt.scale[2] = std::clamp(std::exp(s2), 0.005f, 0.25f);
            } else {
                pt.scale[0] = pt.scale[1] = pt.scale[2] = 0.035f;
            }

            if (r0Off >= 0 && r1Off >= 0 && r2Off >= 0 && r3Off >= 0) {
                std::memcpy(&pt.rot[0], buffer.data() + r0Off, 4);
                std::memcpy(&pt.rot[1], buffer.data() + r1Off, 4);
                std::memcpy(&pt.rot[2], buffer.data() + r2Off, 4);
                std::memcpy(&pt.rot[3], buffer.data() + r3Off, 4);
            } else {
                pt.rot[0] = 1.0f; pt.rot[1] = pt.rot[2] = pt.rot[3] = 0.0f;
            }

            minX = std::min(minX, pt.x); maxX = std::max(maxX, pt.x);
            minY = std::min(minY, pt.y); maxY = std::max(maxY, pt.y);
            minZ = std::min(minZ, pt.z); maxZ = std::max(maxZ, pt.z);

            m_points.push_back(pt);
        }
    }

    if (m_points.empty()) return false;

    m_centerX = (minX + maxX) * 0.5f;
    m_centerY = (minY + maxY) * 0.5f;
    m_centerZ = (minZ + maxZ) * 0.5f;

    float spanX = maxX - minX;
    float spanY = maxY - minY;
    float spanZ = maxZ - minZ;
    float maxDim = std::max({spanX, spanY, spanZ, 0.01f});
    m_autoScale = 2.0f / maxDim;

    // 归一化居中
    for (auto& p : m_points) {
        p.x = (p.x - m_centerX) * m_autoScale;
        p.y = (p.y - m_centerY) * m_autoScale;
        p.z = (p.z - m_centerZ) * m_autoScale;
        p.scale[0] *= m_autoScale;
        p.scale[1] *= m_autoScale;
        p.scale[2] *= m_autoScale;
    }

    m_sortedPoints = m_points;
    LOGI("Loaded %zu native 3DGS points successfully", m_points.size());
    return true;
}

void NativeGaussianEngine::updateAndSort(float eyeX, float eyeY, float eyeZ,
                                        float lookX, float lookY, float lookZ) {
    if (m_points.empty()) return;

    // 1. 计算每个高斯点在相机观察视线上的投影距离
    for (auto& p : m_points) {
        float dx = p.x - eyeX;
        float dy = p.y - eyeY;
        float dz = p.z - eyeZ;
        p.depth = dx * lookX + dy * lookY + dz * lookZ;
    }

    // 2. 高速标准 C++ 深度排序：从远到近 (Back-to-Front)，保障 Alpha 混合质感
    m_sortedPoints = m_points;
    std::sort(m_sortedPoints.begin(), m_sortedPoints.end(), [](const GaussianPoint& a, const GaussianPoint& b) {
        return a.depth > b.depth;
    });
}
