package com.yuki.yukihub.tyrano

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.yuki.yukihub.engine.EngineStorageProbe
import org.tvp.kirikiri2.EnginePlaySessionJournal
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** Tyrano WebView 宿主；资源服务与存档沙箱分别由独立组件负责。 */
class TyranoActivity : Activity() {
    private var webView: WebView? = null
    private var gameDir: String? = null
    private var gameRootFile: File? = null
    private var saveDirectory: File? = null
    private var gameUsesAsar = false
    private var asarPath: String? = null
    private var asarArchive: AsarArchive? = null
    private var firstResume = true
    private var localServer: TyranoLocalHttpServer? = null
    private val processExitScheduled = AtomicBoolean(false)
    private var playSessionJournal: EnginePlaySessionJournal? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playSessionJournal = EnginePlaySessionJournal.attach(this, intent)
        if (intent.getBooleanExtra(EXTRA_KEEP_SCREEN_ON, true)) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (intent.getBooleanExtra(EXTRA_IGNORE_CUTOUT, false) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes =
                window.attributes.apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
        }
        requestedOrientation = intent.getIntExtra(EXTRA_ORIENTATION, ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
        enterFullscreen()

        gameDir = resolveGameDir(intent)
        Log.i(TAG, "onCreate gameDir=$gameDir")
        val resolvedGameDir = gameDir
        if (resolvedGameDir.isNullOrBlank()) {
            failLaunch("Tyrano 启动失败：游戏目录为空")
            return
        }

        val gameRoot = File(resolvedGameDir)
        gameRootFile = gameRoot
        val saves = resolveSaveDirectory(intent, gameRoot)
        saveDirectory = saves
        val probe = EngineStorageProbe.probeDirectory(saves)
        if (!probe.ok) {
            Log.e(TAG, "save probe failed: ${probe.logMessage()}")
            failLaunch("Tyrano 启动失败：${probe.userMessage()}")
            return
        }
        Log.i(TAG, "save directory=${saves!!.absolutePath} scoped=${intent.getBooleanExtra(EXTRA_SCOPED_SAVE_DIR, false)}")

        val rootAsar = File(gameRoot, "app.asar")
        val resourcesAsar = File(File(gameRoot, "resources"), "app.asar")
        val index = File(gameRoot, "index.html")
        when {
            rootAsar.isFile -> {
                gameUsesAsar = true
                asarPath = rootAsar.absolutePath
            }
            resourcesAsar.isFile -> {
                gameUsesAsar = true
                asarPath = resourcesAsar.absolutePath
            }
            !index.isFile -> {
                Log.e(TAG, "entry not found index=${index.absolutePath} app.asar=${rootAsar.absolutePath} resources/app.asar=${resourcesAsar.absolutePath}")
                failLaunch("Tyrano 启动失败：未找到 index.html 或 app.asar")
                return
            }
        }

        if (gameUsesAsar) {
            try {
                asarArchive = AsarArchive(File(requireNotNull(asarPath)))
            } catch (error: Throwable) {
                Log.e(TAG, "open asar failed", error)
                failLaunch("Tyrano 启动失败：无法读取 app.asar")
                return
            }
        }
        Log.i(TAG, "entry mode=${if (gameUsesAsar) "asar" else "dir"} asar=$asarPath")

        try {
            val hook = assets.open(TYRANO_HOOK_ASSET).buffered().use { it.readBytes() }
            Log.i(TAG, "asset loaded $TYRANO_HOOK_ASSET bytes=${hook.size}")
            localServer =
                if (gameUsesAsar) {
                    TyranoLocalHttpServer(gameRoot, asarArchive, hook)
                } else {
                    TyranoLocalHttpServer(gameRoot, hook)
                }.also { it.start() }
        } catch (error: Throwable) {
            Log.e(TAG, "start local server failed", error)
            failLaunch("Tyrano 启动失败：本地服务器启动失败")
            return
        }

        val root =
            FrameLayout(this).apply {
                setBackgroundColor(Color.BLACK)
                layoutParams = ViewGroup.LayoutParams(-1, -1)
            }
        val browser =
            WebView(this).apply {
                layoutParams = ViewGroup.LayoutParams(-1, -1)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) defaultFocusHighlightEnabled = false
            }
        webView = browser
        root.addView(browser)
        setContentView(root)

