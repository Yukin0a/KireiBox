package com.akira.tyranoemu.remote;

public final class Kirikiroid139 extends KirikiroidLauncherBaseActivity {
    @Override
    public void onLoadNativeLibraries() {
        System.loadLibrary("SDL2");
        System.loadLibrary("ffmpeg");
        System.loadLibrary("game");
        System.loadLibrary("krkr_bridge");
        super.onLoadNativeLibraries();
    }

    @Override
    public String soName() {
        return "libgame.so";
    }
}
