package com.yuki.yukihub.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.yuki.yukihub.model.EngineType;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class EngineSaveLocationsTest {
    @Test
    public void resolve_reportsMissingContextWithoutThrowing() {
        EngineSaveLocations.Location location = EngineSaveLocations.resolve(
                null, EngineType.KIRIKIRI, "/game", null, 0L);

        assertFalse(location.available);
        assertEquals("应用上下文不可用", location.description);
    }

    @Test
    public void uniqueDirectories_preservesOrderAndCanonicalizesDuplicates() throws Exception {
        File root = Files.createTempDirectory("save-locations").toFile();
        File first = new File(root, "game/../save");
        File duplicate = new File(root, "save");
        File second = new File(root, "other");

        List<File> result = EngineSaveLocations.uniqueDirectories(
                Arrays.asList(first, null, duplicate, second));

        assertEquals(Arrays.asList(first.getCanonicalFile(), second.getCanonicalFile()), result);
    }
}
