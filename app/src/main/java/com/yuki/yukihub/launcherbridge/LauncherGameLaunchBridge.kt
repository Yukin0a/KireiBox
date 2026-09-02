package com.yuki.yukihub.launcherbridge

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.kireibox.external.shortcut.ExternalShortcutDirectLauncher
import com.kireibox.external.shortcut.ExternalShortcutIdentity
import com.kireibox.external.shortcut.ShizukuShortcutGateway
import com.yuki.yukihub.data.GameRepository
import com.yuki.yukihub.launcher.EmulatorLauncher
import com.yuki.yukihub.launcher.ArtemisVersion
import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.model.Game
import com.yuki.yukihub.model.SupportedEngines
import com.yuki.yukihub.launch.GameLocationPreflight
import com.yuki.yukihub.util.AppExecutors
import com.yuki.yukihub.util.DevLogger
import com.yuki.yukihub.util.RxMainScheduler
import java.util.Locale
import org.tvp.kirikiri2.EnginePlaySessionJournal

/**
 * 游戏启动桥接：数据库准备、SAF 校验、模拟器分发。
 */
object LauncherGameLaunchBridge {

    private const val APP_PREFS = "yukihub_prefs"
    private const val KEY_KR_ENGINE_VERSION = "kr_engine_version"
    private const val MIN_RECORDED_PLAY_MS = 3_000L
    private const val MAX_RECORDED_PLAY_MS = 12L * 60L * 60L * 1000L
    private const val UNATTACHED_SESSION_GRACE_MS = 2L * 60L * 1000L
    private const val EXTERNAL_GATE_PREFS = "launcher_external_game_gate"
    private const val KEY_EXT_SESSION_ID = "external_session_id"
    private const val KEY_EXT_GAME_ID = "external_game_id"
    private const val KEY_EXT_GAME_TITLE = "external_game_title"
    private const val KEY_EXT_PROCESS_PACKAGE = "external_process_package"
    private const val KEY_EXT_STARTED_AT = "external_started_at"

    interface LaunchCallback {
        fun onResult(result: LaunchResult)
    }

    /** 在 IO 线程执行数据库、SAF 和文件准备。 */
    @JvmStatic
    fun launchAsync(context: Context?, game: Game?, callback: LaunchCallback?) {
        launchAsync(context, game, false, callback)
    }

    @JvmStatic
    fun launchAsync(context: Context?, game: Game?, krSafCompatibility: Boolean, callback: LaunchCallback?) {
        if (callback == null) return
        val app = context?.applicationContext
        AppExecutors.runOnIo {
            val result = launch(app, game, krSafCompatibility)
            RxMainScheduler.post { callback.onResult(result) }
        }
    }

    @JvmStatic
    fun launch(context: Context?, game: Game?): LaunchResult {
        return launch(context, game, false)
    }

