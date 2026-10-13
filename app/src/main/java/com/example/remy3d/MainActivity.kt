package com.example.remy3d

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.AudioManager
import android.media.ExifInterface
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.ScreenRotationAlt
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Drafts
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.remy3d.camera.NativeProCameraController
import com.example.remy3d.camera.ProCameraSettings
import com.example.remy3d.engine.ArCorePointCloudManager
import com.example.remy3d.engine.ImageQualityEvaluator
import com.example.remy3d.engine.MediaStoreSaver
import com.example.remy3d.engine.PhotoReconstructor
import com.example.remy3d.plyio.PlyExporter
import com.example.remy3d.engine.ReconstructionResult
import com.example.remy3d.engine.SpatialCaptureGuide
import com.example.remy3d.engine.SplatPoint
import com.example.remy3d.engine.UniversalPlyLoader
import com.example.remy3d.engine.VideoKeyframeExtractor
import com.example.remy3d.gl.GaussianRenderer
import com.example.remy3d.gl.SplatGLView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue

// ── 色彩系统（白色明亮主题 + 3D展示区黑色背景）──────────────────────
private val Bg        = Color(0xFFF2F5F9)
private val BgCard    = Color(0xFFFFFFFF)
private val BgPanel   = Color(0xFFFFFFFF)
private val Accent    = Color(0xFF4F5BFF)
private val AccentAlt = Color(0xFF3D49E6)
private val Cyan      = Color(0xFF0095B6)
private val Green     = Color(0xFF0FA968)
private val Muted     = Color(0xFF64748B)
private val TextPri   = Color(0xFF0F172A)
private val TextSec   = Color(0xFF475569)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.WHITE
        window.navigationBarColor = android.graphics.Color.WHITE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
        }
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Accent,
                    onPrimary = Color.White,
                    secondary = Cyan,
                    onSecondary = Color.White,
                    background = Bg,
                    onBackground = TextPri,
                    surface = BgCard,
                    onSurface = TextPri
                )
            ) { RemyApp() }
        }
    }
}

// ── 数据模型 ──────────────────────────────────────────────────
// ── 数据模型 ──────────────────────────────────────────────────
data class SceneRecord(
    val name: String,
    val date: String,
    val plyPath: String?,
    val pointCount: Int,
    val imageCount: Int,
    val isPreset: Boolean,
    val presetType: GaussianRenderer.SceneType? = null,
    val points: List<SplatPoint>? = null,
    val imagePaths: List<String> = emptyList()
)

// ── 草稿模型（支持草稿列表、详细存储路径、模式与时间戳）──────────────
data class DraftItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val date: String,
    val mode: SpatialCaptureGuide.ScanMode,
    val imagePaths: List<String>,
    val note: String = ""
) {
    val existingFiles: List<File>
        get() = imagePaths.map { File(it) }.filter { it.exists() }
}

// ── 本地磁盘数据持久化与草稿箱管理 ──────────────────────────────
object LocalRecordStore {
    private const val RECORDS_FILE = "records_cache.json"
    private const val DRAFTS_FILE = "draft_sessions_list.json"
    private const val LEGACY_DRAFT_FILE = "draft_session.json"

    fun loadRecords(context: android.content.Context): List<SceneRecord> {
        fun getBuiltinPresets(): List<SceneRecord> = listOf(
            SceneRecord("🐼 国宝大熊猫", "单体实物预设 · 3DGS", null, 3600, 0, true, GaussianRenderer.SceneType.PANDA),
            SceneRecord("🐤 阳光小黄鸭", "单体实物预设 · 3DGS", null, 3200, 0, true, GaussianRenderer.SceneType.DUCK),
            SceneRecord("🧸 暖心泰迪熊", "单体实物预设 · 3DGS", null, 3800, 0, true, GaussianRenderer.SceneType.BEAR),
            SceneRecord("🏺 经典青花瓷", "单体实物预设 · 3DGS", null, 2800, 0, true, GaussianRenderer.SceneType.OBJECT),
            SceneRecord("🛋️ 现代轻奢客厅", "空间场景预设 · 3DGS", null, 3500, 0, true, GaussianRenderer.SceneType.ROOM),
            SceneRecord("🏛️ 几何艺术雕塑", "户外建筑预设 · 3DGS", null, 2600, 0, true, GaussianRenderer.SceneType.OUTDOOR),
        )

        val file = File(context.getExternalFilesDir(null), RECORDS_FILE)
        if (!file.exists()) {
            return getBuiltinPresets()
        }
        return try {
            val jsonStr = file.readText()
            val arr = org.json.JSONArray(jsonStr)
            val list = mutableListOf<SceneRecord>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val isPreset = obj.optBoolean("isPreset", false)
                val presetStr = obj.optString("presetType", null)
                val presetType = if (!presetStr.isNullOrEmpty()) {
                    try { GaussianRenderer.SceneType.valueOf(presetStr) } catch (_: Exception) { null }
                } else null

                val imagePathsList = mutableListOf<String>()
                if (obj.has("imagePaths")) {
                    val pathsArr = obj.getJSONArray("imagePaths")
                    for (pi in 0 until pathsArr.length()) {
                        imagePathsList.add(pathsArr.getString(pi))
                    }
                }

                list.add(
                    SceneRecord(
                        name = obj.optString("name", "未命名"),
                        date = obj.optString("date", ""),
                        plyPath = if (obj.has("plyPath")) obj.optString("plyPath") else null,
                        pointCount = obj.optInt("pointCount", 0),
                        imageCount = obj.optInt("imageCount", 0),
                        isPreset = isPreset,
                        presetType = presetType,
                        imagePaths = imagePathsList
                    )
                )
            }
            val defaultPresets = getBuiltinPresets()
            val existingPresetTypes = list.filter { it.isPreset }.mapNotNull { it.presetType }.toSet()
            val missingPresets = defaultPresets.filter { it.presetType !in existingPresetTypes }
            val combined = if (missingPresets.isNotEmpty()) {
                missingPresets + list
            } else {
                list
            }
            if (combined.isEmpty()) defaultPresets else combined
        } catch (_: Exception) {
            getBuiltinPresets()
        }
    }

    fun saveRecords(context: android.content.Context, records: List<SceneRecord>) {
        try {
            val arr = org.json.JSONArray()
            for (r in records) {
                val obj = org.json.JSONObject().apply {
                    put("name", r.name)
                    put("date", r.date)
                    if (r.plyPath != null) put("plyPath", r.plyPath)
                    put("pointCount", r.pointCount)
                    put("imageCount", r.imageCount)
                    put("isPreset", r.isPreset)
                    if (r.presetType != null) put("presetType", r.presetType.name)
                    if (r.imagePaths.isNotEmpty()) {
                        val pathsArr = org.json.JSONArray()
                        r.imagePaths.forEach { pathsArr.put(it) }
                        put("imagePaths", pathsArr)
                    }
                }
                arr.put(obj)
            }
            val file = File(context.getExternalFilesDir(null), RECORDS_FILE)
            file.writeText(arr.toString())
        } catch (_: Exception) {}
    }

    // ── 草稿列表读写 ──────────────────────────────────────────
    fun loadDrafts(context: android.content.Context): List<DraftItem> {
        val file = File(context.getExternalFilesDir(null), DRAFTS_FILE)
        val drafts = mutableListOf<DraftItem>()
        if (file.exists()) {
            try {
                val arr = org.json.JSONArray(file.readText())
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val id = obj.optString("id", java.util.UUID.randomUUID().toString())
                    val name = obj.optString("name", "未命名草稿")
                    val date = obj.optString("date", "")
                    val modeStr = obj.optString("mode", SpatialCaptureGuide.ScanMode.SPATIAL_SCENE.name)
                    val mode = try { SpatialCaptureGuide.ScanMode.valueOf(modeStr) } catch (_: Exception) { SpatialCaptureGuide.ScanMode.SPATIAL_SCENE }
                    val note = obj.optString("note", "")
                    val pathArr = obj.optJSONArray("images") ?: org.json.JSONArray()
                    val paths = mutableListOf<String>()
                    for (k in 0 until pathArr.length()) {
                        paths.add(pathArr.getString(k))
                    }
                    val validFiles = paths.filter { File(it).exists() }
                    if (validFiles.isNotEmpty()) {
                        drafts.add(DraftItem(id, name, date, mode, validFiles, note))
                    }
                }
            } catch (_: Exception) {}
        }

        // 兼容单草稿旧文件
        val legacy = File(context.getExternalFilesDir(null), LEGACY_DRAFT_FILE)
        if (legacy.exists() && drafts.isEmpty()) {
            try {
                val root = org.json.JSONObject(legacy.readText())
                val modeStr = root.optString("mode", SpatialCaptureGuide.ScanMode.SPATIAL_SCENE.name)
                val mode = try { SpatialCaptureGuide.ScanMode.valueOf(modeStr) } catch (_: Exception) { SpatialCaptureGuide.ScanMode.SPATIAL_SCENE }
                val arr = root.optJSONArray("images") ?: org.json.JSONArray()
                val paths = mutableListOf<String>()
                for (i in 0 until arr.length()) paths.add(arr.getString(i))
                val valid = paths.filter { File(it).exists() }
                if (valid.isNotEmpty()) {
                    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    val d = DraftItem(name = "未命名草稿", date = fmt.format(Date()), mode = mode, imagePaths = valid)
                    drafts.add(d)
                    saveDrafts(context, drafts)
                    legacy.delete()
                }
            } catch (_: Exception) {}
        }

        return drafts
    }

    fun saveDrafts(context: android.content.Context, drafts: List<DraftItem>) {
        try {
            val file = File(context.getExternalFilesDir(null), DRAFTS_FILE)
            val arr = org.json.JSONArray()
            for (d in drafts) {
                val obj = org.json.JSONObject().apply {
                    put("id", d.id)
                    put("name", d.name)
                    put("date", d.date)
                    put("mode", d.mode.name)
                    put("note", d.note)
                    val pArr = org.json.JSONArray()
                    d.imagePaths.forEach { pArr.put(it) }
                    put("images", pArr)
                }
                arr.put(obj)
            }
            file.writeText(arr.toString())
        } catch (_: Exception) {}
    }

    fun addOrUpdateDraft(
        context: android.content.Context,
        imageFiles: List<File>,
        mode: SpatialCaptureGuide.ScanMode,
        draftId: String? = null,
        customName: String? = null
    ): DraftItem? {
        val paths = imageFiles.filter { it.exists() }.map { it.absolutePath }
        if (paths.isEmpty()) return null
        val pathSet = paths.toSet()
        val drafts = loadDrafts(context).filterNot { existing ->
            (draftId != null && existing.id == draftId) || existing.imagePaths.toSet() == pathSet
        }.toMutableList()
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val name = customName ?: if (mode == SpatialCaptureGuide.ScanMode.SPATIAL_SCENE) "空间场景草稿 (${paths.size}张)" else "单体实物草稿 (${paths.size}张)"
        val newDraft = DraftItem(
            id = draftId ?: java.util.UUID.randomUUID().toString(),
            name = name,
            date = fmt.format(Date()),
            mode = mode,
            imagePaths = paths,
            note = "${paths.firstOrNull()?.let { File(it).parent ?: "" } ?: ""}"
        )
        drafts.add(0, newDraft)
        saveDrafts(context, drafts)
        return newDraft
    }

    fun deleteDraft(context: android.content.Context, draftId: String) {
        val drafts = loadDrafts(context).filter { it.id != draftId }
        saveDrafts(context, drafts)
    }

    /**
     * 重建生成模型后，自动清理草稿箱中对应的草稿记录 (按ID或按照片路径匹配自动删除)
     */
    fun deleteDraftsMatchingFiles(context: android.content.Context, files: List<File>, draftId: String? = null) {
        val targetPathSet = files.map { it.absolutePath }.toSet()
        val drafts = loadDrafts(context).filter { draft ->
            if (draftId != null && draft.id == draftId) return@filter false
            // 检查该草稿所含图片与已重建生成的图片是否有重叠
            val overlap = draft.imagePaths.any { it in targetPathSet }
            !overlap
        }
        saveDrafts(context, drafts)
    }

    fun clearAllDrafts(context: android.content.Context) {
        try {
            val file = File(context.getExternalFilesDir(null), DRAFTS_FILE)
            if (file.exists()) file.delete()
            val legacy = File(context.getExternalFilesDir(null), LEGACY_DRAFT_FILE)
            if (legacy.exists()) legacy.delete()
        } catch (_: Exception) {}
    }
}

// ── 页面路由 ──────────────────────────────────────────────────
private enum class Page { HOME, CAPTURE, REBUILD, VIEWER, LIBRARY, DRAFTS }

