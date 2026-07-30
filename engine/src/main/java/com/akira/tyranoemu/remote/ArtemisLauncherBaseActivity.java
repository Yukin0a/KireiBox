package com.akira.tyranoemu.remote;

import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.content.Intent;
import org.tvp.kirikiri2.EnginePlaySessionJournal;

public abstract class ArtemisLauncherBaseActivity extends com.ies_net.artemis.ArtemisActivity {
    private static final long EARLY_EXIT_WINDOW_MS = 3_000L;
    private static final String PREFS_NAME = "yukihub_prefs";
    private static final String KEY_ARTEMIS_ENGINE_PREFIX = "artemis_engine.";
    private long createdAtElapsed;
    private boolean userRequestedFinish;
    private EnginePlaySessionJournal playSessionJournal;
    /** Loads the revision-specific Artemis native library (e.g. libartemis.so). Called once from onCreate. */
    public abstract void loadEngineLibrary();

    @Override
    public java.io.File getExternalFilesDir(String type) {
        String path = getIntent() == null ? null : getIntent().getStringExtra("path");
        if (path == null || path.isEmpty()) {
            java.io.File fallback = super.getExternalFilesDir(type);
            Log.i("YukiArtemis", "getExternalFilesDir type=" + type + " fallback=" + (fallback == null ? "null" : fallback.getAbsolutePath()));
            return fallback;
        }
        if (path.startsWith("file://")) path = path.substring("file://".length());
        java.io.File out = new java.io.File(path);
        Log.i("YukiArtemis", "getExternalFilesDir type=" + type + " path=" + out.getAbsolutePath() + " scoped=" + getIntent().getBooleanExtra("scopedSaveDir", false));
        return out;
    }

    @Override
    public final void onCreate(Bundle bundle) {
        playSessionJournal = EnginePlaySessionJournal.attach(this, getIntent());
        super.onCreate(bundle);
        createdAtElapsed = SystemClock.elapsedRealtime();
        Log.i("YukiArtemis", "onCreate path=" + (getIntent() == null ? null : getIntent().getStringExtra("path")) + " scoped=" + (getIntent() != null && getIntent().getBooleanExtra("scopedSaveDir", false)) + " saveName=" + (getIntent() == null ? null : getIntent().getStringExtra("scopedSaveName")));
        loadEngineLibrary();
    }

    @Override
    public final void onResume() {
        super.onResume();
        if (playSessionJournal != null) playSessionJournal.resume();
        setRequestedOrientation(getIntent().getIntExtra("orientation", 6));
    }

    @Override
    protected void onPause() {
        if (playSessionJournal != null) playSessionJournal.pause();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        userRequestedFinish = true;
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        boolean retried = maybeRetryWithCompatibleArtemis();
        if (playSessionJournal != null) {
            if (retried) playSessionJournal.pause();
            else playSessionJournal.finish();
            playSessionJournal = null;
        }
        super.onDestroy();
    }

    /**
     * Artemis titles target several mutually incompatible native revisions.  A bad
     * revision returns to the launcher almost immediately without a Java exception.
     * Retry only that short startup failure, and never override a user-selected
     * revision or a normal, longer-running game exit.
     */
    private boolean maybeRetryWithCompatibleArtemis() {
        Intent source = getIntent();
        if (source == null || userRequestedFinish
                || !source.getBooleanExtra("artemisAutoFallback", false)
                || SystemClock.elapsedRealtime() - createdAtElapsed > EARLY_EXIT_WINDOW_MS) return false;
        int stage = source.getIntExtra("artemisFallbackStage", 0);
        String nextPackage = stage == 0 ? "internal.artemis.compat" : stage == 1 ? "internal.artemis.compat.v2" : null;
        String path = source.getStringExtra("path");
        if (nextPackage == null || path == null || path.trim().isEmpty()) return false;

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(KEY_ARTEMIS_ENGINE_PREFIX + Integer.toHexString(path.hashCode()), nextPackage)
                .apply();
        Intent retry = new Intent(this,
                stage == 0 ? com.akira.tyranoemu.remote.ArtemisActivityV2.class
                        : com.akira.tyranoemu.remote.ArtemisActivityV3.class);
        retry.putExtras(source);
        retry.putExtra("artemisFallbackStage", stage + 1);
        retry.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Log.w("YukiArtemis", "Artemis exited during startup; retrying with " + nextPackage + " path=" + path);
        try {
            startActivity(retry);
            return true;
        } catch (Throwable t) {
            Log.e("YukiArtemis", "Artemis compatibility retry failed", t);
            return false;
        }
    }
}
