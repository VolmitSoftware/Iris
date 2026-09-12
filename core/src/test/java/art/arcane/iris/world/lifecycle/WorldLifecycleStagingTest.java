package art.arcane.iris.world.lifecycle;

import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;

public class WorldLifecycleStagingTest {
    @Test
    public void stagedGeneratorIsConsumedExactlyOnce() {
        ChunkGenerator generator = mock(ChunkGenerator.class);

        WorldLifecycleStaging.stageGenerator("world", generator, null);

        assertSame(generator, WorldLifecycleStaging.consumeGenerator("world"));
        assertNull(WorldLifecycleStaging.consumeGenerator("world"));
    }

    @Test
    public void stagedStemGeneratorIsIndependentFromGeneratorConsumption() {
        ChunkGenerator generator = mock(ChunkGenerator.class);

        WorldLifecycleStaging.stageGenerator("world", generator, null);
        WorldLifecycleStaging.stageStemGenerator("world", generator);

        assertSame(generator, WorldLifecycleStaging.consumeGenerator("world"));
        assertSame(generator, WorldLifecycleStaging.consumeStemGenerator("world"));
    }

    @Test
    public void stagedStemGeneratorCanBeInspectedWithoutConsumption() {
        ChunkGenerator generator = mock(ChunkGenerator.class);

        WorldLifecycleStaging.stageStemGenerator("world", generator);

        assertSame(generator, WorldLifecycleStaging.peekStemGenerator("world"));
        assertSame(generator, WorldLifecycleStaging.peekStemGenerator("world"));
        assertSame(generator, WorldLifecycleStaging.consumeStemGenerator("world"));
        assertNull(WorldLifecycleStaging.peekStemGenerator("world"));
    }

    @Test
    public void stagedStemGeneratorCannotBeConsumedByDifferentWorldName() {
        ChunkGenerator generator = mock(ChunkGenerator.class);

        WorldLifecycleStaging.stageStemGenerator("iris_world", generator);

        assertNull(WorldLifecycleStaging.consumeStemGenerator("world"));
        assertSame(generator, WorldLifecycleStaging.consumeStemGenerator("iris_world"));
        assertNull(WorldLifecycleStaging.consumeStemGenerator("world"));
    }

    @Test
    public void clearAllRemovesGeneratorBiomeAndStemState() {
        ChunkGenerator generator = mock(ChunkGenerator.class);
        BiomeProvider biomeProvider = mock(BiomeProvider.class);

        WorldLifecycleStaging.stageGenerator("world", generator, biomeProvider);
        WorldLifecycleStaging.stageStemGenerator("world", generator);
        WorldLifecycleStaging.clearAll("world");

        assertNull(WorldLifecycleStaging.consumeGenerator("world"));
        assertNull(WorldLifecycleStaging.consumeBiomeProvider("world"));
        assertNull(WorldLifecycleStaging.consumeStemGenerator("world"));
    }
}
