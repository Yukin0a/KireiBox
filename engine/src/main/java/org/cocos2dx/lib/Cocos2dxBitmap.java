package org.cocos2dx.lib;

import android.annotation.SuppressLint;
import android.content.Context;

public class Cocos2dxBitmap {
    @SuppressLint("StaticFieldLeak") // Stores only context.getApplicationContext().
    private static Context sContext;

    public static void setContext(Context context) {
        sContext = context == null ? null : context.getApplicationContext();
    }

    public static Context getContext() {
        return sContext;
    }

    public static native void nativeInitBitmapDC(int width, int height, byte[] pixels);
}
