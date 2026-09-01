package com.kireibox.external.shortcut

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.Build
import android.os.Process
import com.yuki.yukihub.BuildConfig
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import rikka.shizuku.Shizuku

class ShizukuShortcutGateway(context: Context) {
    private val app = context.applicationContext
    private val bindLock = Any()
    @Volatile private var activeConnection: ServiceConnection? = null
    @Volatile private var activeService: IShortcutShellService? = null
    @Volatile private var binding = false
    @Volatile private var connectionLost = false

    fun status(): ShortcutServiceStatus {
        val installed = isManagerInstalled()
        val running = installed && runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        return ShortcutServiceStatusPolicy.resolve(
            installed = installed,
            running = running,
            supportedVersion = running && runCatching {
                !Shizuku.isPreV11() && Shizuku.getVersion() >= MIN_SHIZUKU_API
            }.getOrDefault(false),
            permissionGranted = running && runCatching {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false),
            rationale = running && runCatching { Shizuku.shouldShowRequestPermissionRationale() }.getOrDefault(false),
            uid = if (running) runCatching { Shizuku.getUid() }.getOrDefault(-1) else -1,
            binding = binding,
            connectionLost = connectionLost,
        )
    }

    fun requestPermission(requestCode: Int = PERMISSION_REQUEST_CODE): Result<Unit> = runCatching {
        when (status()) {
            ShortcutServiceStatus.READY -> Unit
            ShortcutServiceStatus.PERMISSION_REQUIRED -> Shizuku.requestPermission(requestCode)
            ShortcutServiceStatus.PERMISSION_DENIED -> error("Shizuku 已拒绝 KireiBox，请在 Shizuku 的授权应用中重新允许。")
            ShortcutServiceStatus.NOT_INSTALLED -> error("请先安装 Shizuku。")
            ShortcutServiceStatus.NOT_RUNNING -> error("请先在 Shizuku 中完成无线调试配对并启动服务。")
            ShortcutServiceStatus.UNSUPPORTED_VERSION -> error("需要 Shizuku 13.1.5 或更新版本。")
            ShortcutServiceStatus.UNSUPPORTED_IDENTITY -> error("快捷方式功能只支持无线调试启动的 Shizuku（shell 模式）。")
            else -> error("Shizuku 快捷方式服务暂不可用。")
        }
    }

    fun bind(): Result<Unit> = runCatching {
        ensureReady()
        check(service().canReadPinnedShortcuts(currentUserId())) {
            "当前系统没有授予 shell 读取桌面快捷方式的权限。"
        }
    }

    fun scan(userId: Int = currentUserId()): Result<List<ExternalShortcutDescriptor>> = withRemote { remote ->
        if (!remote.canReadPinnedShortcuts(userId)) {
            error("当前系统没有授予 shell 读取桌面快捷方式的权限。")
        }
        val values = remote.scan(userId).mapNotNull { bundle ->
            val packageName = bundle.getString(ShortcutShellUserService.KEY_PACKAGE)?.trim().orEmpty()
            val shortcutId = bundle.getString(ShortcutShellUserService.KEY_ID).orEmpty()
            if (packageName.isEmpty() || shortcutId.isEmpty()) return@mapNotNull null
            ExternalShortcutDescriptor(
                userId = bundle.getInt(ShortcutShellUserService.KEY_USER_ID, userId),
                packageName = packageName,
                appName = bundle.getString(ShortcutShellUserService.KEY_APP_NAME)?.trim().orEmpty()
                    .ifEmpty { packageName },
                shortcutId = shortcutId,
                title = bundle.getString(ShortcutShellUserService.KEY_TITLE)?.trim().orEmpty()
                    .ifEmpty { shortcutId },
                activityName = bundle.getString(ShortcutShellUserService.KEY_ACTIVITY)?.trim()
                    ?.takeIf(String::isNotEmpty),
                pinned = bundle.getBoolean(ShortcutShellUserService.KEY_PINNED),
                dynamic = bundle.getBoolean(ShortcutShellUserService.KEY_DYNAMIC),
                directLaunchTarget = bundle.getString(ShortcutShellUserService.KEY_DIRECT_LAUNCH_TARGET)
                    ?.trim()?.takeIf(String::isNotEmpty),
            )
        }
        ExternalShortcutPolicy.descriptors(values, app.packageName)
    }

    fun exists(identity: ExternalShortcutIdentity.Parsed): Boolean = withRemote { remote ->
        remote.canReadPinnedShortcuts(identity.userId) &&
            remote.exists(identity.userId, identity.packageName, identity.shortcutId)
    }.getOrDefault(false)

