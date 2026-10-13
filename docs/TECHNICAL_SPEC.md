# Remy3D-Android 端侧曲面重构方案与生态规范

> 本文档说明本项目采用的 **端侧三维稠密曲面网格化重构方案**，并对相关开源 3D 重建与 3DGS 渲染技术栈进行对比归纳。

---

## 1. 离散点云与连续曲面重构

在三维视觉中，从 2D 图像恢复 3D 结构的标准链路为：
$$\text{2D 特征点} \xrightarrow{\text{相机内外参估计 (SfM)}} \text{空间 3D 稀疏点} \xrightarrow{\text{稠密立体匹配 (MVS)}} \text{致密点云} \xrightarrow{\text{表面网格化 (Meshing)}} \text{连续实体}$$

若仅在三维空间放置单像素点基元（`GL_POINTS`），当相机拉近观察时，点间欧氏距离被透视投影放大，容易产生离散缝隙。为此本项目结合分箱特征提取、曲面拓扑细分与各向异性高斯椭球展开实现连续表面效果。

---

## 2. 核心算法实现：SurfaceMesher

在 [`SurfaceMesher.kt`](../app/src/main/java/com/example/remy3d/engine/SurfaceMesher.kt) 中实现了四级曲面致密化流水线：

### 2.1 空间体素网格化聚类 (Voxel Grid Clustering)
通过三维空间哈希将邻近点聚合至同一体素单元，滤除离群噪点：
$$\text{Key}(v_x, v_y, v_z) = (v_x \ll 42) \mid (v_y \ll 21) \mid v_z$$

### 2.2 球面极坐标参数化 (Spherical Radial Parameterization)
针对环绕拍摄特性，计算每个点相对于物体质心 $C$ 的球面极坐标：
$$\phi = \arccos\left(\frac{y - C_y}{\|P - C\|}\right), \quad \theta = \operatorname{atan2}(z - C_z, x - C_x)$$
按 $(\phi, \theta)$ 拓扑顺序执行局部空间三角剖分（Triangulation），生成三角面片集合。

### 2.3 连续表面重心插值致密化 (Barycentric Subdivision)
在三角面片内部，按重心坐标公式（Barycentric Coordinates）细分填充高斯椭球粒子：
$$P = \alpha A + \beta B + \gamma C, \quad (\alpha + \beta + \gamma = 1)$$
配合 OpenGL ES 片元着色器中的高斯指数衰减与 Gamma 色彩校正，输出连续闭合表面。

---

## 3. 相关开源技术生态参考

### 3.1 渲染引擎参考
| 引擎名称 | 维护方 | 渲染机制 | 适用场景 | 开源链接 |
| :--- | :--- | :--- | :--- | :--- |
| **Google Sceneview** | SceneView / Google Filament | 原生 PBR (物理渲染) | 加载 `.glb`、手势控制、AR 融合 | [Sceneview-Android](https://github.com/SceneView/sceneview-android) |
| **Mobile-3DGS** | Anon11110 | Vulkan Compute Shader | 原生实时 3DGS 椭球渲染 | [Mobile-3D-Gaussian-Splatting](https://github.com/Anon11110/Mobile-3D-Gaussian-Splatting) |
| **Three.js WebGL** | Mr.doob | WebGL 2.0 / WebGL 1.0 | 跨平台嵌入、PBR 材质 | [three.js](https://github.com/mrdoob/three.js) |

### 3.2 空间跟踪与重建算法参考
| 工具/算法库 | 适用阶段 | 核心技术 | 开源参考链接 |
| :--- | :--- | :--- | :--- |
| **Google ARCore** | 拍摄采集 | VIO 视觉惯导 SLAM + 实时点云提取 | [Google ARCore SDK](https://github.com/google-ar/arcore-android-sdk) |
| **Open3D** | 点云几何处理 | 泊松表面重建 (Poisson Reconstruction) | [Open3D](https://github.com/isl-org/Open3D) |
| **COLMAP** | 图像位姿求解 | 特征匹配 (SIFT) + 光束法平差 (Bundle Adjustment) | [COLMAP](https://github.com/colmap/colmap) |
| **Nerfstudio** | 3DGS 训练与重构 | 神经辐射场与高斯光栅化 | [Nerfstudio](https://github.com/nerfstudio-project/nerfstudio) |

---

## 4. 本地源码索引

- 曲面拓扑剖分实现：[`SurfaceMesher.kt`](../app/src/main/java/com/example/remy3d/engine/SurfaceMesher.kt)
- 重建流水线控制：[`PhotoReconstructor.kt`](../app/src/main/java/com/example/remy3d/engine/PhotoReconstructor.kt)
- ARCore 空间特征点提取：[`ArCorePointCloudManager.kt`](../app/src/main/java/com/example/remy3d/engine/ArCorePointCloudManager.kt)
- 36 单元环绕雷达引导：[`SpatialCaptureGuide.kt`](../app/src/main/java/com/example/remy3d/engine/SpatialCaptureGuide.kt)
- 目标一致性实时追踪：[`TargetTrackerEngine.kt`](../app/src/main/java/com/example/remy3d/engine/TargetTrackerEngine.kt)
- 通用 PLY 解析器：[`UniversalPlyLoader.kt`](../app/src/main/java/com/example/remy3d/engine/UniversalPlyLoader.kt)
- 原生高斯着色器渲染：[`GaussianRenderer.kt`](../app/src/main/java/com/example/remy3d/gl/GaussianRenderer.kt)
