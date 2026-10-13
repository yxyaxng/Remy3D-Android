# Wiki 专题一：空间采样算法与 3DGS 渲染物理机制解析

> 本文档涵盖 **Remy3D** 项目的空间采样数学推导、3DGS 辐射场物理机制以及 360° 球面轨道旋转与实时深度排序原理。  
> 交互式可视化版本请查阅同目录下的 [`TECHNICAL_SHOWCASE.html`](TECHNICAL_SHOWCASE.html)。

---

## 一、 空间采样算法实现原理

在实景 3D 重建与高斯点云渲染中，**采样（Sampling）** 是连接 2D 连续图像信号与 3D 离散几何辐射场的桥梁。本项目实现了以下 7 类采样机制与空间划分策略：

### 1. 体素网格空间哈希下采样 (Voxel Grid Spatial Hashing Downsampling)
- **源码**：[`SurfaceMesher.kt`](../app/src/main/java/com/example/remy3d/engine/SurfaceMesher.kt)、[`PhotoReconstructor.kt`](../app/src/main/java/com/example/remy3d/engine/PhotoReconstructor.kt)
- **概念定义**：为消除离散空间点云在多视点交汇处的过度聚集并滤除孤立噪点（Floaters），采用三维体素滤波（Voxel Grid Filtering）。
- **实现原理**：
  将连续 3D 欧氏空间 $\mathbb{R}^3$ 离散化为尺寸为 $v_{\text{size}}$ 的规则立方体体素（Voxel）。任意点 $p = (x, y, z)$ 映射到三维离散索引 $(i, j, k)$：
  $$i = \left\lfloor \frac{x}{v_{\text{size}}} \right\rfloor, \quad j = \left\lfloor \frac{y}{v_{\text{size}}} \right\rfloor, \quad k = \left\lfloor \frac{z}{v_{\text{size}}} \right\rfloor$$
  通过大素数空间哈希函数 $H(i, j, k) = (i \cdot 73856093) \oplus (j \cdot 19349663) \oplus (k \cdot 83492791)$ 实现 $O(1)$ 查找。
  在每个非空体素内，以该体素所有采样点的质心（Centroid）与颜色均值替代原始散点：
  $$\bar{p} = \frac{1}{N_v} \sum_{m=1}^{N_v} p_m, \quad \bar{c} = \frac{1}{N_v} \sum_{m=1}^{N_v} c_m$$
  保证点云在空间中的结构均匀性，降低移动端 GPU 顶点缓冲开销。

### 2. 泊松盘均匀空间采样约束 (Poisson Disk Sampling)
- **概念定义**：在单体模型的表面网格生成与椭球构造阶段，通过最小距离约束控制高斯椭球排布密度。
- **实现原理**：
  约束物体表面生成的任意两个高斯质心点 $p_i, p_j$ 之间的欧氏距离大于最小盘半径 $r_{\min}$：
  $$\|p_i - p_j\|_2 \ge r_{\min}$$
  利用蓝噪声（Blue Noise）空间频谱特性，使高斯椭球在物体表面均匀排布，避免过密导致的深度闪烁或过疏导致的表面缝隙。

### 3. 对极几何与多视角极线搜索采样 (Epipolar Sampling)
- **概念定义**：在基于多张环绕图像进行空间深度解算时，引入双目立体视觉的对极几何约束（Epipolar Constraint）。
- **实现原理**：
  设两相邻相机的内参为 $K$，外参相对旋转平移为 $[R \mid T]$，则本质矩阵 $E = [T]_\times R$，基础矩阵为：
  $$F = K^{-T} E K^{-1}$$
  参考图中的特征像素点 $x_1$ 在目标视图中的同名对应点位于对极线 $l_2 = F x_1$ 上。沿该对极线执行一维步长采样（1D Line Sampling），将平面 $O(W \times H)$ 的二维匹配搜索简化为极线上的 $O(L)$ 一维采样。

### 4. 梯度显著性与分箱配额采样 (Gradient Importance & Binning Sampling)
- **源码**：[`PhotoReconstructor.kt`](../app/src/main/java/com/example/remy3d/engine/PhotoReconstructor.kt)
- **概念定义**：兼顾物体高频边缘轮廓与内部平滑表面的双轨分箱采样策略。
- **实现原理**：
  利用二维 Sobel 梯度算子计算像素亮度梯度幅值：
  $$M(x, y) = \sqrt{G_x(x, y)^2 + G_y(x, y)^2}$$
  将归一化梯度幅值作为采样概率质量函数（PMF）：
  $$P(x, y) = \frac{M(x, y)^\gamma}{\sum_{u, v} M(u, v)^\gamma}, \quad (\gamma \approx 1.5)$$
  在物体边缘和结构转折处分配高频特征采样配额（单图 `2,500` 点），在平滑区域分配均匀基底采样配额（单图 `3,500` 点），同时保留刚性轮廓与连续表面色彩。

