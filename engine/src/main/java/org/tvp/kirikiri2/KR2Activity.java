package org.tvp.kirikiri2;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Debug;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import bridge.NativeBridge;
import bridge.KrPathUtils;
import java.util.Locale;
import org.cocos2dx.lib.Cocos2dxActivity;
import org.cocos2dx.lib.Cocos2dxGLSurfaceView;

@SuppressLint("StaticFieldLeak") // JNI-compatible static handles are cleared in onDestroy().
public class KR2Activity extends Cocos2dxActivity {
    public static KR2Activity sInstance;
    static Handler msgHandler;
    static KrDialogModel mDialogMessage = new KrDialogModel();
    static Dialog mCurrentDialog; // 防止 GC 回收导致弹窗被自动 dismiss
    protected static View mTextEdit;
    static ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
    static ActivityManager mAcitivityManager = null;
    static Debug.MemoryInfo mDbgMemoryInfo = new Debug.MemoryInfo();
    SharedPreferences Sp;
    static EnginePlaySessionJournal sPlaySessionJournal;

    public static KR2Activity GetInstance() { return sInstance; }
    public static KR2Activity getInstance() { return sInstance; }

    public static String GetVersion() {
        try { return sInstance.getPackageManager().getPackageInfo(sInstance.getPackageName(), 0).versionName; }
        catch (PackageManager.NameNotFoundException e) { return null; }
    }

    public static boolean CreateFolders(String path) {
        try {
            File f = new File(KrPathUtils.canonicalizeKrStoragePath(path));
            boolean ok = f.exists() || f.mkdirs();
            if (!ok && isSafFallbackEnabled()) ok = NativeBridge.createDirectoryViaSafIfPossible(path);
            android.util.Log.i("KR2Activity", "CreateFolders " + path + " -> " + f.getAbsolutePath() + " ok=" + ok);
            return ok;
        } catch (Throwable t) {
            return isSafFallbackEnabled() && NativeBridge.createDirectoryViaSafIfPossible(path);
        }
    }

    public static boolean DeleteFile(String path) {
        try {
            File original = new File(KrPathUtils.canonicalizeKrStoragePath(path));
            boolean existed = original.exists();
            boolean ok = !existed || original.delete();
            if ((!ok || !existed) && isSafFallbackEnabled()) ok = NativeBridge.deleteViaSafIfPossible(path) || ok;
            android.util.Log.i("KR2Activity", "DeleteFile " + path + " original=" + original.getAbsolutePath() + " existed=" + existed + " ok=" + ok);
            return ok;
        } catch (Throwable t) { return false; }
    }

    public static boolean RenameFile(String from, String to) {
        try {
            File originalSrc = new File(KrPathUtils.canonicalizeKrStoragePath(from));
            File dst = new File(KrPathUtils.canonicalizeKrStoragePath(to));
            File parent = dst.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            File src = originalSrc;
            boolean ok;
            boolean srcExisted = src.exists();
            if (!srcExisted) {
                if (isSafFallbackEnabled() && NativeBridge.existsViaSafIfPossible(from)) {
                    ok = NativeBridge.renameViaSafIfPossible(from, to);
                } else {
                    ok = true;
                }
            } else {
                ok = src.renameTo(dst);
                if (!ok) ok = copyThenDelete(src, dst);
                if (!ok && isSafFallbackEnabled()) ok = NativeBridge.renameViaSafIfPossible(from, to);
            }
            android.util.Log.i("KR2Activity", "RenameFile " + from + " -> " + to + " originalSrc=" + originalSrc.getAbsolutePath() + " dst=" + dst.getAbsolutePath() + " srcExisted=" + srcExisted + " ok=" + ok);
            return ok;
        } catch (Throwable t) { return false; }
    }

