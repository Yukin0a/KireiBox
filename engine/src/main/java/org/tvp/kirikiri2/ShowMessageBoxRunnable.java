package org.tvp.kirikiri2;

import bridge.NativeBridge;
import android.content.Intent;
import android.os.SystemClock;

public final class ShowMessageBoxRunnable implements Runnable {
    @Override
    public void run() {
        KrDialogModel dialog = KR2Activity.mDialogMessage;
        if (shouldSkipSingleButtonStartupInfo(KR2Activity.sInstance, dialog)) {
            // No dialog is shown, so the launcher mask must not yield and flash.
            KR2Activity.notifyDialogResult(0);
            return;
        }
        NativeBridge.onKrkrDialogShown();
        KR2Activity.mCurrentDialog = KrDialogStyle.showMessageBox(
                KR2Activity.sInstance,
                dialog.title,
                dialog.message,
                dialog.buttons,
                (which, text) -> {
                    KR2Activity.mCurrentDialog = null;
                    NativeBridge.onKrkrDialogDismissed();
                    KR2Activity.notifyDialogResult(which);
                });
    }

    static boolean shouldSkipSingleButtonStartupInfo(KR2Activity activity, KrDialogModel dialog) {
        if (activity == null || dialog == null || dialog.buttons == null || dialog.buttons.length != 1) {
            return false;
        }
        Intent intent = activity.getIntent();
        if (intent == null || !"SKIP_SINGLE_BUTTON_STARTUP_INFO".equals(
                intent.getStringExtra("krStartupDialogPolicy"))) {
            return false;
        }
        long started = intent.getLongExtra("launchStartedElapsed", 0L);
        long elapsed = started > 0L ? SystemClock.elapsedRealtime() - started : Long.MAX_VALUE;
        return shouldSkipSingleButtonStartupInfo(
                intent.getStringExtra("krStartupDialogPolicy"),
                dialog.buttons.length,
                elapsed);
    }

    /** Pure policy seam used by unit tests; input dialogs use a separate runnable and never enter here. */
    public static boolean shouldSkipSingleButtonStartupInfo(
            String policy,
            int buttonCount,
            long elapsedSinceLaunchMs) {
        return "SKIP_SINGLE_BUTTON_STARTUP_INFO".equals(policy)
                && buttonCount == 1
                && elapsedSinceLaunchMs >= 0L
                && elapsedSinceLaunchMs <= 30_000L;
    }
}
