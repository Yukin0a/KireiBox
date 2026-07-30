package com.yuki.yukihub.launcher;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.file.Files;
import org.junit.Test;

public class ScriptEngineLaunchersTest {
    @Test
    public void resolveOnsGameDirectory_acceptsSelectedDirectory() throws Exception {
        File root = Files.createTempDirectory("ons-root").toFile();
        Files.write(new File(root, "0.txt").toPath(), new byte[]{1});

        assertEquals(root.getAbsolutePath(), ScriptEngineLaunchers.resolveOnsGameDirectory(root.getPath()));
    }

    @Test
    public void resolveOnsGameDirectory_acceptsOneDirectChild() throws Exception {
        File container = Files.createTempDirectory("ons-container").toFile();
        File game = new File(container, "game");
        game.mkdirs();
        Files.write(new File(game, "nscript.dat").toPath(), new byte[]{1});

        assertEquals(game.getAbsolutePath(), ScriptEngineLaunchers.resolveOnsGameDirectory(container.getPath()));
    }

    @Test
    public void resolveTyranoGameDirectory_usesTargetParentForFile() throws Exception {
        File root = Files.createTempDirectory("tyrano-root").toFile();
        File nested = new File(root, "nested");
        nested.mkdirs();
        File index = new File(nested, "index.html");
        Files.write(index.toPath(), new byte[]{1});

        assertEquals(
                nested.getAbsolutePath(),
                ScriptEngineLaunchers.resolveTyranoGameDirectory(root.getPath(), "nested/index.html"));
    }
}
