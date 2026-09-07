package art.arcane.iris.engine.history;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public final class GenerationSemanticKeyValidationTest {
    @Test
    public void duplicateKeysRetainValidationAndCanonicalOrdering() {
        ChunkGenerationSemantics.Builder builder = ChunkGenerationSemantics.builder(-2, 3, 5L);
        List<String> keys = List.of("iris:deep", "Caves/Crystal Gallery", "Région/Highlands", "cave/\ud801\udc00");
        for (int repetition = 0; repetition < 8; repetition++) {
            for (String key : keys) {
                builder.addSurfaceBiome(new String(key)).addCaveBiome(new String(key))
                        .addRegion(new String(key)).addRiverProfile(new String(key)).addObject(new String(key));
            }
        }
        for (String invalid : List.of("", " iris:deep", "iris:deep ", "iris:\\deep", "iris:\ndeep",
                "é".repeat(ChunkGenerationSemantics.MAX_KEY_BYTES))) {
            assertThrows(IllegalArgumentException.class, () -> builder.addSurfaceBiome(invalid));
            assertThrows(IllegalArgumentException.class, () -> builder.addCaveBiome(invalid));
            assertThrows(IllegalArgumentException.class, () -> builder.addRegion(invalid));
            assertThrows(IllegalArgumentException.class, () -> builder.addRiverProfile(invalid));
            assertThrows(IllegalArgumentException.class, () -> builder.addObject(invalid));
        }
        assertEquals("key", assertThrows(NullPointerException.class, () -> builder.addCaveBiome(null)).getMessage());
        ChunkGenerationSemantics result = builder.seal().build();
        assertEquals(Set.copyOf(keys), result.surfaceBiomeKeys());
        assertEquals(Set.copyOf(keys), result.caveBiomeKeys());
        assertEquals(Set.copyOf(keys), result.regionKeys());
        assertEquals(Set.copyOf(keys), result.riverProfileKeys());
        assertEquals(Set.copyOf(keys), result.objectKeys());
        List<String> sorted = new ArrayList<>(keys);
        Collections.sort(sorted);
        for (Set<String> recorded : List.of(result.surfaceBiomeKeys(), result.caveBiomeKeys(), result.regionKeys(),
                result.riverProfileKeys(), result.objectKeys())) {
            assertEquals(sorted, List.copyOf(recorded));
        }
    }

    @Test
    public void fullKeySetAcceptsDuplicatesAndRejectsNewKeysWithoutMutation() {
        ChunkGenerationSemantics.Builder builder = ChunkGenerationSemantics.builder(0, 0, 5L);
        for (int index = 0; index < ChunkGenerationSemantics.MAX_KEYS_PER_KIND; index++) {
            builder.addCaveBiome("iris:cave_" + index);
        }
        builder.addCaveBiome(new String("iris:cave_0"));
        assertThrows(IllegalArgumentException.class, () -> builder.addCaveBiome("iris:overflow"));
        assertThrows(IllegalArgumentException.class, () -> builder.addCaveBiome(" iris:cave_0"));
        assertEquals(ChunkGenerationSemantics.MAX_KEYS_PER_KIND, builder.build().caveBiomeKeys().size());
    }
}
