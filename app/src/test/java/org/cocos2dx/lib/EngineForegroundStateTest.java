package org.cocos2dx.lib;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class EngineForegroundStateTest {
    @Test
    public void resumesOnlyAfterActivityAndWindowAreForeground() {
        EngineForegroundState state = new EngineForegroundState();

        assertEquals(EngineForegroundState.Transition.NONE, state.onWindowFocusChanged(true));
        assertEquals(EngineForegroundState.Transition.ENTER_FOREGROUND, state.onActivityResumed(true));
        assertEquals(EngineForegroundState.Transition.NONE, state.onActivityResumed(true));
    }

    @Test
    public void pausesOnceOnFocusLossAndResumesOnceOnFocusReturn() {
        EngineForegroundState state = new EngineForegroundState();
        state.onActivityResumed(true);
        state.onWindowFocusChanged(true);

        assertEquals(EngineForegroundState.Transition.ENTER_BACKGROUND, state.onWindowFocusChanged(false));
        assertEquals(EngineForegroundState.Transition.NONE, state.onWindowFocusChanged(false));
        assertEquals(EngineForegroundState.Transition.ENTER_FOREGROUND, state.onWindowFocusChanged(true));
        assertEquals(EngineForegroundState.Transition.NONE, state.onWindowFocusChanged(true));
    }

    @Test
    public void activityPauseWinsEvenIfFocusCallbackArrivesLater() {
        EngineForegroundState state = new EngineForegroundState();
        state.onActivityResumed(true);
        state.onWindowFocusChanged(true);

        assertEquals(EngineForegroundState.Transition.ENTER_BACKGROUND, state.onActivityResumed(false));
        assertEquals(EngineForegroundState.Transition.NONE, state.onWindowFocusChanged(true));
        assertEquals(EngineForegroundState.Transition.ENTER_FOREGROUND, state.onActivityResumed(true));
    }
}
