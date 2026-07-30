package com.akira.tyranoemu.remote;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import bridge.NativeBridge;
import org.tvp.kirikiri2.KR2Activity;

/**
 * Base activity for Kirikiroid134/139 KRKR engine launches.
 *
 * ARCHITECTURE NOTE:
 * This class historically used reflection to call com.apps.LauncherActivity for theme
 * colors (primary color, dark mode). This creates a reverse dependency from the engine
 * library module to the app application module, which violates Gradle module dependency
 * direction. We are migrating to passing these values via Intent extras:
 *   - "primaryColor" (int): theme primary color
 *   - "darkMode" (boolean): whether dark mode is active
 *
 * The reflection calls are retained as deprecated fallbacks for backward compatibility
 * with com.apps callers that have not yet been updated to pass these extras. Once all
 * callers are migrated, the reflection paths will be removed.
 */
public abstract class KirikiroidLauncherBaseActivity extends KR2Activity {
    private static final String TAG = "Kirikiroid2";
    private static final long SAFE_FALLBACK_REVEAL_MS = 20_000L;
    @SuppressLint("StaticFieldLeak") // Dedicated process activity; cleared immediately before process termination.
    public static Context app;
    private FrameLayout mask;
    private TextView maskMessage;
    private TextView maskHint;
    private TextView launchLogView;
    private TextView logAction;
    private KrLaunchProgressView launchProgress;
    private boolean expandedLog;
    private boolean rendererLogged;
    private boolean fallbackReveal;
    private boolean dialogWaiting;
    private boolean gameReadyWhileDialog;
    private boolean gameReadyConfirmed;
    private KrLaunchLogger.Listener launchLogListener;
    private volatile KrLaunchLogger.Event pendingLogEvent;
    private final AtomicBoolean logUiScheduled = new AtomicBoolean(false);
    private volatile boolean nativeBridgeInitialized;
    private volatile boolean destroyed;
    private volatile boolean firstFrameRendered;
    private volatile boolean launchDispatched;
    private volatile boolean launchSucceeded;
    private volatile boolean maskRevealRequested;
    private volatile long launchDispatchedElapsed;
    private final Runnable fallbackRevealRunnable = this::revealGameWithFallback;
    private int launchReadinessFrames;
    private String pendingGamePath;
    private boolean pendingMaps;
    private LaunchMaskState launchMaskState = LaunchMaskState.LAUNCHING;

    private enum LaunchMaskState {
        LAUNCHING,
        DIALOG_VISIBLE,
        GAME_VISIBLE,
        COMPLETE,
        ERROR
    }

