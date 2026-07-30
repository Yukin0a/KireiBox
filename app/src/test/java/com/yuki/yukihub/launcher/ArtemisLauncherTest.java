package com.yuki.yukihub.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ArtemisLauncherTest {
    @Test
    public void fallbackStage_matchesThreeEmbeddedEngines() {
        assertEquals(0, ArtemisLauncher.fallbackStage("internal.artemis"));
        assertEquals(1, ArtemisLauncher.fallbackStage("internal.artemis.compat"));
        assertEquals(2, ArtemisLauncher.fallbackStage("internal.artemis.compat.v2"));
    }

    @Test
    public void resourceFilter_linksOnlyEngineResources() {
        assertTrue(ArtemisLauncher.isResourceName("System"));
        assertTrue(ArtemisLauncher.isResourceName("root.pfs.001"));
        assertTrue(ArtemisLauncher.isResourceName("patch.xp3"));
        assertFalse(ArtemisLauncher.isResourceName("save.dat"));
        assertFalse(ArtemisLauncher.isResourceName("settings.json"));
    }

    @Test
    public void resolveGamePath_usesGameDirectoryInsteadOfDetectionTarget() {
        assertEquals(
                "/storage/emulated/0/Games/Artemis",
                ArtemisLauncher.resolveGamePath(
                        "/storage/emulated/0/Games/Artemis",
                        "root.pfs"));
    }
}
