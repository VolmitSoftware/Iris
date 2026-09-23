package art.arcane.iris.world.history;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class GenerationSemanticKeyValidationTest {
    @Test
    public void resourceKeyLimitCountsUtf8BytesAcrossCharacterWidths() {
        for (String unit : List.of("a", "é", "漢", "\ud801\udc00", "\ud801")) {
            int unitBytes = unit.getBytes(StandardCharsets.UTF_8).length;
            int repetitions = ChunkGenerationSemantics.MAX_KEY_BYTES / unitBytes;
            int remainder = ChunkGenerationSemantics.MAX_KEY_BYTES % unitBytes;
            String accepted = unit.repeat(repetitions) + "a".repeat(remainder);
            assertEquals(ChunkGenerationSemantics.MAX_KEY_BYTES, accepted.getBytes(StandardCharsets.UTF_8).length);
            assertEquals(accepted, ChunkGenerationSemantics.requireResourceKey(accepted));
            assertThrows(IllegalArgumentException.class,
                    () -> ChunkGenerationSemantics.requireResourceKey(accepted + "a"));
        }
        for (int length : new int[]{ChunkGenerationSemantics.MAX_KEY_BYTES / 3,
                ChunkGenerationSemantics.MAX_KEY_BYTES / 3 + 1}) {
            String accepted = "a".repeat(length);
            assertEquals(accepted, ChunkGenerationSemantics.requireResourceKey(accepted));
        }
    }

    @Test
    public void emptyAndPopulatedRecordsRemainImmutableWhenTheirBuilderChanges() {
        ChunkGenerationSemantics.Builder builder = ChunkGenerationSemantics.builder(0, 0, 1L);
        ChunkGenerationSemantics empty = builder.build();
        builder.addObject("iris:b").addObject("iris:a");
        ChunkGenerationSemantics populated = builder.build();
        builder.addObject("iris:c");

        assertTrue(empty.objectKeys().isEmpty());
        assertEquals(List.of("iris:a", "iris:b"), List.copyOf(populated.objectKeys()));
        assertThrows(UnsupportedOperationException.class, () -> empty.objectKeys().add("iris:a"));
        assertThrows(UnsupportedOperationException.class, () -> populated.objectKeys().add("iris:c"));
        assertThrows(IllegalArgumentException.class, () -> builder.addObject(" iris:d"));
        assertEquals(List.of("iris:a", "iris:b", "iris:c"), List.copyOf(builder.build().objectKeys()));
    }

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
