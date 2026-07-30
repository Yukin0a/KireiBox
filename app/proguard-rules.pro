# App-specific ProGuard rules.
# Engine keep rules are in engine/consumer-rules.pro and applied automatically.

# Keep model classes used in JSON serialization
-keep class com.yuki.yukihub.model.** { *; }

# Keep Retrofit API service interface
-keep class com.yuki.yukihub.net.ApiService { *; }
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Keep data classes used with SQL
-keep class com.yuki.yukihub.data.** { *; }

# Preserve line numbers for stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