@Composable
private fun RemyApp() {
    val context = LocalContext.current
    var page by remember { mutableStateOf(Page.HOME) }

    // ── 采集状态 ──
    var capturedFiles by remember { mutableStateOf(listOf<File>()) }

    // ── 重建状态 ──
    var rebuildStage by remember { mutableStateOf("") }
    var rebuildProgress by remember { mutableFloatStateOf(0f) }
    var isRebuilding by remember { mutableStateOf(false) }
    var lastResult by remember { mutableStateOf<ReconstructionResult?>(null) }

    // ── 查看器状态 ──
    var viewerPoints by remember { mutableStateOf<List<SplatPoint>?>(null) }
    var viewerPresetType by remember { mutableStateOf<GaussianRenderer.SceneType?>(GaussianRenderer.SceneType.ROOM) }
    var viewerTitle by remember { mutableStateOf("") }
    var viewerInfo by remember { mutableStateOf("") }
    var viewerPlyPath by remember { mutableStateOf<String?>(null) }
    var viewerImagePaths by remember { mutableStateOf<List<String>>(emptyList()) }

    // ── 记录列表 (磁盘持久化) ──
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    var records by remember { mutableStateOf(LocalRecordStore.loadRecords(context)) }
    var isOpeningRecord by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // ── 采集与 AR/传感器管理 ──
    val captureGuide = remember { SpatialCaptureGuide(context) }
    val arManager = remember { ArCorePointCloudManager(context) }
    var arPointsBuffer by remember { mutableStateOf<List<SplatPoint>>(emptyList()) }

    // ── 采集模式：3DGS 环绕重建 vs ARCore 空间实境扫描 ──
    var isArCaptureMode by remember { mutableStateOf(false) }
    var currentScanMode by remember { mutableStateOf(SpatialCaptureGuide.ScanMode.SPATIAL_SCENE) }

    // 草稿列表与恢复提示
    var draftsList by remember { mutableStateOf(LocalRecordStore.loadDrafts(context)) }
    var activeSessionDraftId by remember { mutableStateOf<String?>(null) }

    // ── 外部媒体导入状态 (相册多图 / 视频抽帧) ──
    var isImportingMedia by remember { mutableStateOf(false) }
    var importDialogStatus by remember { mutableStateOf("") }
    var importCurrentFrame by remember { mutableIntStateOf(0) }
    var importTotalFrames by remember { mutableIntStateOf(0) }

    val multiImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            isImportingMedia = true
            importDialogStatus = "正在从相册读取并规范化 ${uris.size} 张照片..."
            importCurrentFrame = 0
            importTotalFrames = uris.size
            scope.launch(Dispatchers.IO) {
                val targetDir = File(context.getExternalFilesDir(null), "imported_captures").apply { mkdirs() }
                val importedFiles = VideoKeyframeExtractor.importImageUris(
                    context = context,
                    uris = uris,
                    targetDir = targetDir,
                    onProgress = { cur, total ->
                        importCurrentFrame = cur
                        importTotalFrames = total
                        importDialogStatus = "正在导入相册照片 ($cur/$total)..."
                    }
                )
                withContext(Dispatchers.Main) {
                    isImportingMedia = false
                    if (importedFiles.isNotEmpty()) {
                        if (capturedFiles.isEmpty() || captureGuide.targetTracker.currentStatus == com.example.remy3d.engine.TargetTrackerEngine.TargetStatus.NOT_SET) {
                            captureGuide.targetTracker.lockTargetFromImage(importedFiles.first(), 0f)
                        }
                        capturedFiles = capturedFiles + importedFiles
                        importedFiles.forEach { _ -> captureGuide.recordShot() }
                        page = Page.CAPTURE
                        Toast.makeText(context, "成功导入 ${importedFiles.size} 张照片！已锁定三维中心点位", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "未能导入照片，请重试", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { videoUri ->
        if (videoUri != null) {
            isImportingMedia = true
            importDialogStatus = "正在读取视频流并分析关键帧..."
            importCurrentFrame = 0
            importTotalFrames = 30
            scope.launch(Dispatchers.IO) {
                val targetDir = File(context.getExternalFilesDir(null), "video_frames").apply { mkdirs() }
                val extractedFrames = VideoKeyframeExtractor.extractFrames(
                    context = context,
                    videoUri = videoUri,
                    targetDir = targetDir,
                    stepMs = 500L,
                    maxFrames = 45,
                    callback = object : VideoKeyframeExtractor.ExtractCallback {
                        override fun onProgress(currentFrame: Int, totalEstimated: Int, status: String) {
                            importCurrentFrame = currentFrame
                            importTotalFrames = totalEstimated
                            importDialogStatus = status
                        }
                    }
                )
                withContext(Dispatchers.Main) {
                    isImportingMedia = false
                    if (extractedFrames.size >= 3) {
                        if (capturedFiles.isEmpty() || captureGuide.targetTracker.currentStatus == com.example.remy3d.engine.TargetTrackerEngine.TargetStatus.NOT_SET) {
                            captureGuide.targetTracker.lockTargetFromImage(extractedFrames.first(), 0f)
                        }
                        capturedFiles = capturedFiles + extractedFrames
                        extractedFrames.forEach { _ -> captureGuide.recordShot() }
                        page = Page.CAPTURE
                        Toast.makeText(context, "视频抽帧完成！成功抽取并过滤出 ${extractedFrames.size} 张高质视角帧并锁定点位", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "有效清晰关键帧不足 (${extractedFrames.size} 帧)，请录制环绕更平稳清晰的视频", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // 底部 Tab 切换防误触确认弹窗 (当当前在拍摄页且已有未保存照片时)
    var pendingTargetPage by remember { mutableStateOf<Page?>(null) }
    var showTabSwitchConfirmDialog by remember { mutableStateOf(false) }

    val handleNavigate: (Page) -> Unit = { target ->
        if (page == Page.CAPTURE && capturedFiles.isNotEmpty() && target != Page.CAPTURE) {
            pendingTargetPage = target
            showTabSwitchConfirmDialog = true
        } else {
            page = target
        }
    }

    Scaffold(
        containerColor = Bg,
        bottomBar = {
            if (page != Page.VIEWER) {
                RemyNavBar(current = page, draftCount = draftsList.size, onNav = handleNavigate)
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (page) {
                Page.HOME -> HomeScreen(
                    currentScanMode = currentScanMode,
                    draftCount = draftsList.size,
                    onResumeDraft = {
                        val latest = draftsList.firstOrNull()
                        if (latest != null) {
                            val validFiles = latest.existingFiles
                            activeSessionDraftId = latest.id
                            capturedFiles = validFiles
                            arPointsBuffer = emptyList()
                            isArCaptureMode = true
                            currentScanMode = latest.mode
                            captureGuide.scanMode = latest.mode
                            captureGuide.resetCoverage()
                            if (latest.mode == SpatialCaptureGuide.ScanMode.OBJECT && validFiles.isNotEmpty()) {
                                captureGuide.targetTracker.lockTargetFromImage(validFiles.first(), 0f)
                            }
                            arManager.resetTrajectory()
                            page = Page.CAPTURE
                            Toast.makeText(context, "已恢复草稿【${latest.name}】(${validFiles.size} 张照片)", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onViewDraftsList = {
                        draftsList = LocalRecordStore.loadDrafts(context)
                        page = Page.DRAFTS
                    },
                    onCaptureSpatialScene = {
                        activeSessionDraftId = null
                        capturedFiles = emptyList()
                        arPointsBuffer = emptyList()
                        isArCaptureMode = true
                        currentScanMode = SpatialCaptureGuide.ScanMode.SPATIAL_SCENE
                        captureGuide.scanMode = SpatialCaptureGuide.ScanMode.SPATIAL_SCENE
                        captureGuide.resetCoverage()
                        arManager.resetTrajectory()
                        page = Page.CAPTURE
                    },
                    onCaptureObject = {
                        activeSessionDraftId = null
                        capturedFiles = emptyList()
                        arPointsBuffer = emptyList()
                        isArCaptureMode = true
                        currentScanMode = SpatialCaptureGuide.ScanMode.OBJECT
                        captureGuide.scanMode = SpatialCaptureGuide.ScanMode.OBJECT
                        captureGuide.resetCoverage()
                        arManager.resetTrajectory()
                        page = Page.CAPTURE
                    },
                    onViewer  = {
                        viewerPoints = null
                        viewerPresetType = GaussianRenderer.SceneType.ROOM
                        viewerTitle = "客厅演示"
                        viewerInfo = "预设场景"
                        viewerPlyPath = null
                        viewerImagePaths = emptyList()
                        page = Page.VIEWER
                    },
                    onImportPhotos = { multiImageLauncher.launch("image/*") },
                    onImportVideo = { videoLauncher.launch("video/*") },
                    onLibrary = { page = Page.LIBRARY }
                )
                Page.CAPTURE -> CaptureScreen(
                    capturedFiles   = capturedFiles,
                    guide           = captureGuide,
                    arManager       = arManager,
                    isArMode        = isArCaptureMode,
                    scanMode        = currentScanMode,
                    onFileAdded   = { file ->
                        capturedFiles = capturedFiles + file
                        captureGuide.recordShot()
                        // 如果 ARCore 可用，抽取本帧 3D 物理点
                        val pts = arManager.extractPointsFromCurrentFrame()
                        if (pts.isNotEmpty()) {
                            arPointsBuffer = arPointsBuffer + pts
                        }
                    },
                    onImportPhotos = { multiImageLauncher.launch("image/*") },
                    onImportVideo = { videoLauncher.launch("video/*") },
                    onSwitchMode = { newMode ->
                        currentScanMode = newMode
                        captureGuide.scanMode = newMode
                        captureGuide.resetCoverage()
                        arManager.resetTrajectory()
                    },
                    onSaveDraft = {
                        if (capturedFiles.isNotEmpty()) {
                            val savedCount = capturedFiles.size
                            LocalRecordStore.addOrUpdateDraft(context, capturedFiles, currentScanMode, activeSessionDraftId)
                            draftsList = LocalRecordStore.loadDrafts(context)
                            activeSessionDraftId = null
                            capturedFiles = emptyList()
                            arPointsBuffer = emptyList()
                            captureGuide.resetCoverage()
                            arManager.resetTrajectory()
                            Toast.makeText(context, "已保存到草稿箱 ($savedCount 张照片)", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onResetSession = {
                        val filesToDiscard = capturedFiles
                        val draftIdToDiscard = activeSessionDraftId
                        if (draftIdToDiscard != null || filesToDiscard.isNotEmpty()) {
                            LocalRecordStore.deleteDraftsMatchingFiles(context, filesToDiscard, draftIdToDiscard)
                            draftsList = LocalRecordStore.loadDrafts(context)
                        }
                        activeSessionDraftId = null
                        capturedFiles = emptyList()
                        arPointsBuffer = emptyList()
                        captureGuide.resetCoverage()
                        arManager.resetTrajectory()
                        scope.launch(Dispatchers.IO) {
                            filesToDiscard.forEach { file ->
                                try { if (file.exists()) file.delete() } catch (_: Exception) {}
                            }
                        }
                        Toast.makeText(context, "已放弃当前照片并清空会话", Toast.LENGTH_SHORT).show()
                    },
                    onExitToHome = {
                        page = Page.HOME
                    },
                    onFinish      = {
                        if (capturedFiles.isNotEmpty()) {
                            val filesToReconstruct = capturedFiles
                            rebuildProgress = 0f; rebuildStage = "准备中…"; isRebuilding = true; lastResult = null
                            page = Page.REBUILD

                            scope.launch(Dispatchers.IO) {
                                val reconstructor = PhotoReconstructor()
                                val outDir = File(context.getExternalFilesDir(null), "reconstructions")
                                val result = reconstructor.reconstruct(
                                    imageFiles = filesToReconstruct,
                                    outputDir  = outDir,
                                    arPoints   = if (arPointsBuffer.isNotEmpty()) arPointsBuffer else null,
                                    isSpatialScene = (currentScanMode == SpatialCaptureGuide.ScanMode.SPATIAL_SCENE),
                                    callback   = object : PhotoReconstructor.ProgressCallback {
                                        override fun onStageChanged(stage: String, progress: Float) {
                                            rebuildStage = stage; rebuildProgress = progress
                                        }
                                    }
                                )
                                withContext(Dispatchers.Main) {
                                    lastResult = result
                                    isRebuilding = false

                                    // 生成完成，同步清理关联的草稿记录
                                    LocalRecordStore.deleteDraftsMatchingFiles(context, filesToReconstruct, activeSessionDraftId)
                                    draftsList = LocalRecordStore.loadDrafts(context)
                                    activeSessionDraftId = null

                                    // 重置采集页面状态与内存
                                    capturedFiles = emptyList()
                                    arPointsBuffer = emptyList()
                                    captureGuide.resetCoverage()
                                    arManager.resetTrajectory()

                                    // 添加到记录并立即持久化存储到本地磁盘 records_cache.json
                                    val newRecord = SceneRecord(
                                        name = "重建 ${fmt.format(Date())}",
                                        date = fmt.format(Date()),
                                        plyPath = result.plyFile.absolutePath,
                                        pointCount = result.pointCount,
                                        imageCount = result.imageCount,
                                        isPreset = false,
                                        points = result.points,
                                        imagePaths = result.sourceImagePaths
                                    )
                                    val updatedRecords = listOf(newRecord) + records
                                    records = updatedRecords
                                    LocalRecordStore.saveRecords(context, updatedRecords)
                                }
                            }
                        }
                    }
                )
                Page.DRAFTS -> DraftsScreen(
                    drafts = draftsList,
                    onRestoreDraft = { draft ->
                        val validFiles = draft.existingFiles
                        activeSessionDraftId = draft.id
                        capturedFiles = validFiles
                        arPointsBuffer = emptyList()
                        isArCaptureMode = true
                        currentScanMode = draft.mode
                        captureGuide.scanMode = draft.mode
                        captureGuide.resetCoverage()
                        if (draft.mode == SpatialCaptureGuide.ScanMode.OBJECT && validFiles.isNotEmpty()) {
                            captureGuide.targetTracker.lockTargetFromImage(validFiles.first(), 0f)
                        }
                        arManager.resetTrajectory()
                        page = Page.CAPTURE
                        Toast.makeText(context, "已恢复草稿【${draft.name}】(${validFiles.size} 张)", Toast.LENGTH_SHORT).show()
                    },
                    onDeleteDraft = { draft ->
                        if (activeSessionDraftId == draft.id) {
                            activeSessionDraftId = null
                            capturedFiles = emptyList()
                        }
                        LocalRecordStore.deleteDraft(context, draft.id)
                        draftsList = LocalRecordStore.loadDrafts(context)
                        Toast.makeText(context, "已删除草稿【${draft.name}】", Toast.LENGTH_SHORT).show()
                    },
                    onClearAllDrafts = {
                        activeSessionDraftId = null
                        LocalRecordStore.clearAllDrafts(context)
                        draftsList = emptyList()
                        Toast.makeText(context, "已清空所有草稿", Toast.LENGTH_SHORT).show()
                    },
                    onBack = { page = Page.HOME }
                )
                Page.REBUILD -> RebuildScreen(
                    stage       = rebuildStage,
                    progress    = rebuildProgress,
                    isRebuilding = isRebuilding,
                    result      = lastResult,
                    imageCount  = lastResult?.imageCount ?: 0,
                    onViewResult = {
                        val r = lastResult ?: return@RebuildScreen
                        viewerPoints = r.points
                        viewerPresetType = null
                        viewerTitle = "重建结果"
                        viewerInfo = "${r.pointCount} 个点 · ${r.imageCount} 张图片 · ${r.processingTimeMs/1000}s"
                        viewerPlyPath = r.plyFile.absolutePath
                        viewerImagePaths = r.sourceImagePaths
                        page = Page.VIEWER
                    }
                )
                Page.VIEWER -> ViewerScreen(
                    title       = viewerTitle,
                    info        = viewerInfo,
                    points      = viewerPoints,
                    presetScene = viewerPresetType,
                    plyPath     = viewerPlyPath,
                    imagePaths  = viewerImagePaths,
                    onBack      = { page = Page.HOME }
                )
                Page.LIBRARY -> LibraryScreen(
                    records = records,
                    onClearRecords = {
                        val defaultList = LocalRecordStore.loadRecords(context).filter { it.isPreset }.ifEmpty {
                            listOf(
                                SceneRecord("🐼 国宝大熊猫", "单体实物预设 · 3DGS", null, 3600, 0, true, GaussianRenderer.SceneType.PANDA),
                                SceneRecord("🐤 阳光小黄鸭", "单体实物预设 · 3DGS", null, 3200, 0, true, GaussianRenderer.SceneType.DUCK),
                                SceneRecord("🧸 暖心泰迪熊", "单体实物预设 · 3DGS", null, 3800, 0, true, GaussianRenderer.SceneType.BEAR),
                                SceneRecord("🏺 经典青花瓷", "单体实物预设 · 3DGS", null, 2800, 0, true, GaussianRenderer.SceneType.OBJECT),
                                SceneRecord("🛋️ 现代轻奢客厅", "空间场景预设 · 3DGS", null, 3500, 0, true, GaussianRenderer.SceneType.ROOM),
                                SceneRecord("🏛️ 几何艺术雕塑", "户外建筑预设 · 3DGS", null, 2600, 0, true, GaussianRenderer.SceneType.OUTDOOR),
                            )
                        }
                        records = defaultList
                        LocalRecordStore.saveRecords(context, defaultList)
                    },
                    onImportExternalPly = { uri ->
                        scope.launch(Dispatchers.IO) {
                            // 将外部 content:// URI 流式拷贝到沙盒私有文件，以便 Native C++ 与 Vulkan 直接 mmap/读写
                            val fileName = uri.lastPathSegment?.split("/")?.last() ?: "imported_model_${System.currentTimeMillis()}.ply"
                            val safeFile = File(context.cacheDir, "native_ply_${System.currentTimeMillis()}.ply")
                            try {
                                context.contentResolver.openInputStream(uri)?.use { input ->
                                    safeFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                            } catch (_: Exception) {}

                            val targetPath = if (safeFile.exists() && safeFile.length() > 0) safeFile.absolutePath else uri.toString()
                            val pts = UniversalPlyLoader.loadFromFile(safeFile).ifEmpty {
                                UniversalPlyLoader.loadFromUri(context, uri)
                            }

                            withContext(Dispatchers.Main) {
                                if (pts.isNotEmpty()) {
                                    val newRecord = SceneRecord(
                                        name = fileName,
                                        date = fmt.format(Date()),
                                        plyPath = targetPath,
                                        pointCount = pts.size,
                                        imageCount = 0,
                                        isPreset = false,
                                        points = pts
                                    )
                                    val updatedRecords = listOf(newRecord) + records
                                    records = updatedRecords
                                    LocalRecordStore.saveRecords(context, updatedRecords)
                                    // 立即打开查看外部模型
                                    viewerPoints = pts
                                    viewerPresetType = null
                                    viewerTitle = fileName
                                    viewerInfo = "导入的外部 3DGS 模型 (${pts.size} 点)"
                                    viewerPlyPath = targetPath
                                    viewerImagePaths = emptyList()
                                    page = Page.VIEWER
                                } else {
                                    Toast.makeText(context, "PLY 文件解析失败或点数为空", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    onOpen  = { record ->
                        if (record.isPreset) {
                            viewerPoints = null
                            viewerPresetType = record.presetType ?: GaussianRenderer.SceneType.ROOM
                            viewerTitle = record.name
                            viewerInfo = "预设 3DGS 演示场景"
                            viewerPlyPath = null
                            viewerImagePaths = emptyList()
                            page = Page.VIEWER
                        } else {
                            if (record.points != null && record.points.isNotEmpty()) {
                                viewerPoints = record.points
                                viewerPresetType = null
                                viewerTitle = record.name
                                viewerInfo = "${record.pointCount} 个连续高斯椭球 · ${record.imageCount} 张图片"
                                viewerPlyPath = record.plyPath
                                viewerImagePaths = record.imagePaths
                                page = Page.VIEWER
                            } else {
                                // 异步加载大体积 PLY 文件，杜绝阻塞主线程导致 Input dispatching timed out (ANR)
                                isOpeningRecord = true
                                scope.launch(Dispatchers.IO) {
                                    val pts = record.plyPath?.let { path ->
                                        try {
                                            if (path.startsWith("content://")) {
                                                UniversalPlyLoader.loadFromUri(context, Uri.parse(path))
                                            } else {
                                                UniversalPlyLoader.loadFromFile(File(path))
                                            }
                                        } catch (e: Exception) {
                                            null
                                        }
                                    } ?: emptyList()

                                    withContext(Dispatchers.Main) {
                                        isOpeningRecord = false
                                        if (pts.isNotEmpty()) {
                                            viewerPoints = pts
                                            viewerPresetType = null
                                            viewerTitle = record.name
                                            viewerInfo = "${pts.size} 个连续高斯椭球 · ${record.imageCount} 张图片"
                                            viewerPlyPath = record.plyPath
                                            viewerImagePaths = record.imagePaths
                                            page = Page.VIEWER
                                        } else {
                                            Toast.makeText(context, "打开失败，请重试", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }
                    }
                )
            }

            // 底部 Tab 导航切换时，若当前拍摄会话有未处理照片，弹出防误触拦截确认弹窗
            if (showTabSwitchConfirmDialog && pendingTargetPage != null) {
                val targetPage = pendingTargetPage!!
                androidx.compose.ui.window.Dialog(onDismissRequest = {
                    showTabSwitchConfirmDialog = false
                    pendingTargetPage = null
                }) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = BgCard,
                        border = BorderStroke(1.5.dp, Color(0xFFFFB800)),
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        Column(
                            Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(Modifier.size(48.dp).background(Color(0xFFFFB800).copy(.15f), CircleShape), contentAlignment = Alignment.Center) {
                                Text("⚠️", fontSize = 24.sp)
                            }

                            Text("切换页面提示", color = TextPri, fontSize = 17.sp, fontWeight = FontWeight.Bold)

                            Text(
                                "当前相机中有 ${capturedFiles.size} 张已拍照片尚未生成 3D 模型。切换到其他页面前，请选择处理方式：",
                                color = TextSec,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )

                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = {
                                        LocalRecordStore.addOrUpdateDraft(context, capturedFiles, currentScanMode, activeSessionDraftId)
                                        draftsList = LocalRecordStore.loadDrafts(context)
                                        activeSessionDraftId = null
                                        capturedFiles = emptyList()
                                        arPointsBuffer = emptyList()
                                        captureGuide.resetCoverage()
                                        arManager.resetTrajectory()
                                        showTabSwitchConfirmDialog = false
                                        page = targetPage
                                        pendingTargetPage = null
                                        Toast.makeText(context, "已存入草稿箱并离开", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth().height(46.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Cyan)
                                ) {
                                    Text("保存到草稿箱并离开", color = Bg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val filesToDiscard = capturedFiles
                                        val draftIdToDiscard = activeSessionDraftId
                                        if (draftIdToDiscard != null || filesToDiscard.isNotEmpty()) {
                                            LocalRecordStore.deleteDraftsMatchingFiles(context, filesToDiscard, draftIdToDiscard)
                                            draftsList = LocalRecordStore.loadDrafts(context)
                                        }
                                        activeSessionDraftId = null
                                        capturedFiles = emptyList()
                                        arPointsBuffer = emptyList()
                                        captureGuide.resetCoverage()
                                        arManager.resetTrajectory()
                                        scope.launch(Dispatchers.IO) {
                                            filesToDiscard.forEach { file ->
                                                try { if (file.exists()) file.delete() } catch (_: Exception) {}
                                            }
                                        }
                                        showTabSwitchConfirmDialog = false
                                        page = targetPage
                                        pendingTargetPage = null
                                        Toast.makeText(context, "已放弃当前拍摄", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth().height(46.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFFFF4D4D))
                                ) {
                                    Text("放弃当前拍摄并离开", color = Color(0xFFFF6E6E), fontSize = 13.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        showTabSwitchConfirmDialog = false
                                        pendingTargetPage = null
                                    },
                                    modifier = Modifier.fillMaxWidth().height(40.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Muted.copy(.35f))
                                ) {
                                    Text("留在此页继续拍摄", color = Muted, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 外部相册多图 / 视频抽帧智能导入处理进度弹窗
            if (isImportingMedia) {
                androidx.compose.ui.window.Dialog(onDismissRequest = { /* 抽取分析中，防误触退出 */ }) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = BgCard,
                        border = BorderStroke(1.5.dp, Cyan),
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        Column(
                            Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(color = Cyan, modifier = Modifier.size(46.dp))
                            Text("智能媒体分析与特征点位提取", color = TextPri, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(importDialogStatus, color = TextSec, fontSize = 12.sp, textAlign = TextAlign.Center)
                            if (importTotalFrames > 0) {
                                val progress = (importCurrentFrame.toFloat() / importTotalFrames.toFloat()).coerceIn(0f, 1f)
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = Cyan,
                                    trackColor = Muted.copy(0.2f)
                                )
                                Text("${(progress * 100).toInt()}%  ($importCurrentFrame / $importTotalFrames)", color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("正在剔除运动拖影帧并反投影 3D 特征点位…", color = Muted, fontSize = 10.sp)
                        }
                    }
                }
            }

            // 记录打开加载中提示
            if (isOpeningRecord) {
                androidx.compose.ui.window.Dialog(onDismissRequest = { /* 加载中不可外部关闭 */ }) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = BgCard,
                        border = BorderStroke(1.5.dp, Green),
                        modifier = Modifier.fillMaxWidth().padding(24.dp)
                    ) {
                        Column(
                            Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(color = Green, modifier = Modifier.size(46.dp))
                            Text("正在加载…", color = TextPri, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("正在加载，请稍候", color = TextSec, fontSize = 13.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

// ── 底部导航栏 ────────────────────────────────────────────────
@Composable
private fun RemyNavBar(current: Page, draftCount: Int = 0, onNav: (Page) -> Unit) {
    NavigationBar(containerColor = BgPanel, tonalElevation = 0.dp) {
        data class N(val p: Page, val l: String, val i: ImageVector, val si: ImageVector, val badge: Int = 0)
        listOf(
            N(Page.HOME, "首页", Icons.Outlined.Home, Icons.Filled.Home),
            N(Page.CAPTURE, "采集", Icons.Outlined.CameraAlt, Icons.Filled.CameraAlt),
            N(Page.DRAFTS, "草稿箱", Icons.Outlined.Drafts, Icons.Filled.Drafts, badge = draftCount),
            N(Page.LIBRARY, "记录", Icons.Outlined.PhotoLibrary, Icons.Filled.PhotoLibrary),
        ).forEach { n ->
            val sel = current == n.p
            NavigationBarItem(
                selected = sel, onClick = { onNav(n.p) },
                icon = {
                    Box {
                        Icon(if (sel) n.si else n.i, null, tint = if (sel) Accent else Muted)
                        if (n.badge > 0) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFFFB800),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(14.dp)
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("${n.badge.coerceAtMost(99)}", color = Bg, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                },
                label = { Text(n.l, color = if (sel) Accent else Muted, fontSize = 11.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = Accent.copy(0.18f))
            )
        }
    }
}

// ════════════════════════════════════════════════════════════
//  1 · 首页
// ════════════════════════════════════════════════════════════
@Composable
private fun HomeScreen(
    currentScanMode: SpatialCaptureGuide.ScanMode = SpatialCaptureGuide.ScanMode.SPATIAL_SCENE,
    draftCount: Int = 0,
    onResumeDraft: () -> Unit = {},
    onViewDraftsList: () -> Unit = {},
    onCaptureSpatialScene: () -> Unit,
    onCaptureObject: () -> Unit,
    onImportPhotos: () -> Unit = {},
    onImportVideo: () -> Unit = {},
    onViewer: () -> Unit,
    onLibrary: () -> Unit
) {
    val isSpatialSelected = (currentScanMode == SpatialCaptureGuide.ScanMode.SPATIAL_SCENE)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Remy", fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = TextPri)
                        Spacer(Modifier.width(8.dp))
                        Surface(color = Accent.copy(.18f), shape = RoundedCornerShape(6.dp)) {
                            Text("3D Demo", Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = AccentAlt, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text("实景 3D 重建与高斯泼溅演示 · 算法原型验证", color = Muted, fontSize = 13.sp)
                }
                Box(Modifier.size(52.dp).background(Accent.copy(.13f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.ViewInAr, null, tint = AccentAlt, modifier = Modifier.size(28.dp))
                }
            }
        }

        // 草稿箱快捷入口与提示横幅
        if (draftCount > 0) {
            item {
                Surface(
                    onClick = onViewDraftsList,
                    color = Color(0xFFFFF8E6),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, Color(0xFFFFB800))
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📦", fontSize = 24.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("本地草稿箱", color = Color(0xFFD97706), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFFFB800).copy(0.2f)) {
                                    Text("$draftCount 条草稿", Modifier.padding(horizontal = 5.dp, vertical = 1.dp), color = Color(0xFFB45309), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text("保留未完成拍摄进度、模式与存储路径，点击管理恢复", color = TextSec, fontSize = 11.sp)
                        }
                        Surface(
                            onClick = onResumeDraft,
                            color = Color(0xFFFF9800),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("恢复最新", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF06101E))
                    .border(1.dp, Muted.copy(0.25f), RoundedCornerShape(24.dp))
            ) {
                AndroidView(factory = { ctx -> SplatGLView(ctx).apply { renderer.sceneType = GaussianRenderer.SceneType.ROOM } }, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF06101E).copy(.75f)))))
                Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                    Text("3D 空间场景预览", color = Color(0xFFF0F4FF), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("全空间高斯泼溅 · 拖动旋转 · 支持空间走进漫游", color = Color(0xFFB0BCCF), fontSize = 12.sp)
                }
            }
        }

        // 核心入口 1：🏛️ 三维空间场景记忆构建 (带选中状态光晕、边框与徽标)
        item {
            Surface(
                onClick = onCaptureSpatialScene,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = if (isSpatialSelected) Cyan.copy(0.18f) else BgCard,
                border = BorderStroke(
                    width = if (isSpatialSelected) 2.dp else 1.dp,
                    color = if (isSpatialSelected) Cyan else Muted.copy(0.25f)
                )
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(46.dp).background(if (isSpatialSelected) Cyan.copy(0.25f) else Accent.copy(0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🏛️", fontSize = 22.sp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("三维空间场景记忆", color = if (isSpatialSelected) Cyan else TextPri, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            if (isSpatialSelected) {
                                Surface(shape = RoundedCornerShape(6.dp), color = Cyan) {
                                    Text("当前模式", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = Bg, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                        Text("房间 · 客厅 · 展厅 · 咖啡馆 · 6-DoF 轨迹球漫步引导", color = if (isSpatialSelected) TextPri.copy(0.85f) else Muted, fontSize = 11.sp)
                    }
                    if (isSpatialSelected) {
                        Icon(Icons.Filled.CheckCircle, null, tint = Cyan, modifier = Modifier.size(22.dp))
                    } else {
                        Icon(Icons.Default.ChevronRight, null, tint = Muted)
                    }
                }
            }
        }

        // 核心入口 2：🎯 单体实物高精度扫描 (带选中状态光晕、边框与徽标)
        item {
            val isObjectSelected = !isSpatialSelected
            Surface(
                onClick = onCaptureObject,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = if (isObjectSelected) Green.copy(0.18f) else BgCard,
                border = BorderStroke(
                    width = if (isObjectSelected) 2.dp else 1.dp,
                    color = if (isObjectSelected) Green else Muted.copy(0.25f)
                )
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(46.dp).background(if (isObjectSelected) Green.copy(0.25f) else Muted.copy(0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🎯", fontSize = 22.sp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("单体实物高精扫描", color = if (isObjectSelected) Green else TextPri, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            if (isObjectSelected) {
                                Surface(shape = RoundedCornerShape(6.dp), color = Green) {
                                    Text("当前模式", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = Bg, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                        Text("手办 · 雕塑 · 物品 · 向心环绕雷达 · 目标主体一致性锁定", color = if (isObjectSelected) TextPri.copy(0.85f) else Muted, fontSize = 11.sp)
                    }
                    if (isObjectSelected) {
                        Icon(Icons.Filled.CheckCircle, null, tint = Green, modifier = Modifier.size(22.dp))
                    } else {
                        Icon(Icons.Default.ChevronRight, null, tint = Muted)
                    }
                }
            }
        }

        // 核心入口 3：⚡ 外部媒体导入 3D 重建 (相册多图与视频抽帧)
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = BgCard,
                border = BorderStroke(1.dp, Muted.copy(0.25f))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(42.dp).background(Accent.copy(0.18f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("⚡", fontSize = 20.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("外部媒体导入 3D 重建", color = TextPri, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("相册多视角导入 · 视频智能抽取关键帧 · 自动过滤运动拖影", color = Muted, fontSize = 11.sp)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onImportPhotos,
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Cyan.copy(0.22f)),
                            border = BorderStroke(1.dp, Cyan)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, null, tint = Cyan, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("导入多张照片", color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onImportVideo,
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Green.copy(0.22f)),
                            border = BorderStroke(1.dp, Green)
                        ) {
                            Text("🎬", fontSize = 14.sp)
                            Spacer(Modifier.width(6.dp))
                            Text("导入视频抽帧", color = Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onViewer, Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Accent.copy(.5f))) {
                    Icon(Icons.Default.ViewInAr, null, tint = AccentAlt); Spacer(Modifier.width(6.dp)); Text("预览演示", color = AccentAlt)
                }
                OutlinedButton(onClick = onLibrary, Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Muted.copy(.35f))) {
                    Icon(Icons.Default.PhotoLibrary, null, tint = Muted); Spacer(Modifier.width(6.dp)); Text("我的记录", color = Muted)
                }
            }
        }
        item {
            Surface(color = BgCard, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Muted.copy(0.2f))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("工作流程", color = TextSec, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    listOf(
                        Triple(Icons.Default.CameraAlt, "多角度拍摄", "环绕目标拍 10–30 张照片"),
                        Triple(Icons.Default.AutoAwesome, "特征提取", "Sobel 边缘检测 + 颜色采样"),
                        Triple(Icons.Default.ScreenRotationAlt, "3D 反投影", "相机位姿估计 → 点云生成"),
                        Triple(Icons.Default.SaveAlt, "导出 .ply", "标准格式，可用 MeshLab 打开"),
                    ).forEach { (icon, title, sub) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(36.dp).background(Accent.copy(.12f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                Icon(icon, null, tint = AccentAlt, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column { Text(title, color = TextPri, fontSize = 13.sp, fontWeight = FontWeight.SemiBold); Text(sub, color = Muted, fontSize = 11.sp) }
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
//  2 · 采集页（集成 Google ARCore + 360° 空间立体拍摄指引）
// ════════════════════════════════════════════════════════════
@SuppressLint("MissingPermission")
@Composable
private fun CaptureScreen(
    capturedFiles: List<File>,
    guide: SpatialCaptureGuide,
    arManager: ArCorePointCloudManager,
    isArMode: Boolean = false,
    scanMode: SpatialCaptureGuide.ScanMode = SpatialCaptureGuide.ScanMode.SPATIAL_SCENE,
    onFileAdded: (File) -> Unit,
    onImportPhotos: () -> Unit = {},
    onImportVideo: () -> Unit = {},
    onSwitchMode: (SpatialCaptureGuide.ScanMode) -> Unit = {},
    onSaveDraft: () -> Unit = {},
    onResetSession: () -> Unit = {},
    onExitToHome: () -> Unit = {},
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cameraGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { cameraGranted = it }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var isShooting by remember { mutableStateOf(false) }

    // 模式切换与重置警示弹窗状态
    var showModeSwitchWarningDialog by remember { mutableStateOf(false) }
    var targetModeToSwitch by remember { mutableStateOf<SpatialCaptureGuide.ScanMode?>(null) }
    var showExitWarningDialog by remember { mutableStateOf(false) }

    // 相机模式：自动模式 (AUTO) vs 专业手动模式 (PRO 手动)
    val proController = remember { NativeProCameraController() }
    var isManualMode by remember { mutableStateOf(false) } // false = 自动模式 (全自动测光/对焦/白平衡), true = 专业手动模式
    var showProPanel by remember { mutableStateOf(false) }
    var currentEvIndex by remember { mutableStateOf(0) }
    var currentIso by remember { mutableStateOf(0) } // 0 = Auto
    var currentShutterIndex by remember { mutableStateOf(0) } // 0 = Auto, 1 = 1/30s, 2 = 1/60s, 3 = 1/100s, 4 = 1/250s, 5 = 1/500s, 6 = 1/1000s
    var currentFocusDist by remember { mutableFloatStateOf(0f) } // 0 = Auto AF, >0 = 手动对焦 (0f..10f)
    var isTorchEnabled by remember { mutableStateOf(false) }
    var isAeLocked by remember { mutableStateOf(false) }
    var currentZoom by remember { mutableFloatStateOf(1.0f) }
    var touchFocusPoint by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) } // 触屏黄色对焦环
    var activeProTab by remember { mutableStateOf<String?>(null) } // 当前打开的滚动轴参数：null 表示收起滚动条不遮挡界面，非空为 "EV", "ISO", "SHUTTER", "FOCUS"

    // 模糊/画质检测待确认状态
    var pendingQualityCheckFile by remember { mutableStateOf<File?>(null) }
    var pendingQualityReport by remember { mutableStateOf<ImageQualityEvaluator.QualityReport?>(null) }

    // 扫描主体锁定与偏离拦截状态
    var isTargetLockEnabled by remember { mutableStateOf(true) }
    var pendingDeviationFile by remember { mutableStateOf<File?>(null) }
    var pendingDeviationReason by remember { mutableStateOf<String?>(null) }
    var targetTrackerStatus by remember { mutableStateOf(guide.targetTracker.currentStatus) }
    var targetMatchScore by remember { mutableFloatStateOf(guide.targetTracker.lastMatchScore) }

    // 机械拨盘物理齿轮刻度滑动音效与触感反馈 (ToneGenerator & Vibrator)
    val dialTone = remember {
        try { ToneGenerator(AudioManager.STREAM_MUSIC, 85) } catch (_: Exception) { null }
    }
    val vibrator = remember { context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator }

    // 齿轮微音效函数 (采用 STREAM_MUSIC 与 TONE_PROP_BEEP 确保各机型物理声效清晰响亮)
    val playDialTickSound: () -> Unit = {
        try {
            dialTone?.startTone(ToneGenerator.TONE_PROP_BEEP, 25)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(18)
            }
        } catch (_: Exception) {}
    }

    // 传感器实时监听与预览帧实时偏移监测
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var isImageAnalysisBound by remember { mutableStateOf(false) }
    val analysisExecutor = remember { java.util.concurrent.Executors.newSingleThreadExecutor() }
    var lastPreviewAnalyzeMs by remember { mutableStateOf(0L) }

    var currentYaw by remember { mutableFloatStateOf(guide.currentYawDeg) }
    var currentPitch by remember { mutableFloatStateOf(guide.currentPitchDeg) }
    var currentRoll by remember { mutableFloatStateOf(guide.currentRollDeg) }
    var currentLayerIndex by remember { mutableIntStateOf(guide.getCurrentLayerIndex()) }
    var ringCoverage by remember { mutableStateOf(guide.getHorizontalRingCoverage()) }
    var guideFeedback by remember { mutableStateOf(guide.evaluateGuide()) }
    var spatialCoveragePercent by remember { mutableFloatStateOf(guide.calculateCoverage()) }
    var currentWalkDistance by remember { mutableFloatStateOf(arManager.totalWalkDistance) }

    val count = capturedFiles.size

    // 拦截系统物理返回键/侧滑返回手势，与左上角返回按钮行为保持一致
    BackHandler(enabled = true) {
        if (count > 0) {
            showExitWarningDialog = true
        } else {
            onExitToHome()
        }
    }

    DisposableEffect(Unit) {
        guide.start()
        arManager.resume()
        guide.onAttitudeChanged = { yaw, pitch, roll ->
            currentYaw = yaw
            currentPitch = pitch
            currentRoll = roll
            currentLayerIndex = guide.getCurrentLayerIndex()
            guideFeedback = guide.evaluateGuide()
        }
        guide.onStepDetected = { stepMeters, yaw ->
            if (scanMode == SpatialCaptureGuide.ScanMode.SPATIAL_SCENE) {
                arManager.addEstimatedDistance(stepMeters, yaw)
                currentWalkDistance = arManager.totalWalkDistance
            }
        }
        onDispose {
            guide.stop()
            guide.onAttitudeChanged = null
            guide.onStepDetected = null
            arManager.pause()
            try { analysisExecutor.shutdown() } catch (_: Exception) {}
        }
    }

    // 实时刷新姿态、偏移状态、未拍方位与走动轨迹距离（含 PreviewView 兜底帧监测）
    LaunchedEffect(scanMode, isTargetLockEnabled) {
        while (true) {
            currentYaw = guide.currentYawDeg
            currentPitch = guide.currentPitchDeg
            currentRoll = guide.currentRollDeg
            currentLayerIndex = guide.getCurrentLayerIndex()
            ringCoverage = guide.getHorizontalRingCoverage()
            spatialCoveragePercent = guide.calculateCoverage()

            // 若设备未绑定 ImageAnalysis 通道，使用 PreviewView 快照作为实时偏移监测兜底
            val now = System.currentTimeMillis()
            if (!isImageAnalysisBound &&
                now - lastPreviewAnalyzeMs >= 240L &&
                scanMode == SpatialCaptureGuide.ScanMode.OBJECT &&
                isTargetLockEnabled &&
                guide.targetTracker.currentStatus != com.example.remy3d.engine.TargetTrackerEngine.TargetStatus.NOT_SET &&
                !isShooting &&
                pendingDeviationFile == null &&
                pendingQualityCheckFile == null
            ) {
                lastPreviewAnalyzeMs = now
                val pvBmp = try { previewViewRef?.bitmap } catch (_: Exception) { null }
                if (pvBmp != null) {
                    withContext(Dispatchers.Default) {
                        try {
                            val status = guide.targetTracker.verifyRealtimePreview(pvBmp, currentFocusDist)
                            if (status == com.example.remy3d.engine.TargetTrackerEngine.TargetStatus.LOCKED) {
                                guide.targetDeviationWarning = null
                            }
                        } finally {
                            pvBmp.recycle()
                        }
                    }
                }
            }

            targetTrackerStatus = guide.targetTracker.currentStatus
            targetMatchScore = guide.targetTracker.lastMatchScore
            guideFeedback = guide.evaluateGuide()
            // 实时轮询 ARCore 位姿，确保预览走动时行走距离实时累加
            arManager.updatePoseOnly()
            currentWalkDistance = arManager.totalWalkDistance
            delay(100)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 顶部标题与模式切换/退出警告栏
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // 退出返回键
            Surface(
                onClick = {
                    if (count > 0) {
                        showExitWarningDialog = true
                    } else {
                        onExitToHome()
                    }
                },
                color = BgCard,
                border = BorderStroke(1.dp, Muted.copy(0.25f)),
                shape = CircleShape,
                modifier = Modifier.size(38.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.ArrowBack, null, tint = TextPri, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.width(10.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    if (scanMode == SpatialCaptureGuide.ScanMode.SPATIAL_SCENE) "🏛️ 三维空间场景构建" else "🎯 单体实物高精扫描",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPri
                )
                Text(
                    if (scanMode == SpatialCaptureGuide.ScanMode.SPATIAL_SCENE) {
                        "走位 ${(currentWalkDistance * 10).toInt() / 10f}m · 空间估算 ${String.format("%.1f", arManager.spatialBoundX)}m × ${String.format("%.1f", arManager.spatialBoundZ)}m"
                    } else {
                        "围绕物体 360° 缓慢环绕，补齐多层角度"
                    },
                    color = Muted,
                    fontSize = 11.sp
                )
            }

            // 模式切换胶囊 (点击切换模式，带未保存确认)
            Surface(
                onClick = {
                    val nextMode = if (scanMode == SpatialCaptureGuide.ScanMode.SPATIAL_SCENE) {
                        SpatialCaptureGuide.ScanMode.OBJECT
                    } else {
                        SpatialCaptureGuide.ScanMode.SPATIAL_SCENE
                    }
                    if (count > 0) {
                        targetModeToSwitch = nextMode
                        showModeSwitchWarningDialog = true
                    } else {
                        onSwitchMode(nextMode)
                    }
                },
                color = if (scanMode == SpatialCaptureGuide.ScanMode.SPATIAL_SCENE) Cyan.copy(.2f) else Green.copy(.2f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, if (scanMode == SpatialCaptureGuide.ScanMode.SPATIAL_SCENE) Cyan.copy(.5f) else Green.copy(.5f))
            ) {
                Text(
                    if (scanMode == SpatialCaptureGuide.ScanMode.SPATIAL_SCENE) "切换至实物" else "切换至空间",
                    Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    color = if (scanMode == SpatialCaptureGuide.ScanMode.SPATIAL_SCENE) Cyan else Green,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 360° 环绕空间罗盘覆盖雷达与智能警告栏
        Surface(color = BgCard, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Muted.copy(0.2f))) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    // 左侧：双层/三层立体同心环雷达 (外圈: 顶部俯视角, 中圈: 正面平视角, 内圈: 底部仰视角)
                    Box(Modifier.size(68.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                            val curLayer = currentLayerIndex
                            val curSector = ((currentYaw / 30f).toInt()) % 12

                            // 绘制 3 层同心环 (3 x 12 = 36 个空间单元)
                            // rLevels: [0] 顶部外环, [1] 中部中环, [2] 底部内环
                            val ringRadii = listOf(size.width / 2f - 4.dp.toPx(), size.width / 2f - 11.dp.toPx(), size.width / 2f - 18.dp.toPx())
                            val ringWidths = listOf(3.5.dp.toPx(), 4.dp.toPx(), 3.5.dp.toPx())

                            for (l in 0 until 3) {
                                val r = ringRadii[l]
                                val w = ringWidths[l]
                                val layerCov = guide.getLayerRingCoverage(l)
                                val isCurrentActiveLayer = (l == curLayer)

                                for (i in 0 until 12) {
                                    val startAngle = i * 30f - 90f
                                    val isDone = if (i < layerCov.size) layerCov[i] else false
                                    val arcColor = when {
                                        isDone -> Green
                                        isCurrentActiveLayer && (i == curSector) -> Cyan.copy(alpha = 0.85f)
                                        else -> Muted.copy(alpha = if (isCurrentActiveLayer) 0.35f else 0.15f)
                                    }
                                    drawArc(
                                        color = arcColor,
                                        startAngle = startAngle + 2f,
                                        sweepAngle = 26f,
                                        useCenter = false,
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                                    )
                                }
                            }

                            // 当前朝向指针
                            val curRad = Math.toRadians((currentYaw - 90.0)).toFloat()
                            val ptrR = ringRadii[curLayer.coerceIn(0, 2)]
                            val ptrX = center.x + ptrR * kotlin.math.cos(curRad)
                            val ptrY = center.y + ptrR * kotlin.math.sin(curRad)
                            drawCircle(TextPri, radius = 3.5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(ptrX, ptrY))
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    // 右侧：实时引导建议与层级指示
                    Column(Modifier.weight(1f)) {
                        val (topCov, midCov, botCov) = guide.getLayerCoverages()
                        val curLayerName = when(currentLayerIndex) {
                            0 -> "顶部俯视区"
                            1 -> "中段平视区"
                            else -> "底沿仰视区"
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "【$curLayerName】${guideFeedback.targetLevel}",
                                color = if (guideFeedback.isWarning) Color(0xFFE53935) else Green,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "立体覆盖 ${(spatialCoveragePercent * 100).toInt()}%",
                                color = if (spatialCoveragePercent >= 0.75f) Green else AccentAlt,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            guideFeedback.prompt,
                            color = if (guideFeedback.isWarning) Color(0xFFE53935) else TextPri,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        // 顶部/中部/底部三层覆盖微型进度标签
                        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("顶 ${(topCov*100).toInt()}%", color = if (topCov >= 0.5f) Green else Muted, fontSize = 10.sp)
                            Text("中 ${(midCov*100).toInt()}%", color = if (midCov >= 0.5f) Green else Muted, fontSize = 10.sp)
                            Text("底 ${(botCov*100).toInt()}%", color = if (botCov >= 0.5f) Green else Muted, fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        // 相机预览区
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
        ) {
            if (cameraGranted) {
                AndroidView(factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    previewViewRef = previewView
                    previewView.setOnTouchListener { _, event ->
                        if (event.action == android.view.MotionEvent.ACTION_UP) {
                            val normX = event.x / previewView.width.coerceAtLeast(1)
                            val normY = event.y / previewView.height.coerceAtLeast(1)
                            touchFocusPoint = androidx.compose.ui.geometry.Offset(event.x, event.y)
                            proController.startFocusAndMetering(normX, normY)
                            // 点击取景器任意区域，自动收起展开的滚动条标尺与气泡，防止遮挡
                            if (activeProTab != null) {
                                activeProTab = null
                            }
                        }
                        true
                    }
                    val future = ProcessCameraProvider.getInstance(ctx)
                    future.addListener({
                        val provider = future.get()
                        val rot = previewView.display?.rotation ?: android.view.Surface.ROTATION_0
                        val preview = Preview.Builder()
                            .setTargetRotation(rot)
                            .build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                        val resolutionSelector = androidx.camera.core.resolutionselector.ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                androidx.camera.core.resolutionselector.ResolutionStrategy(
                                    android.util.Size(1280, 960),
                                    androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                )
                            )
                            .build()
                        val imageCapture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .setResolutionSelector(resolutionSelector)
                            .setJpegQuality(85)
                            .setTargetRotation(rot)
                            .build()
                        capture = imageCapture

                        val analysisSelector = androidx.camera.core.resolutionselector.ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                androidx.camera.core.resolutionselector.ResolutionStrategy(
                                    android.util.Size(320, 240),
                                    androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                                )
                            )
                            .build()
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setResolutionSelector(analysisSelector)
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setTargetRotation(rot)
                            .build().also { analysis ->
                                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                    try {
                                        val now = System.currentTimeMillis()
                                        if (now - lastPreviewAnalyzeMs >= 180L &&
                                            guide.scanMode == SpatialCaptureGuide.ScanMode.OBJECT &&
                                            isTargetLockEnabled &&
                                            guide.targetTracker.currentStatus != com.example.remy3d.engine.TargetTrackerEngine.TargetStatus.NOT_SET &&
                                            !isShooting &&
                                            pendingDeviationFile == null &&
                                            pendingQualityCheckFile == null
                                        ) {
                                            lastPreviewAnalyzeMs = now
                                            val bmp = imageProxy.toBitmap()
                                            try {
                                                val status = guide.targetTracker.verifyRealtimePreview(bmp, currentFocusDist)
                                                if (status == com.example.remy3d.engine.TargetTrackerEngine.TargetStatus.LOCKED) {
                                                    guide.targetDeviationWarning = null
                                                }
                                                targetTrackerStatus = guide.targetTracker.currentStatus
                                                targetMatchScore = guide.targetTracker.lastMatchScore
                                                guideFeedback = guide.evaluateGuide()
                                            } finally {
                                                bmp.recycle()
                                            }
                                        }
                                    } catch (_: Exception) {
                                    } finally {
                                        imageProxy.close()
                                    }
                                }
                            }

                        try {
                            provider.unbindAll()
                            val cam = try {
                                val c = provider.bindToLifecycle(ctx as ComponentActivity, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, imageAnalysis)
                                isImageAnalysisBound = true
                                c
                            } catch (_: Exception) {
                                isImageAnalysisBound = false
                                provider.bindToLifecycle(ctx as ComponentActivity, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                            }
                            proController.attachCamera(cam)
                        } catch (_: Exception) {}
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                }, Modifier.fillMaxSize())

                // 构图十字瞄准、三分辅助线、触屏黄色对焦环与 3D 主体目标锁定框
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width; val h = size.height
                    val lc = Color.White.copy(alpha = 0.12f)
                    drawLine(lc, androidx.compose.ui.geometry.Offset(w / 3, 0f), androidx.compose.ui.geometry.Offset(w / 3, h), 1f)
                    drawLine(lc, androidx.compose.ui.geometry.Offset(2 * w / 3, 0f), androidx.compose.ui.geometry.Offset(2 * w / 3, h), 1f)
                    drawLine(lc, androidx.compose.ui.geometry.Offset(0f, h / 3), androidx.compose.ui.geometry.Offset(w, h / 3), 1f)
                    drawLine(lc, androidx.compose.ui.geometry.Offset(0f, 2 * h / 3), androidx.compose.ui.geometry.Offset(w, 2 * h / 3), 1f)

                    val cx = w / 2f; val cy = h / 2f

                    // 空间目标定位规范：仅在单体实物扫描 (OBJECT) 模式下启用 3D 目标主体追踪锚点四角框 (Target Lock Reticle)
                    // 在空间场景构建 (SPATIAL_SCENE) 模式下，用户是对整个大空间漫游扫描，不绘制主体锁定框，避免产生“锁定了奇怪物体”的误解
                    if (scanMode == SpatialCaptureGuide.ScanMode.OBJECT) {
                        val boxW = w * 0.46f; val boxH = boxW * 0.9f
                        val left = cx - boxW / 2f; val right = cx + boxW / 2f
                        val top = cy - boxH / 2f; val bottom = cy + boxH / 2f
                        val cornerLen = 18.dp.toPx()
                        val strokeW = 2.dp.toPx()

                        val targetColor = when (targetTrackerStatus) {
                            com.example.remy3d.engine.TargetTrackerEngine.TargetStatus.LOCKED -> Green
                            com.example.remy3d.engine.TargetTrackerEngine.TargetStatus.DEVIATED_OBJECT,
                            com.example.remy3d.engine.TargetTrackerEngine.TargetStatus.DEVIATED_DISTANCE -> Color(0xFFFF4D4D)
                            else -> Cyan.copy(alpha = 0.45f)
                        }

                        // 绘制 4 个角标 L 型拐角
                        // 左上
                        drawLine(targetColor, androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Offset(left + cornerLen, top), strokeW)
                        drawLine(targetColor, androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Offset(left, top + cornerLen), strokeW)
                        // 右上
                        drawLine(targetColor, androidx.compose.ui.geometry.Offset(right, top), androidx.compose.ui.geometry.Offset(right - cornerLen, top), strokeW)
                        drawLine(targetColor, androidx.compose.ui.geometry.Offset(right, top), androidx.compose.ui.geometry.Offset(right, top + cornerLen), strokeW)
                        // 左下
                        drawLine(targetColor, androidx.compose.ui.geometry.Offset(left, bottom), androidx.compose.ui.geometry.Offset(left + cornerLen, bottom), strokeW)
                        drawLine(targetColor, androidx.compose.ui.geometry.Offset(left, bottom), androidx.compose.ui.geometry.Offset(left, bottom - cornerLen), strokeW)
                        // 右下
                        drawLine(targetColor, androidx.compose.ui.geometry.Offset(right, bottom), androidx.compose.ui.geometry.Offset(right - cornerLen, bottom), strokeW)
                        drawLine(targetColor, androidx.compose.ui.geometry.Offset(right, bottom), androidx.compose.ui.geometry.Offset(right, bottom - cornerLen), strokeW)

                        // 中心聚焦靶心
                        drawCircle(targetColor.copy(alpha = 0.35f), radius = 24.dp.toPx(), center = androidx.compose.ui.geometry.Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                    }

                    // 触屏手动测光对焦动态靶环 (黄色对焦指示框)
                    touchFocusPoint?.let { pt ->
                        drawCircle(Color(0xFFFFCC00), radius = 28.dp.toPx(), center = pt, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                        drawCircle(Color(0xFFFFCC00).copy(alpha = 0.25f), radius = 6.dp.toPx(), center = pt)
                    }
                }

                // 顶部一体化状态栏 (自适应横向布局，彻底解决各机型左侧按钮/偏移值徽标挤压溢出重叠问题)
                val currentLayerBadge = when {
                    currentPitch >= 30f -> "俯视"
                    currentPitch >= 0f  -> "平视"
                    else                -> "仰视"
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左侧：姿态与层级指示徽标
                    Surface(
                        color = Bg.copy(.82f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "$currentLayerBadge ${currentPitch.toInt()}° · 朝向 ${currentYaw.toInt()}°",
                            Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            color = if (currentPitch >= 30f) Green else Cyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }

                    // 中间：模式与主体/空间状态指示 (实物模式支持点击实时锁定主体，空间模式显示走位距离)
                    Surface(
                        onClick = {
                            if (scanMode == SpatialCaptureGuide.ScanMode.OBJECT) {
                                val pvBmp = try { previewViewRef?.bitmap } catch (_: Exception) { null }
                                if (pvBmp != null) {
                                    guide.targetTracker.lockTargetFromBitmap(pvBmp, currentFocusDist)
                                    pvBmp.recycle()
                                    guide.targetDeviationWarning = null
                                    targetTrackerStatus = guide.targetTracker.currentStatus
                                    targetMatchScore = guide.targetTracker.lastMatchScore
                                    guideFeedback = guide.evaluateGuide()
                                    Toast.makeText(context, "🎯 已锁定当前画面中心目标主体", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        color = Bg.copy(.85f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            1.dp,
                            if (scanMode == SpatialCaptureGuide.ScanMode.OBJECT) {
                                when (targetTrackerStatus) {
                                    com.example.remy3d.engine.TargetTrackerEngine.TargetStatus.LOCKED -> Green
                                    com.example.remy3d.engine.TargetTrackerEngine.TargetStatus.DEVIATED_OBJECT,
                                    com.example.remy3d.engine.TargetTrackerEngine.TargetStatus.DEVIATED_DISTANCE -> Color(0xFFFF4D4D)
                                    else -> Cyan.copy(alpha = 0.5f)
                                }
                            } else Cyan.copy(alpha = 0.4f)
                        )
                    ) {
                        Text(
                            if (scanMode == SpatialCaptureGuide.ScanMode.OBJECT) {
                                when (targetTrackerStatus) {
                                    com.example.remy3d.engine.TargetTrackerEngine.TargetStatus.LOCKED -> "🎯 主体已锁定 (${(targetMatchScore * 100).toInt()}%)"
                                    com.example.remy3d.engine.TargetTrackerEngine.TargetStatus.DEVIATED_OBJECT -> "⚠️ 偏离目标！"
                                    com.example.remy3d.engine.TargetTrackerEngine.TargetStatus.DEVIATED_DISTANCE -> "⚠️ 物距异常！"
                                    else -> "🎯 点击锁定主体"
                                }
                            } else {
                                "🏛️ 漫游走位 ${(arManager.totalWalkDistance * 10).toInt() / 10f}m"
                            },
                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = if (scanMode == SpatialCaptureGuide.ScanMode.OBJECT) {
                                when (targetTrackerStatus) {
                                    com.example.remy3d.engine.TargetTrackerEngine.TargetStatus.LOCKED -> Green
                                    com.example.remy3d.engine.TargetTrackerEngine.TargetStatus.DEVIATED_OBJECT,
                                    com.example.remy3d.engine.TargetTrackerEngine.TargetStatus.DEVIATED_DISTANCE -> Color(0xFFFF6E6E)
                                    else -> Cyan
                                }
                            } else Cyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    // 右侧：采集张数徽标
                    Surface(
                        color = Bg.copy(.85f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "$count 张",
                            Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            color = if (count >= 12) Green else TextPri,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }

                // ════════════════════════════════════════════════════════════
                // Kiri Engine 风格：右侧专业参数悬浮列 + 垂直物理机械滚动标尺 (Vertical Rotary Wheel)
                // ════════════════════════════════════════════════════════════
                val shutterValues = listOf(0L, 33_333_333L, 16_666_667L, 10_000_000L, 4_000_000L, 2_000_000L, 1_000_000L)
                val shutterLabels = listOf("AUTO", "1/30", "1/60", "1/100", "1/250", "1/500", "1/1000")

                val isoValues = listOf(0, 100, 160, 200, 400, 800, 1600, 3200)
                val isoLabels = listOf("AUTO", "100", "160", "200", "400", "800", "1600", "3200")

                // 范围化焦距刻度 (从微距 0.1m 到无穷远对焦，支持连续平滑调节，不再固定死)
                val focusValues = listOf(0f, 0.5f, 1.0f, 1.5f, 2.0f, 3.0f, 4.0f, 5.0f, 7.0f, 10.0f)
                val focusLabels = listOf("AF", "2.0m", "1.0m", "0.7m", "0.5m", "0.3m", "0.25m", "0.2m", "0.15m", "0.1m")

                val evRange = (proController.exposureRange.lower..proController.exposureRange.upper).toList().ifEmpty { listOf(-4, -3, -2, -1, 0, 1, 2, 3, 4) }
                val evLabels = evRange.map { idx ->
                    val evVal = idx * proController.exposureStep
                    if (idx == 0) "0.0" else String.format(java.util.Locale.US, "%+.1f", evVal)
                }

                val zoomValues = listOf(1.0f, 1.2f, 1.5f, 2.0f, 2.5f, 3.0f, 4.0f, 5.0f)
                val zoomLabels = zoomValues.map { "${it}x" }

                // 当前激活项的范围集合与选中索引
                val (curListSize, curIndex, curDisplayVal) = when (activeProTab) {
                    "EV" -> {
                        val idx = evRange.indexOf(currentEvIndex).coerceAtLeast(0)
                        val evVal = currentEvIndex * proController.exposureStep
                        val label = if (currentEvIndex == 0) "0.0" else String.format(java.util.Locale.US, "%+.1f", evVal)
                        Triple(evRange.size, idx, label)
                    }
                    "ISO" -> {
                        val idx = isoValues.indexOf(currentIso).coerceAtLeast(0)
                        Triple(isoValues.size, idx, if (currentIso == 0) "AUTO" else "$currentIso")
                    }
                    "SHUTTER" -> {
                        val idx = currentShutterIndex.coerceIn(0, shutterValues.size - 1)
                        Triple(shutterValues.size, idx, shutterLabels[idx])
                    }
                    "FOCUS" -> {
                        val idx = focusValues.indexOfFirst { (it - currentFocusDist).absoluteValue < 0.25f }.coerceAtLeast(0)
                        Triple(focusValues.size, idx, focusLabels[idx])
                    }
                    else -> {
                        val idx = zoomValues.indexOfFirst { (it - currentZoom).absoluteValue < 0.15f }.coerceAtLeast(0)
                        Triple(zoomValues.size, idx, "${String.format(java.util.Locale.US, "%.1f", currentZoom)}x")
                    }
                }

                // 1. 右侧参数切换栏 ([A/M] -> [EV] -> [ISO] -> [快门] -> [对焦])
                Column(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 56.dp, end = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // A/M 自动与手动切换圆形胶囊
                    Surface(
                        onClick = {
                            isManualMode = !isManualMode
                            if (!isManualMode) {
                                currentEvIndex = 0
                                currentIso = 0
                                currentShutterIndex = 0
                                currentFocusDist = 0f
                                isAeLocked = false
                                touchFocusPoint = null
                                proController.resetToAutoMode()
                                Toast.makeText(context, "已切换为自动模式 (AUTO)", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "已开启专业手动模式 (MANUAL)", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = CircleShape,
                        color = Bg.copy(alpha = 0.92f),
                        border = BorderStroke(1.5.dp, if (isManualMode) Cyan else Muted.copy(alpha = 0.45f)),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "A",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (!isManualMode) Green else TextSec.copy(alpha = 0.5f),
                                    lineHeight = 11.sp
                                )
                                Text(
                                    "M",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isManualMode) Cyan else TextSec.copy(alpha = 0.5f),
                                    lineHeight = 11.sp
                                )
                            }
                        }
                    }

                    // 参数快捷项：EV 曝光补偿 (支持点击展开/收起)
                    val evVal = currentEvIndex * proController.exposureStep
                    val evStr = if (currentEvIndex == 0) "0.0" else String.format(java.util.Locale.US, "%+.1f", evVal)
                    Surface(
                        onClick = {
                            if (!isManualMode) isManualMode = true
                            activeProTab = if (activeProTab == "EV") null else "EV"
                        },
                        shape = CircleShape,
                        color = if (activeProTab == "EV" && isManualMode) Bg.copy(alpha = 0.95f) else Bg.copy(alpha = 0.88f),
                        border = BorderStroke(1.dp, if (activeProTab == "EV" && isManualMode) Cyan else Muted.copy(alpha = 0.3f)),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Column(
                            Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("EV", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (activeProTab == "EV" && isManualMode) Cyan else TextPri)
                            Text(evStr, fontSize = 8.sp, color = if (activeProTab == "EV" && isManualMode) Cyan else TextSec)
                        }
                    }

                    // 参数快捷项：ISO 感光度 (支持点击展开/收起)
                    Surface(
                        onClick = {
                            if (!isManualMode) isManualMode = true
                            activeProTab = if (activeProTab == "ISO") null else "ISO"
                        },
                        shape = CircleShape,
                        color = if (activeProTab == "ISO" && isManualMode) Bg.copy(alpha = 0.95f) else Bg.copy(alpha = 0.88f),
                        border = BorderStroke(1.dp, if (activeProTab == "ISO" && isManualMode) Cyan else Muted.copy(alpha = 0.3f)),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Column(
                            Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("ISO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (activeProTab == "ISO" && isManualMode) Cyan else TextPri)
                            Text(if (currentIso == 0) "AUTO" else "$currentIso", fontSize = 8.sp, color = if (activeProTab == "ISO" && isManualMode) Cyan else TextSec)
                        }
                    }

                    // 参数快捷项：快门速度 (支持点击展开/收起)
                    val curShutter = shutterLabels[currentShutterIndex.coerceIn(0, shutterLabels.size - 1)]
                    Surface(
                        onClick = {
                            if (!isManualMode) isManualMode = true
                            activeProTab = if (activeProTab == "SHUTTER") null else "SHUTTER"
                        },
                        shape = CircleShape,
                        color = if (activeProTab == "SHUTTER" && isManualMode) Bg.copy(alpha = 0.95f) else Bg.copy(alpha = 0.88f),
                        border = BorderStroke(1.dp, if (activeProTab == "SHUTTER" && isManualMode) Cyan else Muted.copy(alpha = 0.3f)),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Column(
                            Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("S", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (activeProTab == "SHUTTER" && isManualMode) Cyan else TextPri)
                            Text(curShutter, fontSize = 8.sp, color = if (activeProTab == "SHUTTER" && isManualMode) Cyan else TextSec)
                        }
                    }

                    // 参数快捷项：对焦 FOCUS (支持点击展开/收起)
                    val curFocusStr = if (currentFocusDist == 0f) "AF" else focusLabels[focusValues.indexOfFirst { (it - currentFocusDist).absoluteValue < 0.25f }.coerceAtLeast(0)]
                    Surface(
                        onClick = {
                            if (!isManualMode) isManualMode = true
                            activeProTab = if (activeProTab == "FOCUS") null else "FOCUS"
                        },
                        shape = CircleShape,
                        color = if (activeProTab == "FOCUS" && isManualMode) Bg.copy(alpha = 0.95f) else Bg.copy(alpha = 0.88f),
                        border = BorderStroke(1.dp, if (activeProTab == "FOCUS" && isManualMode) Cyan else Muted.copy(alpha = 0.3f)),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Column(
                            Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("MF", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (activeProTab == "FOCUS" && isManualMode) Cyan else TextPri)
                            Text(curFocusStr, fontSize = 8.sp, color = if (activeProTab == "FOCUS" && isManualMode) Cyan else TextSec)
                        }
                    }

                    // 补光灯快捷独立开关
                    Surface(
                        onClick = {
                            isTorchEnabled = !isTorchEnabled
                            proController.setTorch(isTorchEnabled)
                        },
                        color = if (isTorchEnabled) Color(0xFFFFB800) else Bg.copy(alpha = 0.88f),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, if (isTorchEnabled) Color(0xFFFFB800) else Muted.copy(alpha = 0.3f)),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(if (isTorchEnabled) "💡" else "🔦", fontSize = 13.sp)
                        }
                    }

                    // 重置锚点按钮
                    Surface(
                        onClick = {
                            guide.targetTracker.resetTarget()
                            targetTrackerStatus = com.example.remy3d.engine.TargetTrackerEngine.TargetStatus.NOT_SET
                            Toast.makeText(context, "已重置追踪锚点", Toast.LENGTH_SHORT).show()
                        },
                        color = Bg.copy(alpha = 0.88f),
                        border = BorderStroke(1.dp, Muted.copy(alpha = 0.3f)),
                        shape = CircleShape,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("↺", color = TextPri, fontSize = 14.sp)
                        }
                    }
                }

                // 2. 垂直刻度滚动标尺 + 悬浮数值圆环 (Vertical Rotary Scroll Wheel)
                // 仅在手动模式且用户主动点击了某个参数项 (activeProTab != null) 时才展开滚动条；点击其他区域或再次点击该项立即收起，避免常驻遮挡
                if (isManualMode && activeProTab != null) {
                    var vDragAccumulator by remember { mutableFloatStateOf(0f) }

                    // 全屏轻触关闭遮罩（点击预览画面任意空白区域即可收起滚动轴）
                    Box(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(activeProTab) {
                                detectTapGestures {
                                    activeProTab = null
                                }
                            }
                    )

                    Box(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 56.dp) // 往左错开，不遮挡右侧参数按钮和下方拍摄区
                            .height(200.dp)
                            .width(84.dp)
                    ) {
                        // 垂直滚动标尺背板 (圆角白色半透明柱，内置垂直刻度线)
                        Box(
                            Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(30.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(Bg.copy(alpha = 0.90f))
                                .border(1.dp, Muted.copy(alpha = 0.35f), RoundedCornerShape(15.dp))
                                .pointerInput(activeProTab, currentEvIndex, currentIso, currentShutterIndex, currentFocusDist, currentZoom) {
                                    detectVerticalDragGestures(
                                        onDragEnd = { vDragAccumulator = 0f },
                                        onDragCancel = { vDragAccumulator = 0f }
                                    ) { _, dragAmount ->
                                        // 向上滑动数值增加，向下滑动数值减少
                                        vDragAccumulator -= dragAmount
                                        val stepPx = 20f // 降低阈值，提升响应灵敏度
                                        if (vDragAccumulator > stepPx) {
                                            vDragAccumulator = 0f
                                            playDialTickSound()
                                            when (activeProTab) {
                                                "EV" -> {
                                                    val idx = evRange.indexOf(currentEvIndex)
                                                    if (idx < evRange.size - 1) {
                                                        currentEvIndex = evRange[idx + 1]
                                                        proController.setExposureCompensation(currentEvIndex)
                                                    }
                                                }
                                                "ISO" -> {
                                                    val idx = isoValues.indexOf(currentIso)
                                                    if (idx < isoValues.size - 1) {
                                                        currentIso = isoValues[idx + 1]
                                                        proController.applyAdvancedSettings(ProCameraSettings(exposureCompensationIndex = currentEvIndex, isoValue = currentIso, shutterTimeNs = shutterValues[currentShutterIndex], focusDistance = currentFocusDist, isTorchOn = isTorchEnabled))
                                                    }
                                                }
                                                "SHUTTER" -> {
                                                    if (currentShutterIndex < shutterValues.size - 1) {
                                                        currentShutterIndex += 1
                                                        proController.applyAdvancedSettings(ProCameraSettings(exposureCompensationIndex = currentEvIndex, isoValue = currentIso, shutterTimeNs = shutterValues[currentShutterIndex], focusDistance = currentFocusDist, isTorchOn = isTorchEnabled))
                                                    }
                                                }
                                                "FOCUS" -> {
                                                    val idx = focusValues.indexOfFirst { (it - currentFocusDist).absoluteValue < 0.25f }
                                                    if (idx in 0 until focusValues.size - 1) {
                                                        currentFocusDist = focusValues[idx + 1]
                                                        proController.applyAdvancedSettings(ProCameraSettings(exposureCompensationIndex = currentEvIndex, isoValue = currentIso, shutterTimeNs = shutterValues[currentShutterIndex], focusDistance = currentFocusDist, isTorchOn = isTorchEnabled))
                                                        guide.targetTracker.updateFocusDistanceRealtime(currentFocusDist)
                                                        targetTrackerStatus = guide.targetTracker.currentStatus
                                                        guideFeedback = guide.evaluateGuide()
                                                    }
                                                }
                                                "ZOOM" -> {
                                                    val idx = zoomValues.indexOfFirst { (it - currentZoom).absoluteValue < 0.15f }
                                                    if (idx in 0 until zoomValues.size - 1) {
                                                        currentZoom = zoomValues[idx + 1]
                                                        proController.setZoom(currentZoom)
                                                    }
                                                }
                                            }
                                        } else if (vDragAccumulator < -stepPx) {
                                            vDragAccumulator = 0f
                                            playDialTickSound()
                                            when (activeProTab) {
                                                "EV" -> {
                                                    val idx = evRange.indexOf(currentEvIndex)
                                                    if (idx > 0) {
                                                        currentEvIndex = evRange[idx - 1]
                                                        proController.setExposureCompensation(currentEvIndex)
                                                    }
                                                }
                                                "ISO" -> {
                                                    val idx = isoValues.indexOf(currentIso)
                                                    if (idx > 0) {
                                                        currentIso = isoValues[idx - 1]
                                                        proController.applyAdvancedSettings(ProCameraSettings(exposureCompensationIndex = currentEvIndex, isoValue = currentIso, shutterTimeNs = shutterValues[currentShutterIndex], focusDistance = currentFocusDist, isTorchOn = isTorchEnabled))
                                                    }
                                                }
                                                "SHUTTER" -> {
                                                    if (currentShutterIndex > 0) {
                                                        currentShutterIndex -= 1
                                                        proController.applyAdvancedSettings(ProCameraSettings(exposureCompensationIndex = currentEvIndex, isoValue = currentIso, shutterTimeNs = shutterValues[currentShutterIndex], focusDistance = currentFocusDist, isTorchOn = isTorchEnabled))
                                                    }
                                                }
                                                "FOCUS" -> {
                                                    val idx = focusValues.indexOfFirst { (it - currentFocusDist).absoluteValue < 0.25f }
                                                    if (idx > 0) {
                                                        currentFocusDist = focusValues[idx - 1]
                                                        proController.applyAdvancedSettings(ProCameraSettings(exposureCompensationIndex = currentEvIndex, isoValue = currentIso, shutterTimeNs = shutterValues[currentShutterIndex], focusDistance = currentFocusDist, isTorchOn = isTorchEnabled))
                                                        guide.targetTracker.updateFocusDistanceRealtime(currentFocusDist)
                                                        targetTrackerStatus = guide.targetTracker.currentStatus
                                                        guideFeedback = guide.evaluateGuide()
                                                    }
                                                }
                                                "ZOOM" -> {
                                                    val idx = zoomValues.indexOfFirst { (it - currentZoom).absoluteValue < 0.15f }
                                                    if (idx > 0) {
                                                        currentZoom = zoomValues[idx - 1]
                                                        proController.setZoom(currentZoom)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                        ) {
                            // 垂直刻度绘制 (随拖动垂直平移，中间游标指针，顶部与底部渐隐)
                            Canvas(Modifier.fillMaxSize()) {
                                val w = size.width
                                val h = size.height
                                val midY = h / 2f
                                val halfH = h / 2f
                                val tickSpacing = 16.dp.toPx()

                                for (k in 0 until curListSize) {
                                    // 向上滑为正
                                    val tickY = midY - (k - curIndex) * tickSpacing - vDragAccumulator
                                    if (tickY in -10f..(h + 10f)) {
                                        val dist = kotlin.math.abs(tickY - midY)
                                        val normDist = (dist / halfH).coerceIn(0f, 1f)
                                        val alphaFactor = ((1f - normDist) * (1f - normDist)).coerceIn(0f, 1f)
                                        val isCenter = (k == curIndex && kotlin.math.abs(vDragAccumulator) < 14f)

                                        val lineLen = if (isCenter) w * 0.72f else w * 0.44f
                                        val strokeW = if (isCenter) 2.dp.toPx() else 1.2.dp.toPx()
                                        val tickColor = if (isCenter) TextPri.copy(alpha = alphaFactor) else TextSec.copy(alpha = 0.6f * alphaFactor)

                                        drawLine(
                                            color = tickColor,
                                            start = androidx.compose.ui.geometry.Offset(w - lineLen, tickY),
                                            end = androidx.compose.ui.geometry.Offset(w - 6.dp.toPx(), tickY),
                                            strokeWidth = strokeW
                                        )
                                    }
                                }

                                // 中心高亮游标横线
                                drawLine(
                                    color = Cyan,
                                    start = androidx.compose.ui.geometry.Offset(2.dp.toPx(), midY),
                                    end = androidx.compose.ui.geometry.Offset(w - 2.dp.toPx(), midY),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                        }

                        // 刻度盘左侧悬浮数值指示圈
                        Surface(
                            shape = CircleShape,
                            color = Bg.copy(alpha = 0.95f),
                            border = BorderStroke(1.5.dp, Cyan),
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .size(46.dp)
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    curDisplayVal,
                                    color = TextPri,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            } else {
                Box(Modifier.fillMaxSize().background(BgCard), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("需要相机权限以进行空间扫描", color = TextPri, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }, shape = RoundedCornerShape(12.dp)) {
                            Text("授权相机")
                        }
                    }
                }
            }
        }

        // 拍摄与外部媒体导入控制栏
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    val cap = capture ?: return@Button
                    if (isShooting) return@Button
                    isShooting = true
                    val dir = File(context.getExternalFilesDir(null), "captures").apply { mkdirs() }
                    val file = File(dir, "img_${System.currentTimeMillis()}.jpg")
                    cap.takePicture(
                        ImageCapture.OutputFileOptions.Builder(file).build(),
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(o: ImageCapture.OutputFileResults) {
                                // 在后台线程执行单次下采样解码与毫秒级质检，避免阻塞主线程与全尺寸重编码
                                val focusDistSnapshot = currentFocusDist
                                val isFirstOrUnset = capturedFiles.isEmpty() || guide.targetTracker.currentStatus == com.example.remy3d.engine.TargetTrackerEngine.TargetStatus.NOT_SET
                                val checkTargetLock = (scanMode == SpatialCaptureGuide.ScanMode.OBJECT) && isTargetLockEnabled

                                scope.launch(Dispatchers.Default) {
                                    val sampleBmp = decodeBitmapWithExif(file, maxSide = 360)
                                    var isSameTarget = true
                                    var deviationReason: String? = null

                                    if (sampleBmp != null) {
                                        if (scanMode == SpatialCaptureGuide.ScanMode.OBJECT && !isFirstOrUnset && checkTargetLock) {
                                            val res = guide.targetTracker.verifyConsistency(sampleBmp, focusDistSnapshot)
                                            isSameTarget = res.first
                                            deviationReason = res.second
                                        }
                                    }

                                    val report = if (!isSameTarget) {
                                        null
                                    } else if (sampleBmp != null) {
                                        ImageQualityEvaluator.evaluate(sampleBmp)
                                    } else {
                                        ImageQualityEvaluator.evaluate(file)
                                    }

                                    val passesQuality = report == null || (!report.isBlurry && !report.isUnderExposed && !report.isOverExposed)
                                    if (sampleBmp != null && scanMode == SpatialCaptureGuide.ScanMode.OBJECT && isFirstOrUnset && passesQuality) {
                                        // 仅在首张照片画质合格直接录入时锁定目标；若命中模糊弹窗，等用户确认“仍然录入”再锁定
                                        guide.targetTracker.lockTargetFromBitmap(sampleBmp, focusDistSnapshot)
                                    }

                                    sampleBmp?.recycle()

                                    withContext(Dispatchers.Main) {
                                        isShooting = false
                                        if (scanMode == SpatialCaptureGuide.ScanMode.OBJECT) {
                                            targetTrackerStatus = guide.targetTracker.currentStatus
                                            targetMatchScore = guide.targetTracker.lastMatchScore
                                            guideFeedback = guide.evaluateGuide()
                                        }

                                        if (!isSameTarget && deviationReason != null) {
                                            // 命中目标偏离：唤起专属偏离拦截弹窗
                                            pendingDeviationFile = file
                                            pendingDeviationReason = deviationReason
                                            return@withContext
                                        }

                                        if (report != null && (report.isBlurry || report.isUnderExposed || report.isOverExposed)) {
                                            // 命中模糊或曝光异常：唤起确认弹窗供用户选择
                                            pendingQualityCheckFile = file
                                            pendingQualityReport = report
                                        } else {
                                            // 画质清晰合格且属于同一目标物体：直接录入
                                            onFileAdded(file)
                                        }
                                    }
                                }
                            }
                            override fun onError(e: ImageCaptureException) {
                                isShooting = false
                                Toast.makeText(context, "拍摄失败：${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                },
                modifier = Modifier.weight(1f).height(54.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = cameraGranted && !isShooting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = when {
                        targetTrackerStatus == com.example.remy3d.engine.TargetTrackerEngine.TargetStatus.DEVIATED_OBJECT ||
                        targetTrackerStatus == com.example.remy3d.engine.TargetTrackerEngine.TargetStatus.DEVIATED_DISTANCE -> Color(0xFFFF4D4D)
                        guideFeedback.isWarning -> Accent
                        else -> Cyan
                    }
                )
            ) {
                Icon(Icons.Default.AddAPhoto, null, tint = Bg)
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        isShooting -> "捕获中…"
                        targetTrackerStatus == com.example.remy3d.engine.TargetTrackerEngine.TargetStatus.DEVIATED_OBJECT -> "⚠️ 已偏离目标物体"
                        targetTrackerStatus == com.example.remy3d.engine.TargetTrackerEngine.TargetStatus.DEVIATED_DISTANCE -> "⚠️ 对焦距离偏移过大"
                        else -> "拍摄此视角"
                    },
                    color = Bg,
                    fontWeight = FontWeight.Bold
                )
            }

            // 导入相册多图按钮
            OutlinedButton(
                onClick = onImportPhotos,
                modifier = Modifier.height(54.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Cyan.copy(0.7f)),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Cyan.copy(0.12f)),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.PhotoLibrary, null, tint = Cyan, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.height(2.dp))
                    Text("导图", fontSize = 10.sp, color = Cyan, fontWeight = FontWeight.Bold)
                }
            }

            // 导入视频抽帧按钮
            OutlinedButton(
                onClick = onImportVideo,
                modifier = Modifier.height(54.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Green.copy(0.7f)),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Green.copy(0.12f)),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("🎬", fontSize = 15.sp)
                    Spacer(Modifier.height(2.dp))
                    Text("抽帧", fontSize = 10.sp, color = Green, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 进入重建按钮
        val (topCov, midCov, botCov) = guide.getLayerCoverages()
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            enabled = count >= 3,
            colors = ButtonDefaults.buttonColors(containerColor = if (spatialCoveragePercent >= 0.7f && topCov > 0.1f) Green else Accent)
        ) {
            Text(
                when {
                    count < 3 -> "至少采集 3 个不同视角"
                    topCov < 0.1f -> "建议抬高手机补拍 1-2 张顶部俯角 (当前 $count 张)"
                    spatialCoveragePercent < 0.6f -> "建议继续绕拍补齐角度 (当前 $count 张)"
                    else -> "✓ 顶部与环绕立体角度充足，开始重建 ($count 张)"
                },
                fontWeight = FontWeight.SemiBold
            )
        }

        // 照片模糊与曝光异常质检拦截确认卡片
        if (pendingQualityCheckFile != null && pendingQualityReport != null) {
            val rep = pendingQualityReport!!
            val pFile = pendingQualityCheckFile!!
            androidx.compose.ui.window.Dialog(onDismissRequest = {
                // 点击遮罩取消：删除模糊临时文件并丢弃，不触发草稿箱
                pFile.delete()
                if (capturedFiles.isEmpty()) {
                    guide.targetTracker.resetTarget()
                } else {
                    guide.targetTracker.clearDeviationState()
                }
                guide.targetDeviationWarning = null
                targetTrackerStatus = guide.targetTracker.currentStatus
                targetMatchScore = guide.targetTracker.lastMatchScore
                guideFeedback = guide.evaluateGuide()
                pendingQualityCheckFile = null
                pendingQualityReport = null
            }) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = BgCard,
                    border = BorderStroke(1.5.dp, Color(0xFFFFB800)),
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(Modifier.size(48.dp).background(Color(0xFFFFB800).copy(.15f), CircleShape), contentAlignment = Alignment.Center) {
                            Text("⚠️", fontSize = 24.sp)
                        }

                        Text("画质与模糊度预警", color = TextPri, fontSize = 17.sp, fontWeight = FontWeight.Bold)

                        Surface(
                            color = Bg.copy(.6f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("• ${rep.summaryPrompt}", color = Color(0xFFD97706), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Text("• 锐度得分: ${rep.sharpnessScore.toInt()} (合格基准 > 25)", color = Muted, fontSize = 11.sp)
                                Text("• 提示: 模糊照片加入可能导致 3D 表面出现噪斑撕裂", color = TextSec, fontSize = 11.sp)
                            }
                        }

                        Text("是否坚持将本张照片录入 3DGS 训练集？", color = TextPri, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // 取消并丢弃（仅删除当前废片并恢复引导状态，绝不触发草稿箱）
                            OutlinedButton(
                                onClick = {
                                    pFile.delete() // 彻底清理丢弃废片
                                    if (capturedFiles.isEmpty()) {
                                        guide.targetTracker.resetTarget()
                                    } else {
                                        guide.targetTracker.clearDeviationState()
                                    }
                                    guide.targetDeviationWarning = null
                                    targetTrackerStatus = guide.targetTracker.currentStatus
                                    targetMatchScore = guide.targetTracker.lastMatchScore
                                    guideFeedback = guide.evaluateGuide()
                                    pendingQualityCheckFile = null
                                    pendingQualityReport = null
                                    Toast.makeText(context, "已取消录入并放弃该照片", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f).height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Muted.copy(.4f))
                            ) {
                                Text("取消放弃", color = Muted, fontSize = 13.sp)
                            }

                            // 仍然录入
                            Button(
                                onClick = {
                                    if (scanMode == SpatialCaptureGuide.ScanMode.OBJECT &&
                                        (capturedFiles.isEmpty() || guide.targetTracker.currentStatus == com.example.remy3d.engine.TargetTrackerEngine.TargetStatus.NOT_SET)
                                    ) {
                                        guide.targetTracker.lockTargetFromImage(pFile, currentFocusDist)
                                        targetTrackerStatus = guide.targetTracker.currentStatus
                                        targetMatchScore = guide.targetTracker.lastMatchScore
                                    }
                                    onFileAdded(pFile)
                                    guideFeedback = guide.evaluateGuide()
                                    pendingQualityCheckFile = null
                                    pendingQualityReport = null
                                    Toast.makeText(context, "已将照片强制录入", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f).height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB800))
                            ) {
                                Text("仍然录入", color = Bg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 扫描目标主体偏离拦截对话框
        if (pendingDeviationFile != null && pendingDeviationReason != null) {
            val devFile = pendingDeviationFile!!
            val devReason = pendingDeviationReason!!
            androidx.compose.ui.window.Dialog(onDismissRequest = {
                // 点击背景默认放弃该偏离废片，并立即恢复实时状态，不触发草稿箱
                devFile.delete()
                guide.targetTracker.clearDeviationState()
                guide.targetDeviationWarning = null
                targetTrackerStatus = guide.targetTracker.currentStatus
                targetMatchScore = guide.targetTracker.lastMatchScore
                guideFeedback = guide.evaluateGuide()
                pendingDeviationFile = null
                pendingDeviationReason = null
            }) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = BgCard,
                    border = BorderStroke(1.5.dp, Color(0xFFFF4D4D)),
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(Modifier.size(48.dp).background(Color(0xFFFF4D4D).copy(.15f), CircleShape), contentAlignment = Alignment.Center) {
                            Text("🎯", fontSize = 24.sp)
                        }

                        Text("扫描主体偏离预警", color = TextPri, fontSize = 17.sp, fontWeight = FontWeight.Bold)

                        Surface(
                            color = Bg.copy(.6f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("• $devReason", color = Color(0xFFFF6E6E), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text("• 当前特征相似度: ${(targetMatchScore * 100).toInt()}% (基准门限 ≥ 45%)", color = Muted, fontSize = 11.sp)
                                Text("• 危害: 录入其他物体或非目标背景会导致 3DGS 特征点云严重撕裂变形", color = TextSec, fontSize = 11.sp)
                            }
                        }

                        Text("请选择针对本张照片的处理方式：", color = TextPri, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)

                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                // 放弃此张 (推荐)：删除废片并立即清除偏离锁定状态，由实时预览接管，不触发草稿箱
                                Button(
                                    onClick = {
                                        devFile.delete()
                                        guide.targetTracker.clearDeviationState()
                                        guide.targetDeviationWarning = null
                                        targetTrackerStatus = guide.targetTracker.currentStatus
                                        targetMatchScore = guide.targetTracker.lastMatchScore
                                        guideFeedback = guide.evaluateGuide()
                                        pendingDeviationFile = null
                                        pendingDeviationReason = null
                                        Toast.makeText(context, "已放弃该偏离照片，实时检测已恢复", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f).height(46.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Cyan)
                                ) {
                                    Text("放弃此张 (推荐)", color = Bg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                // 切换为主体
                                OutlinedButton(
                                    onClick = {
                                        guide.targetTracker.lockTargetFromImage(devFile, currentFocusDist)
                                        guide.targetDeviationWarning = null
                                        targetTrackerStatus = guide.targetTracker.currentStatus
                                        targetMatchScore = guide.targetTracker.lastMatchScore
                                        guideFeedback = guide.evaluateGuide()
                                        onFileAdded(devFile)
                                        pendingDeviationFile = null
                                        pendingDeviationReason = null
                                        Toast.makeText(context, "已将此物体设为新目标主体并录入", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f).height(46.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Green)
                                ) {
                                    Text("更替为主体", color = Green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            // 强制忽略并录入
                            OutlinedButton(
                                onClick = {
                                    guide.targetTracker.clearDeviationState()
                                    guide.targetDeviationWarning = null
                                    targetTrackerStatus = guide.targetTracker.currentStatus
                                    targetMatchScore = guide.targetTracker.lastMatchScore
                                    guideFeedback = guide.evaluateGuide()
                                    onFileAdded(devFile)
                                    pendingDeviationFile = null
                                    pendingDeviationReason = null
                                    Toast.makeText(context, "已忽略偏离警告并录入", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth().height(40.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Muted.copy(.35f))
                            ) {
                                Text("仍要录入 (可能产生噪点)", color = Muted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // 模式切换确认与草稿保存警示弹窗
        if (showModeSwitchWarningDialog && targetModeToSwitch != null) {
            val next = targetModeToSwitch!!
            androidx.compose.ui.window.Dialog(onDismissRequest = {
                showModeSwitchWarningDialog = false
                targetModeToSwitch = null
            }) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = BgCard,
                    border = BorderStroke(1.5.dp, Color(0xFFFFB800)),
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(Modifier.size(48.dp).background(Color(0xFFFFB800).copy(.15f), CircleShape), contentAlignment = Alignment.Center) {
                            Text("⚠️", fontSize = 24.sp)
                        }

                        Text("切换拍摄模式提示", color = TextPri, fontSize = 17.sp, fontWeight = FontWeight.Bold)

                        Text(
                            "当前已有 $count 张未重建照片。切换至【${if (next == SpatialCaptureGuide.ScanMode.SPATIAL_SCENE) "三维空间场景记忆" else "单体实物高精扫描"}】将清空当前的罗盘覆盖及轨迹，请选择处理方式：",
                            color = TextSec,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )

                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // 存为草稿并切换
                            Button(
                                onClick = {
                                    onSaveDraft()
                                    onSwitchMode(next)
                                    targetTrackerStatus = guide.targetTracker.currentStatus
                                    targetMatchScore = guide.targetTracker.lastMatchScore
                                    guideFeedback = guide.evaluateGuide()
                                    showModeSwitchWarningDialog = false
                                    targetModeToSwitch = null
                                },
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Cyan)
                            ) {
                                Text("保存到草稿箱并切换", color = Bg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            // 放弃当前照片并重置（绝不保存草稿，且若关联了旧草稿则一并清理）
                            OutlinedButton(
                                onClick = {
                                    onResetSession()
                                    onSwitchMode(next)
                                    targetTrackerStatus = guide.targetTracker.currentStatus
                                    targetMatchScore = guide.targetTracker.lastMatchScore
                                    guideFeedback = guide.evaluateGuide()
                                    showModeSwitchWarningDialog = false
                                    targetModeToSwitch = null
                                },
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFFF4D4D))
                            ) {
                                Text("放弃当前拍摄并重置", color = Color(0xFFFF6E6E), fontSize = 13.sp)
                            }

                            // 取消切换
                            OutlinedButton(
                                onClick = {
                                    showModeSwitchWarningDialog = false
                                    targetModeToSwitch = null
                                },
                                modifier = Modifier.fillMaxWidth().height(42.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Muted.copy(.35f))
                            ) {
                                Text("取消", color = Muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // 中途退出拍摄确认与草稿保存警示弹窗
        if (showExitWarningDialog) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { showExitWarningDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = BgCard,
                    border = BorderStroke(1.5.dp, Accent),
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Column(
                        Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(Modifier.size(48.dp).background(Accent.copy(.15f), CircleShape), contentAlignment = Alignment.Center) {
                            Text("💾", fontSize = 24.sp)
                        }

                        Text("退出拍摄会话", color = TextPri, fontSize = 17.sp, fontWeight = FontWeight.Bold)

                        Text(
                            "当前已采集 $count 张照片尚未生成 3D 模型。退出前是否保存草稿？",
                            color = TextSec,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )

                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // 保存草稿并退出
                            Button(
                                onClick = {
                                    onSaveDraft()
                                    showExitWarningDialog = false
                                    onExitToHome()
                                },
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Green)
                            ) {
                                Text("保存到草稿箱并退出", color = Bg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            // 放弃所有照片并清空（绝不保存草稿）
                            OutlinedButton(
                                onClick = {
                                    onResetSession()
                                    showExitWarningDialog = false
                                    onExitToHome()
                                },
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFFF4D4D))
                            ) {
                                Text("放弃当前照片并退出", color = Color(0xFFFF6E6E), fontSize = 13.sp)
                            }

                            // 继续拍摄
                            OutlinedButton(
                                onClick = { showExitWarningDialog = false },
                                modifier = Modifier.fillMaxWidth().height(42.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Muted.copy(.35f))
                            ) {
                                Text("继续拍摄", color = Muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 按指定最大边长快速下采样解码图片，并在内存中应用 EXIF 物理方向旋转校正：
 * 避免对全尺寸原图执行解码、旋转与 JPEG 重编码覆写磁盘，将拍照后处理耗时从 ~1000ms 降至 < 10ms
 */
private fun decodeBitmapWithExif(file: File, maxSide: Int = 360, preferRgb565: Boolean = false): Bitmap? {
    return try {
        val boundsOpt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOpt)
        if (boundsOpt.outWidth <= 0 || boundsOpt.outHeight <= 0) return null

        val sample = maxOf(1, maxOf(boundsOpt.outWidth, boundsOpt.outHeight) / maxSide)
        val loadOpt = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = if (preferRgb565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        }
        val bmp = BitmapFactory.decodeFile(file.absolutePath, loadOpt) ?: return null

        val exif = ExifInterface(file.absolutePath)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees != 0f) {
            val matrix = Matrix().apply { postRotate(degrees) }
            val rotatedBmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
            if (rotatedBmp != bmp) bmp.recycle()
            rotatedBmp
        } else {
            bmp
        }
    } catch (_: Exception) {
        null
    }
}

// ════════════════════════════════════════════════════════════
//  3 · 真实重建页面
// ════════════════════════════════════════════════════════════
@Composable
private fun RebuildScreen(
    stage: String, progress: Float, isRebuilding: Boolean,
    result: ReconstructionResult?, imageCount: Int,
    onViewResult: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Text(if (isRebuilding) "正在重建 3D 场景" else "重建完成！",
            fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = TextPri)
        Text(if (isRebuilding) "正在处理 $imageCount 张照片" else "已生成 .ply 点云文件",
            color = Muted, fontSize = 13.sp)

        Spacer(Modifier.height(8.dp))

        // 环形进度
        Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 10.dp,
                color = if (progress >= 1f) Green else Accent,
                trackColor = Accent.copy(.15f)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${(progress * 100).toInt()}%", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
                    color = if (progress >= 1f) Green else TextPri)
                if (isRebuilding) {
                    CircularProgressIndicator(Modifier.size(16.dp).padding(top = 4.dp), color = Accent, strokeWidth = 2.dp)
                }
            }
        }

        // 当前阶段
        Surface(color = BgCard, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Muted.copy(0.2f))) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("处理阶段", color = TextSec, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isRebuilding) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = Accent, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.CheckCircle, null, tint = Green, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(stage, color = TextPri, fontSize = 14.sp)
                }
            }
        }

        // 结果信息
        if (result != null) {
            Surface(color = BgCard, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Muted.copy(0.2f))) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("重建结果", color = TextSec, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        StatItem("点数", "${result.pointCount}", Green)
                        StatItem("图片", "${result.imageCount}", Cyan)
                        StatItem("耗时", "${result.processingTimeMs / 1000}s", AccentAlt)
                    }
                    HorizontalDivider(color = Muted.copy(.15f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SaveAlt, null, tint = Green, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("PLY 文件已导出", color = TextPri, fontSize = 13.sp)
                            Text(result.plyFile.name, color = Muted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        if (!isRebuilding && result != null) {
            Button(
                onClick = onViewResult,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Green)
            ) {
                Icon(Icons.Default.ViewInAr, null)
                Spacer(Modifier.width(8.dp))
                Text("查看 3D 重建结果", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Muted, fontSize = 11.sp)
    }
}

// ════════════════════════════════════════════════════════════
//  4 · 3D 查看器（纯原生 Android OpenGL ES 2.0 硬件渲染）
// ════════════════════════════════════════════════════════════
@Composable
private fun ViewerScreen(
    title: String,
    info: String,
    points: List<SplatPoint>?,
    presetScene: GaussianRenderer.SceneType? = null,
    plyPath: String?,
    imagePaths: List<String> = emptyList(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var glViewInstance by remember { mutableStateOf<SplatGLView?>(null) }

    var isAutoRotate by remember { mutableStateOf(false) }
    var isPointCloudMode by remember { mutableStateOf(true) }
    var densityLevel by remember { mutableIntStateOf(2) } // 1: 1.2x (细致), 2: 2.4x (标准实物连续), 3: 3.6x (超致密填充)
    var selectedPhotoPath by remember { mutableStateOf<String?>(null) }
    var showPhotoRibbon by remember { mutableStateOf(true) }
    var activePhotoIndex by remember { mutableIntStateOf(-1) }
    val scope = rememberCoroutineScope()

    // 导出 PLY 文件选择器 (SAF 导出到用户指定的手机目录 / Download / 微信等)
    val exportPlyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { destUri ->
        if (destUri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    // 若有现存的本地 PLY 文件直接流拷贝
                    if (plyPath != null && File(plyPath).exists()) {
                        context.contentResolver.openOutputStream(destUri)?.use { out ->
                            File(plyPath).inputStream().use { inp -> inp.copyTo(out) }
                        }
                    } else {
                        // 若是预设场景或内存点云，现场导出生成 PLY 写入
                        val ptsToExport = points ?: (presetScene?.let { GaussianRenderer.generatePresetPoints(it) } ?: emptyList())
                        val tempFile = File(context.cacheDir, "temp_export_${System.currentTimeMillis()}.ply")
                        PlyExporter.exportAscii(ptsToExport, tempFile)
                        context.contentResolver.openOutputStream(destUri)?.use { out ->
                            tempFile.inputStream().use { inp -> inp.copyTo(out) }
                        }
                        tempFile.delete()
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "✅ PLY 模型已成功导出！", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val validPhotos = remember(imagePaths) {
        imagePaths.map { File(it) }.filter { it.exists() }
    }

    Box(Modifier.fillMaxSize()) {
        // ── 移动端轻量级 OpenGL ES 2.0 渲染查看器 (演示 Demo 原型实现，无需外部 WebView) ──
        AndroidView(
            factory = { ctx ->
                SplatGLView(ctx).also { view ->
                    glViewInstance = view
                    view.renderer.isAutoRotate = isAutoRotate
                    view.renderer.isPointCloudMode = isPointCloudMode
                    view.renderer.splatDensityScale = when (densityLevel) {
                        1 -> 1.2f
                        3 -> 3.6f
                        else -> 2.4f
                    }


                    if (points != null && points.isNotEmpty()) {
                        val currentPts = points
                        view.queueEvent { view.renderer.loadPoints(currentPts) }
                    } else if (plyPath != null && !plyPath.startsWith("content://") && java.io.File(plyPath).exists()) {
                        val path = plyPath
                        view.queueEvent { view.renderer.loadFromNativePly(path) }
                    } else if (presetScene != null) {
                        val currentScene = presetScene
                        view.renderer.sceneType = currentScene
                        view.renderer.externalPoints = null
                        view.queueEvent { view.renderer.reloadScene() }
                    }
                }
            },
            update = { view ->
                glViewInstance = view
                view.renderer.isAutoRotate = isAutoRotate
                view.renderer.isPointCloudMode = isPointCloudMode
                view.renderer.splatDensityScale = when (densityLevel) {
                    1 -> 1.2f
                    3 -> 3.6f
                    else -> 2.4f
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 仅在数据源变更时触发 GL 重载，防止任何 Compose 状态触发 update 时反复申请巨额 DirectBuffer
        LaunchedEffect(points, presetScene, plyPath) {
            glViewInstance?.let { view ->
                if (points != null && points.isNotEmpty()) {
                    val currentPts = points
                    view.queueEvent { view.renderer.loadPoints(currentPts) }
                } else if (plyPath != null && !plyPath.startsWith("content://") && java.io.File(plyPath).exists()) {
                    val path = plyPath
                    view.queueEvent { view.renderer.loadFromNativePly(path) }
                } else if (presetScene != null) {
                    val currentScene = presetScene
                    view.renderer.sceneType = currentScene
                    view.renderer.externalPoints = null
                    view.queueEvent { view.renderer.reloadScene() }
                }
            }
        }

        // 顶部栏
        Row(Modifier.fillMaxWidth().padding(12.dp).align(Alignment.TopStart), verticalAlignment = Alignment.CenterVertically) {
            Surface(onClick = onBack, color = Bg.copy(.65f), shape = CircleShape) {
                Icon(Icons.Default.ArrowBack, null, tint = TextPri, modifier = Modifier.padding(10.dp).size(22.dp))
            }
            Spacer(Modifier.width(10.dp))
            Surface(color = Bg.copy(.65f), shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                Column(Modifier.padding(12.dp, 8.dp)) {
                    Text(title, color = TextPri, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(info, color = Muted, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.width(6.dp))
            Surface(
                onClick = {
                    val defaultFileName = "model_${title.replace(" ", "_")}_${System.currentTimeMillis()}.ply"
                    exportPlyLauncher.launch(defaultFileName)
                },
                color = Accent.copy(.22f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Cyan.copy(.6f))
            ) {
                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(Icons.Default.SaveAlt, contentDescription = "导出 PLY", tint = Cyan, modifier = Modifier.size(13.dp))
                    Text("导出 PLY", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(6.dp))
            Surface(
                onClick = {
                    glViewInstance?.let { view ->
                        view.queueEvent {
                            view.renderer.captureSnapshot { bitmap ->
                                scope.launch(Dispatchers.IO) {
                                    val uri = MediaStoreSaver.saveBitmapToGallery(context, bitmap, "Remy3D_${title.replace(" ", "_")}")
                                    withContext(Dispatchers.Main) {
                                        if (uri != null) {
                                            Toast.makeText(context, "🎉 已将 3D 模型高清渲染图保存到相册！", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "保存到相册失败，请检查存储空间", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        }
                    } ?: run {
                        Toast.makeText(context, "渲染器尚未准备就绪", Toast.LENGTH_SHORT).show()
                    }
                },
                color = Green.copy(.20f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Green.copy(.6f))
            ) {
                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "存相册", tint = Green, modifier = Modifier.size(13.dp))
                    Text("存相册", color = Green, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(6.dp))
            Surface(
                color = Green.copy(.18f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Green.copy(.4f))
            ) {
                Text(
                    if (isPointCloudMode) "透视点云" else "高斯实体",
                    Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    color = Green,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 右侧快捷浮动控制栏：360°自动展台、实体/点云透视切换、实物致密填满度、一键复位、缩放
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 360° 自动环绕展台 (Auto-Turntable 经典空间展示)
            Surface(
                onClick = {
                    isAutoRotate = !isAutoRotate
                    glViewInstance?.renderer?.isAutoRotate = isAutoRotate
                },
                color = if (isAutoRotate) Cyan else Bg.copy(.85f),
                shape = CircleShape,
                border = BorderStroke(1.5.dp, if (isAutoRotate) Cyan else Cyan.copy(.4f))
            ) {
                Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (isAutoRotate) "⏸" else "▶ 360°",
                        color = if (isAutoRotate) Bg else Cyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            // 实体/点云渲染模式切换
            Surface(
                onClick = {
                    isPointCloudMode = !isPointCloudMode
                    glViewInstance?.renderer?.isPointCloudMode = isPointCloudMode
                },
                color = if (isPointCloudMode) AccentAlt else Bg.copy(.85f),
                shape = CircleShape,
                border = BorderStroke(1.5.dp, if (isPointCloudMode) AccentAlt else Accent.copy(.4f))
            ) {
                Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (isPointCloudMode) "点云" else "实体",
                        color = if (isPointCloudMode) Bg else TextPri,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 实物填充致密度切换：仅在“实体”模式下才需要调整高斯致密度；点云透视模式下自动隐藏
            if (!isPointCloudMode) {
                Surface(
                    onClick = {
                        densityLevel = if (densityLevel >= 3) 1 else densityLevel + 1
                        val scale = when (densityLevel) {
                            1 -> 1.2f
                            3 -> 3.6f
                            else -> 2.4f
                        }
                        glViewInstance?.renderer?.splatDensityScale = scale
                    },
                    color = Bg.copy(.85f),
                    shape = CircleShape,
                    border = BorderStroke(1.5.dp, Green.copy(.6f))
                ) {
                    Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                        Text(
                            when (densityLevel) {
                                1 -> "致密1x"
                                3 -> "致密3x"
                                else -> "致密2x"
                            },
                            color = Green,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // 空间沉浸式第一人称漫游：🚶 走进空间漫游 vs 🔄 展台环视模式切换
            var isWalkthrough by remember { mutableStateOf(false) }
            Surface(
                onClick = {
                    isWalkthrough = !isWalkthrough
                    glViewInstance?.renderer?.cameraMode = if (isWalkthrough) {
                        GaussianRenderer.CameraMode.WALKTHROUGH
                    } else {
                        GaussianRenderer.CameraMode.ORBIT
                    }
                    if (isWalkthrough) isAutoRotate = false
                },
                color = if (isWalkthrough) Green else Bg.copy(.85f),
                shape = CircleShape,
                border = BorderStroke(1.5.dp, if (isWalkthrough) Green else Green.copy(.4f))
            ) {
                Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (isWalkthrough) "漫游中" else "🚶漫游",
                        color = if (isWalkthrough) Bg else Green,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            // 一键复位居中
            Surface(
                onClick = {
                    glViewInstance?.renderer?.resetView()
                    glViewInstance?.renderer?.resetWalkToCenter()
                },
                color = Bg.copy(.85f),
                shape = CircleShape,
                border = BorderStroke(1.dp, Cyan.copy(.4f))
            ) {
                Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                    Text("⟲", color = Cyan, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }

            // 放大
            Surface(
                onClick = { glViewInstance?.renderer?.adjustZoom(1.25f) },
                color = Bg.copy(.85f),
                shape = CircleShape,
                border = BorderStroke(1.dp, Accent.copy(.4f))
            ) {
                Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                    Text("+", color = TextPri, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }

            // 缩小
            Surface(
                onClick = { glViewInstance?.renderer?.adjustZoom(0.8f) },
                color = Bg.copy(.85f),
                shape = CircleShape,
                border = BorderStroke(1.dp, Accent.copy(.4f))
            ) {
                Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                    Text("−", color = TextPri, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 底部区域：实物照片对比胶卷 + 漫游控制/操作指引
        val isWalking = glViewInstance?.renderer?.cameraMode == GaussianRenderer.CameraMode.WALKTHROUGH
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 实物底片对比胶卷（当存在拍摄的源照片时展示，让用户随时对照 3D 渲染与真实实物照片）
            if (validPhotos.isNotEmpty()) {
                Surface(
                    color = BgCard.copy(.90f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Cyan.copy(.35f)),
                    modifier = Modifier.padding(horizontal = 14.dp)
                ) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Photo, null, tint = Cyan, modifier = Modifier.size(13.dp))
                                Text("📷 拍摄实物底片对比 (${validPhotos.size}张)", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Text(
                                if (showPhotoRibbon) "收起 ▼" else "展开 ▲",
                                color = TextSec,
                                fontSize = 10.sp,
                                modifier = Modifier.pointerInput(Unit) {
                                    detectTapGestures { showPhotoRibbon = !showPhotoRibbon }
                                }
                            )
                        }
                        if (showPhotoRibbon) {
                            Spacer(Modifier.height(4.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(validPhotos.size) { index ->
                                    val photoFile = validPhotos[index]
                                    val isSelected = (index == activePhotoIndex)
                                    PhotoThumbnailItem(
                                        index = index,
                                        photoFile = photoFile,
                                        isSelected = isSelected,
                                        onSelect = {
                                            activePhotoIndex = index
                                            isAutoRotate = false
                                            glViewInstance?.renderer?.isAutoRotate = false
                                            glViewInstance?.renderer?.alignCameraToPhoto(index, validPhotos.size)
                                        },
                                        onZoom = {
                                            selectedPhotoPath = photoFile.absolutePath
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 底部操作指引 / 漫游控制器
            if (isWalking) {
                Surface(
                    color = Bg.copy(.88f),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Green.copy(.5f))
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { glViewInstance?.renderer?.walkForward(0.35f) },
                            colors = ButtonDefaults.buttonColors(containerColor = Green),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("▲ 走进空间", color = Bg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { glViewInstance?.renderer?.walkForward(-0.35f) },
                            colors = ButtonDefaults.buttonColors(containerColor = BgCard),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("▼ 退出空间", color = TextPri, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("单指滑动环顾房间四壁与天花板", color = TextSec, fontSize = 11.sp)
                    }
                }
            } else {
                Surface(color = Bg.copy(.75f), shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.padding(14.dp, 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.OpenWith, null, tint = Cyan, modifier = Modifier.size(15.dp))
                            Text("单指环绕", color = TextSec, fontSize = 11.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("✥", color = Cyan, fontSize = 13.sp)
                            Text("双指缩放", color = TextSec, fontSize = 11.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.ZoomIn, null, tint = Cyan, modifier = Modifier.size(15.dp))
                            Text("双击复位", color = TextSec, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // 拍摄实物原图全屏预览弹窗
        if (selectedPhotoPath != null) {
            val photoFile = File(selectedPhotoPath!!)
            val fullBitmap = remember(selectedPhotoPath) {
                decodeBitmapWithExif(photoFile, maxSide = 1280)
            }
            androidx.compose.ui.window.Dialog(onDismissRequest = { selectedPhotoPath = null }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = BgCard,
                    border = BorderStroke(1.dp, Cyan.copy(.4f)),
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                ) {
                    Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📷 采集现场实物照片", color = TextPri, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Surface(onClick = { selectedPhotoPath = null }, shape = CircleShape, color = Bg) {
                                Icon(Icons.Default.Close, null, tint = TextSec, modifier = Modifier.padding(6.dp).size(18.dp))
                            }
                        }
                        if (fullBitmap != null) {
                            Image(
                                bitmap = fullBitmap.asImageBitmap(),
                                contentDescription = "实物原图",
                                modifier = Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(10.dp)),
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit
                            )
                        } else {
                            Text("无法加载图片: ${photoFile.name}", color = TextSec, fontSize = 12.sp)
                        }
                        Text("此照片为当前 3DGS 点云特征来源底片之一", color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoThumbnailItem(
    index: Int,
    photoFile: File,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onZoom: () -> Unit
) {
    val bitmap = remember(photoFile.absolutePath) {
        decodeBitmapWithExif(photoFile, maxSide = 120, preferRgb565 = true)
    }
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) Cyan else Muted.copy(.3f)
        ),
        color = Bg
    ) {
        Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "实物底片",
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Icon(Icons.Default.Photo, null, tint = Muted, modifier = Modifier.size(20.dp))
            }
            // 序号角标
            Surface(
                color = if (isSelected) Cyan else Bg.copy(0.75f),
                shape = RoundedCornerShape(bottomEnd = 6.dp),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Text(
                    "${index + 1}",
                    color = if (isSelected) Bg else TextPri,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
            // 右下角放大预览按钮
            Surface(
                onClick = onZoom,
                color = Bg.copy(0.7f),
                shape = CircleShape,
                modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp)
            ) {
                Icon(
                    Icons.Default.ZoomIn,
                    contentDescription = "查看大图",
                    tint = TextPri,
                    modifier = Modifier.padding(2.dp).size(12.dp)
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
//  5 · 记录库
// ════════════════════════════════════════════════════════════
@Composable
private fun LibraryScreen(
    records: List<SceneRecord>,
    onClearRecords: () -> Unit,
    onImportExternalPly: (Uri) -> Unit,
    onOpen: (SceneRecord) -> Unit
) {
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            onImportExternalPly(uri)
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("我的 3D 记录", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = TextPri)
                Text("${records.size} 个场景 · 支持导入外部 PLY", color = Muted, fontSize = 13.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    onClick = { filePicker.launch(arrayOf("*/*")) },
                    color = Accent.copy(.2f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Accent.copy(.5f))
                ) {
                    Text("导入 .ply", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = AccentAlt, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Surface(
                    onClick = onClearRecords,
                    color = BgCard,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Muted.copy(.3f))
                ) {
                    Text("重置", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = Muted, fontSize = 12.sp)
                }
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(records) { record ->
                Surface(onClick = { onOpen(record) }, color = BgCard, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Muted.copy(0.2f))) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        // 图标
                        Box(Modifier.size(56.dp).background(if (record.isPreset) Accent.copy(.12f) else Green.copy(.12f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                            Icon(if (record.isPreset) Icons.Default.ViewInAr else Icons.Default.ScreenRotationAlt, null,
                                tint = if (record.isPreset) AccentAlt else Green, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(record.name, color = TextPri, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(record.date, color = Muted, fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("${record.pointCount} 点", color = if (record.isPreset) AccentAlt else Green, fontSize = 11.sp)
                                if (record.imageCount > 0) Text("${record.imageCount} 张照片", color = Cyan, fontSize = 11.sp)
                                if (record.plyPath != null) Text(".ply", color = Green, fontSize = 11.sp)
                            }
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = Muted)
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
//  6 · 草稿箱页面 (列表管理、查看具体存储路径、恢复拍摄与清理)
// ════════════════════════════════════════════════════════════
@Composable
private fun DraftsScreen(
    drafts: List<DraftItem>,
    onRestoreDraft: (DraftItem) -> Unit,
    onDeleteDraft: (DraftItem) -> Unit,
    onClearAllDrafts: () -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = onBack,
                color = BgCard,
                shape = CircleShape,
                modifier = Modifier.size(38.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.ArrowBack, null, tint = TextPri, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text("采集草稿箱", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = TextPri)
                Text("${drafts.size} 条未完成的草稿 · 点击直接恢复", color = Muted, fontSize = 12.sp)
            }

            if (drafts.isNotEmpty()) {
                Surface(
                    onClick = onClearAllDrafts,
                    color = BgCard,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFFF4D4D).copy(0.4f))
                ) {
                    Text("清空全部", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = Color(0xFFFF6E6E), fontSize = 12.sp)
                }
            }
        }

        if (drafts.isEmpty()) {
            Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("📦", fontSize = 48.sp)
                    Text("草稿箱空空如也", color = TextPri, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("拍摄中途中断或切换模式时选择【存为草稿】将展示在此处", color = Muted, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(drafts) { draft ->
                    val isSpatial = (draft.mode == SpatialCaptureGuide.ScanMode.SPATIAL_SCENE)
                    val sampleFile = draft.existingFiles.firstOrNull()

                    Surface(
                        onClick = { onRestoreDraft(draft) },
                        color = BgCard,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, if (isSpatial) Cyan.copy(0.35f) else Green.copy(0.35f))
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(46.dp).background(if (isSpatial) Cyan.copy(.15f) else Green.copy(.15f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(if (isSpatial) "🏛️" else "🎯", fontSize = 20.sp)
                                }

                                Spacer(Modifier.width(12.dp))

                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(draft.name, color = TextPri, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isSpatial) Cyan.copy(0.2f) else Green.copy(0.2f)
                                        ) {
                                            Text(
                                                if (isSpatial) "空间场景" else "实物扫描",
                                                Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                color = if (isSpatial) Cyan else Green,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Text(draft.date, color = Muted, fontSize = 11.sp)
                                }

                                // 删除单条草稿
                                Surface(
                                    onClick = { onDeleteDraft(draft) },
                                    color = Color.Transparent,
                                    shape = CircleShape,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Delete, null, tint = Muted, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            // 存储信息与实际文件路径展示
                            Surface(
                                color = Bg.copy(0.6f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("有效照片: ${draft.existingFiles.size} / ${draft.imagePaths.size} 张", color = Color(0xFFD97706), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        Text("点击卡片继续拍摄 →", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    if (sampleFile != null) {
                                        Text(
                                            "存储根路径: ${sampleFile.parentFile?.absolutePath ?: sampleFile.absolutePath}",
                                            color = Muted,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── PLY 导出工具函数（在 engine 中复用）─────────────────────────
fun exportPly(points: List<SplatPoint>, file: File) = PlyExporter.exportAscii(points, file)
