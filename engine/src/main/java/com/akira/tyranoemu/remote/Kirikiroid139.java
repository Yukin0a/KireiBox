package com.akira.tyranoemu.remote;

import bridge.NativeBridge;

public final class Kirikiroid139 extends KirikiroidLauncherBaseActivity {
    @Override
    public void onLoadNativeLibraries() {
        System.loadLibrary("krkr_bridge");
        NativeBridge.prepareAudioCapture();
        System.loadLibrary("SDL2");
        System.loadLibrary("ffmpeg");
        System.loadLibrary("game");
        super.onLoadNativeLibraries();
    }

    @Override
    public String soName() {
        return "libgame.so";
    }
}
