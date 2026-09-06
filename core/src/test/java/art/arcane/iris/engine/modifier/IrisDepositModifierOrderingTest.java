package art.arcane.iris.engine.modifier;

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.IrisDepositGenerator;
import art.arcane.iris.engine.object.IrisDepositHeightDistribution;
import art.arcane.iris.engine.object.IrisDepositPlacementScope;
import art.arcane.iris.engine.object.IrisObject;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.util.common.math.IrisBlockVector;
import art.arcane.iris.util.common.parallel.BurstExecutor;
import art.arcane.iris.util.common.parallel.MultiBurst;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.matter.Matter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IrisDepositModifierOrderingTest {
    @Before
    public void bindPlatform() {
        IrisPlatforms.unbind();
        PlatformRegistries registries = mock(PlatformRegistries.class);
        PlatformBlockState stone = state("stone");
        when(registries.block(anyString())).thenReturn(stone);
        when(registries.deepSlateOre(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);
    }

    @After
    public void unbindPlatform() {
        IrisPlatforms.unbind();
    }

    @Test
    public void reverseWorkerCompletionKeepsDimensionRegionBiomePlacementOrder() throws Exception {
        try (Fixture fixture = new Fixture(true)) {
            fixture.generate();
            assertEquals(List.of("biome", "region", "dimension"), fixture.completed);
            fixture.assertBiomeWins();
        }
    }

    @Test
    public void inlinePreparationUsesTheSameOverlappingHostRules() throws Exception {
        try (Fixture fixture = new Fixture(false)) {
            fixture.generate();
            assertEquals(List.of("dimension", "region", "biome"), fixture.completed);
            fixture.assertBiomeWins();
        }
    }

    private static PlatformBlockState state(String material) {
        PlatformBlockState state = mock(PlatformBlockState.class);
        when(state.key()).thenReturn("minecraft:" + material);
        when(state.materialKey()).thenReturn("minecraft:" + material);
        return state;
    }

    private static final class Fixture implements AutoCloseable {
        private final ExecutorService executor = Executors.newFixedThreadPool(3);
        private final List<String> completed = Collections.synchronizedList(new ArrayList<>());
        private final CountDownLatch biomeReady = new CountDownLatch(1);
        private final CountDownLatch regionReady = new CountDownLatch(1);
        private final Engine engine = mock(Engine.class, RETURNS_DEEP_STUBS);
        private final ChunkContext context = mock(ChunkContext.class, RETURNS_DEEP_STUBS);
        private final PlatformBlockState stone = state("stone");
        private final PlatformBlockState granite = state("granite");
        private final PlatformBlockState diorite = state("diorite");
        private final PlatformBlockState diamond = state("diamond_ore");
        private final Hunk<PlatformBlockState> output = Hunk.newArrayHunk(16, 16, 16);
        private final MantleChunk<Matter> chunk;
        private final boolean parallel;

        @SuppressWarnings("unchecked")
        private Fixture(boolean parallel) {
            this.parallel = parallel;
            when(engine.getHeight()).thenReturn(16);
            when(engine.getCaveBiome(anyInt(), anyInt(), anyInt(), any())).thenReturn(null);
            when(engine.getDimension().getDepositVariants()).thenReturn(new KList<>());
            when(context.getRegion().get(anyInt(), anyInt()).getDepositVariants()).thenReturn(new KList<>());
            when(context.getGenerationSessionId()).thenReturn(11L);
            chunk = mock(MantleChunk.class);
            Mantle<Matter> mantle = engine.getMantle().getMantle();
            doReturn(chunk).when(mantle).getChunk(0, 0);
            when(chunk.use()).thenReturn(chunk);
            MultiBurst pool = mock(MultiBurst.class);
            BurstExecutor burst = new BurstExecutor(executor, 3);
            burst.setMulticore(parallel);
            when(engine.burst()).thenReturn(pool);
            when(pool.burst(parallel)).thenReturn(burst);
            IrisDepositGenerator dimensionDeposit = generator("dimension", stone, granite);
            IrisDepositGenerator regionDeposit = generator("region", granite, diorite);
            IrisDepositGenerator biomeDeposit = generator("biome", diorite, diamond);
            when(engine.getDimension().getDeposits()).thenReturn(new KList<>(dimensionDeposit));
            when(context.getRegion().get(7, 7).getDeposits()).thenReturn(new KList<>(regionDeposit));
            when(context.getBiome().get(7, 7).getDeposits()).thenReturn(new KList<>(biomeDeposit));
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    output.set(x, 4, z, stone);
                }
            }
        }

        private IrisDepositGenerator generator(String name, PlatformBlockState requiredHost, PlatformBlockState replacement) {
            IrisDepositGenerator generator = mock(IrisDepositGenerator.class);
            when(generator.getSpawnChance()).thenReturn(1D);
            when(generator.getPerClumpSpawnChance()).thenReturn(1D);
            when(generator.getMinPerChunk()).thenReturn(1);
            when(generator.getMaxPerChunk()).thenReturn(1);
            when(generator.getMinHeight()).thenReturn(4);
            when(generator.getMaxHeight()).thenReturn(4);
            when(generator.getPlacementScope()).thenReturn(IrisDepositPlacementScope.FULL_HEIGHT);
            when(generator.getHeightDistribution()).thenReturn(IrisDepositHeightDistribution.UNIFORM);
            when(generator.matchesBiome(any(), any())).thenReturn(true);
            when(generator.canReplace(requiredHost)).thenReturn(true);
            IrisObject clump = new IrisObject(33, 1, 33);
            for (int x = -16; x <= 16; x++) {
                for (int z = -16; z <= 16; z++) {
                    clump.getBlocks().put(new IrisBlockVector(x, 0, z), replacement);
                }
            }
            when(generator.getClump(any(), any(), any())).thenAnswer(invocation -> {
                if (parallel && name.equals("dimension")) {
                    assertTrue(regionReady.await(10, TimeUnit.SECONDS));
                } else if (parallel && name.equals("region")) {
                    assertTrue(biomeReady.await(10, TimeUnit.SECONDS));
                }
                completed.add(name);
                if (name.equals("biome")) {
                    biomeReady.countDown();
                } else if (name.equals("region")) {
                    regionReady.countDown();
                }
                return clump;
            });
            return generator;
        }

        private void generate() {
            new IrisDepositModifier(engine).generateDeposits(output, 0, 0, parallel, context);
            verify(chunk).release();
        }

        private void assertBiomeWins() {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    assertSame("Deposit collision at " + x + ",4," + z, diamond, output.get(x, 4, z));
                }
            }
        }

        @Override
        public void close() throws Exception {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
