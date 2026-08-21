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
    private boolean foreground;

    public Transition onActivityResumed(boolean resumed) {
        activityResumed = resumed;
        return updateForegroundState();
    }

    public Transition onWindowFocusChanged(boolean focused) {
        windowFocused = focused;
        return updateForegroundState();
    }

    private Transition updateForegroundState() {
        boolean shouldBeForeground = activityResumed && windowFocused;
        if (shouldBeForeground == foreground) return Transition.NONE;
        foreground = shouldBeForeground;
        return foreground ? Transition.ENTER_FOREGROUND : Transition.ENTER_BACKGROUND;
    }
}
