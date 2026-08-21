package com.akira.tyranoemu.remote;

import bridge.NativeBridge;

public final class Kirikiroid134 extends KirikiroidLauncherBaseActivity {
    @Override
    public void onLoadNativeLibraries() {
        System.loadLibrary("krkr_bridge");
        NativeBridge.prepareAudioCapture();
        System.loadLibrary("ffmpeg");
        System.loadLibrary("game134");
        super.onLoadNativeLibraries();
    }

    @Override
    public String soName() {
        return "libgame134.so";
    }
}
