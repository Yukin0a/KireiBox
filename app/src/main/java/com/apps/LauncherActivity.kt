package com.apps

import android.Manifest
import android.content.ClipData
import android.content.pm.PackageManager
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
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import com.kireibox.ui.KireiActions
import com.kireibox.ui.BuiltinFileBrowserMode
import com.kireibox.ui.BuiltinFileBrowserRequest
import com.kireibox.ui.KireiBoxApp
import com.kireibox.ui.KireiPrompt
import com.kireibox.ui.KireiPublicCopy
import com.kireibox.ui.KireiViewModel
import com.kireibox.ui.SystemBarVisibilityPolicy
import com.kireibox.ui.LauncherMemoryCoordinator
import com.kireibox.shortcut.DesktopGameShortcutFactory
import com.kireibox.external.shortcut.ExternalShortcutIdentity
import com.kireibox.external.shortcut.ExternalShortcutLaunchPolicy
import com.kireibox.external.shortcut.ShizukuShortcutGateway
import com.kireibox.external.shortcut.ShortcutServiceStatus
import com.kireibox.update.UpdateInstaller
import com.yuki.yukihub.R
import com.yuki.yukihub.data.GameRepository
import com.yuki.yukihub.engine.EngineStorageProbe
import com.yuki.yukihub.util.DevLogger
import com.yuki.yukihub.launcher.EmulatorLauncher
import com.yuki.yukihub.launcherbridge.LauncherGameLaunchBridge
import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.model.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import rikka.shizuku.Shizuku

