package art.arcane.iris.pack.datapack.export.compile.biome;

import art.arcane.iris.pack.datapack.export.VanillaResourceIdentifier;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class BiomeIdSanitizerTest {
    private static final List<String> KEYS = List.of(
            "Forest",
            "forest",
            "forest_2",
            "FOREST",
            "caves/Dark Cave",
            "Caves/dark cave",
            "ocean\\deep",
            "Odd Name!");

    @Test
    public void collidingKeysGetStableSuffixes() {
        List<String> first = sanitizeAll(KEYS);

        assertEquals(List.of(
                "iris_export:forest",
                "iris_export:forest_2",
                "iris_export:forest_2_2",
                "iris_export:forest_3",
                "iris_export:caves/dark_cave",
                "iris_export:caves/dark_cave_2",
                "iris_export:ocean/deep",
                "iris_export:odd_name_"), first);
        assertEquals(first, sanitizeAll(KEYS));
        assertEquals(KEYS.size(), new HashSet<>(first).size());
        for (String id : first) {
            assertTrue(id, VanillaResourceIdentifier.validIdentifier(id));
        }
    }

    @Test
    public void sanitizedIdIsClaimedInTheTakenSet() {
        Set<String> taken = new HashSet<>();
        String id = BiomeIdSanitizer.sanitize("iris_export", "Plains", taken);

        assertEquals("iris_export:plains", id);
        assertTrue(taken.contains(id));
    }

    @Test
    public void invalidNamespaceIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> BiomeIdSanitizer.sanitize("Iris Export", "plains", new HashSet<>()));
    }

    private static List<String> sanitizeAll(List<String> keys) {
        Set<String> taken = new HashSet<>();
        List<String> ids = new ArrayList<>(keys.size());
        for (String key : keys) {
            ids.add(BiomeIdSanitizer.sanitize("iris_export", key, taken));
        }
        return ids;
    }
}