        configureWebView(browser)
        browser.addJavascriptInterface(TyranoJsBridge(saves), JS_BRIDGE_NAME)
        val url = "http://localhost:${requireNotNull(localServer).port}/index.html"
        Log.i(TAG, "loadUrl=$url")
        browser.loadUrl(url)
    }

    private fun failLaunch(message: String) {
        Log.e(TAG, message)
        if (isFinishing || isDestroyed) return
        val density = resources.displayMetrics.density
        val root =
            FrameLayout(this).apply {
                setBackgroundColor(Color.BLACK)
                layoutParams = ViewGroup.LayoutParams(-1, -1)
            }
        val messageText =
            TextView(this).apply {
                setText(message)
                setTextColor(Color.WHITE)
                textSize = 14f
                setLineSpacing(0f, 1.15f)
                setPadding(
                    (24 * density).toInt(),
                    (24 * density).toInt(),
                    (24 * density).toInt(),
                    (12 * density).toInt(),
                )
            }
        val close =
            TextView(this).apply {
                text = "返回"
                setTextColor(Color.rgb(120, 170, 255))
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(0, (12 * density).toInt(), 0, (24 * density).toInt())
                setOnClickListener { finish() }
            }
        val column =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(messageText)
                addView(close)
            }
        root.addView(column)
        setContentView(root)
    }

    private fun configureWebView(browser: WebView) {
        browser.isHorizontalScrollBarEnabled = false
        browser.isVerticalScrollBarEnabled = false
        runCatching { browser.clearCache(true) }
        runCatching { browser.setLayerType(View.LAYER_TYPE_HARDWARE, null) }
        browser.setBackgroundColor(Color.BLACK)
        browser.webViewClient =
            object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    return handleNavigation(request?.url?.toString(), request?.isForMainFrame != false)
                }

                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    url: String?
                ): Boolean = handleNavigation(url, true)

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): WebResourceResponse? =
                    request?.url
                        ?.takeUnless(::isAllowedGameResource)
                        ?.let { blockedResponse() }

                @Suppress("DEPRECATION")
                override fun shouldInterceptRequest(
                    view: WebView?,
                    url: String?
                ): WebResourceResponse? {
                    val uri = url?.let(Uri::parse)
                    return uri?.takeUnless(::isAllowedGameResource)?.let { blockedResponse() }
                }
            }
        browser.webChromeClient = WebChromeClient()
        browser.settings.apply {
            userAgentString = "$userAgentString;tyranoplayer-android-1.0;yukihub-internal-tyrano"
            javaScriptEnabled = true
            allowContentAccess = false
            allowFileAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            javaScriptCanOpenWindowsAutomatically = false
            loadsImagesAutomatically = true
            blockNetworkImage = false
            mediaPlaybackRequiresUserGesture = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }
        }
    }

    private fun handleNavigation(
        url: String?,
        mainFrame: Boolean
    ): Boolean {
        if (url == null) return true
        if (handleSpecialScheme(url)) return true
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return true
        if (isLocalGameUri(uri)) return false
        if (mainFrame) openExternalHttpUrl(uri)
        return true
    }

    private fun isLocalGameUri(uri: Uri?): Boolean {
        val server = localServer ?: return false
        if (!uri?.scheme.equals("http", ignoreCase = true)) return false
        return (uri?.host.equals("localhost", ignoreCase = true) || uri?.host == "127.0.0.1") &&
            uri?.port == server.port
    }

    private fun isAllowedGameResource(uri: Uri): Boolean =
        isLocalGameUri(uri) ||
            uri.scheme.equals("data", ignoreCase = true) ||
            uri.scheme.equals("blob", ignoreCase = true) ||
            uri.scheme.equals("about", ignoreCase = true)

    private fun blockedResponse() =
        WebResourceResponse(
            "text/plain",
            "UTF-8",
            ByteArrayInputStream(ByteArray(0)),
        )

    private fun openExternalHttpUrl(uri: Uri?) {
        if (uri == null ||
            (
                !uri.scheme.equals("http", ignoreCase = true) &&
                    !uri.scheme.equals("https", ignoreCase = true)
            )
        ) {
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (error: Throwable) {
            Log.w(TAG, "open external URL failed: $uri", error)
        }
    }

    private fun resolveGameDir(source: Intent?): String? {
        source ?: return null
        val path =
            uriToFilePath(
                firstNonEmpty(
                    source.getStringExtra("path"),
                    source.getStringExtra("gamePath"),
                    source.getStringExtra("projectRoot"),
                    source.getStringExtra("gamedir"),
                    source.getStringExtra("rootUri"),
                ),
            ) ?: return null
        val file = File(path).let { if (it.isFile) it.parentFile else it }
        return file?.absolutePath
    }

    private fun uriToFilePath(value: String?): String? {
        val raw = value?.trim() ?: return null
        if (raw.startsWith("file://")) return raw.removePrefix("file://")
        if (raw.startsWith("content://")) {
            val segment = Uri.parse(raw).lastPathSegment
            val colon = segment?.indexOf(':') ?: -1
            if (segment != null && colon >= 0) {
                val volume = segment.substring(0, colon)
                val relative = segment.substring(colon + 1)
                return if (volume.equals("primary", ignoreCase = true)) {
                    "/storage/emulated/0/$relative"
                } else {
                    "/storage/$volume/$relative"
                }
            }
        }
        return raw
    }

    private fun firstNonEmpty(vararg values: String?): String? = values.firstOrNull { !it.isNullOrBlank() }?.trim()

    private fun handleSpecialScheme(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        return try {
            when {
                lower.startsWith("tyranoplayer-save://") -> {
                    persistTyranoPlayerSave(url)
                    true
                }
                lower.startsWith("tyranoplayer-web://") -> {
                    val target =
                        Uri.decode(
                            queryParam(url, "url")?.takeIf(String::isNotBlank)
                                ?: url.removePrefix("tyranoplayer-web://"),
                        )
                    target?.takeIf(String::isNotBlank)?.let { openExternalHttpUrl(Uri.parse(it.trim())) }
                    true
                }
                lower.startsWith("tyranoplayer-back://") -> {
                    runOnUiThread(::onBackPressed)
                    true
                }
                else -> false
            }
        } catch (error: Throwable) {
            Log.w(TAG, "handleSpecialScheme failed url=$url", error)
            true
        }
    }

    private fun persistTyranoPlayerSave(url: String) {
        try {
            TyranoStorage.write(saveDirectory, queryParam(url, "key"), queryParam(url, "data"))
        } catch (error: Throwable) {
            Log.w(TAG, "persistTyranoPlayerSave failed url=$url", error)
        }
    }

    private fun confirmReturnToTitle() {
        showEngineConfirm(
            "返回标题",
            "确定要返回到标题界面吗？（请注意保存游戏进度。）",
            "确定",
        ) {
            webView?.post { runCatching { webView?.reload() } }
        }
    }

    override fun finish() {
        super.finish()
        if (processExitScheduled.compareAndSet(false, true)) {
            Handler(Looper.getMainLooper()).postDelayed({
                runCatching { android.os.Process.killProcess(android.os.Process.myPid()) }
            }, PROCESS_EXIT_DELAY_MS)
        }
    }

    private fun queryParam(
        url: String?,
        key: String?
    ): String? {
        if (url == null || key == null) return null
        val queryStart = url.indexOf('?')
        if (queryStart < 0 || queryStart + 1 >= url.length) return null
        for (pair in url.substring(queryStart + 1).split('&')) {
            val equals = pair.indexOf('=')
            val encodedKey = if (equals >= 0) pair.substring(0, equals) else pair
            if (key.equals(Uri.decode(encodedKey), ignoreCase = true)) {
                return if (equals >= 0) Uri.decode(pair.substring(equals + 1)) else ""
            }
        }
        return null
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        showEngineConfirm(
            "结束游戏",
            "确定要结束当前游戏吗？",
            "结束游戏",
            ::finish,
        )
    }

    private fun showEngineConfirm(
        title: String,
        message: String,
        confirmLabel: String,
        onConfirm: () -> Unit,
    ) {
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton("取消", null)
            .setPositiveButton(confirmLabel) { _, _ -> onConfirm() }
            .show()
    }

    override fun onPause() {
        playSessionJournal?.pause()
        runCatching { webView?.loadUrl("javascript:if(window._tyrano_player){_tyrano_player.pauseAllAudio();}") }
        runCatching { webView?.onPause() }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        playSessionJournal?.resume()
        enterFullscreen()
        if (firstResume) {
            firstResume = false
        } else {
            runCatching { webView?.loadUrl("javascript:if(window._tyrano_player){_tyrano_player.resumeAllAudio();}") }
        }
        runCatching { webView?.onResume() }
    }

    override fun onDestroy() {
        playSessionJournal?.finish()
        playSessionJournal = null
        runCatching {
            webView?.stopLoading()
            webView?.loadUrl("about:blank")
            webView?.destroy()
        }
        webView = null
        runCatching { localServer?.stop() }
        localServer = null
        runCatching { asarArchive?.close() }
        asarArchive = null
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterFullscreen()
    }

    @Suppress("DEPRECATION")
    private fun enterFullscreen() {
        val flags =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        runCatching { window.decorView.systemUiVisibility = flags }
    }

    private fun resolveSaveDirectory(
        source: Intent?,
        gameRoot: File?
    ): File? {
        if (source?.getBooleanExtra(EXTRA_SCOPED_SAVE_DIR, false) == true) {
            val explicit =
                source.getStringExtra(EXTRA_SCOPED_SAVE_ROOT)?.takeIf(String::isNotBlank)
                    ?: return null
            return try {
                val external = getExternalFilesDir(null) ?: return null
                val namespace = File(File(external, "save"), "tyrano").canonicalFile
                File(explicit).canonicalFile.takeIf {
                    it.path.startsWith(namespace.path + File.separator)
                }
            } catch (_: Throwable) {
                null
            }
        }
        return gameRoot?.let { File(it, "savedata") }
    }

    inner class TyranoJsBridge(private val saveDirectory: File?) {
        @JavascriptInterface
        fun closeGame() = runOnUiThread(::onBackPressed)

        @JavascriptInterface
        fun finishGame() = runOnUiThread(::confirmReturnToTitle)

        @JavascriptInterface
        fun getStorage(key: String?): String = TyranoStorage.read(saveDirectory, key)

        @JavascriptInterface
        fun setStorage(
            key: String?,
            value: String?
        ) = TyranoStorage.write(saveDirectory, key, value)

        @JavascriptInterface
        fun openUrl(url: String?) =
            runOnUiThread {
                try {
                    openExternalHttpUrl(Uri.parse(url))
                } catch (error: Throwable) {
                    Log.w(TAG, "invalid external URL", error)
                }
            }

        @JavascriptInterface fun stopMovie() = Unit

        @JavascriptInterface fun audio(value: String?) = Unit
    }

    companion object {
        private const val TAG = "YukiTyrano"
        private const val TYRANO_HOOK_ASSET = "__tyrano__.js"
        private const val JS_BRIDGE_NAME = "appJsInterface"
        private const val EXTRA_SCOPED_SAVE_DIR = "scopedSaveDir"
        private const val EXTRA_ORIENTATION = "orientation"
        private const val EXTRA_KEEP_SCREEN_ON = "keepScreenOn"
        private const val EXTRA_IGNORE_CUTOUT = "ignoreCutout"
        private const val EXTRA_SCOPED_SAVE_ROOT = "scopedSaveRoot"
        private const val PROCESS_EXIT_DELAY_MS = 500L

        @JvmStatic
        @Throws(Exception::class)
        fun resolveStorageFile(
            directory: File?,
            key: String?
        ): File? = TyranoStorage.resolveFile(directory, key)
    }
}
