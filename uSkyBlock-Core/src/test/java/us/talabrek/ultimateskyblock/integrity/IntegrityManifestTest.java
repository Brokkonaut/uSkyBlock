package us.talabrek.ultimateskyblock.integrity;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IntegrityManifestTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void detectsContentAndFileListChanges() throws Exception {
        File root = temporaryFolder.newFolder("data");
        File first = new File(root, "players/first.yml");
        assertTrue(first.getParentFile().mkdirs());
        Files.write(first.toPath(), Collections.singletonList("value: one"), StandardCharsets.UTF_8);
        IntegrityManifest manifest = IntegrityManifest.capture(root, Collections.singletonList(first));

        assertTrue(manifest.hasSameFileList(Collections.singletonList(first)));
        assertTrue(manifest.matchesFile(first));

        Files.write(first.toPath(), Collections.singletonList("value: two"), StandardCharsets.UTF_8);
        assertFalse(manifest.matchesFile(first));

        File second = new File(root, "players/second.yml");
        Files.write(second.toPath(), Collections.singletonList("value: two"), StandardCharsets.UTF_8);
        assertFalse(manifest.hasSameFileList(Arrays.asList(first, second)));
    }
}