    @JvmStatic
    fun launch(context: Context?, game: Game?, krSafCompatibility: Boolean): LaunchResult {
        if (context == null) return LaunchResult.failure("上下文不可用")
        if (game == null) return LaunchResult.failure("游戏不存在")
        if (!SupportedEngines.contains(game.engine)) return LaunchResult.failure("KireiBox 不支持该游戏引擎")
        val appContext = context.applicationContext
        val repository = GameRepository(appContext)
        val prepared = GameLocationPreflight.verify(appContext, repository, game)
        val launchGame = prepared.game ?: run {
            DevLogger.event("launch blocked by preflight: ${prepared.message}")
            return LaunchResult.failure(prepared.message)
        }
        DevLogger.event("launch start engine=${launchGame.engine} root=${launchGame.rootUri} target=${launchGame.launchTarget}")
        val previousGate = readExternalGate(appContext)
        if (previousGate != null) {
            val withinSpawnGrace = System.currentTimeMillis() - previousGate.startedAtWallMs <
                ExternalSessionRecoveryPolicy.PROCESS_SPAWN_GRACE_MS
            if (isProcessRunning(appContext, previousGate.processPackage) || withinSpawnGrace) {
                return LaunchResult.failure("已有游戏正在运行：${previousGate.gameTitle}")
            }
            recoverExternalGate(appContext, repository, previousGate)
        }
        val externalShortcut = ExternalShortcutIdentity.fromGame(launchGame)
        if (externalShortcut != null) {
            val sessionId = repository.startPlaySession(launchGame.id, System.currentTimeMillis(), "external.shortcut")
            if (sessionId <= 0L) return LaunchResult.failure("无法记录本次快捷方式启动")
            val direct = ExternalShortcutDirectLauncher.launch(appContext, externalShortcut, launchGame.launchTarget)
            val launched = if (direct?.isSuccess == true) direct else {
                ShizukuShortcutGateway(appContext).launch(externalShortcut).recoverCatching { fallbackError ->
                    val directMessage = direct?.exceptionOrNull()?.message
                    if (!directMessage.isNullOrBlank()) throw IllegalStateException("$directMessage Shizuku 回退也不可用：${fallbackError.message.orEmpty()}", fallbackError)
                    throw fallbackError
                }
            }
            return if (launched.isSuccess) {
                repository.markPlaySessionLaunched(sessionId, System.currentTimeMillis())
                val gate = ExternalGameGate(
                    sessionId = sessionId,
                    gameId = launchGame.id,
                    gameTitle = launchGame.title?.ifBlank { "未命名游戏" } ?: "未命名游戏",
                    processPackage = externalShortcut.packageName,
                    startedAtWallMs = System.currentTimeMillis(),
                )
                if (!writeExternalGate(appContext, gate)) {
                    repository.cancelPlaySession(sessionId)
                    return LaunchResult.failure("无法记录本次快捷方式游玩会话")
                }
                LaunchResult.success(sessionId, LaunchHandoff.SHORTCUT)
            } else {
                repository.cancelPlaySession(sessionId)
                LaunchResult.failure(launched.exceptionOrNull()?.message ?: "快捷方式已失效，请重新扫描。")
            }
        }
        val emulatorPackage = resolveEmulatorPackage(launchGame)
        val launchTarget = resolveLaunchTarget(launchGame)
        val validationError = validate(launchGame, emulatorPackage)
        if (validationError != null) {
            DevLogger.event("launch validation failed: $validationError")
            return LaunchResult.failure(validationError)
        }

        val startedAt = System.currentTimeMillis()
        val sessionId = repository.startPlaySession(launchGame.id, startedAt, resolveLaunchType(emulatorPackage))
        if (sessionId <= 0L) return LaunchResult.failure("无法记录本次游玩启动")
        val launchError = startGameActivity(
            context, launchGame, emulatorPackage, launchTarget, krSafCompatibility,
            sessionId
        )
        if (launchError == null) {
            DevLogger.event("launch started session=$sessionId engine=$emulatorPackage")
            repository.markPlaySessionLaunched(sessionId, System.currentTimeMillis())
            if (launchGame.engine == EngineType.ANDROID_APP) {
                val gate = ExternalGameGate(
                    sessionId = sessionId,
                    gameId = launchGame.id,
                    gameTitle = launchGame.title?.ifBlank { "未命名游戏" } ?: "未命名游戏",
                    processPackage = emulatorPackage,
                    startedAtWallMs = System.currentTimeMillis(),
                )
                if (!writeExternalGate(appContext, gate)) {
                    repository.cancelPlaySession(sessionId)
                    return LaunchResult.failure("无法记录本次 Android 应用游玩会话")
                }
                return LaunchResult.success(sessionId, LaunchHandoff.EXTERNAL_APP)
            }
            return LaunchResult.success(sessionId, LaunchHandoff.INTERNAL_ENGINE)
        }
        repository.cancelPlaySession(sessionId)
        DevLogger.event("launch failed: $launchError")
        return LaunchResult.failure(launchError)
    }

    @JvmStatic
    fun finishExternalSessionAsync(
        context: Context?,
        sessionId: Long,
        effectiveDuration: Long,
        onComplete: (() -> Unit)? = null,
    ) {
        if (context == null || sessionId <= 0L) return
        val app = context.applicationContext
        AppExecutors.runOnIo {
            GameRepository(app).finishPlaySessionWithDuration(
                sessionId = sessionId,
                end = System.currentTimeMillis(),
                recordedDuration = effectiveDuration,
                minDuration = MIN_RECORDED_PLAY_MS,
                maxDuration = MAX_RECORDED_PLAY_MS,
            )
            releaseExternalGateIfMatches(app, sessionId)
            RxMainScheduler.post { onComplete?.invoke() }
        }
    }