    public static boolean WriteFile(String path, byte[] data) {
        try {
            String mapped = KrPathUtils.canonicalizeKrStoragePath(path);
            File f = new File(mapped);
            File parent = f.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                if (isSafFallbackEnabled()) return NativeBridge.writeViaSafIfPossible(path, data);
                return false;
            }
            try (FileOutputStream fos = new FileOutputStream(f)) {
                if (data != null) fos.write(data);
            }
            android.util.Log.i("KR2Activity", "WriteFile " + path + " -> " + f.getAbsolutePath() + " bytes=" + (data == null ? 0 : data.length));
            return true;
        } catch (Throwable t) {
            return isSafFallbackEnabled() && NativeBridge.writeViaSafIfPossible(path, data);
        }
    }

    public static void MessageController(int what, int arg1, int arg2) {
        if (msgHandler == null) return;
        Message m = msgHandler.obtainMessage();
        m.what = what;
        m.arg1 = arg1;
        m.arg2 = arg2;
        msgHandler.sendMessage(m);
    }

    public static String getLocaleName() {
        Locale locale = Locale.getDefault();
        String language = locale.getLanguage();
        String country = locale.getCountry();
        return country.isEmpty() ? language : language + "_" + country.toLowerCase(Locale.ROOT);
    }

    public static void ShowMessageBox(String title, String msg, String[] buttons) {
        KrDialogModel dialogModel = mDialogMessage;
        dialogModel.title = title;
        dialogModel.message = msg;
        dialogModel.buttons = buttons;
        if (msgHandler != null) msgHandler.post(new ShowMessageBoxRunnable());
    }
    public static void ShowInputBox(String title, String msg, String text, String[] buttons) {
        KrDialogModel dialogModel = mDialogMessage;
        dialogModel.title = title;
        dialogModel.message = msg;
        dialogModel.buttons = buttons;
        if (msgHandler != null) msgHandler.post(new ShowInputBoxRunnable(text));
    }

    /** 弹窗是否正在显示（供 r.revealGame 检查，防止未确认就隐藏启动遮罩） */
    public static boolean isDialogShowing() {
        return mCurrentDialog != null && mCurrentDialog.isShowing();
    }

    /** KrDialogStyle 回调 — 仅消息弹窗 */
    static void notifyDialogResult(int which) {
        onMessageBoxOK(which);
    }
    /** KrDialogStyle 回调 — 输入弹窗 */
    static void notifyDialogResult(int which, String text) {
        onMessageBoxText(text);
        onMessageBoxOK(which);
    }

    public static void showTextInput(int x, int y, int w, int h) {
        if (msgHandler == null) return;
        ShowTextInputRunnable r = new ShowTextInputRunnable();
        r.x = x;
        r.y = y;
        r.width = w;
        r.height = h;
        msgHandler.post(r);
    }
    public static void hideTextInput() { if (msgHandler != null) msgHandler.post(KR2Activity::lambdaHideTextInput); }
    private static void lambdaHideTextInput() {
        View view = mTextEdit;
        if (view != null) {
            view.setVisibility(View.GONE);
            ((InputMethodManager) sInstance.getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    public static void updateMemoryInfo() {
        if (mAcitivityManager == null) mAcitivityManager = (ActivityManager) sInstance.getSystemService(ACTIVITY_SERVICE);
        mAcitivityManager.getMemoryInfo(memoryInfo);
        Debug.getMemoryInfo(mDbgMemoryInfo);
    }
    public static long getAvailMemory() { return memoryInfo.availMem; }
    public static long getUsedMemory() { return mDbgMemoryInfo.getTotalPss(); }
    public static void exit() {
        // All KRKR activities are declared in the isolated :kirikiri2 process.
        // Calling finish() first causes GLSurfaceView.onPause() to asynchronously
        // tear down Cocos state while HWUI worker threads can still access it. On
        // current Android releases this becomes a FORTIFY abort on a destroyed
        // mutex. End the dedicated process at the engine's real exit callback
        // instead; Android will return to the launcher task without running that
        // unsafe mixed Java/native teardown.
        try {
            if (sPlaySessionJournal != null) sPlaySessionJournal.finish();
            android.util.Log.i("KR2Activity", "engine exit: terminate dedicated KRKR process");
            android.os.Process.killProcess(android.os.Process.myPid());
        } catch (Throwable ignored) { }
    }
    public static boolean isWritableNormal(String path) { return true; }
    public static boolean isWritableNormalOrSaf(String path) { return true; }
    public static void requireLEXA(String path) { }

    private static boolean copyThenDelete(File src, File dst) {
        try {
            File parent = dst.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            copyFile(src, dst);
            return src.delete();
        } catch (Throwable t) {
            android.util.Log.w("KR2Activity", "copyThenDelete failed " + src + " -> " + dst, t);
            return false;
        }
    }

    private static boolean isSafFallbackEnabled() {
        try {
            Intent intent = sInstance != null ? sInstance.getIntent() : null;
            return intent != null && intent.getBooleanExtra("safFileFallback", false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void copyFile(File src, File dst) throws java.io.IOException {
        File parent = dst.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (FileInputStream in = new FileInputStream(src); FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.flush();
        }
    }

    private static native void initDump(String path);
    private static native void nativeOnLowMemory();
    private static native boolean nativeGetHideSystemButton();
    public static native void nativeCharInput(int ch);
    public static native void nativeCommitText(String text, int newCursorPosition);
    public static native void nativeDeleteBackward();
    public static native void nativeHoverMoved(float x, float y);
    public static native void nativeInsertText(String text);
    public static native boolean nativeKeyAction(int keyCode, boolean down);
    public static native void nativeMouseScrolled(float v);
    public static native void nativeTouchesBegin(int id, float x, float y);
    public static native void nativeTouchesCancel(int[] ids, float[] xs, float[] ys);
    public static native void nativeTouchesEnd(int id, float x, float y);
    public static native void nativeTouchesMove(int[] ids, float[] xs, float[] ys);
    public static native void onBannerSizeChanged(int w, int h);
    public static native void onMessageBoxOK(int which);
    public static native void onMessageBoxText(String text);
    public static native void onNativeInit();

    @Override public void onLoadNativeLibraries() {
        System.loadLibrary("SDL2");
        System.loadLibrary("ffmpeg");
        System.loadLibrary("game");
        System.loadLibrary("krkr_bridge");
    }
    @Override public void onCreate(Bundle savedInstanceState) {
        sInstance = this;
        msgHandler = new Handler(Looper.getMainLooper()) { @Override public void handleMessage(Message msg) { KR2Activity.this.handleMessage(msg); } };
        Sp = PreferenceManager.getDefaultSharedPreferences(this);
        super.onCreate(savedInstanceState);
        sPlaySessionJournal = EnginePlaySessionJournal.attach(this, getIntent());
        // 1.2.6 libgame126.so 缺失 initDump/nativeOnLowMemory 的 JNI 实现，
        // 此处用 try-catch 兜底以兼容该版本；1.3.4/1.3.9 的 .so 都有这两个方法，不会进入 catch。
        try {
            initDump(getFilesDir().getAbsolutePath() + "/dump");
        } catch (UnsatisfiedLinkError e) {
            android.util.Log.w("KR2Activity", "initDump unavailable in this engine version: " + e.getMessage());
        }
        android.util.Log.i("KR2Activity", "KRKR saves use the game-local savedata directory");
    }


    public void handleMessage(Message message) { }

    public void doSetSystemUiVisibility() { getWindow().getDecorView().setSystemUiVisibility(5894); }
    public void hideSystemUI() {
        if (nativeGetHideSystemButton()) doSetSystemUiVisibility();
    }

    @Override public Cocos2dxGLSurfaceView onCreateView() {
        KrGLSurfaceView gl = new KrGLSurfaceView(this);
        hideSystemUI();
        if (mGLContextAttrs != null && mGLContextAttrs.length > 3 && mGLContextAttrs[3] > 0) gl.getHolder().setFormat(-3);
        if (mGLContextAttrs != null) gl.setEGLConfigChooser(this.new Cocos2dxEGLConfigChooser(this, mGLContextAttrs));
        return gl;
    }

    @Override public void onResume() {
        super.onResume();
        if (sPlaySessionJournal != null) sPlaySessionJournal.resume();
        doSetSystemUiVisibility();
    }
    @Override public void onPause() {
        if (sPlaySessionJournal != null) sPlaySessionJournal.pause();
        super.onPause();
    }
    @Override protected void onEngineForegroundChanged(final boolean foreground) {
        runOnGLThread(new Runnable() {
            @Override public void run() {
                boolean applied = false;
                try {
                    applied = NativeBridge.setAudioForeground(foreground);
                } catch (Throwable error) {
                    android.util.Log.w("KR2Activity", "KRKR audio lifecycle bridge failed foreground=" + foreground, error);
                }
                android.util.Log.i("KR2Activity", "KRKR audio foreground=" + foreground + " applied=" + applied);
            }
        });
    }
    @Override public void onDestroy() {
        try {
            if (sPlaySessionJournal != null) sPlaySessionJournal.finish();
            sPlaySessionJournal = null;
            android.util.Log.i("KR2Activity", "destroy KR2Activity");
            mTextEdit = null;
            if (msgHandler != null) msgHandler.removeCallbacksAndMessages(null);
            msgHandler = null;
            mCurrentDialog = null;
            if (sInstance == this) sInstance = null;
        } catch (Throwable ignored) { }
        super.onDestroy();
    }
    @Override public void onLowMemory() {
        // 1.2.6 libgame126.so 缺失 nativeOnLowMemory JNI 实现，try-catch 兜底。
        try { nativeOnLowMemory(); } catch (UnsatisfiedLinkError ignored) { }
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        // 弹窗显示期间，阻止 Cocos2dx 恢复 GL 线程（super 会调用 resumeIfHasFocus），
        // 防止引擎在弹窗未确认时自动继续执行。
        if (hasFocus && mCurrentDialog != null && mCurrentDialog.isShowing()) {
            doSetSystemUiVisibility();
            return;
        }
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) doSetSystemUiVisibility();
    }
    public String[] getStoragePath() {
        // The native engine uses this array for writable data and archive/plugin
        // discovery. Keep the real game root first so savedata stays beside the game.
        java.util.LinkedHashSet<String> paths = new java.util.LinkedHashSet<>();
        try {
            Intent intent = getIntent();
            if (intent != null) {
                addKrStoragePathFromIntent(paths, intent, "projectRoot");
                addKrStoragePathFromIntent(paths, intent, "gamedir");
                addKrStoragePathFromIntent(paths, intent, "gamePath");
                addKrStoragePathFromIntent(paths, intent, "path");
                addKrStoragePathFromIntent(paths, intent, "rootUri");
            }
        } catch (Throwable t) {
            android.util.Log.w("KR2Activity", "collect intent storage path failed", t);
        }
        try {
            File appExternal = getExternalFilesDir(null);
            if (appExternal != null) addKrStoragePath(paths, appExternal.getAbsolutePath());
        } catch (Throwable ignored) { }
        addKrStoragePath(paths, Environment.getExternalStorageDirectory().getAbsolutePath());
        if (paths.isEmpty()) return new String[]{Environment.getExternalStorageDirectory().getAbsolutePath()};
        String[] out = paths.toArray(new String[0]);
        android.util.Log.i("KR2Activity", "getStoragePath " + java.util.Arrays.toString(out));
        return out;
    }

    private static void addKrStoragePathFromIntent(java.util.LinkedHashSet<String> out, Intent intent, String key) {
        if (intent == null || key == null) return;
        addKrStoragePath(out, intent.getStringExtra(key));
    }

    @SuppressLint("SdCardPath") // The native KR engine still emits these aliases; SAF handles actual access.
    private static void addKrStoragePath(java.util.LinkedHashSet<String> out, String rawPath) {
        if (out == null || rawPath == null) return;
        String p = KrPathUtils.normalizeFilePath(rawPath);
        if (p == null || p.trim().isEmpty()) return;
        if (p.startsWith("content://")) p = contentUriToRawPath(p);
        p = KrPathUtils.normalizeFilePath(p);
        if (p == null || !p.startsWith("/")) return;
        while (p.endsWith("/") && p.length() > 1) p = p.substring(0, p.length() - 1);
        String lower = p.toLowerCase(Locale.ROOT);
        if (lower.equals("/sdcard") || lower.startsWith("/sdcard/")) {
            out.add("/sdcard");
            return;
        }
        if (lower.equals("/storage/emulated/0") || lower.startsWith("/storage/emulated/0/")) {
            out.add("/storage/emulated/0");
            return;
        }
        if (lower.startsWith("/storage/")) {
            String rest = p.substring("/storage/".length());
            int slash = rest.indexOf('/');
            if (slash > 0) {
                out.add("/storage/" + rest.substring(0, slash));
                return;
            }
            out.add(p);
            return;
        }
        try {
            File f = new File(p);
            if (f.isFile()) {
                File parent = f.getParentFile();
                if (parent != null) out.add(parent.getAbsolutePath());
            } else {
                out.add(f.getAbsolutePath());
            }
        } catch (Throwable ignored) { }
    }

    private static String contentUriToRawPath(String value) {
        try {
            android.net.Uri uri = android.net.Uri.parse(value);
            String docId = null;
            String path = uri.getPath();
            if (path != null && path.contains("/document/")) {
                try { docId = android.provider.DocumentsContract.getDocumentId(uri); } catch (Throwable ignored) { }
            }
            if (docId == null || docId.isEmpty()) {
                try { docId = android.provider.DocumentsContract.getTreeDocumentId(uri); } catch (Throwable ignored) { }
            }
            if (docId == null || docId.isEmpty()) {
                try { docId = android.provider.DocumentsContract.getDocumentId(uri); } catch (Throwable ignored) { }
            }
            if (docId == null || docId.isEmpty()) return value;
            int colon = docId.indexOf(':');
            String volume = colon >= 0 ? docId.substring(0, colon) : docId;
            String rel = colon >= 0 ? docId.substring(colon + 1) : "";
            if (isInternalVolume(volume)) {
                String base = externalStorageBase();
                return rel.isEmpty() ? base : base + "/" + rel;
            }
            if (volume != null && !volume.isEmpty()) return rel.isEmpty() ? "/storage/" + volume : "/storage/" + volume + "/" + rel;
        } catch (Throwable ignored) { }
        return value;
    }

    /** Internal-storage volume aliases found in SAF document IDs (OEM/HarmonyOS). */
    private static boolean isInternalVolume(String volume) {
        if (volume == null || volume.isEmpty()) return false;
        String v = volume.toLowerCase(Locale.ROOT);
        return "primary".equals(v) || "internal".equals(v) || "emulated".equals(v) || "0".equals(v);
    }

    private static String externalStorageBase() {
        try {
            String base = Environment.getExternalStorageDirectory().getAbsolutePath();
            if (base != null && !base.isEmpty()) return base;
        } catch (Throwable ignored) { }
        return "/storage/emulated/0";
    }
}
