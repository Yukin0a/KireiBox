package com.yuki.yukihub.tyrano;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.nio.file.Files;
import org.junit.Test;

public class TyranoStorageTest {
    @Test
    public void resolveStorageFile_acceptsSimpleKeyInsideRoot() throws Exception {
        File root = Files.createTempDirectory("tyrano-storage").toFile();
        assertEquals(
                new File(root, "slot01.sav").getCanonicalFile(),
                TyranoActivity.resolveStorageFile(root, "slot01"));
    }

    @Test
    public void resolveStorageFile_rejectsTraversalAndSeparators() throws Exception {
        File root = Files.createTempDirectory("tyrano-storage").toFile();
        assertNull(TyranoActivity.resolveStorageFile(root, "../slot"));
        assertNull(TyranoActivity.resolveStorageFile(root, "nested/slot"));
        assertNull(TyranoActivity.resolveStorageFile(root, "nested\\slot"));
    }
}
