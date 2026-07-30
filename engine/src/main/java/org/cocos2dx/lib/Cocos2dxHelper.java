package org.cocos2dx.lib;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;
import java.io.UnsupportedEncodingException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

@SuppressLint("StaticFieldLeak") // Native callbacks require a current Activity; destroy() clears every owner.
public class Cocos2dxHelper {
    private static final String PREFS_NAME = "Cocos2dxPrefsFile";

    private static boolean sAccelerometerEnabled;
    private static Activity sActivity;
    private static boolean sActivityVisible;
    private static AssetManager sAssetManager;
    private static Cocos2dxMusic sCocos2dMusic;
    private static Cocos2dxSound sCocos2dSound;
    private static String sFileDirectory;
    private static String sPackageName;
    private static Cocos2dxHelperListener sCocos2dxHelperListener;
    private static final Set<PreferenceManager.OnActivityResultListener> onActivityResultListeners = new LinkedHashSet<>();
    private static Vibrator sVibrateService = null;
    private static boolean sInited = false;

    public interface Cocos2dxHelperListener {
        void runOnGLThread(Runnable runnable);
        void showDialog(String title, String message);
    }

    public static void addOnActivityResultListener(PreferenceManager.OnActivityResultListener listener) {
        onActivityResultListeners.add(listener);
    }

    public static Set<PreferenceManager.OnActivityResultListener> getOnActivityResultListeners() {
        return onActivityResultListeners;
    }

    public static byte[] conversionEncoding(byte[] text, String fromCharset, String newCharset) {
        try {
            return new String(text, fromCharset).getBytes(newCharset);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        } catch (Throwable t) {
            return text;
        }
    }

    public static void init(Activity activity) {
        sActivity = activity;
        if (activity instanceof Cocos2dxHelperListener) sCocos2dxHelperListener = (Cocos2dxHelperListener) activity;
        if (sInited) return;
        ApplicationInfo applicationInfo = activity.getApplicationInfo();
        sPackageName = applicationInfo.packageName;
        sFileDirectory = activity.getFilesDir().getAbsolutePath();
        nativeSetApkPath(applicationInfo.sourceDir);
        sCocos2dMusic = new Cocos2dxMusic(activity);
        sCocos2dSound = new Cocos2dxSound(activity);
        sAssetManager = activity.getAssets();
        nativeSetContext(activity, sAssetManager);
        Cocos2dxBitmap.setContext(activity);
        sVibrateService = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
        sInited = true;
    }

    public static Activity getActivity() { return sActivity; }
    public static Context getContext() { return sActivity; }
    public static Context getApplicationContext() { return sActivity != null ? sActivity.getApplicationContext() : null; }
    public static AssetManager getAssetManager() { return sAssetManager; }
    public static String getCocos2dxPackageName() { return sPackageName; }
    public static String getCocos2dxWritablePath() { return sFileDirectory; }
    public static String getCurrentLanguage() { return Locale.getDefault().getLanguage(); }
    public static String getDeviceModel() { return Build.MODEL; }
    public static boolean isActivityVisible() { return sActivityVisible; }

    public static int getDPI() {
        Display defaultDisplay;
        if (sActivity == null) return -1;
        DisplayMetrics displayMetrics = new DisplayMetrics();
        WindowManager windowManager = sActivity.getWindowManager();
        if (windowManager == null || (defaultDisplay = windowManager.getDefaultDisplay()) == null) return -1;
        defaultDisplay.getMetrics(displayMetrics);
        return (int) (displayMetrics.density * 160.0f);
    }

    public static String getVersion() {
        try {
            return Cocos2dxActivity.getContext().getPackageManager().getPackageInfo(Cocos2dxActivity.getContext().getPackageName(), 0).versionName;
        } catch (Exception unused) {
            return "";
        }
    }

    public static void onPause() {
        sActivityVisible = false;
    }

    public static void onResume() {
        sActivityVisible = true;
    }

    public static void onEnterBackground() {
        if (sCocos2dSound != null) sCocos2dSound.onEnterBackground();
        if (sCocos2dMusic != null) sCocos2dMusic.onEnterBackground();
    }

    public static void onEnterForeground() {
        if (sCocos2dSound != null) sCocos2dSound.onEnterForeground();
        if (sCocos2dMusic != null) sCocos2dMusic.onEnterForeground();
    }

    public static void runOnGLThread(Runnable runnable) {
        if (sCocos2dxHelperListener != null) sCocos2dxHelperListener.runOnGLThread(runnable);
    }

    public static void setKeepScreenOn(boolean keepScreenOn) {
        if (sActivity instanceof Cocos2dxActivity) ((Cocos2dxActivity) sActivity).setKeepScreenOn(keepScreenOn);
    }

    private static void showDialog(String title, String message) {
        if (sCocos2dxHelperListener != null) sCocos2dxHelperListener.showDialog(title, message);
    }

    public static void setEditTextDialogResult(String text) {
        try {
            final byte[] bytes = text.getBytes("UTF8");
            runOnGLThread(new Runnable() {
                @Override public void run() { nativeSetEditTextDialogResult(bytes); }
            });
        } catch (UnsupportedEncodingException ignored) {
        }
    }

    public static boolean openURL(String url) {
        try {
            Intent intent = new Intent("android.intent.action.VIEW");
            intent.setData(Uri.parse(url));
            sActivity.startActivity(intent);
            return true;
        } catch (Exception unused) {
            return false;
        }
    }