### 5. 自适应步长流式采样 (Adaptive Spatial Stride Sampling)
- **源码**：[`UniversalPlyLoader.kt`](../app/src/main/java/com/example/remy3d/engine/UniversalPlyLoader.kt)
- **概念定义**：针对移动端 JVM 堆内存与显存预算，在解析外部百万级 PLY 模型时采用的流式步进采样。
- **实现原理**：
  设输入的 PLY 文件声明总顶点数为 $N$，移动端安全渲染预算上限为 $N_{\max} = 50,000$。在流式二进制读取时动态计算采样步长（Stride）：
  $$\text{sampleStep} = \begin{cases} \left\lceil \frac{N}{N_{\max}} \right\rceil, & N > N_{\max} \\ 1, & N \le N_{\max} \end{cases}$$
  遍历顶点流时仅保留满足 $\text{index} \pmod{\text{sampleStep}} == 0$ 的高斯单元，确保导入百兆级模型时内存占用稳定在 $160\text{MB}$ 阈值内。

### 6. 球谐函数 (Spherical Harmonics) 空间角度辐射度采样
- **源码**：[`UniversalPlyLoader.kt`](../app/src/main/java/com/example/remy3d/engine/UniversalPlyLoader.kt)、[`native_gaussian_engine.cpp`](../app/src/main/cpp/native_gaussian_engine.cpp)
- **概念定义**：球谐函数是定义在球面 $S^2$ 上的正交基函数系，用于在角度域对视角相关的光辐射度函数进行频谱分解与采样。
- **实现原理**：
  任意视线方向 $\vec{d} = (\sin\theta\cos\phi, \sin\theta\sin\phi, \cos\theta)$ 处的出射颜色 $C(\vec{d})$ 表示为球谐基展开：
  $$C(\vec{d}) = \sum_{l=0}^{l_{\max}} \sum_{m=-l}^{l} c_l^m Y_l^m(\vec{d})$$
  - 零阶基函数常数（各向同性漫反射）：$Y_0^0 = \frac{1}{2\sqrt{\pi}} \approx 0.28209479$；
  - 一阶基函数：
    $$Y_1^{-1} = \sqrt{\frac{3}{4\pi}} \frac{y}{r}, \quad Y_1^0 = \sqrt{\frac{3}{4\pi}} \frac{z}{r}, \quad Y_1^1 = \sqrt{\frac{3}{4\pi}} \frac{x}{r}$$
  移动端轻量级解析中提取 PLY 中的零阶基系数 `f_dc_0, f_dc_1, f_dc_2` 计算基础漫反射色：$C_{\text{base}} = 0.5 + 0.28209479 \times f\_dc$。