/** KireiBox Compose shell. YukiHub repositories are the only runtime data source. */
class LauncherActivity : AppCompatActivity() {
    private lateinit var viewModel: KireiViewModel
    private var pendingLaunchFile: Uri? = null
    private var pendingPermissionRoot: String? = null
    private var runningSessionId = -1L
    private var engineWasOpened = false
    private var runningSessionJournaled = false
    private var launcherHandoffActive = false
    private var pendingNativeLaunch: Game? = null
    private val coverAlbumPicker = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(viewModel::importLocalCover) }
    private val coverFilePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importLocalCover) }
    private var pendingNativeShell = false
    private var pendingShizukuLaunch: Game? = null
    private val launcherPrompt = androidx.compose.runtime.mutableStateOf<KireiPrompt?>(null)
    private val fileBrowserRequest = androidx.compose.runtime.mutableStateOf<BuiltinFileBrowserRequest?>(null)
    private var pendingBrowserMode: BuiltinFileBrowserMode? = null
    private var pendingUpdateApkPath: String? = null
    private val updateInstaller by lazy { UpdateInstaller(this) }
    private val shortcutGateway by lazy { ShizukuShortcutGateway(this) }
    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode != ShizukuShortcutGateway.PERMISSION_REQUEST_CODE) return@OnRequestPermissionResultListener
        val granted = grantResult == PackageManager.PERMISSION_GRANTED
        viewModel.onShizukuPermissionResult(granted)
        val pending = pendingShizukuLaunch
        pendingShizukuLaunch = null
        if (granted && pending != null) window.decorView.post { launchGame(pending) }
    }
    private val shizukuBinderReceivedListener = Shizuku.OnBinderReceivedListener {
        if (::viewModel.isInitialized) viewModel.refreshShortcutServiceStatus()
    }
    private val shizukuBinderDeadListener = Shizuku.OnBinderDeadListener {
        shortcutGateway.release()
        if (::viewModel.isInitialized) viewModel.refreshShortcutServiceStatus()
    }

    private val krkrStoragePermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants[Manifest.permission.WRITE_EXTERNAL_STORAGE] != true) {
                // Denied: guide the user to the system details page. Granting
                // resumes the pending launch from onResume.
                showNativeFileAccessDialog()
            }
        }

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@registerForActivityResult
        if (persist(uri)) viewModel.addScanRoot(uri)
    }

    private val updateInstallLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != android.app.Activity.RESULT_OK && !isFinishing && !isDestroyed) {
                showMessage("安装未完成", "系统安装器未完成覆盖安装；更新文件仍已校验，可稍后再次安装。")
            }
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
            viewModel.replaceScanRoot(old, uri)
            if (!old.isNullOrBlank()) showMessage("权限已更新", "目录授权已重新建立，游戏记录和封面不会删除。")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_YukiHub_Launcher)
        configureEdgeToEdge(isLauncherDarkMode(this))
        super.onCreate(savedInstanceState)
        configureWindow()
        viewModel = ViewModelProvider(this)[KireiViewModel::class.java]
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        Shizuku.addBinderReceivedListenerSticky(shizukuBinderReceivedListener)
        Shizuku.addBinderDeadListener(shizukuBinderDeadListener)
        setContent {
            KireiBoxApp(
                viewModel,
                KireiActions(
                    chooseDirectory = { chooseStorage(BuiltinFileBrowserMode.DIRECTORY) },
                    chooseLaunchFile = { chooseStorage(BuiltinFileBrowserMode.LAUNCH_FILE) },
                    chooseAndroidApp = viewModel::openAndroidAppPicker,
                    chooseExternalShortcuts = viewModel::openExternalShortcutPicker,
                    chooseCoverFromAlbum = {
                        coverAlbumPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    chooseCoverFromFiles = { coverFilePicker.launch(arrayOf("image/*")) },
                    launchGame = ::launchGame,
                    pinGameShortcut = ::pinGameShortcut,
                    selectedGame = viewModel::selectedGame,
                    launchKrkrNative = ::launchKrkrNative,
                    checkPermissions = ::checkPermissions,
                    repairPermission = ::repairPermission,
                    copyLogs = ::copyDiagnosticLogs,
                    exportLogs = ::exportDiagnosticLogs,
                    setDarkMode = { dark ->
                        viewModel.setDarkMode(dark)
                        applySystemBarTone(dark)
                    },
                    showAbout = ::showAbout,
                    installUpdate = ::installUpdate,
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
                },
                onSystemBarIconsLight = ::applyStatusBarTone
            )
        }
        applySystemBars()
        consumeDesktopGameIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeDesktopGameIntent(intent)
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        Shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener)
        Shizuku.removeBinderDeadListener(shizukuBinderDeadListener)
        shortcutGateway.release()
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        LauncherMemoryCoordinator.onTrimMemory(level)
    }

    override fun onResume() {
        super.onResume()
        applySystemBars()
        viewModel.refreshShortcutServiceStatus()
        if (launcherHandoffActive) {
            launcherHandoffActive = false
            viewModel.resumeAfterEngineHandoff()
        }
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
        val updatePath = pendingUpdateApkPath
        if (updatePath != null && updateInstaller.canRequestPackageInstalls()) {
            pendingUpdateApkPath = null
            window.decorView.post { installUpdate(updatePath) }
        }
        viewModel.onAppForeground()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applySystemBars()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applySystemBars()
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
                message = buildString {
                    append("Android 11 及以上需要“所有文件访问权限”才能使用内置浏览器。授权后会自动继续刚才的操作；KireiBox 本版本只浏览和选择，不会复制、移动、重命名或删除文件。")
                    if (EngineStorageProbe.isHuaweiFamily()) {
                        append("\n\n华为/HarmonyOS 路径：设置 → 应用 → KireiBox → 权限管理 → 存储 → 允许；HarmonyOS 3 及以上还需开启“所有文件访问”。")
                    }
                },
                confirmLabel = "前往授权",
                dismissLabel = "使用系统 SAF",
                onConfirm = {
                    val manageApp = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    runCatching { startActivity(manageApp) }
                        .recoverCatching { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                        .recoverCatching {
                            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:$packageName")
                            })
                        }
                },
                onDismiss = {
                    pendingBrowserMode = null
                    openSystemPicker(mode)
                }
            )
        )
    }

    private fun launchGame(game: Game) {
        if (ExternalShortcutLaunchPolicy.requiresShizuku(game)) {
            when (shortcutGateway.status()) {
                ShortcutServiceStatus.READY -> Unit
                ShortcutServiceStatus.PERMISSION_REQUIRED -> {
                    pendingShizukuLaunch = game
                    shortcutGateway.requestPermission().onFailure {
                        pendingShizukuLaunch = null
                        showMessage("无法授权 Shizuku", it.message ?: "Shizuku 权限请求失败。")
                    }
                    return
                }
                else -> {
                    val message = shortcutGateway.requestPermission().exceptionOrNull()?.message
                        ?: "Shizuku 快捷方式服务不可用。"
                    showMessage("无法启动快捷方式", message)
                    return
                }
            }
        }
        if (game.engine == EngineType.KIRIKIRI && !hasNativeFileAccess()) {
            DevLogger.event("krkr launch gate: native file access missing -> prompt")
            pendingNativeLaunch = game
            requestNativeFileAccess()
            return
        }
        launchPreparedGame(game)
    }

    private fun consumeDesktopGameIntent(source: Intent?) {
        if (source?.action != DesktopGameShortcutFactory.ACTION_LAUNCH_GAME) return
        val gameId = source.getLongExtra(DesktopGameShortcutFactory.EXTRA_GAME_ID, -1L)
        source.action = Intent.ACTION_MAIN
        source.removeExtra(DesktopGameShortcutFactory.EXTRA_GAME_ID)
        if (gameId <= 0L) {
            showMessage("快捷方式已失效", "桌面快捷方式没有有效的游戏 ID。")
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val game = GameRepository(applicationContext).findById(gameId)
            withContext(Dispatchers.Main) {
                when {
                    game == null -> showMessage("快捷方式已失效", "游戏已不在 KireiBox 游戏库中。")
                    game.hidden -> showMessage("快捷方式已失效", "该游戏已从 KireiBox 游戏库隐藏。")
                    else -> launchGame(game)
                }
            }
        }
    }

    private fun pinGameShortcut(game: Game) {
        val result = DesktopGameShortcutFactory.requestPin(this, game)
        if (result.isFailure) {
            showMessage("无法添加到桌面", result.exceptionOrNull()?.message ?: "桌面快捷方式创建失败。")
        } else if (result.getOrNull() == DesktopGameShortcutFactory.PinOutcome.UPDATED) {
            viewModel.showNotice("已更新桌面快捷方式")
        } else {
            viewModel.showNotice("请在系统弹窗中确认添加到桌面")
        }
    }

    private fun launchPreparedGame(game: Game) {
        LauncherGameLaunchBridge.launchAsync(this, game, object : LauncherGameLaunchBridge.LaunchCallback {
            override fun onResult(result: LauncherGameLaunchBridge.LaunchResult) {
                if (result.success) {
                    runningSessionId = if (result.handoff == LauncherGameLaunchBridge.LaunchHandoff.INTERNAL_ENGINE) result.sessionId else -1L
                    engineWasOpened = result.handoff == LauncherGameLaunchBridge.LaunchHandoff.INTERNAL_ENGINE
                    runningSessionJournaled = engineWasOpened
                    launcherHandoffActive = result.handoff != LauncherGameLaunchBridge.LaunchHandoff.NONE
                    viewModel.suspendForEngineHandoff()
                    LauncherMemoryCoordinator.onEngineHandoff()
                    viewModel.reload()
                } else if (!hasNativeFileAccess() && isPermissionLaunchFailure(result.message)) {
                    DevLogger.event("launch permission failure routed to grant dialog: ${result.message}")
                    pendingNativeLaunch = game
                    showNativeFileAccessDialog(hint = result.message)
                } else {
                    showMessage("无法启动", result.message)
                }
            }
        })
    }

    private fun launchKrkrNative() {
        if (!hasNativeFileAccess()) {
            pendingNativeShell = true
            requestNativeFileAccess()
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

    /** Permission guide text; Huawei/HarmonyOS gets its own path (权限管理 → 存储). */
    private fun storagePermissionGuide(android11Plus: Boolean): String =
        if (EngineStorageProbe.isHuaweiFamily()) {
            buildString {
                append("华为/HarmonyOS 路径：设置 → 应用 → KireiBox → 权限管理 → 存储 → 允许。")
                if (android11Plus) append("HarmonyOS 3 及以上还需开启“所有文件访问”。")
                append("已开启仍无法写入，请把游戏目录移到 Download 等常规位置后重试。")
            }
        } else if (android11Plus) {
            "Android 11 及以上需要“所有文件访问权限”：设置 → 应用 → KireiBox → 权限 → 所有文件访问 → 允许。授权后返回会自动继续；仍无法写入请把游戏目录移到内部存储常规位置后重试。"
        } else {
            "Android 10 及以下请授予 KireiBox“存储”权限：设置 → 应用 → KireiBox → 权限 → 存储 → 允许。已开启仍无法写入，请把游戏目录移到 Download 等常规位置后重试。"
        }

    private fun isPermissionLaunchFailure(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        return message.contains("EACCES", ignoreCase = true) ||
            message.contains("Permission denied", ignoreCase = true) ||
            message.contains("所有文件访问", ignoreCase = true) ||
            message.contains("无法创建 savedata", ignoreCase = true) ||
            message.contains("savedata 无法写入", ignoreCase = true) ||
            message.contains("只读目录", ignoreCase = true) ||
            message.contains("写入检查失败", ignoreCase = true)
    }

    /**
     * Android 11+ requires MANAGE_EXTERNAL_STORAGE (all-files access) because
     * scoped storage is mandatory. Android 6-10 use the legacy
     * WRITE_EXTERNAL_STORAGE runtime permission: with targetSdk 29 and
     * requestLegacyExternalStorage it grants full physical access, which is
     * what the KRKR native engine needs to write <game>/savedata.
     */
    private fun hasNativeFileAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        }

    /** Requests the permission model matching this Android version; onResume continues the pending action. */
    private fun requestNativeFileAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            showNativeFileAccessDialog()
        } else {
            krkrStoragePermission.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ),
            )
        }
    }

    private fun showNativeFileAccessDialog(hint: String? = null) {
        if (isFinishing || isDestroyed) return
        val android11Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val permissionName = if (android11Plus) "所有文件访问" else "存储"
        showPrompt(
            KireiPrompt(
                title = "允许 KRKR 读取游戏",
                message = buildString {
                    append("目录授权用于扫描和管理；KRKR 原生内核还需要“")
                    append(permissionName)
                    append("”权限才能直接读取 XP3，并在游戏目录的 savedata 中保存进度。KireiBox 不会修改 XP3。")
                    if (!hint.isNullOrBlank()) {
                        append("\n\n上次失败原因：")
                        append(hint)
                    }
                    append("\n\n")
                    append(storagePermissionGuide(android11Plus))
                },
                confirmLabel = "前往授权",
                dismissLabel = "稍后",
                onConfirm = {
                    val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    if (android11Plus) {
                        val manageApp = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        runCatching { startActivity(manageApp) }
                            .recoverCatching { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                            .recoverCatching { startActivity(details) }
                            .onFailure { showMessage("无法打开设置", "请在系统设置中手动允许 KireiBox 的“所有文件访问”后再试。") }
                    } else {
                        runCatching { startActivity(details) }
                            .onFailure { showMessage("无法打开设置", "请在系统设置中手动允许 KireiBox 的“存储”权限后再试。") }
                    }
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
            append("\nSAF 负责扫描、封面与目录管理；KRKR 原生内核使用物理文件路径。\n")
            append(storagePermissionGuide(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R))
        }
        showMessage("权限检查", details)
    }

    private fun repairPermission(root: String?) {
        pendingPermissionRoot = root
        permissionRepairPicker.launch(root?.let(Uri::parse))
    }

    private fun showAbout() {
        showMessage(
            "关于 KireiBox",
            KireiPublicCopy.aboutText,
        )
    }

    private fun installUpdate(apkPath: String) {
        val updateRoot = runCatching { File(cacheDir, "updates").canonicalFile }.getOrNull()
        val apk = runCatching { File(apkPath).canonicalFile }.getOrNull()
        if (updateRoot == null || apk == null || !apk.isFile || apk.parentFile != updateRoot) {
            showMessage("无法安装更新", "更新文件位置无效，请重新下载。")
            return
        }
        if (!updateInstaller.canRequestPackageInstalls()) {
            pendingUpdateApkPath = apk.absolutePath
            runCatching { startActivity(updateInstaller.unknownSourcesSettingsIntent()) }
                .onFailure {
                    pendingUpdateApkPath = null
                    showMessage("无法打开授权页面", it.message ?: "请在系统设置中允许 KireiBox 安装未知应用。")
                }
            return
        }
        pendingUpdateApkPath = null
        runCatching { updateInstallLauncher.launch(updateInstaller.installIntent(apk)) }
            .onFailure { showMessage("无法启动安装器", it.message ?: "系统安装器不可用。") }
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

    /**
     * Alpha 8 V3 system-bar contract: only non-Pad phone landscape hides the status bar.
     * The transient top-swipe reveal never triggers a layout remeasure, and the navigation
     * bar stays visible in every mode. Reapplied on resume and configuration changes so a
     * rotation or split-screen resize restores the bar immediately.
     */
    private fun applySystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        val config = resources.configuration
        val visibility = SystemBarVisibilityPolicy.visibilitySpec(
            widthDp = config.screenWidthDp.toFloat(),
            heightDp = config.screenHeightDp.toFloat(),
            smallestScreenWidthDp = config.smallestScreenWidthDp,
            screenLayoutSize = config.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK,
            orientation = config.orientation,
        )
        if (visibility.hideStatusBar) {
            controller.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
        if (visibility.hideNavigationBar) {
            controller.hide(WindowInsetsCompat.Type.navigationBars())
        } else {
            controller.show(WindowInsetsCompat.Type.navigationBars())
        }
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

    /**
     * Applies the system-bar icon tone requested by the composed page. [lightIcons] is
     * true when the page background is dark and icons must be white (light); it maps to
     * [WindowInsetsControllerCompat.isAppearanceLightStatusBars] == false, never inverted.
     * The immersive home always requests light icons because its backdrop is dark in both
     * themes; the initial theme tone is applied before composition and this corrects it
     * on the first frame, on theme changes and on configuration changes.
     */
    private fun applyStatusBarTone(lightIcons: Boolean) {
        val style = if (lightIcons) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !lightIcons
            isAppearanceLightNavigationBars = !lightIcons
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
