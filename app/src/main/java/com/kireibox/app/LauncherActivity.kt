package com.kireibox.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModelProvider
import com.kireibox.app.launcher.applySystemBars
import com.kireibox.app.launcher.configureEdgeToEdge
import com.kireibox.app.launcher.configureWindow
import com.kireibox.app.launcher.consumeDesktopGameIntent
import com.kireibox.app.launcher.initializeLauncher
import com.kireibox.app.launcher.launchGame
import com.kireibox.app.launcher.onLauncherResume
import com.kireibox.app.launcher.persist
import com.kireibox.app.launcher.routeAddGamePick
import com.kireibox.app.launcher.showMessage
import com.kireibox.app.launcher.showNativeFileAccessDialog
import com.kireibox.designsystem.KireiThemeResolver
import com.kireibox.external.shortcut.ShizukuShortcutGateway
import com.kireibox.ui.AddGamePick
import com.kireibox.ui.BuiltinFileBrowserRequest
import com.kireibox.ui.KireiPrompt
import com.kireibox.ui.KireiViewModel
import com.kireibox.ui.LauncherMemoryCoordinator
import com.kireibox.ui.storedLauncherOrientation
import com.kireibox.update.UpdateInstaller
import com.yuki.yukihub.R
import com.yuki.yukihub.launcherbridge.ExternalPlaySessionTimer
import com.yuki.yukihub.model.Game
import com.yuki.yukihub.settings.AppearanceSettings
import com.yuki.yukihub.settings.ThemeMode
import com.yuki.yukihub.settings.ThemeModePolicy
import rikka.shizuku.Shizuku

/** KireiBox Compose shell. YukiHub repositories are the only runtime data source. */
class LauncherActivity : AppCompatActivity() {
    internal lateinit var viewModel: KireiViewModel
    internal var pendingPermissionRoot: String? = null
    internal var runningSessionId = -1L
    internal var engineWasOpened = false
    internal var runningSessionJournaled = false
    internal var launcherHandoffActive = false
    internal var externalPlayActive = false
    internal var externalPlayTimer: ExternalPlaySessionTimer? = null
    internal var pendingNativeLaunch: Game? = null
    internal val coverAlbumPicker =
        registerForActivityResult(
            ActivityResultContracts.PickVisualMedia(),
        ) { uri -> uri?.let(viewModel::importLocalCover) }
    internal val coverFilePicker =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri -> uri?.let(viewModel::importLocalCover) }
    internal var pendingNativeShell = false
    internal var pendingShizukuLaunch: Game? = null
    internal val launcherPrompt = androidx.compose.runtime.mutableStateOf<KireiPrompt?>(null)
    internal val fileBrowserRequest = androidx.compose.runtime.mutableStateOf<BuiltinFileBrowserRequest?>(null)
    internal var pendingBrowserOpen = false
    internal var pendingBrowserPick: AddGamePick? = null
    internal var pendingAddPick: AddGamePick? = null
    internal var pendingUpdateApkPath: String? = null
    internal val updateInstaller by lazy { UpdateInstaller(this) }
    internal val shortcutGateway by lazy { ShizukuShortcutGateway(this) }
    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode != ShizukuShortcutGateway.PERMISSION_REQUEST_CODE) return@OnRequestPermissionResultListener
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            viewModel.onShizukuPermissionResult(granted)
            val pending = pendingShizukuLaunch
            pendingShizukuLaunch = null
            if (granted && pending != null) window.decorView.post { launchGame(pending) }
        }
    private val shizukuBinderReceivedListener =
        Shizuku.OnBinderReceivedListener {
            if (::viewModel.isInitialized) viewModel.refreshShortcutServiceStatus()
        }
    private val shizukuBinderDeadListener =
        Shizuku.OnBinderDeadListener {
            shortcutGateway.release()
            if (::viewModel.isInitialized) viewModel.refreshShortcutServiceStatus()
        }

    internal val krkrStoragePermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants[Manifest.permission.WRITE_EXTERNAL_STORAGE] != true) {
                // Denied: guide the user to the system details page. Granting
                // resumes the pending launch from onResume.
                showNativeFileAccessDialog()
            }
        }

    internal val folderPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null) return@registerForActivityResult
            if (persist(uri)) viewModel.addScanRoot(uri)
        }

    internal val addGameFolderPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            val pick = pendingAddPick
            pendingAddPick = null
            if (uri != null && pick != null && persist(uri)) routeAddGamePick(pick, uri)
        }

    internal val updateInstallLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != android.app.Activity.RESULT_OK && !isFinishing && !isDestroyed) {
                showMessage("安装未完成", "系统安装器未完成覆盖安装；更新文件仍已校验，可稍后再次安装。")
            }
        }

    internal val permissionRepairPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
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
        requestedOrientation = storedLauncherOrientation(this)
        configureEdgeToEdge(isLauncherDarkMode(this))
        super.onCreate(savedInstanceState)
        configureWindow()
        viewModel = ViewModelProvider(this)[KireiViewModel::class.java]
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        Shizuku.addBinderReceivedListenerSticky(shizukuBinderReceivedListener)
        Shizuku.addBinderDeadListener(shizukuBinderDeadListener)
        initializeLauncher()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeDesktopGameIntent(intent)
    }

    override fun onDestroy() {
        externalPlayTimer?.cleanup()
        externalPlayTimer = null
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
        onLauncherResume()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applySystemBars()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applySystemBars()
    }

    override fun attachBaseContext(newBase: Context) {
        // In-app theme switches only recompose (no recreate), so AppCompat's process night mode can
        // lag behind the stored theme. Re-sync it before AppCompat applies it to this new instance;
        // no LauncherActivity delegate is active yet, so this cannot recreate itself.
        syncDefaultNightMode(newBase)
        super.attachBaseContext(wrapLauncherUiMode(newBase) ?: newBase)
    }

    companion object {
        const val APP_PREFS = "yukihub_prefs"

        /** Stored theme mode (theme key, else the legacy dark boolean, else SYSTEM). */
        @JvmStatic fun launcherThemeMode(context: Context): ThemeMode = AppearanceSettings.readThemeMode(context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE))

        /** Resolved launcher theme; SYSTEM follows the device night state, not this activity's override. */
        @JvmStatic fun isLauncherDarkMode(context: Context): Boolean = ThemeModePolicy.isDark(launcherThemeMode(context), KireiThemeResolver.systemNight())

        /** Aligns AppCompat's process-wide night mode with the stored theme mode (no-op when equal). */
        @JvmStatic fun syncDefaultNightMode(context: Context) {
            val mode = KireiThemeResolver.appCompatNightMode(launcherThemeMode(context))
            if (AppCompatDelegate.getDefaultNightMode() != mode) AppCompatDelegate.setDefaultNightMode(mode)
        }

        @JvmStatic fun wrapLauncherUiMode(base: Context?): Context? {
            if (base == null) return null
            val config = Configuration(base.resources.configuration)
            config.uiMode = KireiThemeResolver.withNight(config.uiMode, isLauncherDarkMode(base))
            return base.createConfigurationContext(config)
        }
    }
}
