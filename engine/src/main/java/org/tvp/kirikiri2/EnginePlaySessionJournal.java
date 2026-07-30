package org.tvp.kirikiri2;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.util.AtomicFile;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Cross-process foreground-time journal shared by all embedded engines.
 *
 * Each engine process checkpoints monotonic active time. The launcher only
 * commits a database duration after the engine reports FINISHED or its process
 * is no longer alive, so background time is never treated as play time.
 */
public final class EnginePlaySessionJournal {
    public static final String EXTRA_SESSION_ID = "kireiPlaySessionId";
    public static final String EXTRA_GAME_ID = "kireiPlayGameId";
    public static final String STATE_RUNNING = "RUNNING";
    public static final String STATE_PAUSED = "PAUSED";
    public static final String STATE_FINISHED = "FINISHED";

    private static final String TAG = "EnginePlayJournal";
    private static final String DIRECTORY = "engine_play_sessions";
    private static final String LEGACY_KR_DIRECTORY = "kr_play_sessions";
    private static final long HEARTBEAT_MS = 15_000L;
    private static final long MAX_TICK_MS = 12L * 60L * 60L * 1000L;

    private final Context app;
    private final long sessionId;
    private final long gameId;
    private final int processId;
    private final AtomicFile file;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable heartbeat = new Runnable() {
        @Override public void run() {
            checkpoint(STATE_RUNNING, true);
            if (active) handler.postDelayed(this, HEARTBEAT_MS);
        }
    };

    private long accumulatedMs;
    private long lastTickElapsed;
    private boolean active;
    private boolean finished;

    private EnginePlaySessionJournal(Context context, long sessionId, long gameId) {
        this.app = context.getApplicationContext();
        this.sessionId = sessionId;
        this.gameId = gameId;
        this.processId = Process.myPid();
        File directory = new File(app.getFilesDir(), DIRECTORY);
        if (!directory.isDirectory()) directory.mkdirs();
        this.file = new AtomicFile(new File(directory, sessionId + ".json"));
        Snapshot existing = readSnapshot(this.file.getBaseFile());
        if (existing != null && existing.sessionId == sessionId && !existing.isFinished()) {
            accumulatedMs = Math.max(0L, existing.accumulatedMs);
        }
    }

    public static EnginePlaySessionJournal attach(Context context, Intent intent) {
        if (context == null || intent == null) return null;
        long sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1L);
        long gameId = intent.getLongExtra(EXTRA_GAME_ID, -1L);
        if (sessionId <= 0L || gameId <= 0L) return null;
        return new EnginePlaySessionJournal(context, sessionId, gameId);
    }

    public synchronized void resume() {
        if (finished || active) return;
        active = true;
        lastTickElapsed = SystemClock.elapsedRealtime();
        write(STATE_RUNNING);
        handler.removeCallbacks(heartbeat);
        handler.postDelayed(heartbeat, HEARTBEAT_MS);
    }

    public synchronized void pause() {
        if (finished) return;
        handler.removeCallbacks(heartbeat);
        checkpoint(STATE_PAUSED, false);
        active = false;
    }

    public synchronized void finish() {
        if (finished) return;
        handler.removeCallbacks(heartbeat);
        checkpoint(STATE_FINISHED, false);
        active = false;
        finished = true;
    }

    private synchronized void checkpoint(String state, boolean reschedule) {
        if (finished) return;
        if (active) {
            long now = SystemClock.elapsedRealtime();
            long delta = Math.max(0L, Math.min(MAX_TICK_MS, now - lastTickElapsed));
            accumulatedMs += delta;
            lastTickElapsed = now;
        }
        write(state);
        if (!reschedule) handler.removeCallbacks(heartbeat);
    }

    private void write(String state) {
        FileOutputStream output = null;
        try {
            JSONObject json = new JSONObject()
                    .put("version", 1)
                    .put("sessionId", sessionId)
                    .put("gameId", gameId)
                    .put("processId", processId)
                    .put("accumulatedMs", Math.max(0L, accumulatedMs))
                    .put("lastHeartbeatAt", System.currentTimeMillis())
                    .put("state", state);
            output = file.startWrite();
            output.write(json.toString().getBytes(StandardCharsets.UTF_8));
            output.flush();
            file.finishWrite(output);
        } catch (Throwable error) {
            if (output != null) file.failWrite(output);
            Log.w(TAG, "write failed session=" + sessionId, error);
        }
    }

    public static List<Snapshot> readAll(Context context) {
        ArrayList<Snapshot> result = new ArrayList<>();
        if (context == null) return result;
        readDirectory(new File(context.getFilesDir(), DIRECTORY), result);
        readDirectory(new File(context.getFilesDir(), LEGACY_KR_DIRECTORY), result);
        return result;
    }

    private static void readDirectory(File directory, List<Snapshot> output) {
        File[] files = directory.listFiles((dir, name) -> name != null && name.endsWith(".json"));
        if (files == null) return;
        for (File item : files) {
            Snapshot snapshot = readSnapshot(item);
            if (snapshot != null) output.add(snapshot);
        }
    }

    private static Snapshot readSnapshot(File item) {
        try {
            AtomicFile atomic = new AtomicFile(item);
            byte[] bytes = atomic.readFully();
            JSONObject json = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            long sessionId = json.optLong("sessionId", -1L);
            long gameId = json.optLong("gameId", -1L);
            if (sessionId <= 0L || gameId <= 0L) return null;
            return new Snapshot(
                    sessionId,
                    gameId,
                    Math.max(0L, json.optLong("accumulatedMs", 0L)),
                    Math.max(0L, json.optLong("lastHeartbeatAt", 0L)),
                    Math.max(0, json.optInt("processId", 0)),
                    json.optString("state", STATE_RUNNING)
            );
        } catch (Throwable error) {
            Log.w(TAG, "read failed file=" + item.getName(), error);
            return null;
        }
    }

    public static boolean delete(Context context, long sessionId) {
        if (context == null || sessionId <= 0L) return false;
        boolean deleted = deleteFrom(context, DIRECTORY, sessionId);
        boolean legacyDeleted = deleteFrom(context, LEGACY_KR_DIRECTORY, sessionId);
        return deleted && legacyDeleted;
    }

    private static boolean deleteFrom(Context context, String directory, long sessionId) {
        AtomicFile atomic = new AtomicFile(
                new File(new File(context.getFilesDir(), directory), sessionId + ".json"));
        atomic.delete();
        return !atomic.getBaseFile().exists();
    }

    public static final class Snapshot {
        public final long sessionId;
        public final long gameId;
        public final long accumulatedMs;
        public final long lastHeartbeatAt;
        public final int processId;
        public final String state;

        Snapshot(
                long sessionId,
                long gameId,
                long accumulatedMs,
                long lastHeartbeatAt,
                int processId,
                String state
        ) {
            this.sessionId = sessionId;
            this.gameId = gameId;
            this.accumulatedMs = accumulatedMs;
            this.lastHeartbeatAt = lastHeartbeatAt;
            this.processId = processId;
            this.state = state;
        }

        public boolean isFinished() { return STATE_FINISHED.equals(state); }
        public boolean isPaused() { return STATE_PAUSED.equals(state); }
    }
}
