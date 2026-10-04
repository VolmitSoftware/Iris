package art.arcane.iris.world.entity;

import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class EntitySpawnSeedTest {
    @Test
    public void coordinateAndAttemptSeedsRemainPinned() {
        assertEquals(-4130335506775599505L, EntitySpawnSeed.chunk(0L, 0, 0).getSeed());
        assertEquals(8337887711347547470L, EntitySpawnSeed.chunk(1337L, -2, 3).getSeed());
        assertEquals(7508281853961706446L,
                EntitySpawnSeed.chunk(-1L, Integer.MAX_VALUE, Integer.MIN_VALUE).getSeed());
        assertEquals(-2305592493329120404L, EntitySpawnSeed.marker(0L, 0, 0, 0).getSeed());
        assertEquals(8373759002518926811L, EntitySpawnSeed.marker(1337L, -32, -64, 48).getSeed());
        assertEquals(-8713573891296014035L,
                EntitySpawnSeed.marker(-1L, Integer.MAX_VALUE, Integer.MIN_VALUE, 1).getSeed());
        assertEquals(-6302458730787398690L, EntitySpawnSeed.entity(1337L, -1).getSeed());
        assertEquals(858402071020777840L, EntitySpawnSeed.entity(1337L, 0).getSeed());
        assertEquals(-632476273033191086L, EntitySpawnSeed.entity(1337L, 1).getSeed());
        assertEquals(4034378215462583149L, EntitySpawnSeed.entity(1337L, 17).getSeed());
    }

    @Test
    public void consumptionAndReverseOrderDoNotChangeSiblingAttempts() {
        long batchSeed = EntitySpawnSeed.chunk(1337L, -2, 3).getSeed();
        List<Long> forward = new ArrayList<>();
        for (int ordinal = 0; ordinal < 16; ordinal++) {
            forward.add(EntitySpawnSeed.entity(batchSeed, ordinal).nextLong());
        }
        for (int ordinal = 15; ordinal >= 0; ordinal--) {
            RNG sibling = EntitySpawnSeed.entity(batchSeed, ordinal - 1);
            for (int draw = 0; draw < 100; draw++) {
                sibling.nextLong();
            }
            assertEquals(forward.get(ordinal).longValue(), EntitySpawnSeed.entity(batchSeed, ordinal).nextLong());
        }
        assertNotEquals(EntitySpawnSeed.marker(1337L, -32, -64, 48).getSeed(),
                EntitySpawnSeed.marker(1337L, -32, -63, 48).getSeed());
        assertNotEquals(EntitySpawnSeed.chunk(1337L, -2, 3).getSeed(),
                EntitySpawnSeed.chunk(1338L, -2, 3).getSeed());
    }

    @Test
    public void concurrentCoordinateEvaluationMatchesSerialEvaluation() {
        List<CompletableFuture<Long>> results = new ArrayList<>();
        for (int ordinal = 0; ordinal < 64; ordinal++) {
            int index = ordinal;
            results.add(CompletableFuture.supplyAsync(() -> EntitySpawnSeed.marker(
                    42L, index - 32, -64 + index, -index).nextLong()));
        }
        for (int ordinal = 63; ordinal >= 0; ordinal--) {
            assertEquals(EntitySpawnSeed.marker(42L, ordinal - 32, -64 + ordinal, -ordinal).nextLong(),
                    results.get(ordinal).join().longValue());
        }
    }
}