    fun launch(identity: ExternalShortcutIdentity.Parsed): Result<Unit> = withRemote { remote ->
        if (!remote.canReadPinnedShortcuts(identity.userId)) {
            error("当前系统没有授予 shell 启动桌面快捷方式的权限。")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val launchIntent = remote.launchIntent(
                identity.userId,
                identity.packageName,
                identity.shortcutId,
            ) ?: error("快捷方式已失效，请重新扫描。")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val options = ActivityOptions.makeBasic().apply {
                    pendingIntentBackgroundActivityStartMode =
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                }.toBundle()
                launchIntent.send(app, 0, null, null, null, null, options)
            } else {
                launchIntent.send()
            }
        } else {
            check(remote.launch(identity.userId, identity.packageName, identity.shortcutId)) {
                "快捷方式已失效，请重新扫描。"
            }
        }
    }.recoverCatching { error ->
        if (error is PendingIntent.CanceledException) {
            throw IllegalStateException("快捷方式已失效，请重新扫描。", error)
        }
        throw error
    }

    fun release() {
        val connection = synchronized(bindLock) {
            val value = activeConnection
            activeConnection = null
            activeService = null
            connectionLost = false
            binding = false
            value
        } ?: return
        runCatching { Shizuku.unbindUserService(SERVICE_ARGS, connection, true) }
    }

    private fun <T> withRemote(block: (IShortcutShellService) -> T): Result<T> = runCatching {
        ensureReady()
        block(service())
    }.also { release() }

    private fun service(): IShortcutShellService {
        activeService?.let { if (it.asBinder().pingBinder()) return it }
        synchronized(bindLock) {
            activeService?.let { if (it.asBinder().pingBinder()) return it }
            val latch = CountDownLatch(1)
            var failure: Throwable? = null
            binding = true
            connectionLost = false
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    activeService = binder?.let(IShortcutShellService.Stub::asInterface)
                    binding = false
                    latch.countDown()
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    activeService = null
                    binding = false
                    connectionLost = true
                    failure = IllegalStateException("Shizuku 快捷方式连接已断开。")
                    latch.countDown()
                }
            }
            activeConnection = connection
            Shizuku.bindUserService(SERVICE_ARGS, connection)
            if (!latch.await(BIND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                binding = false
                release()
                error("连接 Shizuku 超时。")
            }
            failure?.let { throw it }
            return activeService ?: error("Shizuku 未返回快捷方式服务。")
        }
    }

    private fun ensureReady() {
        when (status()) {
            ShortcutServiceStatus.READY -> Unit
            ShortcutServiceStatus.NOT_INSTALLED -> error("请先安装 Shizuku。")
            ShortcutServiceStatus.NOT_RUNNING -> error("Shizuku 未运行，请先完成无线调试配对。")
            ShortcutServiceStatus.PERMISSION_REQUIRED -> error("需要先授权 KireiBox 使用 Shizuku。")
            ShortcutServiceStatus.PERMISSION_DENIED -> error("Shizuku 权限已被拒绝。")
            ShortcutServiceStatus.UNSUPPORTED_VERSION -> error("需要 Shizuku 13.1.5 或更新版本。")
            ShortcutServiceStatus.UNSUPPORTED_IDENTITY -> error("只支持 Shizuku shell 模式，不支持 Root/Sui 模式。")
            ShortcutServiceStatus.SYSTEM_PERMISSION_MISSING -> error("系统未向 shell 开放快捷方式权限。")
            ShortcutServiceStatus.BINDING -> error("正在连接 Shizuku。")
            ShortcutServiceStatus.CONNECTION_LOST -> error("Shizuku 连接已失效。")
        }
    }

    private fun isManagerInstalled(): Boolean = runCatching {
        app.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
    }.isSuccess

    private fun currentUserId(): Int = android.os.UserHandle.getUserHandleForUid(Process.myUid()).hashCode()

    companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        const val PERMISSION_REQUEST_CODE = 0x4b53
        private const val MIN_SHIZUKU_API = 13
        private const val BIND_TIMEOUT_SECONDS = 10L
        private val SERVICE_ARGS = Shizuku.UserServiceArgs(
            ComponentName(BuildConfig.APPLICATION_ID, ShortcutShellUserService::class.java.name),
        )
            .daemon(false)
            .processNameSuffix("shortcut_shell")
            .tag("kireibox-shortcut-shell-v1")
            .version(BuildConfig.VERSION_CODE)
            .debuggable(BuildConfig.DEBUG)
    }
}