    @Override
    public void onCreate(Bundle bundle) {
        String launchId = getIntent() == null ? null : getIntent().getStringExtra("launchId");
        KrLaunchLogger.start(this, launchId);
        String requestedPath = getIntent() == null ? null : getIntent().getStringExtra("path");
        KrLaunchLogger.info(
                KrLaunchLogger.Stage.CHECK_PATH,
                requestedPath == null || requestedPath.trim().isEmpty()
                        ? "未收到游戏启动路径"
                        : "已检查游戏路径与启动目标");
        String engineVersion = getIntent() == null ? "auto" : getIntent().getStringExtra("krEngineVersion");
        KrLaunchLogger.info(
                KrLaunchLogger.Stage.SELECT_ENGINE,
                "选择 Kirikiri " + (engineVersion == null || engineVersion.isEmpty() ? "自动版本" : engineVersion));
        Intent launchIntent = getIntent();
        int earlyBackgroundColor = launchIntent == null
                ? Color.rgb(244, 245, 245)
                : launchIntent.getIntExtra(
                        "themeColorBackground",
                        launchIntent.getBooleanExtra("darkMode", false) ? Color.BLACK : Color.rgb(244, 245, 245));
        getWindow().setBackgroundDrawable(new ColorDrawable(earlyBackgroundColor));
        configureLandscapeLoadingWindow(earlyBackgroundColor);
        doSetSystemUiVisibility();
        super.onCreate(bundle);
        app = this;
        if (getIntent().getBooleanExtra("originMode", false)) {
            return;
        }
        int primaryColor = launcherPrimaryColor();
        int backgroundColor = launchIntent == null
                ? launcherColor("launcher_bg_color", Color.rgb(244, 245, 245))
                : launchIntent.getIntExtra(
                        "themeColorBackground",
                        launcherColor("launcher_bg_color", Color.rgb(244, 245, 245)));
        int textColor = launcherColor("launcher_text_color", Color.rgb(20, 34, 27));
        int mutedTextColor = launcherColor("launcher_text_muted_color", Color.rgb(130, 144, 138));

        FrameLayout launchMask = new FrameLayout(this);
        launchMask.setBackgroundColor(backgroundColor);
        configureLandscapeLoadingWindow(backgroundColor);
        // Never expose the KRKR shell scene while the selected game is being
        // started. The overlay is removed only after the game's first frames.
        FrameLayout safeContent = new FrameLayout(this);
        launchMask.addView(safeContent, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout loadingPanel = new LinearLayout(this);
        loadingPanel.setOrientation(LinearLayout.VERTICAL);
        loadingPanel.setGravity(Gravity.CENTER_HORIZONTAL);
        loadingPanel.setPadding(dp(22), dp(20), dp(22), dp(16));

        KrLaunchProgressView progress = new KrLaunchProgressView(this);
        progress.setColors(primaryColor, withAlpha(primaryColor, 48), textColor);
        progress.setContentDescription("游戏加载进度");

        TextView title = new TextView(this);
        title.setText("正在启动游戏");
        title.setTextColor(textColor);
        title.setTextSize(16.0f);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(0);
        loadingPanel.addView(title, titleParams);

        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(dp(92), dp(92));
        spinnerParams.gravity = Gravity.CENTER_HORIZONTAL;
        spinnerParams.topMargin = dp(14);
        loadingPanel.addView(progress, spinnerParams);

        TextView hint = new TextView(this);
        hint.setText("请稍候，正在准备游戏内容");
        hint.setTextColor(mutedTextColor);
        hint.setTextSize(11.0f);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = dp(10);
        loadingPanel.addView(hint, hintParams);

        TextView logs = new TextView(this);
        logs.setTextColor(mutedTextColor);
        logs.setTextSize(10.0f);
        logs.setGravity(Gravity.START);
        logs.setMaxLines(4);
        logs.setLineSpacing(0f, 1.12f);
        logs.setTypeface(Typeface.MONOSPACE);
        logs.setText(KrLaunchLogger.allText());
        LinearLayout.LayoutParams logsParams = new LinearLayout.LayoutParams(dp(410), -2);
        logsParams.topMargin = dp(14);
        loadingPanel.addView(logs, logsParams);

        TextView action = new TextView(this);
        action.setText("查看全部  ·  复制日志");
        action.setTextColor(primaryColor);
        action.setTextSize(11f);
        action.setGravity(Gravity.CENTER);
        action.setPadding(dp(10), dp(10), dp(10), dp(6));
        action.setOnClickListener(view -> {
            expandedLog = !expandedLog;
            if (launchLogView != null) launchLogView.setMaxLines(expandedLog ? 12 : 4);
            if (expandedLog) {
                action.setText("收起日志  ·  长按复制");
            } else {
                action.setText("查看全部  ·  复制日志");
                copyLaunchLog();
            }
        });
        action.setOnLongClickListener(view -> {
            copyLaunchLog();
            return true;
        });
        loadingPanel.addView(action, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        safeContent.addView(loadingPanel, panelParams);
        safeContent.setOnApplyWindowInsetsListener((view, insets) -> {
            // Match PadUi: the background can occupy the whole display, while
            // interactive/readable content stays clear of cutouts and bars.
            safeContent.setPadding(
                    insets.getSystemWindowInsetLeft(),
                    insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(),
                    insets.getSystemWindowInsetBottom());
            return insets;
        });
        safeContent.requestApplyInsets();
        launchMask.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
        this.mask = launchMask;
        this.maskMessage = title;
        this.maskHint = hint;
        this.launchProgress = progress;
        this.launchLogView = logs;
        this.logAction = action;
        this.launchLogListener = (event, allText) -> {
            pendingLogEvent = event;
            scheduleLogUiFlush();
        };
        KrLaunchLogger.addListener(launchLogListener);
        progress.setProgressAnimated(KrLaunchLogger.Stage.SELECT_ENGINE.progress);
        this.mFrameLayout.addView(launchMask);
        NativeBridge.setKrkrGameReadyListener(this::onGameReadySignal);
        NativeBridge.setKrkrDialogListener(new NativeBridge.KrkrDialogListener() {
            @Override
            public void onShown() {
                onEngineDialogShown();
            }

            @Override
            public void onDismissed() {
                onEngineDialogDismissed();
            }
        });
        String path = getIntent().getStringExtra("path");
        if (path != null && path.length() != 0) {
            requestGameLaunch(path, false);
        } else {
            finish();
        }
    }
    @Override
    public void onLoadNativeLibraries() {
        KrLaunchLogger.info(KrLaunchLogger.Stage.LOAD_NATIVE, "正在加载 " + soName() + " Native 库");
        boolean initialized = NativeBridge.initialize(soName());
        nativeBridgeInitialized = initialized;
        Log.i(TAG, "native initialize result=" + initialized + " so=" + soName());
        if (!initialized) {
            KrLaunchLogger.error(KrLaunchLogger.Stage.INIT_BRIDGE, "NativeBridge 初始化失败");
            Log.e(TAG, "native bridge initialization failed; skip KRKR hook setup");
            return;
        }
        if (NativeBridge.isConsoleLogHookInstalled()) {
            KrLaunchLogger.info(
                    KrLaunchLogger.Stage.INIT_BRIDGE,
                    "TVPConsoleLog 已连接，正在显示 KRKR 原始控制台日志");
        } else {
            KrLaunchLogger.warning(
                    KrLaunchLogger.Stage.INIT_BRIDGE,
                    "当前内核无法接管 TVPConsoleLog，已降级为结构化启动日志");
        }
        KrLaunchLogger.info(KrLaunchLogger.Stage.INIT_BRIDGE, "NativeBridge 初始化完成");
        // Do not bypass TVPMainScene's internal delay. It serializes teardown of
        // the selector UI before the game UI is created; forcing it to zero can
        // leave the KRKR shell above an otherwise running game.
        Log.i(TAG, "direct game launch waits for native scene transition so=" + soName());
        Intent intent = getIntent();
        String dialoguePatchPath = intent == null ? "" : intent.getStringExtra("krDialoguePatchPath");
        String dialoguePatchTarget = intent == null ? "" : intent.getStringExtra("krDialoguePatchTarget");
        boolean dialoguePatch = dialoguePatchPath != null && !dialoguePatchPath.trim().isEmpty() &&
                dialoguePatchTarget != null && !dialoguePatchTarget.trim().isEmpty();
        if (dialoguePatch) {
            NativeBridge.configureDialoguePatch(dialoguePatchTarget, dialoguePatchPath);
        } else {
            NativeBridge.clearDialoguePatch();
        }
        KrLaunchLogger.info(
                KrLaunchLogger.Stage.STORAGE,
                "使用游戏原目录与 savedata");
        if (intent == null) {
            Log.i(TAG, "native interceptor skipped: no launch intent");
            return;
        }

        if (!dialoguePatch) {
            Log.i(TAG, "native interceptor skipped: no read-only dialogue overlay");
            return;
        }
        String prefix = null;
        try {
            String rawPath = intent.getStringExtra("path");
            if (rawPath != null && !rawPath.trim().isEmpty()) {
                String resolved = normalizeKrPath(rawPath);
                File root = new File(resolved);
                if (root.isFile()) root = root.getParentFile();
                if (root != null) {
                    Log.i(TAG, "KRKR read-only dialogue overlay hook enabled");
                    prefix = storagePrefix(root.getAbsolutePath());
                }
            }
        } catch (Throwable t) {
            KrLaunchLogger.warning(KrLaunchLogger.Stage.STORAGE, "兼容存储路径准备失败：" + safeMessage(t));
            Log.w(TAG, "resolve scoped hook prefix failed", t);
        }
        if (prefix != null) {
            try {
                NativeBridge.interceptor(prefix);
                NativeBridge.relocate();
                Log.i(TAG, "native interceptor enabled prefix=" + prefix);
            } catch (Throwable t) {
                KrLaunchLogger.error(KrLaunchLogger.Stage.STORAGE, "文件兼容桥启用失败：" + safeMessage(t));
                Log.e(TAG, "enable native interceptor failed", t);
            }
        } else {
            Log.w(TAG, "native interceptor skipped: empty prefix");
        }
    }

    private synchronized void requestGameLaunch(String path, boolean maps) {
        if (!nativeBridgeInitialized) {
            Log.e(TAG, "skip launch because native bridge was not initialized");
            showLaunchFailure("KRKR 引擎初始化失败");
            return;
        }
        pendingGamePath = path;
        pendingMaps = maps;
        dispatchPendingLaunchOnGlThread();
    }

    @Override
    protected void onCocosRendererReady() {
        KrLaunchLogger.info(KrLaunchLogger.Stage.RENDERER, "渲染器已创建，等待第一个画面");
        // nativeInit only constructs the scene.  Its initial UI tasks are still
        // queued until the first render pass, so starting a game here lets the
        // shell initialise after (and above) the game scene.
    }

    @Override
    protected void onCocosFrameRendered() {
        if (!firstFrameRendered) {
            firstFrameRendered = true;
            if (!rendererLogged) {
                rendererLogged = true;
                KrLaunchLogger.info(KrLaunchLogger.Stage.RENDERER, "已收到第一个渲染帧");
            }
        }
        // The file selector is registered a few frames after the renderer. Keep
        // checking until startupFrom can actually dismiss it.
        if (!launchDispatched) dispatchPendingLaunchOnGlThread();
    }

    private synchronized void dispatchPendingLaunchOnGlThread() {
        if (!firstFrameRendered || launchDispatched || pendingGamePath == null || destroyed || isFinishing()) return;
        if (!NativeBridge.isLaunchSceneReady(soName())) {
            launchReadinessFrames++;
            return;
        }
        KrLaunchLogger.info(KrLaunchLogger.Stage.SCENE_READY, "KR 场景已就绪");
        launchDispatched = true;
        try {
            launchSucceeded = NativeBridge.launch(soName(), pendingGamePath, pendingMaps);
            if (launchSucceeded) {
                launchDispatchedElapsed = SystemClock.elapsedRealtime();
                KrLaunchLogger.info(KrLaunchLogger.Stage.DISPATCH, "已派发启动文件：" + new File(pendingGamePath).getName());
            }
            Log.i(TAG, "renderer-ready launch result=" + launchSucceeded + " path=" + pendingGamePath
                    + " frames=" + launchReadinessFrames);
        } catch (Throwable t) {
            KrLaunchLogger.error(KrLaunchLogger.Stage.DISPATCH, "启动派发异常：" + safeMessage(t));
            Log.e(TAG, "renderer-ready launch failed", t);
        }
        if (!launchSucceeded) {
            showLaunchFailure("启动失败");
        } else if (mask != null) {
            // Keep the KRKR shell hidden until the native update hook reports
            // that doStartup and the menu transition completed. The fallback
            // includes the engine's original ten-second scene handoff.
            mask.postDelayed(fallbackRevealRunnable, SAFE_FALLBACK_REVEAL_MS);
        }
    }

    private void revealGameWithFallback() {
        fallbackReveal = true;
        KrLaunchLogger.warning(KrLaunchLogger.Stage.DISPATCH, "未收到就绪回调，使用兼容启动回退");
        hideLaunchMask(false);
    }

    private void onGameReadySignal() {
        if (destroyed || gameReadyConfirmed) return;
        gameReadyConfirmed = true;
        launchMaskState = LaunchMaskState.GAME_VISIBLE;
        if (maskRevealRequested) {
            launchMaskState = LaunchMaskState.COMPLETE;
            KrLaunchLogger.info(KrLaunchLogger.Stage.GAME_READY, "游戏窗口已完成首帧渲染");
            return;
        }
        fallbackReveal = false;
        revealGame();
    }

    private void revealGame() {
        if (destroyed || mask == null || maskRevealRequested) return;
        // 弹窗未确认时不隐藏启动遮罩，防止引擎在用户确认前就显示游戏画面
        if (dialogWaiting || KR2Activity.isDialogShowing()) {
            gameReadyWhileDialog = true;
            keepMaskBehindEngineDialog();
            return;
        }
        maskRevealRequested = true;
        launchMaskState = LaunchMaskState.COMPLETE;
        KrLaunchLogger.info(
                KrLaunchLogger.Stage.GAME_READY,
                fallbackReveal ? "兼容启动完成，正在切换到游戏" : "游戏已就绪");
        mask.post(() -> {
            if (!destroyed && mask != null) {
                Log.i(TAG, "hide KRKR launch mask after game-ready signal");
                mask.animate().alpha(0.0f).setDuration(150L).withEndAction(() -> {
                    if (mask != null) mask.setVisibility(android.view.View.GONE);
                    if (launchProgress != null) launchProgress.stopAnimations();
                    KrLaunchLogger.removeListener(launchLogListener);
                }).start();
            }
        });
    }

    private void hideLaunchMask(boolean confirmedReady) {
        if (destroyed || mask == null || maskRevealRequested) return;
        if (dialogWaiting || KR2Activity.isDialogShowing()) {
            if (confirmedReady) gameReadyWhileDialog = true;
            keepMaskBehindEngineDialog();
            return;
        }
        maskRevealRequested = true;
        launchMaskState = confirmedReady ? LaunchMaskState.COMPLETE : LaunchMaskState.GAME_VISIBLE;
        mask.post(() -> {
            if (destroyed || mask == null) return;
            mask.animate().alpha(0f).setDuration(150L).withEndAction(() -> {
                if (mask != null) mask.setVisibility(View.GONE);
                if (launchProgress != null) launchProgress.stopAnimations();
                KrLaunchLogger.removeListener(launchLogListener);
            }).start();
        });
    }

    private void onEngineDialogShown() {
        dialogWaiting = true;
        if (mask != null) mask.removeCallbacks(fallbackRevealRunnable);
        KrLaunchLogger.info(KrLaunchLogger.Stage.DISPATCH, "检测到游戏启动弹窗，保持稳定背景并等待玩家选择");
        keepMaskBehindEngineDialog();
    }

    private void keepMaskBehindEngineDialog() {
        runOnUiThread(() -> {
            if (destroyed || mask == null || maskRevealRequested) return;
            if (launchMaskState == LaunchMaskState.DIALOG_VISIBLE ||
                    launchMaskState == LaunchMaskState.COMPLETE) return;
            launchMaskState = LaunchMaskState.DIALOG_VISIBLE;
            mask.removeCallbacks(fallbackRevealRunnable);
            mask.animate().cancel();
            mask.setAlpha(1f);
            mask.setVisibility(View.VISIBLE);
            if (maskHint != null) maskHint.setText("请确认游戏提示，之后将继续启动");
        });
    }

    private void onEngineDialogDismissed() {
        dialogWaiting = false;
        KrLaunchLogger.info(KrLaunchLogger.Stage.DISPATCH, "游戏启动弹窗已处理，继续启动");
        if (gameReadyConfirmed || gameReadyWhileDialog) {
            gameReadyWhileDialog = false;
            revealGame();
            return;
        }
        launchMaskState = LaunchMaskState.LAUNCHING;
        if (maskHint != null) maskHint.setText("请稍候，正在准备游戏内容");
        if (mask != null && launchSucceeded && !maskRevealRequested) {
            long elapsed = launchDispatchedElapsed <= 0L
                    ? 0L
                    : Math.max(0L, SystemClock.elapsedRealtime() - launchDispatchedElapsed);
            mask.postDelayed(fallbackRevealRunnable, Math.max(0L, SAFE_FALLBACK_REVEAL_MS - elapsed));
        }
    }

    private void scheduleLogUiFlush() {
        TextView target = launchLogView;
        if (destroyed || target == null || !logUiScheduled.compareAndSet(false, true)) return;
        target.postDelayed(this::flushLogUi, 50L);
    }

    private void flushLogUi() {
        KrLaunchLogger.Event event = pendingLogEvent;
        if (!destroyed && event != null) {
            if (launchLogView != null) {
                launchLogView.setText(lastLines(KrLaunchLogger.allText(), expandedLog ? 12 : 4));
            }
            if (launchProgress != null) {
                launchProgress.setProgressAnimated(event.stage.progress);
                if (event.level == KrLaunchLogger.Level.ERROR) {
                    launchProgress.setErrorColor(
                            launcherColor("launcher_danger_color", Color.rgb(255, 69, 58)));
                }
            }
        }
        logUiScheduled.set(false);
        if (pendingLogEvent != event) scheduleLogUiFlush();
    }

    private void showLaunchFailure(String message) {
        KrLaunchLogger.error(KrLaunchLogger.Stage.DISPATCH, message);
        runOnUiThread(() -> {
            if (destroyed || isFinishing() || mask == null) return;
            if (maskMessage != null) maskMessage.setText(message);
            if (maskHint != null) maskHint.setText("请返回后重试");
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private void copyLaunchLog() {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("KRKR 启动日志", KrLaunchLogger.allText()));
            if (maskHint != null) maskHint.setText("日志已复制");
        } catch (Throwable ignored) { }
    }

    private static String lastLines(String text, int count) {
        if (text == null || text.isEmpty()) return "";
        String[] lines = text.split("\\n");
        int start = Math.max(0, lines.length - count);
        StringBuilder out = new StringBuilder();
        for (int i = start; i < lines.length; i++) {
            if (out.length() > 0) out.append('\n');
            out.append(lines[i]);
        }
        return out.toString();
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null) return "未知错误";
        String message = throwable.getMessage().replace('\n', ' ').replace('\r', ' ').trim();
        return message.length() > 120 ? message.substring(0, 120) : message;
    }

    private void configureLandscapeLoadingWindow(int backgroundColor) {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(backgroundColor);
        window.setNavigationBarColor(backgroundColor);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(attributes);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        if (!isLauncherDarkMode()) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        window.getDecorView().setSystemUiVisibility(flags);
    }

    private int launcherPrimaryColor() {
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("primaryColor")) {
            return intent.getIntExtra("primaryColor", Color.rgb(24, 185, 120));
        }
        // Deprecated reflection fallback: com.apps should pass "primaryColor" via Intent extra.
        // TODO: remove reflection once com.apps migration is complete.
        try {
            Object value = Class.forName("com.apps.LauncherActivity")
                    .getMethod("launcherPrimaryColor", Context.class)
                    .invoke(null, this);
            if (value instanceof Integer) return (Integer) value;
        } catch (Throwable ignored) { }
        return launcherColor("launcher_primary_color", Color.rgb(24, 185, 120));
    }

    @SuppressLint("DiscouragedApi") // Engine cannot compile against the app module's generated R class.
    private int launcherColor(String name, int fallback) {
        Context uiContext = launcherUiContext();
        int id = uiContext.getResources().getIdentifier(name, "color", getPackageName());
        return id == 0 ? fallback : uiContext.getColor(id);
    }

    private Context launcherUiContext() {
        // Deprecated reflection fallback: com.apps should pass "darkMode" via Intent extra.
        // Note: this Context does NOT automatically resolve values-night resources based on
        // the "darkMode" Intent extra; wrapLauncherUiMode in com.apps wraps the Context with
        // a UiModeManager override to force day/night resource resolution. When the reflection
        // fallback is removed, callers passing "darkMode" extra must also apply the night mode
        // override via AppCompatDelegate.setDefaultNightMode() or similar before retrieving colors.
        try {
            Object value = Class.forName("com.apps.LauncherActivity")
                    .getMethod("wrapLauncherUiMode", Context.class)
                    .invoke(null, this);
            if (value instanceof Context) return (Context) value;
        } catch (Throwable ignored) { }
        return this;
    }

    private boolean isLauncherDarkMode() {
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("darkMode")) {
            return intent.getBooleanExtra("darkMode", false);
        }
        // Deprecated reflection fallback: com.apps should pass "darkMode" via Intent extra.
        try {
            Object value = Class.forName("com.apps.LauncherActivity")
                    .getMethod("isLauncherDarkMode", Context.class)
                    .invoke(null, this);
            if (value instanceof Boolean) return (Boolean) value;
        } catch (Throwable ignored) { }
        return (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private static String normalizeKrPath(String path) {
        if (path == null) return "";
        String p = path.trim();
        if (p.startsWith("file://")) p = p.substring("file://".length());
        while (p.startsWith("./")) p = p.substring(2);
        if (p.startsWith("storage/")) p = "/" + p;
        return p;
    }

    @SuppressLint("SdCardPath") // Normalizes legacy KR paths for the read-only dialogue overlay.
    private static String storagePrefix(String path) {
        String p = normalizeKrPath(path);
        String lower = p.toLowerCase(Locale.ROOT);
        if (lower.startsWith("/storage/emulated/0/")) return "/storage/emulated/0";
        if (lower.startsWith("/sdcard/")) return "/sdcard";
        if (lower.startsWith("/storage/")) {
            String rest = p.substring("/storage/".length());
            int slash = rest.indexOf('/');
            if (slash > 0) return "/storage/" + rest.substring(0, slash);
        }
        return p;
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Intent oldIntent = getIntent();
        if (oldIntent == null || intent == null) return;
        String oldPath = oldIntent.getStringExtra("path");
        String newPath = intent.getStringExtra("path");
        if (newPath != null && !newPath.equals(oldPath)) {
            Toast.makeText(this, "已有游戏在运行，请先存档并退出游戏后再启动新游戏", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        setRequestedOrientation(getIntent().getIntExtra("orientation", 6));
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        String focus = getIntent().getStringExtra("focus");
        boolean forceFocus = focus != null && Boolean.parseBoolean(focus);
        super.onWindowFocusChanged(hasFocus || forceFocus);
        if (hasFocus || forceFocus) doSetSystemUiVisibility();
    }

    @Override
    @SuppressLint("MissingSuperCall")
    public void onDestroy() {
        try {
            destroyed = true;
            if (mask != null) {
                mask.removeCallbacks(fallbackRevealRunnable);
                mask.removeCallbacks(this::flushLogUi);
                mask.animate().cancel();
            }
            mask = null;
            maskMessage = null;
            maskHint = null;
            launchLogView = null;
            logAction = null;
            if (launchProgress != null) launchProgress.stopAnimations();
            launchProgress = null;
            KrLaunchLogger.removeListener(launchLogListener);
            launchLogListener = null;
            NativeBridge.setKrkrGameReadyListener(null);
            NativeBridge.setKrkrDialogListener(null);
            // A dialogue overlay is scoped to one KRKR Activity/game.  Clear it
            // before the dedicated process is reused so a later game can never
            // inherit the previous game's Config.tjs replacement.
            NativeBridge.clearDialoguePatch();
            if (app == this) app = null;
        } catch (Throwable ignored) { }
        // This Activity has a dedicated process. Do not enter Cocos teardown first:
        // its RenderThread can still lock native state after that state is destroyed.
        Log.i(TAG, "terminate dedicated KR process before Cocos teardown");
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    public abstract String soName();
}
