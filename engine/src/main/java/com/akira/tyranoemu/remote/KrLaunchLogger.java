package com.akira.tyranoemu.remote;

import android.content.Context;
import android.os.SystemClock;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bounded, player-visible KR launch log shared by the three KR engine activities. */
public final class KrLaunchLogger {
    public enum Level { INFO, WARNING, ERROR }
    public enum Source { KR_CORE, BRIDGE }

    public enum Stage {
        CHECK_PATH(8),
        SELECT_ENGINE(15),
        LOAD_NATIVE(25),
        INIT_BRIDGE(38),
        STORAGE(52),
        RENDERER(68),
        SCENE_READY(82),
        DISPATCH(92),
        GAME_READY(100);

        public final int progress;
        Stage(int progress) { this.progress = progress; }
    }

    public static final class Event {
        public final String launchId;
        public final long elapsedMillis;
        public final Stage stage;
        public final Level level;
        public final Source source;
        public final String message;

        Event(String launchId, long elapsedMillis, Stage stage, Level level, Source source, String message) {
            this.launchId = launchId;
            this.elapsedMillis = elapsedMillis;
            this.stage = stage;
            this.level = level;
            this.source = source;
            this.message = message;
        }

        public String displayLine() {
            return String.format(Locale.US, "%5.1fs  %s", elapsedMillis / 1000f, message);
        }

        String fileLine() {
            return String.format(
                    Locale.US, "%8dms [%s] [%s] %s%n",
                    elapsedMillis, level.name(), source.name() + "/" + stage.name(), message);
        }
    }

    public interface Listener { void onEvent(Event event, String allText); }

    private static final int MAX_LINES = 100;
    private static final long MAX_FILE_BYTES = 256L * 1024L;
    private static final int MAX_FILES = 10;
    private static final ArrayDeque<Event> EVENTS = new ArrayDeque<>();
    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "kr-launch-log");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile String launchId = "";
    private static volatile long startedAt;
    private static volatile File logFile;
    private static volatile Stage currentStage = Stage.CHECK_PATH;

    private KrLaunchLogger() { }

    public static synchronized void start(Context context, String id) {
        launchId = id == null || id.trim().isEmpty() ? Long.toString(System.currentTimeMillis()) : id.trim();
        startedAt = SystemClock.elapsedRealtime();
        EVENTS.clear();
        File dir = new File(context.getFilesDir(), "launch_logs/kr");
        if (!dir.exists()) dir.mkdirs();
        rotate(dir);
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        logFile = new File(dir, stamp + "-" + safeName(launchId) + ".log");
    }

    public static void addListener(Listener listener) {
        if (listener != null) LISTENERS.addIfAbsent(listener);
    }

    public static void removeListener(Listener listener) {
        if (listener != null) LISTENERS.remove(listener);
    }

    public static void info(Stage stage, String message) { append(stage, Level.INFO, Source.BRIDGE, message); }
    public static void warning(Stage stage, String message) { append(stage, Level.WARNING, Source.BRIDGE, message); }
    public static void error(Stage stage, String message) { append(stage, Level.ERROR, Source.BRIDGE, message); }

    /** Receives the engine's original Japanese/English TVP console output. */
    public static void nativeLine(String rawMessage) {
        if (rawMessage == null || rawMessage.trim().isEmpty()) return;
        String[] lines = rawMessage.replace('\r', '\n').split("\\n+");
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.length() > 4096) line = line.substring(0, 4096);
            String lower = line.toLowerCase(Locale.ROOT);
            Level level = lower.contains("error") || lower.contains("exception") || lower.contains("panic")
                    ? Level.ERROR
                    : lower.contains("warn") || lower.contains("failed") ? Level.WARNING : Level.INFO;
            append(currentStage, level, Source.KR_CORE, line);
        }
    }

    public static synchronized String allText() {
        StringBuilder out = new StringBuilder();
        for (Event event : EVENTS) out.append(event.displayLine()).append('\n');
        return out.toString().trim();
    }

    public static File currentFile() { return logFile; }

    private static void append(Stage stage, Level level, Source source, String rawMessage) {
        if (stage == null || level == null) return;
        if (source == Source.BRIDGE) currentStage = stage;
        String message = rawMessage == null ? "" : rawMessage.replace('\n', ' ').replace('\r', ' ').trim();
        Event event;
        String text;
        synchronized (KrLaunchLogger.class) {
            event = new Event(
                    launchId,
                    Math.max(0, SystemClock.elapsedRealtime() - startedAt),
                    stage,
                    level,
                    source,
                    message
            );
            EVENTS.addLast(event);
            while (EVENTS.size() > MAX_LINES) EVENTS.removeFirst();
            text = allText();
        }
        for (Listener listener : LISTENERS) {
            try { listener.onEvent(event, text); } catch (Throwable ignored) { }
        }
        File target = logFile;
        if (target != null) WRITER.execute(() -> write(target, event.fileLine()));
    }

    private static void write(File target, String line) {
        try {
            if (target.length() >= MAX_FILE_BYTES) return;
            byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
            int allowed = (int) Math.min(bytes.length, MAX_FILE_BYTES - target.length());
            if (allowed <= 0) return;
            try (FileOutputStream out = new FileOutputStream(target, true)) {
                out.write(bytes, 0, allowed);
            }
        } catch (Throwable ignored) { }
    }

    private static void rotate(File dir) {
        File[] raw = dir.listFiles((ignored, name) -> name.endsWith(".log"));
        if (raw == null || raw.length < MAX_FILES) return;
        List<File> files = new ArrayList<>();
        java.util.Collections.addAll(files, raw);
        files.sort((left, right) -> Long.compare(left.lastModified(), right.lastModified()));
        while (files.size() >= MAX_FILES) {
            File oldest = files.remove(0);
            try { oldest.delete(); } catch (Throwable ignored) { }
        }
    }

    private static String safeName(String value) {
        String clean = value.replaceAll("[^A-Za-z0-9._-]", "_");
        return clean.length() > 48 ? clean.substring(0, 48) : clean;
    }
}
