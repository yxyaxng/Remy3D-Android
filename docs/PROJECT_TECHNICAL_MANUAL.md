# Remy3D-Android 核心技术架构与模块实现手册

> **系统设计、数学推导与模块实现指南**  
> 本文档提供项目技术底层原理、几何推导、模块实现细节与关键技术问答（Q&A）。

---

## 目录
- [一、 项目定位与技术栈选型](#一-项目定位与技术栈选型)
- [二、 整体系统架构与数据流拓扑](#二-整体系统架构与数据流拓扑)
- [三、 核心模块技术实现细节](#三-核心模块技术实现细节)
  - [1. 原生 Camera2 手动相机控制 (NativeProCameraController)](#1-原生-camera2-手动相机控制-nativeprocameracontroller)
  - [2. 36 单元半球立体穹顶空间引导 (SpatialCaptureGuide)](#2-36-单元半球立体穹顶空间引导-spatialcaptureguide)
  - [3. 目标主体视觉指纹与一致性追踪 (TargetTrackerEngine)](#3-目标主体视觉指纹与一致性追踪-targettrackerengine)
  - [4. 端侧实时画质模糊度评估算子 (ImageQualityEvaluator)](#4-端侧实时画质模糊度评估算子-imagequalityevaluator)
  - [5. 视频流关键帧抽取与模糊过滤 (VideoKeyframeExtractor)](#5-视频流关键帧抽取与模糊过滤-videokeyframeextractor)
  - [6. 端侧 3D 几何反投影与深度解算 (PhotoReconstructor)](#6-端侧-3d-几何反投影与深度解算-photoreconstructor)
  - [7. 稠密曲面网格化拓扑致密化引擎 (SurfaceMesher)](#7-稠密曲面网格化拓扑致密化引擎-surfacemesher)
  - [8. 原生 OpenGL ES 2.0 高斯泼溅与 360° 漫游渲染 (GaussianRenderer & SplatGLView)](#8-原生-opengl-es-20-高斯泼溅与-360-漫游渲染-gaussianrenderer--splatglview)
  - [9. 本地持久化与草稿生命周期管理 (LocalRecordStore)](#9-本地持久化与草稿生命周期管理-localrecordstore)
- [四、 核心技术问题解析 (Q&A)](#四-核心技术问题解析-qa)

---

## 一、 项目定位与技术栈选型

### 1. 为什么采用纯 Android 原生技术栈？
相比于基于 WebView + Three.js / WebGL 的混合封装方案，原生技术栈在移动端具有以下优势：
1. **内存开销可控**：避免引入 Chromium WebView 进程带来的 $150\text{MB} \sim 300\text{MB}$ 额外内存开销；
2. **底层相机 ISP 直控**：支持通过 Camera2 下发硬件级 ISO、快门时间、对焦屈光度以及连续 AE/AF 锁定；
3. **低延迟图形渲染**：无 JS Bridge 跨进程通信开销，通过 C++ NDK 与 DirectByteBuffer 实现 60 FPS 高斯点云实时渲染。

### 2. 核心技术栈组成
- **编程语言**：Kotlin + Coroutines 并发调度 + C++17 (NDK JNI)；
- **UI 框架**：Jetpack Compose（声明式 UI、Canvas 物理刻度标尺与雷达组件）；
- **相机控制**：CameraX + Camera2 互操作 API (`Camera2CameraControl` / `CaptureRequestOptions`)；
- **空间感知**：Google ARCore SDK (`com.google.ar:core:1.44.0`) + 硬件旋转矢量传感器 (`TYPE_ROTATION_VECTOR`)；
- **3D 渲染引擎**：原生 OpenGL ES 2.0 (GLSL 顶点/片元着色器管线)；
- **数据持久化**：沙盒 JSON 存储 + 标准二进制/ASCII `.ply` 格式解析与导出。

---

## 二、 整体系统架构与数据流拓扑

```text
┌────────────────────────────────────────────────────────────────────────┐
│                        用户交互层 (Jetpack Compose)                     │
│  [首页模式选择]    [专业拨盘取景页]    [重建进度等待]    [3D 漫游查看器]   [草稿箱] │
└─────────────────────────────────┬──────────────────────────────────────┘
                                  │ 触发采集 / 导入
┌─────────────────────────────────▼──────────────────────────────────────┐
│                    数据采集与预处理管线 (Data Acquisition)              │
│  • Camera2 硬件 ISP 控制 (ISO / 快门 / 手动微距 / AE/AF 锁定)             │
│  • 外部多图 / 视频流提取 (VideoKeyframeExtractor)                       │
│  • 画质模糊检测 (ImageQualityEvaluator: 拉普拉斯方差算子)               │
│  • 主体一致性追踪 (TargetTrackerEngine: HSV 直方图 + 物距实时监测)      │
│  • 36 单元空间立体雷达覆盖解算 (SpatialCaptureGuide)                    │
└─────────────────────────────────┬──────────────────────────────────────┘
                                  │ 合格照片流集合
┌─────────────────────────────────▼──────────────────────────────────────┐
│                    端侧几何反投影与网格实体化 (3D Reconstruction)        │
│  Stage 1: 图像加载与自适应降采样 (480px, 内存受控)                      │
│  Stage 2: Sobel 边缘梯度检测 + 显著性色彩纹理采样 (~6000 点/图)           │
│  Stage 3: 环形/球面相机位姿估计 (SfM Camera Pose Estimation)            │
│  Stage 4: 针孔几何模型 3D 反投影 (Back-Projection with Adaptive Depth)   │
│  Stage 4.5: 稠密曲面网格化拓扑致密化 (SurfaceMesher: 体素降噪/三角面片细分)│
│  Stage 5: 导出标准 .ply 几何实物点云与持久化存储                        │
└─────────────────────────────────┬──────────────────────────────────────┘
                                  │ 连续高斯点集合
┌─────────────────────────────────▼──────────────────────────────────────┐
│                    原生图形渲染系统 (OpenGL ES 2.0 Engine)               │
│  • SplatGLView (GLSurfaceView 封装，双指缩放/单指拖动/手势阻尼)          │
│  • GaussianRenderer:                                                   │
│    - 顶点着色器 (MVP 矩阵变换 + 屏幕空间 Billboard 展开)                │
│    - 片元着色器 (高斯核衰减 exp(-2.2 * r^2) + Gamma 1.85 校正)          │
│    - Orbit 展台相机 vs Walkthrough 第一人称空间漫游相机矩阵切换        │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 三、 核心模块技术实现细节

### 1. 原生 Camera2 手动相机控制 (NativeProCameraController)
- **源码位置**：[`NativeProCameraController.kt`](../app/src/main/java/com/example/remy3d/camera/NativeProCameraController.kt)
- **实现原理**：
  多视角 3D 重建要求各视角图像保持辐射与焦距一致性。通过 Camera2 互操作 API 控制底层 ISP 参数：
  - `CONTROL_AE_MODE`：设为 `CONTROL_AE_MODE_OFF` 时开启手动快门与 ISO 控制；
  - `SENSOR_SENSITIVITY`：固定 ISO（100 / 200 / 400 / 800 等），控制图像信噪比；
  - `SENSOR_EXPOSURE_TIME`：纳秒级快门控制（如 $1/250\text{s} = 4,000,000\text{ns}$），抑制运动模糊；
  - `LENS_FOCUS_DISTANCE`：按屈光度（$\frac{1}{\text{距离(米)}}$）锁定焦点，消除自动对焦呼吸效应；
  - `CONTROL_AWB_MODE`：锁定白平衡，保持多视角光照色温一致。

### 2. 36 单元半球立体穹顶空间引导 (SpatialCaptureGuide)
- **源码位置**：[`SpatialCaptureGuide.kt`](../app/src/main/java/com/example/remy3d/engine/SpatialCaptureGuide.kt)
- **实现原理**：
  构建 36 单元空间立体半球穹顶模型并结合传感器姿态实时引导：
  - **俯仰角 (Pitch)** 分为 3 层：
    - 顶部俯视角 (Layer 0)：$30^\circ \sim 75^\circ$
    - 中部平视角 (Layer 1)：$0^\circ \sim 30^\circ$
    - 底部仰视角 (Layer 2)：$< 0^\circ$
  - **水平航向角 (Yaw)** 分为 12 个等角扇区（每扇区 $30^\circ$）：
    $$\text{Sector} = \left\lfloor \frac{\text{Yaw} \bmod 360^\circ}{30^\circ} \right\rfloor$$
  - **竖屏坐标系重映射**：
    当手机处于竖屏握持姿态时，通过 `SensorManager.remapCoordinateSystem(rotationMatrix, AXIS_X, AXIS_Z, remapped)` 消除俯仰角接近 $90^\circ$ 时的万向节死锁，实时输出精确的 `Yaw`、`Pitch` 与 `Roll`。

### 3. 目标主体视觉指纹与一致性追踪 (TargetTrackerEngine)
- **源码位置**：[`TargetTrackerEngine.kt`](../app/src/main/java/com/example/remy3d/engine/TargetTrackerEngine.kt)
- **实现原理**：
  - **视觉特征指纹提取**：在首张合格照片或对焦中心提取中心 $42\% \times 42\%$ ROI 区域：
    - 16-bin 归一化色相（Hue）直方图；
    - 8-bin 归一化饱和度（Saturation）直方图；
    - 局部平均亮度与参考对焦物距；
  - **直方图交集相似度计算**：
    $$\text{Similarity} = \sum_{i=1}^{16} \min\left(H_{\text{base}}[i], H_{\text{current}}[i]\right)$$
  - **实时预览监测与拍照拦截**：结合 CameraX `ImageAnalysis` 实时预览帧与手动对焦物距变化，持续更新 `LOCKED` / `DEVIATED_OBJECT` / `DEVIATED_DISTANCE` 状态；当相机移回目标或调回焦距时无需拍照即可自动恢复正常锁定状态。

### 4. 端侧实时画质模糊度评估算子 (ImageQualityEvaluator)
- **源码位置**：[`ImageQualityEvaluator.kt`](../app/src/main/java/com/example/remy3d/engine/ImageQualityEvaluator.kt)
- **实现原理**：
  - **拉普拉斯算子 (Laplacian Kernel)**：
    $$K = \begin{bmatrix} 0 & 1 & 0 \\ 1 & -4 & 1 \\ 0 & 1 & 0 \end{bmatrix}$$
  - **单趟在线拉普拉斯方差计算**：
    通过 `Bitmap.getPixels` 批量读取像素，利用 $\text{Var}(L) = E[L^2] - (E[L])^2$ 在 `< 5ms` 内完成清晰度评分。若方差低于阈值则弹出质量确认框，支持一键丢弃模糊照片。

### 5. 视频流关键帧抽取与模糊过滤 (VideoKeyframeExtractor)
- **源码位置**：[`VideoKeyframeExtractor.kt`](../app/src/main/java/com/example/remy3d/engine/VideoKeyframeExtractor.kt)
- **实现原理**：
  - 基于 Android 原生 `MediaMetadataRetriever`；
  - 自适应时间步长计算：$\text{stepMs} = \max(500\text{ms}, \frac{\text{Duration}}{45})$；
  - 逐帧抽取后等比缩放至最大边长 `960px` 写入沙盒并立即释放内存 `Bitmap`；
  - 结合 `ImageQualityEvaluator` 自动过滤运动模糊帧，保留 $15 \sim 45$ 张清晰关键帧。

### 6. 端侧 3D 几何反投影与深度解算 (PhotoReconstructor)
- **源码位置**：[`PhotoReconstructor.kt`](../app/src/main/java/com/example/remy3d/engine/PhotoReconstructor.kt)
- **实现原理**：
  - **Sobel 边缘与表面分箱提取**：
    $$G_x = \begin{bmatrix} -1 & 0 & 1 \\ -2 & 0 & 2 \\ -1 & 0 & 1 \end{bmatrix} * I, \quad G_y = \begin{bmatrix} -1 & -2 & -1 \\ 0 & 0 & 0 \\ 1 & 2 & 1 \end{bmatrix} * I$$
    单图提取 `2,500` 个强梯度边缘点与 `3,500` 个平滑表面采样点；
  - **相机轨道位姿估算**：
    将 $N$ 张照片沿环绕圆弧轨道参数化分布：
    $$\theta_i = -\frac{\alpha}{2} + \frac{i}{N-1}\alpha, \quad x_c = R \sin\theta_i, \quad z_c = R \cos\theta_i$$
  - **针孔相机反投影**：
    归一化图像平面坐标 $u, v \in [-1, 1]$，结合自适应景深范围（空间场景 $1.2\text{m}\sim 4.5\text{m}$，单体实物 $0.8\text{m}\sim 1.5\text{m}$）映射至三维世界坐标。

### 7. 稠密曲面网格化拓扑致密化引擎 (SurfaceMesher)
- **源码位置**：[`SurfaceMesher.kt`](../app/src/main/java/com/example/remy3d/engine/SurfaceMesher.kt)
- **实现流程**：
  1. **空间体素网格哈希降噪 (Voxel Grid Clustering)**：
     将浮点坐标按体素尺寸（$0.045\text{m}$）离散化，通过 64 位整型移位键：
     $$\text{Key} = (v_x \ll 42) \mid (v_y \ll 21) \mid v_z$$
     聚合体素内散点质心与颜色均值，滤除离群点；
  2. **球面极坐标参数化与三角剖分**：
     计算各点相对于几何质心的天顶角 $\phi$ 与方位角 $\theta$，在局部邻近点间构造空间三角面片 (`MeshFace`)；
  3. **面法线计算与重心插值细分 (Barycentric Subdivision)**：
     计算面法线 $\vec{N} = (\vec{P}_2 - \vec{P}_1) \times (\vec{P}_3 - \vec{P}_1)$，在三角面片内部按重心坐标细分填充高斯椭球，闭合表面空隙。

### 8. 原生 OpenGL ES 2.0 高斯泼溅与 360° 漫游渲染 (GaussianRenderer & SplatGLView)
- **源码位置**：[`GaussianRenderer.kt`](../app/src/main/java/com/example/remy3d/gl/GaussianRenderer.kt)、[`SplatGLView.kt`](../app/src/main/java/com/example/remy3d/gl/SplatGLView.kt)
- **核心渲染机制**：
  1. **AABB 包围盒质心归零与归一化缩放**：加载点云时计算包围盒中心 $\mathbf{c}$ 与缩放因子 $s_{\text{auto}}$，将顶点归一化至原点 $(0,0,0)$；
  2. **球面极坐标轨道相机 (Spherical Orbit Camera)**：
     $$e_x = T_x + R\cos\phi_{\text{pitch}}\sin\theta_{\text{yaw}}, \quad e_y = T_y + R\sin\phi_{\text{pitch}}, \quad e_z = T_z + R\cos\phi_{\text{pitch}}\cos\theta_{\text{yaw}}$$
  3. **C++ NDK 实时视线深度重排序**：每帧将相机位置与视线方向传入 `NativeSplatBridge.nativeUpdateCamera`，按投影深度 $z_i = (\mathbf{p}'_i - \mathbf{E}) \cdot \vec{d}$ 执行基数排序；
  4. **着色器管线**：顶点着色器在裁剪空间沿椭球主轴展开 Billboard，片元着色器执行高斯核衰减 $\exp(-2.2 \cdot r^2)$ 与 Gamma 1.85 色彩校正；
  5. **双相机模式**：支持 `ORBIT` 展台环绕模式与 `WALKTHROUGH` 第一人称空间漫游模式切换。

### 9. 本地持久化与草稿生命周期管理 (LocalRecordStore)
- **源码位置**：[`MainActivity.kt`](../app/src/main/java/com/example/remy3d/MainActivity.kt)
- **实现机制**：
  - **显式保存触发**：仅在模式切换、返回退出或底部导航切换且用户明确选择「保存到草稿箱」时，将未重建照片列表持久化至 `draft_sessions_list.json`，并清空当前内存会话；
  - **放弃操作彻底清理**：单张照片放弃或会话级选择「放弃当前拍摄」时，绝不写入草稿箱，并同步清理已关联的旧草稿与临时图片文件；
  - **重建完成自动移除草稿**：模型重建完成后，通过 `deleteDraftsMatchingFiles` 自动清除对应草稿记录。

---

## 四、 核心技术问题解析 (Q&A)

### Q1: 移动端是如何在数秒内完成端侧 3D 几何重建的？
**A：**  
项目采用两级端侧几何重构管线：  
1. 第一级（`PhotoReconstructor`）：利用 Sobel 边缘/表面分箱采样与针孔相机几何反投影，从多视角图像中快速解算三维空间点；  
2. 第二级（`SurfaceMesher`）：通过体素网格空间哈希降噪、球面极坐标三角剖分与面片重心插值，将稀疏散点致密化为连续表面。整个过程通过 Kotlin 协程在本地 CPU 并发执行，耗时约 `3.2s ~ 7.8s`。

### Q2: 为什么采用原生 OpenGL ES 2.0 + C++ NDK 渲染架构？
**A：**  
1. **低内存占用**：相比于 WebView 方案减少 $150\text{MB}\sim 300\text{MB}$ 内存开销，配合 `DirectByteBuffer` 零拷贝通道直接向 GPU VBO 上传顶点数据；  
2. **稳定 60 FPS 帧率**：C++ NDK 层执行视线深度基数排序，规避 JVM GC 停顿；GLSL 着色器单周期完成高斯指数衰减与 sRGB Gamma 校正；  
3. **硬件协同**：便于将 Camera2、ARCore 与传感器姿态矩阵直接联动。

### Q3: 导入外部相册多图或视频时没有传感器位姿数据，如何完成三维重建？
**A：**  
1. `VideoKeyframeExtractor` 按自适应时间步长抽取视频帧并缩放至标准尺寸，由 `ImageQualityEvaluator` 自动剔除运动模糊帧；  
2. 首张图像通过 `TargetTrackerEngine` 提取中心视觉特征锚定空间参考；  
3. `PhotoReconstructor` 基于环绕圆弧相机轨道模型估算 $N$ 张关键帧的球面相机外参矩阵，经针孔反投影与 `SurfaceMesher` 曲面插值输出三维点云。

### Q4: 空间场景模式 (SPATIAL_SCENE) 与单体实物模式 (OBJECT) 在实现上有何差异？
**A：**  
1. **反投影景深范围**：单体模式聚焦于中心 $0.8\text{m}\sim 1.5\text{m}$ 紧凑范围；空间场景模式将反投影景深拓展至 $1.2\text{m}\sim 4.5\text{m}$，保留室内墙面与空间陈设；  
2. **主体锁定策略**：单体模式启用 `TargetTrackerEngine` 实时监测中心物体 HSV 直方图与对焦物距；空间场景模式关闭单体锁定以支持大范围漫游取景；  
3. **观察相机矩阵**：单体模式默认采用 `ORBIT` 球面轨道相机围绕中心旋转；空间模式支持切换 `WALKTHROUGH` 第一人称漫游相机在场景内部移动环视。

### Q5: 拍照确认流程是如何优化到毫秒级响应的？
**A：**  
1. **采集分辨率收敛**：为 `ImageCapture` 配置 `ResolutionSelector(1280×960)` 与 `setJpegQuality(85)`；  
2. **后台单次下采样解码复用**：在 `Dispatchers.Default` 协程中通过 `decodeBitmapWithExif(file, maxSide = 360)` 仅解码一次小尺寸正向位图，复用于目标一致性校验与清晰度评估，消除主线程全尺寸原图重编码阻塞；  
3. **批量数组读取与单趟方差算子**：使用 `Bitmap.getPixels` 一次性读入 `IntArray`，基于 $\text{Var}(X) = E[X^2] - (E[X])^2$ 单趟完成拉普拉斯方差计算，单帧质检耗时 `< 5ms`。