    public static void terminateProcess() { Process.killProcess(Process.myPid()); }
    public static void vibrate(float seconds) { if (sVibrateService != null) sVibrateService.vibrate((long) (seconds * 1000.0f)); }

    public static void enableAccelerometer() { sAccelerometerEnabled = true; }
    public static void disableAccelerometer() { sAccelerometerEnabled = false; }
    public static void setAccelerometerInterval(float interval) { }

    public static void end() { if (sCocos2dMusic != null) sCocos2dMusic.end(); if (sCocos2dSound != null) sCocos2dSound.end(); }

    public static void destroy(Activity activity) {
        if (sActivity != activity) return;
        end();
        sAccelerometerEnabled = false;
        sActivityVisible = false;
        sActivity = null;
        sCocos2dxHelperListener = null;
        sCocos2dMusic = null;
        sCocos2dSound = null;
        sVibrateService = null;
        sAssetManager = null;
        Cocos2dxBitmap.setContext(null);
        onActivityResultListeners.clear();
        sInited = false;
    }
    public static void playBackgroundMusic(String path, boolean loop) { if (sCocos2dMusic != null) sCocos2dMusic.playBackgroundMusic(path, loop); }
    public static void preloadBackgroundMusic(String path) { if (sCocos2dMusic != null) sCocos2dMusic.preloadBackgroundMusic(path); }
    public static void pauseBackgroundMusic() { if (sCocos2dMusic != null) sCocos2dMusic.pauseBackgroundMusic(); }
    public static void resumeBackgroundMusic() { if (sCocos2dMusic != null) sCocos2dMusic.resumeBackgroundMusic(); }
    public static void rewindBackgroundMusic() { if (sCocos2dMusic != null) sCocos2dMusic.rewindBackgroundMusic(); }
    public static void stopBackgroundMusic() { if (sCocos2dMusic != null) sCocos2dMusic.stopBackgroundMusic(); }
    public static boolean isBackgroundMusicPlaying() { return sCocos2dMusic != null && sCocos2dMusic.isBackgroundMusicPlaying(); }
    public static float getBackgroundMusicVolume() { return sCocos2dMusic != null ? sCocos2dMusic.getBackgroundVolume() : 0.0f; }
    public static void setBackgroundMusicVolume(float volume) { if (sCocos2dMusic != null) sCocos2dMusic.setBackgroundVolume(volume); }

    public static int playEffect(String path, boolean loop, float pitch, float pan, float gain) { return sCocos2dSound != null ? sCocos2dSound.playEffect(path, loop, pitch, pan, gain) : 0; }
    public static void preloadEffect(String path) { if (sCocos2dSound != null) sCocos2dSound.preloadEffect(path); }
    public static void pauseEffect(int soundId) { if (sCocos2dSound != null) sCocos2dSound.pauseEffect(soundId); }
    public static void pauseAllEffects() { if (sCocos2dSound != null) sCocos2dSound.pauseAllEffects(); }
    public static void resumeEffect(int soundId) { if (sCocos2dSound != null) sCocos2dSound.resumeEffect(soundId); }
    public static void resumeAllEffects() { if (sCocos2dSound != null) sCocos2dSound.resumeAllEffects(); }
    public static void stopEffect(int soundId) { if (sCocos2dSound != null) sCocos2dSound.stopEffect(soundId); }
    public static void stopAllEffects() { if (sCocos2dSound != null) sCocos2dSound.stopAllEffects(); }
    public static void unloadEffect(String path) { if (sCocos2dSound != null) sCocos2dSound.unloadEffect(path); }
    public static float getEffectsVolume() { return sCocos2dSound != null ? sCocos2dSound.getEffectsVolume() : 0.0f; }
    public static void setEffectsVolume(float volume) { if (sCocos2dSound != null) sCocos2dSound.setEffectsVolume(volume); }

    private static SharedPreferences prefs() { return sActivity.getSharedPreferences(PREFS_NAME, 0); }
    public static void deleteValueForKey(String key) { prefs().edit().remove(key).apply(); }
    public static boolean getBoolForKey(String key, boolean def) { return prefs().getBoolean(key, def); }
    public static int getIntegerForKey(String key, int def) { return prefs().getInt(key, def); }
    public static float getFloatForKey(String key, float def) { return prefs().getFloat(key, def); }
    public static double getDoubleForKey(String key, double def) { return getFloatForKey(key, (float) def); }
    public static String getStringForKey(String key, String def) { return prefs().getString(key, def); }
    public static void setBoolForKey(String key, boolean value) { prefs().edit().putBoolean(key, value).apply(); }
    public static void setIntegerForKey(String key, int value) { prefs().edit().putInt(key, value).apply(); }
    public static void setFloatForKey(String key, float value) { prefs().edit().putFloat(key, value).apply(); }
    public static void setDoubleForKey(String key, double value) { prefs().edit().putFloat(key, (float) value).apply(); }
    public static void setStringForKey(String key, String value) { prefs().edit().putString(key, value).apply(); }

    public static int fastLoading(int value) { return -1; }
    public static int getTemperature() { return -1; }
    public static int setFPS(int fps) { return -1; }
    public static int setLowPowerMode(boolean enabled) { return -1; }
    public static int setResolutionPercent(int percent) { return -1; }
    public static int getDeviceRotation() { return 0; }

    private static native void nativeSetApkPath(String path);
    private static native void nativeSetContext(Context context, AssetManager assetManager);
    private static native void nativeSetEditTextDialogResult(byte[] bytes);
}
