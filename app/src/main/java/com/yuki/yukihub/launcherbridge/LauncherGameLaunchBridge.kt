package com.yuki.yukihub.launcherbridge

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.yuki.yukihub.data.GameRepository
import com.yuki.yukihub.launcher.EmulatorLauncher
import com.yuki.yukihub.model.EngineType
import com.yuki.yukihub.model.Game
import com.yuki.yukihub.model.SupportedEngines
import com.yuki.yukihub.launch.GameLocationPreflight
import com.yuki.yukihub.util.AppExecutors
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
        val launchGame = prepared.game ?: return LaunchResult.failure(prepared.message)
        val emulatorPackage = resolveEmulatorPackage(launchGame)
        val launchTarget = resolveLaunchTarget(launchGame)
        val validationError = validate(launchGame, emulatorPackage)
        if (validationError != null) return LaunchResult.failure(validationError)

        val startedAt = System.currentTimeMillis()
        val sessionId = repository.startPlaySession(launchGame.id, startedAt, resolveLaunchType(emulatorPackage))
        if (sessionId <= 0L) return LaunchResult.failure("无法记录本次游玩启动")
        if (startGameActivity(
                context, launchGame, emulatorPackage, launchTarget, krSafCompatibility,
                sessionId
            )) {
            repository.markPlaySessionLaunched(sessionId, System.currentTimeMillis())
            return LaunchResult.success(sessionId)
        }
        repository.cancelPlaySession(sessionId)
        return LaunchResult.failure("启动失败：未找到该模拟器，或该模拟器不接受当前启动目标")
    }

    @JvmStatic
    fun finishExternalSessionAsync(context: Context?, sessionId: Long, onComplete: (() -> Unit)? = null) {
        if (context == null || sessionId <= 0L) return
        val app = context.applicationContext
        AppExecutors.runOnIo {
            GameRepository(app).finishPlaySessionWithDuration(
                sessionId = sessionId,
                end = System.currentTimeMillis(),
                recordedDuration = 0L,
                minDuration = MIN_RECORDED_PLAY_MS,
                maxDuration = MAX_RECORDED_PLAY_MS,
            )
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
            EngineType.ARTEMIS -> "internal.artemis"
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
    ): Boolean {
        val pkg = emulatorPackage.trim()
        try {
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
                return EmulatorLauncher.launchAndroidApp(context, pkg, launchTarget)
            }
            return false
        } catch (_: Throwable) {
            return false
        }
    }

    private fun startActivitySafely(context: Context?, intent: Intent?): Boolean {
        if (context == null || intent == null) return false
        return try {
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: Throwable) {
            false
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

    class LaunchResult private constructor(
        @JvmField val success: Boolean,
        @JvmField val sessionId: Long,
        @JvmField val message: String
    ) {
        companion object {
            @JvmStatic
            fun success(sessionId: Long): LaunchResult =
                LaunchResult(true, sessionId, "")

            @JvmStatic
            fun failure(message: String?): LaunchResult =
                LaunchResult(false, -1L, if (message.isNullOrBlank()) "启动失败" else message)
        }
    }
}

internal object EngineSessionReconcilePolicy {
    fun shouldFinalize(state: String?, processId: Int, runningProcessIds: Set<Int>): Boolean =
        state == EnginePlaySessionJournal.STATE_FINISHED ||
            processId <= 0 ||
            processId !in runningProcessIds
}