    /**
     * Silently settles an external session whose launcher process died while the
     * game was open. Wall-clock recovery is capped per [ExternalSessionRecoveryPolicy]
     * and never prompts the user.
     */
    @JvmStatic
    fun recoverStaleExternalSession(context: Context?): Boolean {
        val app = context?.applicationContext ?: return false
        val gate = readExternalGate(app) ?: return false
        val duration = ExternalSessionRecoveryPolicy.recoveredDurationMs(
            processAlive = isProcessRunning(app, gate.processPackage),
            startedAtWallMs = gate.startedAtWallMs,
            nowWallMs = System.currentTimeMillis(),
        ) ?: return false
        GameRepository(app).finishPlaySessionWithDuration(
            sessionId = gate.sessionId,
            end = System.currentTimeMillis(),
            recordedDuration = duration,
            minDuration = MIN_RECORDED_PLAY_MS,
            maxDuration = MAX_RECORDED_PLAY_MS,
        )
        clearExternalGate(app)
        return true
    }

    @JvmStatic
    fun recoverStaleExternalSessionAsync(context: Context?, onComplete: (() -> Unit)? = null) {
        val app = context?.applicationContext ?: return
        AppExecutors.runOnIo {
            recoverStaleExternalSession(app)
            RxMainScheduler.post { onComplete?.invoke() }
        }
    }

    /**
     * Commits engine-owned monotonic journals only after an engine finished or
     * its process died. Launcher background time is never counted as play time.
     */
    @JvmStatic
    fun reconcilePlaySessionsAsync(context: Context?, onComplete: (() -> Unit)? = null) {
        val app = context?.applicationContext ?: return
        AppExecutors.runOnIo {
            reconcilePlaySessions(app)
            RxMainScheduler.post { onComplete?.invoke() }
        }
    }

    internal fun reconcilePlaySessions(context: Context) {
        val repository = GameRepository(context)
        val runningProcessIds = (
            context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            )?.runningAppProcesses
            ?.asSequence()
            ?.filter { it.uid == android.os.Process.myUid() }
            ?.map { it.pid }
            ?.toSet()
            .orEmpty()
        val snapshots = EnginePlaySessionJournal.readAll(context)
            .distinctBy { it.sessionId }
        val journalSessionIds = snapshots.mapTo(HashSet()) { it.sessionId }
        val now = System.currentTimeMillis()

        snapshots.forEach { snapshot ->
            if (!EngineSessionReconcilePolicy.shouldFinalize(
                    snapshot.state,
                    snapshot.processId,
                    runningProcessIds,
                )
            ) return@forEach
            val committed = repository.finishPlaySessionWithDuration(
                sessionId = snapshot.sessionId,
                end = now,
                recordedDuration = snapshot.accumulatedMs,
                minDuration = MIN_RECORDED_PLAY_MS,
                maxDuration = MAX_RECORDED_PLAY_MS,
            )
            if (committed) EnginePlaySessionJournal.delete(context, snapshot.sessionId)
        }

        // A launch that never reached engine onCreate has no journal. Close only
        // after a grace period and retain it as a zero-duration launch record.
        repository.getOpenPlaySessions()
            .asSequence()
            .filter { it.launchType?.startsWith("internal.") == true }
            .filter { it.sessionId !in journalSessionIds }
            .filter { now - it.startTime >= UNATTACHED_SESSION_GRACE_MS }
            .forEach {
                repository.finishPlaySessionWithDuration(
                    sessionId = it.sessionId,
                    end = now,
                    recordedDuration = 0L,
                    minDuration = MIN_RECORDED_PLAY_MS,
                    maxDuration = MAX_RECORDED_PLAY_MS,
                )
            }
    }

    private data class ExternalGameGate(
        val sessionId: Long,
        val gameId: Long,
        val gameTitle: String,
        val processPackage: String,
        val startedAtWallMs: Long,
    )

