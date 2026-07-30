package com.apps

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import com.kireibox.ui.KireiActions
import com.kireibox.ui.BuiltinFileBrowserMode
import com.kireibox.ui.BuiltinFileBrowserRequest
import com.kireibox.ui.KireiBoxApp
import com.kireibox.ui.KireiPrompt
import com.kireibox.ui.KireiViewModel
import com.yuki.yukihub.R
import com.yuki.yukihub.data.GameRepository
import com.yuki.yukihub.launcher.EmulatorLauncher
import com.yuki.yukihub.launcherbridge.LauncherGameLaunchBridge
import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.model.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** KireiBox Compose shell. YukiHub repositories are the only runtime data source. */
class LauncherActivity : AppCompatActivity() {
    private lateinit var viewModel: KireiViewModel
    private var pendingLaunchFile: Uri? = null
    private var pendingPermissionRoot: String? = null
    private var runningSessionId = -1L
    private var engineWasOpened = false
    private var runningSessionJournaled = false
    private var pendingNativeLaunch: Game? = null
    private var pendingNativeShell = false
    private val launcherPrompt = androidx.compose.runtime.mutableStateOf<KireiPrompt?>(null)
    private val fileBrowserRequest = androidx.compose.runtime.mutableStateOf<BuiltinFileBrowserRequest?>(null)
    private var pendingBrowserMode: BuiltinFileBrowserMode? = null

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        if (persist(uri)) viewModel.addScanRoot(uri)
    }

    private val launchFilePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        val name = DocumentFile.fromSingleUri(this, uri)?.name.orEmpty()
        if (!isSupportedLaunchFile(name)) {
            showMessage("不支持的文件", "请选择 XP3、ONS 脚本/归档、Tyrano index.html 或 Artemis 启动文件。")
            return@registerForActivityResult
        }
        findPersistedTreeForFile(uri)?.let { tree -> finishLaunchFileImport(uri, tree); return@registerForActivityResult }
        pendingLaunchFile = uri
        launchParentFolderPicker.launch(suggestedParentTreeUri(uri))
    }

    private val launchParentFolderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        val file = pendingLaunchFile
        pendingLaunchFile = null
        if (tree != null && file != null && persist(tree)) finishLaunchFileImport(file, tree)
    }

    private val permissionRepairPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val old = pendingPermissionRoot
        pendingPermissionRoot = null
        if (uri != null && persist(uri)) {
            // Re-scan the new grant. Yuki identity matching preserves existing IDs and history.
            viewModel.addScanRoot(uri)
            if (!old.isNullOrBlank()) showMessage("权限已更新", "目录授权已重新建立，游戏记录和封面不会删除。")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_YukiHub_Launcher)
        configureEdgeToEdge(isLauncherDarkMode(this))
        super.onCreate(savedInstanceState)
        configureWindow()
        viewModel = ViewModelProvider(this)[KireiViewModel::class.java]
        setContent {
            KireiBoxApp(
                viewModel,
                KireiActions(
                    chooseDirectory = { chooseStorage(BuiltinFileBrowserMode.DIRECTORY) },
                    chooseLaunchFile = { chooseStorage(BuiltinFileBrowserMode.LAUNCH_FILE) },
                    chooseAndroidApp = viewModel::openAndroidAppPicker,
                    launchGame = ::launchGame,
                    launchKrkrNative = ::launchKrkrNative,
                    checkPermissions = ::checkPermissions,
                    repairPermission = ::repairPermission,
                    copyLogs = ::copyDiagnosticLogs,
                    exportLogs = ::exportDiagnosticLogs,
                    setDarkMode = { dark ->
                        viewModel.setDarkMode(dark)
                        applySystemBarTone(dark)
                    },
                    showAbout = ::showAbout
                ),
                prompt = launcherPrompt.value,
                onPromptDismiss = { launcherPrompt.value = null },
                fileBrowserRequest = fileBrowserRequest.value,
                onFileBrowserDismiss = { fileBrowserRequest.value = null },
                onFileBrowserUseSaf = {
                    val mode = fileBrowserRequest.value?.mode
                    fileBrowserRequest.value = null
                    mode?.let(::openSystemPicker)
                },
                onFileBrowserSelected = { uri ->
                    val mode = fileBrowserRequest.value?.mode
                    fileBrowserRequest.value = null
                    when (mode) {
                        BuiltinFileBrowserMode.DIRECTORY -> viewModel.addScanRoot(uri)
                        BuiltinFileBrowserMode.LAUNCH_FILE -> viewModel.importPhysicalLaunchFile(uri)
                        null -> Unit
                    }
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (engineWasOpened && runningSessionId > 0) {
            val sessionId = runningSessionId
            val journaled = runningSessionJournaled
            runningSessionId = -1L
            engineWasOpened = false
            runningSessionJournaled = false
            if (journaled) {
                LauncherGameLaunchBridge.reconcilePlaySessionsAsync(this, viewModel::reload)
            } else {
                // Android-app cards deliberately record a real launch count only;
                // KireiBox does not request Usage Access or guess foreground time.
                LauncherGameLaunchBridge.finishExternalSessionAsync(this, sessionId, viewModel::reload)
            }
        } else {
            LauncherGameLaunchBridge.reconcilePlaySessionsAsync(this, viewModel::reload)
        }
        val pending = pendingNativeLaunch
        if (pending != null && hasNativeFileAccess()) {
            pendingNativeLaunch = null
            window.decorView.post { launchPreparedGame(pending) }
        }
        if (pendingNativeShell && hasNativeFileAccess()) {
            pendingNativeShell = false
            window.decorView.post { openKrkrNativeShell() }
        }
        val browserMode = pendingBrowserMode
        if (browserMode != null && hasNativeFileAccess()) {
            pendingBrowserMode = null
            window.decorView.post { openBuiltinBrowser(browserMode) }
        }
    }

    private fun chooseStorage(mode: BuiltinFileBrowserMode) {
        val useBuiltin = com.yuki.yukihub.settings.YukiSettingsStore(this)
            .snapshot().useBuiltinFileChooser
        if (!useBuiltin) {
            openSystemPicker(mode)
            return
        }
        if (hasNativeFileAccess()) {
            openBuiltinBrowser(mode)
        } else {
            pendingBrowserMode = mode
            showBuiltinFileAccessDialog(mode)
        }
    }

    private fun openBuiltinBrowser(mode: BuiltinFileBrowserMode) {
        val internal = Environment.getExternalStorageDirectory()
        val appDirectory = getExternalFilesDir(null) ?: filesDir
        fileBrowserRequest.value = BuiltinFileBrowserRequest(
            mode = mode,
            startPath = internal.absolutePath,
            appPath = appDirectory.absolutePath
        )
    }

    private fun openSystemPicker(mode: BuiltinFileBrowserMode) {
        when (mode) {
            BuiltinFileBrowserMode.DIRECTORY -> folderPicker.launch(null)
            BuiltinFileBrowserMode.LAUNCH_FILE -> launchFilePicker.launch(arrayOf("*/*"))
        }
    }

    private fun showBuiltinFileAccessDialog(mode: BuiltinFileBrowserMode) {
        if (isFinishing || isDestroyed) return
        showPrompt(
            KireiPrompt(
                title = "允许 KireiBox 浏览文件",
                message = "Android 11 及以上需要“所有文件访问权限”才能使用内置浏览器。授权后会自动继续刚才的操作；KireiBox 本版本只浏览和选择，不会复制、移动、重命名或删除文件。",
                confirmLabel = "前往授权",
                dismissLabel = "使用系统 SAF",
                onConfirm = {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    runCatching { startActivity(intent) }
                        .recoverCatching { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                },
                onDismiss = {
                    pendingBrowserMode = null
                    openSystemPicker(mode)
                }
            )
        )
    }

    private fun launchGame(game: Game) {
        if (game.engine == EngineType.KIRIKIRI && !hasNativeFileAccess()) {
            pendingNativeLaunch = game
            showNativeFileAccessDialog()
            return
        }
        launchPreparedGame(game)
    }

    private fun launchPreparedGame(game: Game) {
        LauncherGameLaunchBridge.launchAsync(this, game, object : LauncherGameLaunchBridge.LaunchCallback {
            override fun onResult(result: LauncherGameLaunchBridge.LaunchResult) {
                if (result.success) {
                    runningSessionId = result.sessionId
                    engineWasOpened = true
                    runningSessionJournaled = game.engine != EngineType.ANDROID_APP
                    viewModel.reload()
                } else showMessage("无法启动", result.message)
            }
        })
    }

    private fun launchKrkrNative() {
        if (!hasNativeFileAccess()) {
            pendingNativeShell = true
            showNativeFileAccessDialog()
            return
        }
        openKrkrNativeShell()
    }

    private fun copyDiagnosticLogs() {
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { KireiDiagnosticLogs.buildText(this@LauncherActivity) }
            }
            val text = result.getOrNull()
            if (text == null) {
                showMessage("复制失败", result.exceptionOrNull()?.message ?: "无法读取诊断日志")
                return@launch
            }
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("KireiBox 日志", text))
            showMessage("日志已复制", "设备信息与最近日志已复制，常见 Token 已自动遮盖。")
        }
    }

    private fun exportDiagnosticLogs() {
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val text = KireiDiagnosticLogs.buildText(this@LauncherActivity)
                    KireiDiagnosticLogs.writeExport(this@LauncherActivity, text)
                }
            }
            val file = result.getOrNull()
            if (file == null) {
                showMessage("导出失败", result.exceptionOrNull()?.message ?: "无法生成诊断日志")
                return@launch
            }
            val uri = FileProvider.getUriForFile(
                this@LauncherActivity,
                "$packageName.fileprovider",
                file,
            )
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "KireiBox 诊断日志")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { startActivity(Intent.createChooser(share, "导出日志")) }
                .onFailure { showMessage("导出失败", it.message ?: "没有可用的分享应用") }
        }
    }

    private fun openKrkrNativeShell() {
        val result = runCatching {
            startActivity(
                EmulatorLauncher.buildInternalKrkrIntent(
                    this,
                    null,
                    null,
                    true,
                    "auto",
                    false,
                ),
            )
        }
        if (result.isFailure) {
            showMessage("无法启动", result.exceptionOrNull()?.message ?: "KRKR 原生模拟器启动失败")
        }
    }

    private fun hasNativeFileAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    private fun showNativeFileAccessDialog() {
        if (isFinishing || isDestroyed) return
        showPrompt(
            KireiPrompt(
                title = "允许 KRKR 读取游戏",
                message = "目录授权用于扫描和管理；KRKR 原生内核还需要“所有文件访问权限”才能直接读取 XP3，并在游戏目录的 savedata 中保存进度。KireiBox 不会修改 XP3。",
                confirmLabel = "前往授权",
                dismissLabel = "稍后",
                onConfirm = {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                runCatching { startActivity(intent) }
                    .recoverCatching { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                }
            )
        )
    }

    private fun finishLaunchFileImport(fileUri: Uri, treeUri: Uri) {
        if (!documentBelongsToTree(fileUri, treeUri)) {
            showMessage("文件夹不匹配", "请选择启动文件实际所在的文件夹。该授权仅用于这个游戏。")
            return
        }
        val name = DocumentFile.fromSingleUri(this, fileUri)?.name.orEmpty()
        viewModel.importLaunchFile(treeUri, name)
    }

    private fun checkPermissions() {
        val roots = com.yuki.yukihub.settings.YukiSettingsStore(this).scanRoots()
        val persisted = contentResolver.persistedUriPermissions.associateBy { it.uri.toString() }
        val details = buildString {
            append("KRKR 原生文件访问：")
            append(if (hasNativeFileAccess()) "正常" else "需要授权")
            append('\n')
            if (roots.isEmpty()) append("尚未添加扫描目录。")
            roots.forEachIndexed { index, root ->
                val grant = persisted[root]
                val doc = runCatching { DocumentFile.fromTreeUri(this@LauncherActivity, Uri.parse(root)) }.getOrNull()
                append("目录 ${index + 1}：")
                append(if (grant?.isReadPermission == true && doc?.canRead() == true) "可读取" else "需要重新授权")
                append(if (grant?.isWritePermission == true && doc?.canWrite() == true) " / 可写入" else " / 只读")
                append('\n')
            }
            append("\nSAF 负责扫描、封面与目录管理；KRKR 原生内核使用物理文件路径，因此需要单独的所有文件访问权限。")
        }
        showMessage("权限检查", details)
    }

    private fun repairPermission(root: String?) {
        pendingPermissionRoot = root
        permissionRepairPicker.launch(root?.let(Uri::parse))
    }

    private fun showAbout() {
        val version = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "unknown" }
        showMessage(
            "KireiBox $version",
            "YukiHub 单底层 + Jetpack Compose UI\n" +
                "Kotlin 2.2.21 · Compose UI 1.7.6 · Haze 1.5.4\n" +
                "数据库 v19 · 四引擎 arm64-v8a\n\n" +
                "完整技术栈占比和优化建议见版本文档。"
        )
    }

    private fun showMessage(title: String, message: String) {
        if (isFinishing || isDestroyed) return
        showPrompt(KireiPrompt(title = title, message = message, confirmLabel = "好"))
    }

    private fun showPrompt(prompt: KireiPrompt) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            launcherPrompt.value = prompt
        } else {
            runOnUiThread { launcherPrompt.value = prompt }
        }
    }

    private fun persist(uri: Uri): Boolean {
        return runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            true
        }.recoverCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            true
        }.getOrDefault(false)
    }

    private fun findPersistedTreeForFile(fileUri: Uri): Uri? = contentResolver.persistedUriPermissions
        .filter { it.isReadPermission }.map { it.uri }.firstOrNull { documentBelongsToTree(fileUri, it) }

    private fun suggestedParentTreeUri(fileUri: Uri): Uri? = runCatching {
        if (!DocumentsContract.isDocumentUri(this, fileUri)) return@runCatching null
        val id = DocumentsContract.getDocumentId(fileUri)
        val volume = id.substringBefore(':')
        val path = id.substringAfter(':', "").substringBeforeLast('/', "")
        DocumentsContract.buildTreeDocumentUri(fileUri.authority.orEmpty(), "$volume:$path")
    }.getOrNull()

    private fun documentBelongsToTree(fileUri: Uri, treeUri: Uri): Boolean = runCatching {
        val fileId = DocumentsContract.getDocumentId(fileUri).replace('\\', '/').trimEnd('/')
        val treeId = DocumentsContract.getTreeDocumentId(treeUri).replace('\\', '/').trimEnd('/')
        fileId == treeId || fileId.startsWith("$treeId/")
    }.getOrDefault(false)

    private fun isSupportedLaunchFile(name: String): Boolean {
        val lower = name.trim().lowercase()
        return lower.endsWith(".xp3") || lower.endsWith(".nsa") || lower.endsWith(".ns2") ||
            lower.endsWith(".txt") || lower == "index.html" || lower.endsWith(".pfs") || lower.endsWith(".iet")
    }

    private fun configureWindow() {
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        applySystemBarTone(isLauncherDarkMode(this))
    }

    private fun configureEdgeToEdge(dark: Boolean) {
        val style = if (dark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    private fun applySystemBarTone(dark: Boolean) {
        configureEdgeToEdge(dark)
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(wrapLauncherUiMode(newBase) ?: newBase)
    }

    companion object {
        const val APP_PREFS = "yukihub_prefs"
        private const val KEY_LAUNCHER_DARK_MODE = "launcher_dark_mode"

        @JvmStatic fun isLauncherDarkMode(context: Context): Boolean = context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE).getBoolean(KEY_LAUNCHER_DARK_MODE, true)
        @JvmStatic fun launcherPrimaryColor(context: Context): Int = Color.rgb(113, 107, 216)
        @JvmStatic fun wrapLauncherUiMode(base: Context?): Context? { if (base == null) return null; val config = Configuration(base.resources.configuration); config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if (isLauncherDarkMode(base)) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO; return base.createConfigurationContext(config) }
    }
}
