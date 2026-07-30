package com.akira.tyranoemu.remote;

public final class ArtemisActivityV3 extends ArtemisLauncherBaseActivity {
 @Override public void loadEngineLibrary() {
 System.loadLibrary("artemis-compatible-v2");
 }
}