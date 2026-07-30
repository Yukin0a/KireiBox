package com.bytedance.android.bytehook;

/**
 * Stub façade for the ByteHook PLT interceptor.
 *
 * ARCHITECTURE NOTE:
 * All methods in this class are no-op stubs. The actual ByteHook runtime is loaded
 * directly via dlopen("libbytehook.so") in engine/src/main/cpp/krkr_bytehook.h, and
 * its C API is invoked from native code (krkr_bridge.cpp). This Java class exists
 * only to satisfy compile-time references from third-party code that expects the
 * ByteHook Java API to be present on the classpath.
 *
 * DO NOT call these methods expecting runtime behavior. To enable ByteHook hooks,
 * ensure libbytehook.so is packaged (see engine/src/main/jniLibs/) and that
 * KrkrByteHook::load() is called from native code.
 */
public final class ByteHook {
    public static final int MODE_AUTOMATIC = 0;
    public static final int MODE_MANUAL = 1;
    public static final int RECORDABLE = 1;
    public static final int UNRECORDABLE = 0;

    private ByteHook() {}

    public static void init() {}
    public static void init(int mode, boolean debug) {}
    public static void init(Config config) {}
    public static void setDebug(boolean debug) {}
    public static String getVersion() { return "stub"; }
    public static String getRecords(int itemFlags) { return ""; }
    public static void dumpRecords(String pathname, int itemFlags) {}

    public static class Config {
        public int mode = MODE_AUTOMATIC;
        public boolean debug = false;
        public boolean recordable = false;
        public Config setMode(int mode) { this.mode = mode; return this; }
        public Config setDebug(boolean debug) { this.debug = debug; return this; }
        public Config setRecordable(boolean recordable) { this.recordable = recordable; return this; }
    }
}