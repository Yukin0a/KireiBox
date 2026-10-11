package com.yuki.yukihub

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.kireibox.app.LauncherActivity
import com.kireibox.designsystem.KireiThemeResolver
import com.yuki.yukihub.util.DevLogger

class YukiHubApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // KRKR always writes to <game>/savedata from v0.5 onward. Remove the
        // retired preference so an older value can never reactivate redirection.
        getSharedPreferences("yukihub_prefs", MODE_PRIVATE)
            .edit().remove("kr_scoped_save_dir").apply()
        DevLogger.init(this)
        // 以保存的主题模式初始化进程级 night mode 默认值，确保冷启动期间所有 AppCompat
        // 组件（含尚未调用 setLocalNightMode 的 Activity / Dialog）都能命中正确色调。
        // 跟随系统 → MODE_NIGHT_FOLLOW_SYSTEM；显式浅色/深色 → MODE_NIGHT_NO / MODE_NIGHT_YES。
        AppCompatDelegate.setDefaultNightMode(
            KireiThemeResolver.appCompatNightMode(LauncherActivity.launcherThemeMode(this)),
        )
    }
}
