package com.yuki.yukihub.ons;

import android.annotation.SuppressLint;
import android.content.Context;
import android.system.Os;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public final class OnsLibLoader {
    private static final String TAG = "OnsLibLoader";
    private static final String VERSION_DIR = "Yuri_0.7.6";
    // Bump independently from the asset directory when the private extraction
    // layout changes, so an old process cache can never retain a patched .so.
    private static final String CACHE_DIR = VERSION_DIR + "-2";
    private static final String[] ASSET_LIBS = new String[]{
            "SDL2", "lua", "jpeg", "bz2", "SDL2_image", "SDL2_mixer", "SDL2_ttf", "onsyuri"
    };
    private static boolean loaded;

    private OnsLibLoader() { }

    @SuppressLint("UnsafeDynamicallyLoadedCode") // Fixed assets are extracted into the app-private files directory.
    public static synchronized void load(Context context) {
        if (loaded) return;
        Context app = context.getApplicationContext();
        File outDir = new File(app.getFilesDir(), "libs/" + CACHE_DIR);
        if (!outDir.exists() && !outDir.mkdirs()) {
            Log.w(TAG, "mkdir failed: " + outDir);
        }
        copyAssetFile(app, "DroidSansFallback.ttf", new File(app.getFilesDir(), "DroidSansFallback.ttf"));
        for (String lib : ASSET_LIBS) {
            File so = copyAssetLib(app, lib, outDir);
            Log.i(TAG, "load " + so.getAbsolutePath());
            System.load(so.getAbsolutePath());
        }
        try {
            System.loadLibrary("ONSPatch");
        } catch (Throwable t) {
            Log.w(TAG, "load ONSPatch failed, continue", t);
        }
        loaded = true;
    }

    private static File copyAssetLib(Context context, String lib, File outDir) {
        String asset = "libs/" + VERSION_DIR + "/lib" + lib + ".so";
        File out = new File(outDir, "lib" + lib + ".so");
        copyAssetFile(context, asset, out);
        try {
            Os.chmod(out.getAbsolutePath(), 0700);
        } catch (Throwable t) {
            throw new RuntimeException("secure library permissions failed: " + out, t);
        }
        return out;
    }

    public static File getMainSharedObject(Context context) {
        return new File(context.getFilesDir(), "libs/" + CACHE_DIR + "/libonsyuri.so");
    }

    private static File copyAssetFile(Context context, String asset, File out) {
        try {
            File parent = out.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                Log.w(TAG, "mkdir failed: " + parent);
            }
            if (!out.exists() || out.length() <= 0) {
                try (InputStream in = context.getAssets().open(asset); FileOutputStream fos = new FileOutputStream(out)) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) fos.write(buf, 0, n);
                }
            }
            // Re-apply the private mode even for files created by an older build.
            Os.chmod(out.getAbsolutePath(), 0600);
        } catch (Throwable t) {
            throw new RuntimeException("copy asset failed: " + asset, t);
        }
        return out;
    }
}