### 7. 各向异性高斯核前向泼溅 (Forward Gaussian Kernel Splatting)
- **源码**：[`GaussianRenderer.kt`](../app/src/main/java/com/example/remy3d/gl/GaussianRenderer.kt)
- **概念定义**：以显式各向异性三维高斯核的屏幕二维投影混合（Forward Splatting）替代隐式神经辐射场的高开销光线步进（Ray Marching）。
- **实现原理**：
  三维协方差矩阵直接投影为屏幕 2D 椭圆高斯核：
  $$G_{\text{2D}}(u, v) = \exp\left( -\frac{1}{2} \begin{bmatrix} u \\ v \end{bmatrix}^T (\Sigma')^{-1} \begin{bmatrix} u \\ v \end{bmatrix} \right)$$
  片元着色器根据归一化半径 $r^2 = u^2 + v^2$ 直接计算高斯衰减 $\exp(-2.2 r^2)$，实现 GPU 单周期求值。

---

## 二、 3DGS 基础理论与渲染物理机制

### 1. 3D 高斯函数数学定义与几何椭球参数化
三维空间中的离散高斯单元分布表达为：
$$G(x) = \exp\left( -\frac{1}{2} (x - \mu)^T \Sigma^{-1} (x - \mu) \right)$$
为保证协方差矩阵 $\Sigma \in \mathbb{R}^{3 \times 3}$ 满足半正定性（Positive Semi-Definite），将其参数化分解为单位四元数旋转矩阵 $R$ 与三维尺度矩阵 $S$：
$$\Sigma = R S S^T R^T$$
- 对角尺度阵 $S = \text{diag}(s_x, s_y, s_z)$，在 PLY 中以对数尺度存储：$s_i = \exp(\text{scale}_i)$；
- 旋转矩阵 $R$ 由归一化四元数 $q = (w, x, y, z)$ 构建。

### 2. EWA Splatting：3D 协方差向 2D 屏幕平面的各向异性投影
根据 Zwicker 提出的 EWA (Elliptical Weighted Average) 算法，设视图变换矩阵为 $W$，透视投影变换的一阶雅可比矩阵为 $J$，则 2D 屏幕投影协方差 $\Sigma' \in \mathbb{R}^{2 \times 2}$ 为：
$$\Sigma' = J W \Sigma W^T J^T$$
顶点着色器（Vertex Shader）根据椭球主轴比例与旋转角展开各向异性椭圆四边形面片，闭合离散点云间隙。

### 3. 体积辐射度累积混合方程 (Volume Radiance Accumulation)
对沿视线深度排序后的高斯序列 $\mathcal{N}$，按体积透光率公式逐点混合：
$$C_{\text{final}} = \sum_{i \in \mathcal{N}} c_i \cdot \alpha_i \prod_{j=1}^{i-1} (1 - \alpha_j)$$
其中 $c_i$ 为出射颜色，$\alpha_i = \text{opacity}_i \cdot \exp(-2.2 \cdot r^2)$ 为高斯渐变不透明度，$\prod_{j=1}^{i-1} (1 - \alpha_j)$ 为累积透光率（Transmittance）。

### 4. 线性辐射空间转换与屏幕 Gamma 校正
3DGS 球谐系数计算的辐射颜色位于**物理线性空间 (Linear Radiance)**，而移动设备显示屏幕遵循 **sRGB (Gamma 2.2)** 响应。片元着色器中执行饱和度补偿与 Gamma 空间转换：
```glsl
// 1. 饱和度自适应补偿 (+28%)
float gray = dot(col, vec3(0.299, 0.587, 0.114));
col = mix(vec3(gray), col, 1.28);

// 2. 伽马空间转换 (Gamma 1.85 校正)
col = pow(clamp(col, 0.0, 1.0), vec3(1.0 / 1.85));

// 3. 高斯中心不透明度平滑强化
finalAlpha = smoothstep(0.06, 0.92, finalAlpha);
```

### 5. 不透明度逆 Sigmoid (Logit) 激活解码
3DGS 标准 PLY 中的 `opacity` 以逆 Sigmoid logit 形式存储：
$$\text{raw\_opacity} = \ln\left( \frac{\alpha}{1 - \alpha} \right) \implies \alpha = \frac{1}{1 + \exp(-\text{raw\_opacity})}$$
解析时通过 Sigmoid 激活函数还原 $[0, 1]$ 区间物理不透明度。

### 6. 360° 球面轨道旋转与实时视线深度重排序机制
在 [`GaussianRenderer.kt`](../app/src/main/java/com/example/remy3d/gl/GaussianRenderer.kt) 与 [`SplatGLView.kt`](../app/src/main/java/com/example/remy3d/gl/SplatGLView.kt) 中，360° 轨道旋转与自由环绕观察由以下 4 层机制组成：
1. **AABB 包围盒质心归零与尺度归一化**：
   $$\mathbf{c} = \left( \frac{x_{\min}+x_{\max}}{2},\ \frac{y_{\min}+y_{\max}}{2},\ \frac{z_{\min}+z_{\max}}{2} \right), \quad s_{\text{auto}} = \frac{2.0}{\max(\Delta x, \Delta y, \Delta z)}$$
   将所有高斯中心变换至 $\mathbf{p}'_i = (\mathbf{p}_i - \mathbf{c}) \cdot s_{\text{auto}}$，固定旋转中心于 $(0, 0, 0)$。
2. **球面极坐标轨道相机（Spherical Orbit Camera）**：
   $$\begin{cases} e_x = T_x + R \cdot \cos(\phi_{\text{pitch}}) \cdot \sin(\theta_{\text{yaw}}) \\ e_y = T_y + R \cdot \sin(\phi_{\text{pitch}}) \\ e_z = T_z + R \cdot \cos(\phi_{\text{pitch}}) \cdot \cos(\theta_{\text{yaw}}) \end{cases}$$
3. **60 FPS 恒定角速度自转与触摸手势叠加**：
   开启 `isAutoRotate` 时每帧递增 $\theta_{\text{yaw}}^{(t+1)} = \theta_{\text{yaw}}^{(t)} + 0.008\text{ rad}$，同时支持单指拖拽角度增量与双指缩放平移。
4. **C++ NDK 实时视线深度重排序与裁剪空间 Billboard 展开**：
   每帧计算各高斯球在当前视线方向上的投影深度 $z_i = (\mathbf{p}'_i - \mathbf{E}) \cdot \vec{d}$ 并通过基数排序重排绘制顺序，配合顶点着色器屏幕空间展开，保证 360° 任意角度下前后遮挡关系准确。
