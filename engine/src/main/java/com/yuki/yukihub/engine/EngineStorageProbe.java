package com.yuki.yukihub.engine;

import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;

/**
 * Storage write probe executed inside each dedicated engine process.
 *
 * HarmonyOS 2.0 / Huawei devices have a known defect: the storage grant looks
 * correct in the main process, but a dedicated engine process (e.g. :kirikiri2,
 * :ons, :tyrano, :artemis) can still receive EACCES when writing the game's
 * savedata. Engine activities therefore probe the real save directory before
 * starting native code, so the failure is shown as an actionable page instead
 * of a native crash or an endless grant dialog.
 */
public final class EngineStorageProbe {
    private static final String TAG = "EngineStorageProbe";

    private EngineStorageProbe() {
    }

    public static final class Result {
        public final boolean ok;
        public final String reason;
        public final String detail;

        Result(boolean ok, String reason, String detail) {
            this.ok = ok;
            this.reason = reason;
            this.detail = detail;
        }

        public String userMessage() {
            return reason + "\n\n" + hint();
        }

        public String logMessage() {
            return reason + " (" + detail + ")";
        }
    }

    /** Probes the given directory with a real write/rename/delete cycle. */
    public static Result probeDirectory(File directory) {
        if (directory == null) {
            return new Result(false, "存档目录为空", "null save directory");
        }
        File pending = null;
        File committed = null;
        try {
            if (!directory.exists() && !directory.mkdirs()) {
                return new Result(false, "无法创建存档目录：" + directory.getAbsolutePath(), "mkdirs failed");
            }
            if (!directory.isDirectory()) {
                return new Result(false, "存档路径不是目录：" + directory.getAbsolutePath(), "not a directory");
            }
            String suffix = android.os.Process.myPid() + "-" + System.nanoTime();
            pending = new File(directory, ".kireibox-write-test-" + suffix + ".tmp");
            committed = new File(directory, ".kireibox-write-test-" + suffix + ".ok");
            try (FileOutputStream output = new FileOutputStream(pending)) {
                output.write(new byte[]{0x4B, 0x42});
                output.flush();
                output.getFD().sync();
            }
            if (!pending.renameTo(committed)) {
                return new Result(false, "存档目录无法完成写入检测：" + directory.getAbsolutePath(), "rename failed");
            }
            if (!committed.delete()) {
                return new Result(false, "存档目录无法删除写入检测文件：" + directory.getAbsolutePath(), "delete failed");
            }
            return new Result(true, "", "");
        } catch (Throwable error) {
            String detail = error.getMessage() == null
                    ? error.getClass().getSimpleName()
                    : error.getMessage();
            Log.e(TAG, "probe failed dir=" + directory.getAbsolutePath() + " error=" + detail);
            return new Result(false, "存档目录不可写入：" + directory.getAbsolutePath(), detail);
        } finally {
            if (pending != null && pending.exists()) {
                //noinspection ResultOfMethodCallIgnored
                pending.delete();
            }
            if (committed != null && committed.exists()) {
                //noinspection ResultOfMethodCallIgnored
                committed.delete();
            }
        }
    }

    /** KRKR keeps saves in <game>/savedata. */
    public static Result probeKrkrGameSaveDirectory(String gamePath) {
        if (gamePath == null || gamePath.trim().isEmpty()) {
            return new Result(false, "游戏目录为空", "empty game path");
        }
        String path = gamePath;
        if (path.startsWith("file://")) path = path.substring("file://".length());
        File root = new File(path);
        if (root.isFile()) root = root.getParentFile();
        if (root == null) {
            return new Result(false, "无法解析游戏目录", "null game root");
        }
        return probeDirectory(new File(root, "savedata"));
    }

    public static boolean isHuaweiFamily() {
        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase(Locale.ROOT);
        String brand = Build.BRAND == null ? "" : Build.BRAND.toLowerCase(Locale.ROOT);
        String model = Build.MODEL == null ? "" : Build.MODEL.toLowerCase(Locale.ROOT);
        return manufacturer.contains("huawei") || manufacturer.contains("honor")
                || brand.contains("huawei") || brand.contains("honor")
                || model.contains("huawei");
    }

    /** User-facing permission guide adapted to HarmonyOS/Huawei and Android version. */
    public static String hint() {
        boolean android11Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
        if (isHuaweiFamily()) {
            return "华为/HarmonyOS 提示：请在“设置 → 应用 → KireiBox → 权限管理 → 存储”中开启“存储”权限后返回"
                    + (android11Plus
                    ? "；HarmonyOS 3 及以上还需要开启“所有文件访问”。"
                    : "。")
                    + " 已开启仍无法写入，请把游戏目录移到 Download 等常规位置后重试。";
        }
        if (android11Plus) {
            return "请授予 KireiBox“所有文件访问权限”：设置 → 应用 → KireiBox → 权限 → 所有文件访问 → 允许；"
                    + "授权后返回重试。若仍无法写入，请把游戏目录移到内部存储的常规位置。";
        }
        return "请授予 KireiBox“存储”权限：设置 → 应用 → KireiBox → 权限 → 存储 → 允许；"
                + "已开启仍无法写入，请把游戏目录移到 Download 等常规位置后重试。";
    }
}