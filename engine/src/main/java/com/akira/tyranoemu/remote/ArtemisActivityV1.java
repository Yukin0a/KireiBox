package com.akira.tyranoemu.remote;

public final class ArtemisActivityV1 extends ArtemisLauncherBaseActivity {
 @Override public void loadEngineLibrary() {
 System.loadLibrary("artemis");
 }
}