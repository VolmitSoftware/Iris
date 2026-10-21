package art.arcane.iris.world;

import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.platform.generation.BukkitChunkGenerator;
import art.arcane.iris.world.lifecycle.WorldLifecycleCaller;
import art.arcane.iris.world.lifecycle.WorldLifecycleRequest;
import org.bukkit.NamespacedKey;
import org.bukkit.WorldCreator;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;

public class IrisWorldCreatorSeedTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void explicitSeedsReachTheGeneratorAndLifecycleRequestWithoutDefaultSubstitution() throws Exception {
        for (long seed : new long[]{0L, 78264193L, -734829104958217364L, Long.MIN_VALUE, Long.MAX_VALUE}) {
            assertCreatedSeed(new IrisWorldCreator().seed(seed), seed);
        }
    }

    @Test
    public void omittedSeedUsesTheCreationDefault() throws Exception {
        assertCreatedSeed(new IrisWorldCreator(), 1337L);
    }

    private void assertCreatedSeed(IrisWorldCreator creator, long expectedSeed) throws Exception {
        NamespacedKey key = new NamespacedKey("iris", "seed-selection");
        File root = temporaryFolder.newFolder();
        IrisDimension dimension = new IrisDimension();
        dimension.setLoadKey("cohesion");
        WorldCreator bukkitCreator = WorldCreatorCompat.ofKey(key);
        List<IrisWorld> worlds = new ArrayList<>();
        try (MockedStatic<IrisWorldStorage> storage = mockStatic(IrisWorldStorage.class);
             MockedStatic<WorldCreatorCompat> compatibility = mockStatic(WorldCreatorCompat.class);
             MockedConstruction<BukkitChunkGenerator> generators = mockConstruction(BukkitChunkGenerator.class,
                     (generator, context) -> worlds.add((IrisWorld) context.arguments().getFirst()))) {
            storage.when(() -> IrisWorldStorage.keyFromName("seed-selection")).thenReturn(key);
            storage.when(() -> IrisWorldStorage.dimensionRoot(key)).thenReturn(root);
            compatibility.when(() -> WorldCreatorCompat.ofKey(key)).thenReturn(bukkitCreator);
            compatibility.when(() -> WorldCreatorCompat.keyOf(bukkitCreator)).thenReturn(key);

            WorldCreator created = creator.name("seed-selection").dimension(dimension).create();
            WorldLifecycleRequest request = WorldLifecycleRequest.fromCreator(
                    created, false, false, WorldLifecycleCaller.CREATE);

            assertEquals(1, generators.constructed().size());
            assertEquals(expectedSeed, worlds.getFirst().getRawWorldSeed());
            assertEquals(expectedSeed, created.seed());
            assertEquals(expectedSeed, request.seed());
        }
    }
}
