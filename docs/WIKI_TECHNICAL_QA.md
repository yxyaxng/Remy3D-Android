# Wiki 专题二：移动端 3D 重建与原生工程技术问答 (Q&A)

> 本文档以问答形式整理了 **Remy3D** 在图像采样、渲染色彩还原、原生相机控制、实时姿态监测、内存管理与存储架构上的核心技术实现。  
> 交互式可视化版本请查阅同目录下的 [`TECHNICAL_SHOWCASE.html`](TECHNICAL_SHOWCASE.html)。

---

## Q1: 在拍照采样阶段，传统 Mesh 摄影测量与 3DGS 辐射场采样有什么区别？

**A：**

| 采样对比维度 | 传统 Mesh 摄影测量 (SfM + MVS + UV 贴图) | 3D Gaussian Splatting (3DGS 辐射场采样) | 本项目工程实现 |
| :--- | :--- | :--- | :--- |
| **1. 高光与反光材质** | 依赖漫反射假设，移动高光易导致立体匹配失败与网格破洞 | 利用球谐函数 (SH) 拟合随视角变化的高光，支持陶瓷、金属等反光材质 | 内置高光材质模型支持，引导多仰角环绕采集 |
| **2. 自动曝光 (AE) 波动** | 中度敏感，后期 UV 贴图可通过 Multi-band Blending 匀光平滑接缝 | 高度敏感，全图光度损失 ($L_1+\text{D-SSIM}$) 会将曝光跳变拟合为空中悬浮噪点 (Floaters) | [`NativeProCameraController.kt`](../app/src/main/java/com/example/remy3d/camera/NativeProCameraController.kt) 硬件锁定 ISO、快门与白平衡 |
| **3. 空间视角覆盖策略** | 局部重叠拼接，支持近距离局部特写 | 需全局视锥交叉约束，要求完整覆盖上、中、下三层半球视角 | [`SpatialCaptureGuide.kt`](../app/src/main/java/com/example/remy3d/engine/SpatialCaptureGuide.kt) 3 层 × 12 扇区 = 36 单元穹顶雷达 |
| **4. 细微边缘与薄片结构** | 泊松表面重建易将细小结构粘连或平滑滤除 | 依靠各向异性椭球拉伸与 $\alpha$ 渐变还原边缘透明度 | 支持自由视角与连续视频流关键帧抽取 ([`VideoKeyframeExtractor.kt`](../app/src/main/java/com/example/remy3d/engine/VideoKeyframeExtractor.kt)) |

---

## Q2: 为什么移动端本地重建的模型偏稀疏，而离线训练的 3DGS 模型连续光滑？

**A：**

1. **移动端本地快速重建（`3.5s ~ 7.5s`）**：
   [`PhotoReconstructor.kt`](../app/src/main/java/com/example/remy3d/engine/PhotoReconstructor.kt) 与 [`SurfaceMesher.kt`](../app/src/main/java/com/example/remy3d/engine/SurfaceMesher.kt) 在手机端执行即时特征反投影与局部曲面插值，无需云端 GPU 服务器即可在数秒内完成端侧几何重建与采集完整度验证。
2. **桌面/云端 3DGS 离线训练（`15 ~ 40 分钟`）**：
   在高性能 GPU 上经历 **30,000 轮反向传播迭代训练**，通过光度损失（$L_1 + \text{D-SSIM}$）持续对高斯椭球执行分裂（Splitting）、克隆（Cloning）与剪枝（Pruning），生成 50 万 ~ 200 万个各向异性椭球。本项目通过 [`UniversalPlyLoader.kt`](../app/src/main/java/com/example/remy3d/engine/UniversalPlyLoader.kt) 支持直接导入并以 60 FPS 渲染标准 3DGS `.ply` 模型。

---

## Q3: 为什么标准 3DGS PLY 模型直接渲染会出现色彩发灰或半透明？如何还原？

**A：**

1. **不透明度逆 Sigmoid 解码**：标准 3DGS PLY 中的 `opacity` 存储的是未激活的 logit 值（$\ln\frac{\alpha}{1-\alpha}$），在 [`UniversalPlyLoader.kt`](../app/src/main/java/com/example/remy3d/engine/UniversalPlyLoader.kt) 中需通过 $\alpha = \frac{1}{1 + \exp(-\text{raw\_opacity})}$ 还原物理不透明度。
2. **线性光度至屏幕 sRGB Gamma 校正**：球谐系数计算出的颜色位于物理线性空间（Linear Radiance），直接在 sRGB 屏幕（Gamma 2.2）显示会导致中间调偏暗发灰。[`GaussianRenderer.kt`](../app/src/main/java/com/example/remy3d/gl/GaussianRenderer.kt) 片元着色器通过 `1.28` 倍饱和度补偿、`pow(col, vec3(1.0 / 1.85))` Gamma 校正与 `smoothstep(0.06, 0.92, finalAlpha)` 中心强化还原色彩。
3. **深色渲染画布对比**：3D 渲染视口独立采用深黑背景（`#06101E`），保证浅色与半透明边缘的高对比度显示。

---

## Q4: 拍照确认与端侧质检是如何优化至毫秒级（`< 5ms`）响应的？

**A：**