    private fun readExternalGate(context: Context): ExternalGameGate? {
        val prefs = context.getSharedPreferences(EXTERNAL_GATE_PREFS, Context.MODE_PRIVATE)
        val sessionId = prefs.getLong(KEY_EXT_SESSION_ID, -1L)
        if (sessionId <= 0L) return null
        return ExternalGameGate(
            sessionId = sessionId,
            gameId = prefs.getLong(KEY_EXT_GAME_ID, -1L),
            gameTitle = prefs.getString(KEY_EXT_GAME_TITLE, "当前游戏") ?: "当前游戏",
            processPackage = prefs.getString(KEY_EXT_PROCESS_PACKAGE, "") ?: "",
            startedAtWallMs = prefs.getLong(KEY_EXT_STARTED_AT, 0L),
        )
    }

    private fun writeExternalGate(context: Context, gate: ExternalGameGate): Boolean {
        val prefs = context.getSharedPreferences(EXTERNAL_GATE_PREFS, Context.MODE_PRIVATE)
        return prefs.edit()
            .putLong(KEY_EXT_SESSION_ID, gate.sessionId)
            .putLong(KEY_EXT_GAME_ID, gate.gameId)
            .putString(KEY_EXT_GAME_TITLE, gate.gameTitle)
            .putString(KEY_EXT_PROCESS_PACKAGE, gate.processPackage)
            .putLong(KEY_EXT_STARTED_AT, gate.startedAtWallMs)
            .commit()
    }

