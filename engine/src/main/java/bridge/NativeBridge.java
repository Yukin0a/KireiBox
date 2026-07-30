package bridge;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.UriPermission;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.system.OsConstants;
import android.util.Log;

import com.akira.tyranoemu.remote.KrLaunchLogger;

import org.tvp.kirikiri2.KR2Activity;

import java.io.File;
import java.io.FileDescriptor;
import java.io.RandomAccessFile;
import androidx.documentfile.provider.DocumentFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * JNI bridge between KRKR engine native code and the Java/Kotlin runtime.
 *
 * ARCHITECTURE NOTE:
 * This class is tightly coupled to {@link org.tvp.kirikiri2.KR2Activity} because the
 * KRKR engine runs in the dedicated :kirikiri2 process and expects a single
 * Activity-bound context for SAF fallback, content resolver access, and intent-based
 * configuration (scopedSaveDir, safFileFallback, projectRoot, gamedir, etc.).
 *
 * All KR2Activity.getInstance() lookups are centralized in
 * {@link KrPathUtils#currentActivity()} to minimize the coupling surface. Callers
 * should use KrPathUtils.currentActivity() rather than directly referencing KR2Activity.
 *
 * Native methods are registered in JNI_OnLoad (see krkr_bridge.cpp) and must only be
 * called from the :kirikiri2 process where the engine ClassLoader can resolve this class.
 */
public final class NativeBridge {
    // OPEN_PFDS pfd instances are NOT detachFd()'d (we hand out getFd()), so the
    // ParcelFileDescriptor must be retained until the native side closes the fd or
    // the process exits; otherwise the descriptor may be released prematurely.
    private static final List<ParcelFileDescriptor> OPEN_PFDS = new ArrayList<>();
    private static volatile Runnable krkrGameReadyListener;
    private static volatile KrkrDialogListener krkrDialogListener;
    private static volatile String dialoguePatchTarget = "";
    private static volatile String dialoguePatchPath = "";

    public interface KrkrDialogListener {
        void onShown();
        void onDismissed();
    }

    private NativeBridge() { }

    public static native boolean initialize(String so);
    /** True when the selected KR native library accepted the TVPConsoleLog hook. */
    public static native boolean isConsoleLogHookInstalled();
    /** True only after TVPMainScene has registered the file-selector form that startupFrom must dismiss. */
    public static native boolean isLaunchSceneReady(String so);
    public static native boolean launch(String so, String path, boolean useMaps);
    public static native void interceptor(String prefix);
    public static native void relocate();
    public static native boolean write(String path, byte[] data);

    /** Called by the native TVPMainScene update hook after doStartup has returned. */
    private static void onKrkrGameReady() {
        Runnable listener = krkrGameReadyListener;
        Log.i("NativeBridge", "KRKR game-ready callback listener=" + (listener != null));
        if (listener != null) listener.run();
    }

    public static void setKrkrGameReadyListener(Runnable listener) {
        krkrGameReadyListener = listener;
    }

    /** Called by the KR dialog bridge before an engine-owned dialog is shown. */
    public static void onKrkrDialogShown() {
        KrkrDialogListener listener = krkrDialogListener;
        if (listener != null) listener.onShown();
    }

    /** Called by the KR dialog bridge after the player has made a choice. */
    public static void onKrkrDialogDismissed() {
        KrkrDialogListener listener = krkrDialogListener;
        if (listener != null) listener.onDismissed();
    }

    public static void setKrkrDialogListener(KrkrDialogListener listener) {
        krkrDialogListener = listener;
    }

    /** Configures a read-only KAG message-layer overlay for the next KR launch. */
    public static void configureDialoguePatch(String targetRelativePath, String replacementPath) {
        dialoguePatchTarget = normalizeOverlayTarget(targetRelativePath);
        dialoguePatchPath = replacementPath == null ? "" : replacementPath.trim();
        Log.i("NativeBridge", "dialogue patch target=" + dialoguePatchTarget +
                " replacement=" + dialoguePatchPath);
    }

    public static void clearDialoguePatch() {
        dialoguePatchTarget = "";
        dialoguePatchPath = "";
    }

    /** Called from krkr_bridge.cpp for the engine's original UTF-16 console stream. */
    private static void onKrkrConsoleLine(String line) {
        KrLaunchLogger.nativeLine(line);
    }

    public static synchronized int open(String path, int mode) {
        String normalized = KrPathUtils.canonicalizeKrStoragePath(KrPathUtils.normalizeFilePath(path));
        int overlayFd = openDialogueOverlay(normalized, mode);
        if (overlayFd >= 0) return overlayFd;
        String javaMode;
        try {
            javaMode = toJavaMode(mode);
        } catch (Throwable t) {
            Log.e("NativeBridge", "bad open mode=" + mode + " path=" + path, t);
            return -1;
        }

        try {
            RandomAccessFile raf = new RandomAccessFile(new File(normalized), javaMode);
            // getFd() dups and detachFd()s, so the returned int fd is independent of raf.
            // Closing raf here avoids unbounded growth of retained RandomAccessFile handles
            // (the legacy OPEN_FILES list only ever added and never removed them).
            int fd = getFd(raf);
            raf.close();
            Log.i("NativeBridge", "open " + fd + " " + javaMode + " " + path);
            return fd;
        } catch (Throwable directError) {
            if (isSafFallbackEnabled()) {
                int safFd = openViaSaf(normalized, mode, directError);
                if (safFd >= 0) return safFd;
            }
            Log.e("NativeBridge", "open failed mode=" + mode + " path=" + path, directError);
            return -1;
        }
    }

    private static int openDialogueOverlay(String normalized, int mode) {
        try {
            if (normalized == null || normalized.isEmpty() || dialoguePatchTarget.isEmpty() ||
                    dialoguePatchPath.isEmpty()) return -1;
            int access = mode & OsConstants.O_ACCMODE;
            if (access != OsConstants.O_RDONLY || (mode & OsConstants.O_CREAT) != 0) return -1;
            String candidate = normalizeOverlayTarget(normalized);
            if (!(candidate.equals(dialoguePatchTarget) ||
                    candidate.endsWith("/" + dialoguePatchTarget))) return -1;
            File replacement = new File(dialoguePatchPath);
            if (!replacement.isFile() || !replacement.canRead()) return -1;
            RandomAccessFile raf = new RandomAccessFile(replacement, "r");
            int fd = getFd(raf);
            raf.close();
            Log.i("NativeBridge", "open dialogue overlay " + normalized + " -> " + replacement);
            return fd;
        } catch (Throwable error) {
            Log.w("NativeBridge", "open dialogue overlay failed path=" + normalized, error);
            return -1;
        }
    }

    private static String normalizeOverlayTarget(String value) {
        if (value == null) return "";
        String result = value.trim().replace('\\', '/');
        while (result.startsWith("/")) result = result.substring(1);
        while (result.contains("//")) result = result.replace("//", "/");
        return result.toLowerCase(Locale.ROOT);
    }

    private static boolean isSafFallbackEnabled() {
        try {
            KR2Activity activity = KrPathUtils.currentActivity();
            android.content.Intent intent = activity == null ? null : activity.getIntent();
            return intent != null && intent.getBooleanExtra("safFileFallback", false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int openViaSaf(String path, int mode, Throwable directError) {
        try {
            Uri uri = storagePathToPersistedDocumentUri(path, mode);
            if (uri == null) return -1;
            KR2Activity activity = KrPathUtils.currentActivity();
            if (activity == null) return -1;
            String pfdMode = toPfdMode(mode);
            ParcelFileDescriptor pfd = activity.getContentResolver().openFileDescriptor(uri, pfdMode);
            if (pfd == null) return -1;
            OPEN_PFDS.add(pfd);
            int fd = pfd.getFd();
            Log.i("NativeBridge", "open SAF " + fd + " " + pfdMode + " " + path + " -> " + uri);
            return fd;
        } catch (Throwable safError) {
            Log.w("NativeBridge", "open SAF fallback failed path=" + path + " direct=" + directError, safError);
            return -1;
        }
    }

    public static boolean writeViaSafIfPossible(String path, byte[] data) {
        try {
            path = KrPathUtils.canonicalizeKrStoragePath(path);
            Uri uri = storagePathToPersistedDocumentUri(path, OsConstants.O_WRONLY | OsConstants.O_CREAT | OsConstants.O_TRUNC);
            if (uri == null) return false;
            KR2Activity activity = KrPathUtils.currentActivity();
            if (activity == null) return false;
            try (java.io.OutputStream out = activity.getContentResolver().openOutputStream(uri, "wt")) {
                if (out == null) return false;
                if (data != null) out.write(data);
                out.flush();
            }
            Log.i("NativeBridge", "write SAF " + path + " -> " + uri + " bytes=" + (data == null ? 0 : data.length));
            return true;
        } catch (Throwable t) {
            Log.w("NativeBridge", "write SAF failed path=" + path, t);
            return false;
        }
    }

    public static boolean createDirectoryViaSafIfPossible(String path) {
        try {
            path = KrPathUtils.canonicalizeKrStoragePath(path);
            Uri uri = storagePathToPersistedDocumentUri(path + "/.yukihub_dir_probe", OsConstants.O_WRONLY | OsConstants.O_CREAT | OsConstants.O_TRUNC);
            if (uri == null) return false;
            KR2Activity activity = KrPathUtils.currentActivity();
            if (activity != null) {
                try { DocumentsContract.deleteDocument(activity.getContentResolver(), uri); } catch (Throwable ignored) { }
            }
            Log.i("NativeBridge", "mkdir SAF " + path);
            return true;
        } catch (Throwable t) {
            Log.w("NativeBridge", "mkdir SAF failed path=" + path, t);
            return false;
        }
    }

    public static boolean deleteViaSafIfPossible(String path) {
        try {
            path = KrPathUtils.canonicalizeKrStoragePath(path);
            Uri uri = storagePathToPersistedDocumentUri(path, OsConstants.O_RDONLY);
            if (uri == null) return false;
            KR2Activity activity = KrPathUtils.currentActivity();
            if (activity == null) return false;
            boolean ok = DocumentsContract.deleteDocument(activity.getContentResolver(), uri);
            Log.i("NativeBridge", "delete SAF " + path + " -> " + uri + " ok=" + ok);
            return ok;
        } catch (Throwable t) {
            Log.w("NativeBridge", "delete SAF failed path=" + path, t);
            return false;
        }
    }

    public static boolean existsViaSafIfPossible(String path) {
        try {
            path = KrPathUtils.canonicalizeKrStoragePath(path);
            Uri uri = storagePathToPersistedDocumentUri(path, OsConstants.O_RDONLY);
            if (uri == null) return false;
            KR2Activity activity = KrPathUtils.currentActivity();
            if (activity == null) return false;
            try (java.io.InputStream in = activity.getContentResolver().openInputStream(uri)) {
                return in != null;
            }
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean renameViaSafIfPossible(String from, String to) {
        try {
            from = KrPathUtils.canonicalizeKrStoragePath(from);
            to = KrPathUtils.canonicalizeKrStoragePath(to);
            Uri src = storagePathToPersistedDocumentUri(from, OsConstants.O_RDONLY);
            if (src == null) return false;
            KR2Activity activity = KrPathUtils.currentActivity();
            if (activity == null) return false;
            ContentResolver resolver = activity.getContentResolver();
            try (java.io.InputStream in = resolver.openInputStream(src)) {
                if (in == null) return false;
                Uri dst = storagePathToPersistedDocumentUri(to, OsConstants.O_WRONLY | OsConstants.O_CREAT | OsConstants.O_TRUNC);
                if (dst == null) return false;
                try (java.io.OutputStream out = resolver.openOutputStream(dst, "wt")) {
                    if (out == null) return false;
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    out.flush();
                }
                try { DocumentsContract.deleteDocument(resolver, src); } catch (Throwable ignored) { }
                Log.i("NativeBridge", "rename SAF " + from + " -> " + to + " src=" + src);
                return true;
            }
        } catch (Throwable t) {
            Log.w("NativeBridge", "rename SAF failed " + from + " -> " + to, t);
            return false;
        }
    }

    @SuppressLint("SdCardPath") // Maps legacy engine paths onto persisted SAF tree permissions.
    private static Uri storagePathToPersistedDocumentUri(String path, int mode) {
        if (path == null) return null;
        String p = KrPathUtils.normalizeFilePath(path);
        if (!p.startsWith("/storage/") && !p.startsWith("/sdcard")) return null;
        String volume;
        String rel;
        if (p.startsWith("/storage/emulated/0/")) {
            volume = "primary";
            rel = p.substring("/storage/emulated/0/".length());
        } else if ("/storage/emulated/0".equals(p)) {
            volume = "primary";
            rel = "";
        } else if (p.startsWith("/sdcard/")) {
            volume = "primary";
            rel = p.substring("/sdcard/".length());
        } else if ("/sdcard".equals(p)) {
            volume = "primary";
            rel = "";
        } else {
            String rest = p.substring("/storage/".length());
            int slash = rest.indexOf('/');
            if (slash <= 0) return null;
            volume = rest.substring(0, slash);
            rel = rest.substring(slash + 1);
        }
        if (volume == null || volume.isEmpty() || rel == null) return null;

        KR2Activity activity = KrPathUtils.currentActivity();
        if (activity == null) return null;
        ContentResolver resolver = activity.getContentResolver();
        String docId = volume + ":" + rel;
        int permissionCount = resolver.getPersistedUriPermissions().size();
        Log.i("NativeBridge", "SAF resolve path=" + path + " volume=" + volume + " rel=" + rel + " persisted=" + permissionCount);
        for (UriPermission perm : resolver.getPersistedUriPermissions()) {
            Uri tree = perm.getUri();
            if (tree == null) continue;
            String treeId;
            try { treeId = DocumentsContract.getTreeDocumentId(tree); } catch (Throwable ignored) { continue; }
            if (treeId == null) continue;
            String decodedTreeId = Uri.decode(treeId);
            Log.i("NativeBridge", "SAF candidate tree=" + decodedTreeId + " uri=" + tree);
            if (!decodedTreeId.startsWith(volume + ":")) continue;
            String treeRel = decodedTreeId.substring((volume + ":").length());
            if (!treeRel.isEmpty()) {
                if (!rel.equals(treeRel) && !rel.startsWith(treeRel + "/")) continue;
            }
            Uri existing = DocumentsContract.buildDocumentUriUsingTree(tree, docId);
            if (!needsCreate(mode)) return existing;
            Uri created = ensureDocumentExists(resolver, tree, decodedTreeId, volume, rel);
            return created == null ? existing : created;
        }
        return null;
    }

    private static boolean needsCreate(int mode) {
        int accessMode = mode & OsConstants.O_ACCMODE;
        return accessMode == OsConstants.O_WRONLY || accessMode == OsConstants.O_RDWR || (mode & OsConstants.O_CREAT) == OsConstants.O_CREAT;
    }

    private static Uri ensureDocumentExists(ContentResolver resolver, Uri tree, String decodedTreeId, String volume, String rel) {
        try {
            KR2Activity activity = KrPathUtils.currentActivity();
            if (activity == null) return null;
            DocumentFile dir = DocumentFile.fromTreeUri(activity, tree);
            if (dir == null) return null;
            String treePrefix = volume + ":";
            String treeRel = decodedTreeId != null && decodedTreeId.startsWith(treePrefix) ? decodedTreeId.substring(treePrefix.length()) : "";
            String localRel = rel;
            if (!treeRel.isEmpty()) {
                if (localRel.equals(treeRel)) return DocumentsContract.buildDocumentUriUsingTree(tree, volume + ":" + rel);
                if (localRel.startsWith(treeRel + "/")) localRel = localRel.substring(treeRel.length() + 1);
            }
            String[] parts = localRel.split("/");
            DocumentFile current = dir;
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                if (part == null || part.isEmpty() || ".".equals(part)) continue;
                boolean last = i == parts.length - 1;
                DocumentFile child = findChildDocument(current, part);
                if (last) {
                    if (child == null) child = current.createFile(guessMime(part), part);
                    return child == null ? null : child.getUri();
                }
                if (child == null) child = current.createDirectory(part);
                if (child == null || !child.isDirectory()) return null;
                current = child;
            }
        } catch (Throwable t) {
            Log.w("NativeBridge", "ensure SAF document failed rel=" + rel, t);
        }
        return null;
    }

    private static String guessMime(String name) {
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".txt") || lower.endsWith(".tjs") || lower.endsWith(".ks") || lower.endsWith(".xml") || lower.endsWith(".json")) return "text/plain";
        return "application/octet-stream";
    }

    private static DocumentFile findChildDocument(DocumentFile dir, String name) {
        if (dir == null || name == null) return null;
        try {
            DocumentFile child = dir.findFile(name);
            if (child != null) return child;
            DocumentFile[] files = dir.listFiles();
            if (files == null) return null;
            for (DocumentFile f : files) {
                String n = f == null ? null : f.getName();
                if (n != null && n.equalsIgnoreCase(name)) return f;
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private static String toJavaMode(int mode) {
        int accessMode = mode & OsConstants.O_ACCMODE;
        if (accessMode == OsConstants.O_RDONLY) return "r";
        if (accessMode == OsConstants.O_WRONLY || accessMode == OsConstants.O_RDWR) return "rw";
        throw new IllegalArgumentException("Bad mode: " + mode);
    }

    private static String toPfdMode(int mode) {
        int accessMode = mode & OsConstants.O_ACCMODE;
        if (accessMode == OsConstants.O_RDONLY) return "r";
        if ((mode & OsConstants.O_APPEND) == OsConstants.O_APPEND) return "wa";
        if ((mode & OsConstants.O_TRUNC) == OsConstants.O_TRUNC) return "wt";
        if (accessMode == OsConstants.O_WRONLY) return "w";
        if (accessMode == OsConstants.O_RDWR) return "rw";
        throw new IllegalArgumentException("Bad mode: " + mode);
    }

    private static int getFd(RandomAccessFile raf) throws Exception {
        ParcelFileDescriptor duplicate = ParcelFileDescriptor.dup(raf.getFD());
        return duplicate.detachFd();
    }
}
