package com.akira.tyranoemu.remote;

public final class ArtemisActivityV2 extends ArtemisLauncherBaseActivity {
 @Override public void loadEngineLibrary() {
 System.loadLibrary("artemis-compatible");
 }
}