    private fun clearExternalGate(context: Context) {
        context.getSharedPreferences(EXTERNAL_GATE_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun releaseExternalGateIfMatches(context: Context, sessionId: Long) {
        val gate = readExternalGate(context) ?: return
        if (gate.sessionId == sessionId) clearExternalGate(context)
    }

    private fun recoverExternalGate(context: Context, repository: GameRepository, gate: ExternalGameGate) {
        val duration = ExternalSessionRecoveryPolicy.recoveredDurationMs(
            processAlive = isProcessRunning(context, gate.processPackage),
            startedAtWallMs = gate.startedAtWallMs,
            nowWallMs = System.currentTimeMillis(),
        ) ?: return
        repository.finishPlaySessionWithDuration(
            sessionId = gate.sessionId,
            end = System.currentTimeMillis(),
            recordedDuration = duration,
            minDuration = MIN_RECORDED_PLAY_MS,
            maxDuration = MAX_RECORDED_PLAY_MS,
        )
        clearExternalGate(context)
    }

    private fun isProcessRunning(context: Context, processPackage: String): Boolean {
        if (processPackage.isBlank()) return false
        return try {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
            manager.runningAppProcesses.orEmpty().any { process ->
                process.processName == processPackage || process.pkgList?.any { it == processPackage } == true
            }
        } catch (_: Exception) {
            // Keep the gate when the platform refuses process visibility.
            true
        }
    }

    private fun validate(game: Game, emulatorPackage: String): String? {
        if (emulatorPackage.isEmpty()) {
            return if (game.engine == EngineType.ANDROID_APP) {
                "请为 Android 应用卡片设置包名。"
            } else {
                "没有可用的内置引擎。"
            }
        }
        return null
    }

    private fun resolveEmulatorPackage(game: Game): String {
        return when (game.engine) {
            EngineType.KIRIKIRI -> "internal.krkr"
            EngineType.ONS -> "internal.ons"
            EngineType.TYRANO -> "internal.tyrano"
            EngineType.ARTEMIS -> ArtemisVersion.fromStored(game.emulatorPackage).emulatorPackage
            EngineType.ANDROID_APP -> game.emulatorPackage?.trim().orEmpty()
            else -> ""
        }
    }

    private fun resolveLaunchTarget(game: Game): String? {
        if (game.engine == EngineType.ARTEMIS || game.engine == EngineType.TYRANO) return "[游戏目录]"
        return game.launchTarget
    }

    private fun startGameActivity(
        context: Context,
        game: Game,
        emulatorPackage: String,
        launchTarget: String?,
        krSafCompatibility: Boolean,
        sessionId: Long
    ): String? {
        val pkg = emulatorPackage.trim()
        return try {
            if (pkg.startsWith("internal.krkr") || pkg == "org.tvp.kirikiri2.internal") {
                val prefs: SharedPreferences =
                    context.applicationContext.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
                val krEngineVersion = prefs.getString(KEY_KR_ENGINE_VERSION, "auto")
                val intent = EmulatorLauncher.buildInternalKrkrIntent(
                        context, game.rootUri, launchTarget, false, krEngineVersion, krSafCompatibility
                    )?.withPlaySession(sessionId, game.id)
                return startActivitySafely(context, intent)
            }
            if (pkg.startsWith("internal.tyrano") || pkg == "com.yuki.yukihub.tyrano") {
                val intent = EmulatorLauncher.buildInternalTyranoIntent(context, game.rootUri, launchTarget)
                    ?.withPlaySession(sessionId, game.id)
                return startActivitySafely(context, intent)
            }
            if (pkg.startsWith("internal.ons") || pkg == "com.yuki.yukihub.ons") {
                val intent = EmulatorLauncher.buildInternalOnsIntent(context, game.rootUri, launchTarget, game.id)
                    ?.withPlaySession(sessionId, game.id)
                return startActivitySafely(context, intent)
            }
            if (pkg.startsWith("internal.artemis")) {
                val intent = EmulatorLauncher.buildInternalArtemisIntent(context, pkg, game.rootUri, launchTarget)
                    ?.withPlaySession(sessionId, game.id)
                return startActivitySafely(context, intent)
            }
            if (game.engine == EngineType.ANDROID_APP) {
                return if (EmulatorLauncher.launchAndroidApp(context, pkg, launchTarget)) {
                    null
                } else {
                    "无法启动 Android 应用：包名或 Activity 无效"
                }
            }
            "没有可用的内置引擎启动方式"
        } catch (error: Throwable) {
            error.message?.takeIf(String::isNotBlank) ?: "启动引擎时发生未知错误"
        }
    }

    private fun startActivitySafely(context: Context?, intent: Intent?): String? {
        if (context == null) return "应用上下文不可用"
        if (intent == null) return "无法构建引擎启动意图"
        return try {
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            null
        } catch (error: Throwable) {
            error.message?.takeIf(String::isNotBlank) ?: "无法启动引擎界面"
        }
    }

    private fun Intent.withPlaySession(sessionId: Long, gameId: Long): Intent = apply {
        putExtra(EnginePlaySessionJournal.EXTRA_SESSION_ID, sessionId)
        putExtra(EnginePlaySessionJournal.EXTRA_GAME_ID, gameId)
    }

    private fun resolveLaunchType(emulatorPackage: String?): String {
        val pkg = emulatorPackage?.trim()?.lowercase(Locale.ROOT) ?: ""
        if (pkg.startsWith("internal.krkr") || pkg == "org.tvp.kirikiri2.internal") return "internal.krkr"
        if (pkg.startsWith("internal.ons") || pkg == "com.yuki.yukihub.ons") return "internal.ons"
        if (pkg.startsWith("internal.tyrano") || pkg == "com.yuki.yukihub.tyrano") return "internal.tyrano"
        if (pkg.startsWith("internal.artemis")) return pkg
        return "external"
    }

    enum class LaunchHandoff { NONE, INTERNAL_ENGINE, EXTERNAL_APP, SHORTCUT }

    class LaunchResult private constructor(
        @JvmField val success: Boolean,
        @JvmField val sessionId: Long,
        @JvmField val message: String,
        @JvmField val handoff: LaunchHandoff,
    ) {
        companion object {
            @JvmStatic
            fun success(sessionId: Long): LaunchResult =
                LaunchResult(true, sessionId, "", LaunchHandoff.INTERNAL_ENGINE)

            @JvmStatic
            fun success(sessionId: Long, handoff: LaunchHandoff): LaunchResult =
                LaunchResult(true, sessionId, "", handoff)

            @JvmStatic
            fun failure(message: String?): LaunchResult =
                LaunchResult(false, -1L, if (message.isNullOrBlank()) "启动失败" else message, LaunchHandoff.NONE)
        }
    }
}

internal object EngineSessionReconcilePolicy {
    fun shouldFinalize(state: String?, processId: Int, runningProcessIds: Set<Int>): Boolean =
        state == EnginePlaySessionJournal.STATE_FINISHED ||
            processId <= 0 ||
            processId !in runningProcessIds
}
