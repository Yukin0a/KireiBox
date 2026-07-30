package com.yuki.yukihub.launcher;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.file.Files;
import org.junit.Test;

public class KrkrLauncherTest {
    @Test
    public void normalizeEngineVersion_preservesSupportedAliases() {
        assertEquals("1.2.6", KrkrLauncher.normalizeEngineVersion("kr126"));
        assertEquals("1.3.4", KrkrLauncher.normalizeEngineVersion("134"));
        assertEquals("1.3.9", KrkrLauncher.normalizeEngineVersion("kirikiroid139"));
        assertEquals("auto", KrkrLauncher.normalizeEngineVersion("future"));
    }

    @Test
    public void resolvePath_xp3FirstSelectsLocalArchive() throws Exception {
        File root = Files.createTempDirectory("krkr-root").toFile();
        File archive = new File(root, "data.xp3");
        Files.write(archive.toPath(), new byte[]{1});

        assertEquals(
                archive.getAbsolutePath(),
                KrkrLauncher.resolvePath(null, root.getAbsolutePath(), "XP3_FIRST"));
    }

    @Test
    public void resolvePath_knownMissingEntryPreservesExpectedPath() throws Exception {
        File root = Files.createTempDirectory("krkr-root").toFile();
        assertEquals(
                new File(root, "startup.tjs").getAbsolutePath(),
                KrkrLauncher.resolvePath(null, root.getAbsolutePath(), "startup.tjs"));
    }

    @Test
    public void rootForPath_usesDirectoryContainingSelectedArchive() throws Exception {
        File root = Files.createTempDirectory("krkr-root").toFile();
        File archive = new File(root, "data.xp3");
        assertEquals(root.getAbsolutePath(), KrkrLauncher.rootForPath(archive.getPath(), archive.getPath()));
    }
}