| 优化维度 | 优化前实现（耗时 `800ms ~ 1800ms`） | 优化后原生实现（总耗时 `< 30ms`，质检 `< 5ms`） |
| :--- | :--- | :--- |
| **1. 图像方向处理** | 主线程同步解码 12MP~48MP 全尺寸原图、旋转矩阵并重压缩 JPEG 覆写磁盘 | 移除冗余原图重编码，改在 `Dispatchers.Default` 后台协程执行内存级方向旋转 |
| **2. 磁盘 I/O 解码次数** | 单次拍照在方向、追踪与清晰度模块中对同一文件重复调用 3 次 `decodeFile` | 通过 `decodeBitmapWithExif(file, maxSide = 360)` 仅执行 **1 次**下采样解码并在内存中复用 |
| **3. 像素遍历与方差计算** | 双层循环逐像素调用 `bitmap.getPixel(x,y)` JNI 方法 + `MutableList<Float>` 装箱 | `bitmap.getPixels()` 一次性读入 `IntArray`，利用 $\text{Var}(X)=E[X^2]-(E[X])^2$ 单趟零分配计算 |
| **4. 采集分辨率与模式隔离** | 默认按传感器最高分辨率输出；未区分场景模式与单体模式 | 配置 `ResolutionSelector(1280×960)`；仅在 `ScanMode.OBJECT` 单体模式下启用主体偏离校验 |

---

## Q5: 如何实现相机角度与目标偏移的实时监测更新（无需按快门拍照即可更新状态）？

**A：**

1. **实时预览流分析 (`ImageAnalysis`)**：在 [`MainActivity.kt`](../app/src/main/java/com/example/remy3d/MainActivity.kt) 的 CameraX 管线中绑定 `ImageAnalysis`（`320×240`、`STRATEGY_KEEP_ONLY_LATEST`），每 `180ms` 调用 [`TargetTrackerEngine.verifyRealtimePreview`](../app/src/main/java/com/example/remy3d/engine/TargetTrackerEngine.kt) 实时比对中心 ROI 的 HSV 直方图与 Camera2 对焦物距。当镜头移回目标或调回焦距时，状态自动恢复为 `LOCKED`。
2. **消除竖屏姿态万向节死锁 (`Gimbal Lock`)**：在 [`SpatialCaptureGuide.kt`](../app/src/main/java/com/example/remy3d/engine/SpatialCaptureGuide.kt) 中，针对竖屏握持姿态采用 `SensorManager.remapCoordinateSystem(rotationMatrix, AXIS_X, AXIS_Z, remapped)` 重映射坐标轴计算偏航角 `Yaw`，并直接利用旋转矩阵光轴分量 `asin(rotationMatrix[8])` 与 `asin(rotationMatrix[6])` 解算俯仰角 `Pitch` 与横滚角 `Roll`。

---

## Q6: 点击实拍缩略图自动对齐 3D 相机机位是如何实现的？

**A：**

1. **路由状态隔离**：在 [`MainActivity.kt`](../app/src/main/java/com/example/remy3d/MainActivity.kt) 中，进入预设场景或外部 `.ply` 模型时自动清空实拍图片路径列表，确保不同数据源状态互不干扰。
2. **实拍机位反向映射 (`alignCameraToPhoto`)**：在 [`GaussianRenderer.kt`](../app/src/main/java/com/example/remy3d/gl/GaussianRenderer.kt) 中，根据照片索引 $i$ 与总张数 $N$ 反算环绕圆弧轨道上的水平偏航角与垂直俯仰角：
   $$\theta_{\text{target}} = -\frac{\text{Arc}}{2} + \frac{i}{N-1} \cdot \text{Arc}$$
   直接将 Orbit 轨道相机旋转至对应物理拍摄视角。

---

## Q7: 移动端如何在有限内存与算力下实现十万级高斯椭球的 60 FPS 渲染并避免 OOM？

**A：**

1. **自适应步长流式读取**：[`UniversalPlyLoader.kt`](../app/src/main/java/com/example/remy3d/engine/UniversalPlyLoader.kt) 采用 `64KB BufferedInputStream` 流式步进抽样，复用定长字节缓冲，避免一次性在 JVM 堆中创建百万级对象。
2. **C++ NDK 基数排序**：景深排序在 C++ 原生层（[`native_gaussian_engine.cpp`](../app/src/main/cpp/native_gaussian_engine.cpp)）通过基数排序完成，规避每帧 JVM GC 停顿。
3. **DirectByteBuffer 零拷贝与渲染更新分离**：使用堆外直接内存（`ByteBuffer.allocateDirect`）对接 OpenGL VBO；在 Compose `AndroidView.update` 中仅更新标量属性与 Shader Uniform，仅当模型数据源变化时在 `LaunchedEffect` 中上传 VBO。
4. **两阶段位图下采样**：多图重建、缩略图（`RGB_565`、`120px`）与视频抽帧均通过 `inJustDecodeBounds` + `inSampleSize` 按需缩放并及时 `recycle()`。

---

## Q8: 如何实现将模型导出为标准 PLY 文件，以及无权限将 OpenGL 3D 渲染画面保存至系统相册？

**A：**

1. **标准 PLY 导出 ([`PlyExporter.kt`](../app/src/main/java/com/example/remy3d/plyio/PlyExporter.kt))**：通过 Android 存储访问框架（SAF `ActivityResultContracts.CreateDocument`）流式写出标准 PLY 点云文件，支持在 MeshLab、CloudCompare 或 Blender 中打开。
2. **无权限保存系统相册 ([`MediaStoreSaver.kt`](../app/src/main/java/com/example/remy3d/engine/MediaStoreSaver.kt))**：在 OpenGL 渲染线程 `onDrawFrame` 帧末调用 `GLES20.glReadPixels` 读取显存 RGBA 缓冲区，通过矩阵 `postScale(1f, -1f)` 垂直翻转校正 OpenGL 左下角原点与 Android Bitmap 左上角原点的坐标差异，随后通过 `MediaStore`（配合 Android 10+ `IS_PENDING` 标记）写入系统 `Pictures/Remy3D` 目录，全程无需申请动态存储权限。
