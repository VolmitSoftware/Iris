package art.arcane.iris.world.runtime;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TransientWorldCleanupSupportTest {
    @Test
    public void identifiesTransientStudioBaseNamesAndSidecars() {
        String baseName = "iris-123e4567-e89b-12d3-a456-426614174000";

        assertTrue(TransientWorldCleanupSupport.isTransientStudioWorldName(baseName));
        assertEquals(baseName, TransientWorldCleanupSupport.transientStudioBaseWorldName(baseName));
        assertEquals(baseName, TransientWorldCleanupSupport.transientStudioBaseWorldName(baseName + "_nether"));
        assertEquals(baseName, TransientWorldCleanupSupport.transientStudioBaseWorldName(baseName + "_the_end"));
        assertFalse(TransientWorldCleanupSupport.isTransientStudioWorldName("iris-smoke-studio-deadbeef"));
        assertNull(TransientWorldCleanupSupport.transientStudioBaseWorldName("overworld"));
    }

    @Test
    public void expandsWorldFamilyNamesForDeletion() {
        String baseName = "iris-123e4567-e89b-12d3-a456-426614174000";

        List<String> names = TransientWorldCleanupSupport.worldFamilyNames(baseName);

        assertEquals(List.of(baseName, baseName + "_nether", baseName + "_the_end"), names);
    }

    @Test
    public void collectsOnlyTransientStudioWorldFamiliesFromContainer() throws IOException {
        File container = Files.createTempDirectory("transient-world-cleanup-test").toFile();
        String baseName = "iris-123e4567-e89b-12d3-a456-426614174000";
        File namespace = new File(container, "dimensions/minecraft");
        File baseFolder = new File(namespace, baseName);
        File netherFolder = new File(namespace, baseName + "_nether");
        File smokeFolder = new File(namespace, "iris-smoke-studio-deadbeef");
        File regularFolder = new File(namespace, "overworld");
        baseFolder.mkdirs();
        netherFolder.mkdirs();
        smokeFolder.mkdirs();
        regularFolder.mkdirs();

        try {
            LinkedHashSet<String> names = TransientWorldCleanupSupport.collectTransientStudioWorldNames(container);

            assertEquals(new LinkedHashSet<>(List.of(baseName)), names);
        } finally {
            Files.deleteIfExists(baseFolder.toPath());
            Files.deleteIfExists(netherFolder.toPath());
            Files.deleteIfExists(smokeFolder.toPath());
            Files.deleteIfExists(regularFolder.toPath());
            Files.deleteIfExists(namespace.toPath());
            Files.deleteIfExists(namespace.getParentFile().toPath());
            Files.deleteIfExists(container.toPath());
        }
    }
}
