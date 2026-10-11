package com.kireibox.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.kireibox.app.LauncherActivity
import com.kireibox.designsystem.AppleDesignTheme
import com.yuki.yukihub.auth.CoverAccounts
import com.yuki.yukihub.auth.CoverOAuthConfig
import com.yuki.yukihub.auth.CoverProfileClaims
import com.yuki.yukihub.metadata.model.CoverSourceMode
import com.yuki.yukihub.settings.AppearanceSettings
import com.yuki.yukihub.settings.YukiSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthorizationManagementActivity
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/** A foreground-owned login session. A killed process starts again instead of replaying a code. */
class CoverLoginActivity : ComponentActivity() {
    private companion object {
        /** NextMoe: the catalog read scope is enough to render the cover page. */
        const val NEXTMOE_SCOPES = "openid profile catalog:read"

        /**
         * Hikarinagi: `catalog:full` is intentionally omitted — it already implies `catalog:read`,
         * and ANY scope outside the console's granted set fails the whole request with
         * invalid_scope. Add `catalog:full` here only after the console grants it, and never
         * alongside `catalog:read`. `offline_access` requires prompt=consent for a refresh token.
         */
        const val HIKARINAGI_SCOPES = "openid profile catalog:read offline_access"

        /** Browsers may open several loopback connections at once (preconnects); queue them instead of refusing. */
        const val LOOPBACK_BACKLOG = 8
    }

    private lateinit var source: CoverSourceMode
    private lateinit var service: AuthorizationService
    private lateinit var request: AuthorizationRequest
    private var socket: ServerSocket? = null

    @Volatile private var callbackConnection: Socket? = null

