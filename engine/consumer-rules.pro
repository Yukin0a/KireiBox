# Preserve JNI/SDL engine entry points used by embedded native engines.
-keep class org.tvp.kirikiri2.** { *; }
-keep class com.yuri.onscripter.** { *; }
-keep class org.libsdl.app.** { *; }
-keep class org.cocos2dx.lib.** { *; }
-keep class bridge.NativeBridge { *; }
-keep class com.akira.tyranoemu.remote.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
