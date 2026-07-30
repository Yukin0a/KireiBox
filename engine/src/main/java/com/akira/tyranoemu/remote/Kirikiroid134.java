package com.akira.tyranoemu.remote;

public final class Kirikiroid134 extends KirikiroidLauncherBaseActivity {
    @Override
    public void onLoadNativeLibraries() {
        System.loadLibrary("ffmpeg");
        System.loadLibrary("game134");
        System.loadLibrary("krkr_bridge");
        super.onLoadNativeLibraries();
    }

    @Override
    public String soName() {
        return "libgame134.so";
    }
}
