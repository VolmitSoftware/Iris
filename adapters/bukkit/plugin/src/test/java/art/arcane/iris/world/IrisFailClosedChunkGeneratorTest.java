package art.arcane.iris.world;

import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Paper 26.3 asks a plugin generator for its biome provider in MinecraftServer.createLevel and
 * CraftServer.createWorld before the level exists, outside any catch. A throw there is the only thing that stops a
 * broken Iris world from being created at all, so every fail-closed generator must keep throwing from it.
 */
public class IrisFailClosedChunkGeneratorTest {
    @Test
    public void everyFailClosedGeneratorRefusesTheBiomeProviderBeforeTheLevelExists() {
        List<ChunkGenerator> generators = List.of(
                IrisFailClosedChunkGenerator.discoveryProbe("world"),
                IrisFailClosedChunkGenerator.startupLock("world", "locked for a test"),
                IrisFailClosedChunkGenerator.refused("world", new IllegalStateException("history drifted")));

        for (ChunkGenerator generator : generators) {
            IllegalStateException refusal = assertThrows(
                    IllegalStateException.class,
                    () -> generator.getDefaultBiomeProvider(mock(WorldInfo.class)));
            assertTrue(refusal.getMessage(), refusal.getMessage().contains("'world'"));
            assertThrows(IllegalStateException.class, () -> generator.getDefaultPopulators(mock(World.class)));
            assertThrows(IllegalStateException.class,
                    () -> generator.getFixedSpawnLocation(mock(World.class), new Random()));
            assertFalse(generator.shouldGenerateNoise());
        }
    }

    @Test
    public void refusalCarriesTheWholeCauseChain() {
        IllegalStateException failure = new IllegalStateException("Iris generation history is unusable at /srv/moon.",
                new IOException("Historical generated registry definition changed"));

        IllegalStateException refusal = assertThrows(
                IllegalStateException.class,
                () -> IrisFailClosedChunkGenerator.refused("world_iris_moon", failure)
                        .getDefaultBiomeProvider(mock(WorldInfo.class)));

        assertTrue(refusal.getMessage(), refusal.getMessage().contains("world_iris_moon"));
        assertTrue(refusal.getMessage(), refusal.getMessage().contains("generation history is unusable"));
        assertTrue(refusal.getMessage(), refusal.getMessage().contains("registry definition changed"));
    }

    /**
     * The refusal is built on the way back to CraftServer, which turns any throw into the vanilla generator, so an
     * unreadable cause still has to produce a generator.
     */
    @Test
    public void aCauseWhoseMessageCannotBeReadStillRefuses() {
        IllegalStateException unreadable = new IllegalStateException() {
            @Override
            public String getMessage() {
                throw new UnsupportedOperationException("message unavailable");
            }
        };

        ChunkGenerator generator = IrisFailClosedChunkGenerator.refused("world_iris_moon", unreadable);

        IllegalStateException refusal = assertThrows(
                IllegalStateException.class,
                () -> generator.getDefaultBiomeProvider(mock(WorldInfo.class)));
        assertTrue(refusal.getMessage(), refusal.getMessage().contains("world_iris_moon"));
    }
}
