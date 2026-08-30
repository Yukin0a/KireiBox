package org.cocos2dx.lib;

/** Idempotent lifecycle gate for native engine rendering and audio. */
public final class EngineForegroundState {
    public enum Transition {
        NONE,
        ENTER_BACKGROUND,
        ENTER_FOREGROUND,
    }

    private boolean activityResumed;
    private boolean windowFocused;
    private boolean multiWindowMode;
    private boolean foreground;

    public Transition onActivityResumed(boolean resumed) {
        activityResumed = resumed;
        return updateForegroundState();
    }

    public Transition onWindowFocusChanged(boolean focused) {
        windowFocused = focused;
        return updateForegroundState();
    }

    public Transition onMultiWindowModeChanged(boolean inMultiWindowMode) {
        multiWindowMode = inMultiWindowMode;
        return updateForegroundState();
    }

    private Transition updateForegroundState() {
        // In split-screen and freeform the engine window stays resumed while another app holds
        // focus. Gating on focus alone would pause rendering the moment the game launches.
        boolean shouldBeForeground = activityResumed && (windowFocused || multiWindowMode);
        if (shouldBeForeground == foreground) return Transition.NONE;
        foreground = shouldBeForeground;
        return foreground ? Transition.ENTER_FOREGROUND : Transition.ENTER_BACKGROUND;
    }
}
