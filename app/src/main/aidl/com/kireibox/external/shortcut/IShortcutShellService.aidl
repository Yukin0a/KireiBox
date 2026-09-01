package com.kireibox.external.shortcut;

import android.app.PendingIntent;
import android.os.Bundle;

interface IShortcutShellService {
    void destroy() = 16777114;
    boolean canReadPinnedShortcuts(int userId) = 1;
    List<Bundle> scan(int userId) = 2;
    boolean exists(int userId, String packageName, String shortcutId) = 3;
    boolean launch(int userId, String packageName, String shortcutId) = 4;
    PendingIntent launchIntent(int userId, String packageName, String shortcutId) = 5;
}