    /** NextMoe: set once the loopback listener holds a valid callback; from then on the tab's own result is moot. */
    private val callbackAccepted = AtomicBoolean(false)
    private val tokenConnections = mutableSetOf<java.net.HttpURLConnection>()
    private val connectionBuilder =
        net.openid.appauth.connectivity.ConnectionBuilder { uri ->
            synchronized(tokenConnections) {
                check(!finished.get()) { "授权已取消" }
                net.openid.appauth.connectivity.DefaultConnectionBuilder.INSTANCE.openConnection(uri).also {
                    it.connectTimeout = 8000
                    it.readTimeout = 20000
                    tokenConnections += it
                }
            }
        }
    private var epoch = 0L
    private val finished = AtomicBoolean(false)
    private var message by mutableStateOf("正在打开官方授权页面…")
    private val browserResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            // A NextMoe tab closes as "cancelled" when the user returns by hand after the callback arrived.
            if (callbackAccepted.get()) return@registerForActivityResult
            val data = result.data
            val response = data?.let(AuthorizationResponse::fromIntent)
            if (response == null) {
                complete(false, "授权已取消，请返回重试")
            } else {
                exchange(response)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        source = CoverSourceMode.selected(intent.getStringExtra("source"))
        service = AuthorizationService(this, AppAuthConfiguration.Builder().setConnectionBuilder(connectionBuilder).build())
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    complete(false, "授权已取消")
                }
            }
        )
        epoch = CoverAccounts.get(this).generation(source)
        setContent {
            // App theme (graphite + accent tokens) so the stock M3 controls never fall back to the
            // Material baseline purple.
            val prefs = getSharedPreferences(LauncherActivity.APP_PREFS, Context.MODE_PRIVATE)
            AppleDesignTheme(
                darkTheme = LauncherActivity.isLauncherDarkMode(this),
                accent = AppearanceSettings.readAccentChoice(prefs),
                highContrast = prefs.getBoolean(YukiSettingsStore.KEY_INCREASED_CONTRAST, false),
            ) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.Center) {
                        Text("连接 ${source.label}", style = MaterialTheme.typography.headlineSmall)
                        Text(message, modifier = Modifier.padding(vertical = 20.dp))
                        Button(onClick = { complete(false, "授权已取消") }) { Text("取消并返回") }
                    }
                }
            }
        }
        if (savedInstanceState != null) {
            complete(false, "授权会话已结束，请重新登录")
            return
        }
        val id = CoverOAuthConfig.clientId(source)
        if (!source.requiresLogin || id.isBlank()) {
            complete(false, "此测试包尚未配置 ${source.label} 客户端，暂不能登录")
            return
        }
        lifecycleScope.launch {
            try {
                val listener =
                    if (source == CoverSourceMode.NEXTMOE) {
                        withContext(Dispatchers.IO) {
                            ServerSocket(0, LOOPBACK_BACKLOG, InetAddress.getByName("127.0.0.1")).apply { soTimeout = 300_000 }
                        }.also { socket = it }
                    } else {
                        null
                    }
                val redirect =
                    listener?.let { Uri.parse("http://127.0.0.1:${it.localPort}/callback") }
                        ?: Uri.parse("kireibox://oauth/hikarinagi")
                request = authorizationRequest(id, redirect)
                message = "请在系统浏览器完成登录和授权。完成后返回 KireiBox，取消不会影响游戏库。"
                // Both sources open in a Custom Tab inside this task (AppAuth falls back to the default
                // browser): still the system browser, never a WebView, and closing it cancels.
                browserResult.launch(service.getAuthorizationRequestIntent(request))
                if (listener != null) finishLoopback(listener, redirect)
            } catch (_: kotlinx.coroutines.CancellationException) {
                socket?.close()
            } catch (_: SocketTimeoutException) {
                complete(false, "授权已超时，请重新登录")
            } catch (_: Exception) {
                complete(false, "无法完成浏览器授权，请检查网络后重试")
            }
        }
        lifecycleScope.launch {
            kotlinx.coroutines.delay(300_000)
            complete(false, "授权已超时，请重新登录")
        }
    }

    private fun authorizationRequest(
        id: String,
        redirect: Uri
    ): AuthorizationRequest {
        val builder =
            AuthorizationRequest.Builder(
                AuthorizationServiceConfiguration(Uri.parse(CoverOAuthConfig.authorizeUrl(source)), Uri.parse(CoverOAuthConfig.tokenUrl(source))),
                id,
                ResponseTypeValues.CODE,
                redirect,
                // Hikarinagi: `catalog:full` already implies `catalog:read`, and requesting a scope
                // outside the console's granted set is rejected with invalid_scope — so request the
                // read scope only.
            ).setScope(
                if (source == CoverSourceMode.NEXTMOE) NEXTMOE_SCOPES else HIKARINAGI_SCOPES
            )
        // Hikarinagi only issues a refresh token for `offline_access` when the request also
        // carries prompt=consent. NextMoe validates Prompt against a `oneof` allowlist and
        // rejects `consent` outright with HTTP 400 (`AuthorizeRequest.Prompt`), so the
        // prompt must never be sent there.
        if (source == CoverSourceMode.HIKARINAGI) builder.setPrompt("consent")
        return builder.build()
    }

    /** NextMoe: the code arrives on the loopback listener, not through the tab's result. */
    private suspend fun finishLoopback(
        listener: ServerSocket,
        redirect: Uri
    ) {
        val uri = withContext(Dispatchers.IO) { awaitLoopback(listener, redirect) }
        if (finished.get()) return
        // Pops the tab back to this screen. A plain browser in its own task may be blocked from
        // returning by background-start limits, so the exchange below never waits on it.
        runCatching { startActivity(AuthorizationManagementActivity.createResponseHandlingIntent(this, uri)) }
        if (uri.getQueryParameter("error") != null) {
            complete(false, "授权未完成，请重试")
        } else {
            exchange(AuthorizationResponse.Builder(request).fromUri(uri).build())
        }
    }

    private fun awaitLoopback(
        listener: ServerSocket,
        redirect: Uri
    ): Uri {
        val deadline = System.currentTimeMillis() + 300_000
        listener.use {
            while (!finished.get()) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) throw SocketTimeoutException()
                listener.soTimeout = remaining.coerceAtMost(300_000).toInt()
                val callback = listener.accept().use { connection -> answer(connection, redirect) }
                if (callback != null) return Uri.parse(callback)
            }
        }
        throw java.util.concurrent.CancellationException()
    }

    /**
     * Serves one loopback connection. A connection that never sends a request (a browser preconnect) or
     * drops mid-way is skipped rather than failing the login; a valid callback is kept even if the reply
     * cannot be written.
     */
    private fun answer(
        connection: Socket,
        redirect: Uri
    ): String? {
        callbackConnection = connection
        val callback =
            try {
                connection.soTimeout = 3000
                LoopbackCallback.readRequestLine(connection.getInputStream().buffered())
                    ?.let { LoopbackCallback.parse(it, redirect.toString(), request.state) }
            } catch (_: IOException) {
                return null
            }
        if (callback != null) callbackAccepted.set(true)
        val body = (if (callback != null) "授权结果已收到，请返回 KireiBox。" else "无效的授权回调。").toByteArray(Charsets.UTF_8)
        runCatching {
            connection.getOutputStream().apply {
                write(
                    (
                        "HTTP/1.1 ${if (callback != null) "200 OK" else "400 Bad Request"}\r\n" +
                            "Content-Type: text/plain; charset=utf-8\r\n" +
                            "Cache-Control: no-store\r\n" +
                            "Content-Length: ${body.size}\r\n" +
                            "Connection: close\r\n\r\n"
                    ).toByteArray()
                )
                write(body)
                flush()
            }
        }
        return callback
    }

    private fun exchange(response: AuthorizationResponse) {
        if (finished.get()) return
        if (response.state != request.state || response.authorizationCode.isNullOrBlank()) {
            complete(false, "授权校验失败，请重新登录")
            return
        }
        message = "正在安全保存授权…"
        service.performTokenRequest(response.createTokenExchangeRequest()) { token, error ->
            if (finished.get()) return@performTokenRequest
            if (error != null || token == null) {
                complete(false, "授权换码失败，请重新登录")
                return@performTokenRequest
            }
            // AppAuth delivers on the main thread. Keep the short encrypted commit in this
            // callback so cancel/back cannot race a separate worker that saves credentials later.
            try {
                // NextMoe's id_token has no name; the settings card fills it from userinfo afterwards.
                val profile = CoverProfileClaims.fromJson(source, CoverProfileClaims.idTokenClaims(token.idToken))
                CoverAccounts.get(this).saveOAuth(
                    source,
                    token.accessToken.orEmpty(),
                    token.refreshToken.orEmpty(),
                    ((token.accessTokenExpirationTime ?: (System.currentTimeMillis() + 900_000)) - System.currentTimeMillis()) / 1000,
                    epoch,
                    profile.name,
                    profile.accountId,
                    profile.avatarUrl,
                )
                complete(true, "${source.label} 已连接")
            } catch (_: Exception) {
                complete(false, "无法安全保存授权，请重试")
            }
        }
    }

    private fun complete(
        success: Boolean,
        text: String
    ) {
        if (!finished.compareAndSet(false, true)) return
        closeConnections()
        setResult(
            if (success) Activity.RESULT_OK else Activity.RESULT_CANCELED,
            Intent().putExtra("source", source.key).putExtra("message", text)
        )
        finish()
    }

    private fun closeConnections() {
        runCatching { socket?.close() }
        runCatching { callbackConnection?.close() }
        synchronized(tokenConnections) {
            tokenConnections.forEach { runCatching { it.disconnect() } }
            tokenConnections.clear()
        }
    }

    override fun onDestroy() {
        finished.set(true)
        closeConnections()
        service.dispose()
        super.onDestroy()
    }
}
