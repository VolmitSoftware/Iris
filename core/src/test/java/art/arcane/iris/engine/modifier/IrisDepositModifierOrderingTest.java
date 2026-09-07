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
import art.arcane.iris.util.common.data.B;
import art.arcane.iris.util.common.math.IrisBlockVector;
import art.arcane.iris.util.common.parallel.BurstExecutor;
import art.arcane.iris.util.common.parallel.MultiBurst;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.math.RNG;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

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
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
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
        try (Fixture fixture = new Fixture(true, 1)) {
            fixture.generate();
            assertEquals(List.of("biome", "region", "dimension"), fixture.completed);
            fixture.assertBiomeWins();
        }
    }

    @Test
    public void inlinePreparationUsesTheSameOverlappingHostRules() throws Exception {
        try (Fixture fixture = new Fixture(false, 1)) {
            fixture.generate();
            assertEquals(List.of("dimension", "region", "biome"), fixture.completed);
            fixture.assertBiomeWins();
        }
    }

    @Test
    public void manyAttemptBatchesKeepEveryClumpSeedAndOrderedHostReplacement() throws Exception {
        List<String> expectedSamples = new ArrayList<>();
        RNG generatorRng = new RNG(0L).nextParallelRNG(0L);
        for (String name : List.of("dimension", "region", "biome")) {
            for (int i = 0; i < 25; i++) {
                expectedSamples.add(name + ":" + IrisDepositModifier.clumpSeed(generatorRng.getSeed(), i));
            }
        }
        Collections.sort(expectedSamples);

        for (boolean parallel : new boolean[]{false, true}) {
            try (Fixture fixture = new Fixture(parallel, 25)) {
                fixture.generate();
                Collections.sort(fixture.samples);
                assertEquals(expectedSamples, fixture.samples);
                assertEquals(75, fixture.completed.size());
                fixture.assertBiomeWins();
            }
        }
    }

    private static PlatformBlockState state(String material) {
        return new KeyedBlockState("minecraft:" + material);
    }

    private static final class KeyedBlockState implements PlatformBlockState {
        private final String key;

        private KeyedBlockState(String key) {
            this.key = key;
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public String namespace() {
            return null;
        }

        @Override
        public String materialKey() {
            return key;
        }

        @Override
        public boolean isAir() {
            return false;
        }

        @Override
        public boolean isSolid() {
            return false;
        }

        @Override
        public boolean isOccluding() {
            return false;
        }

        @Override
        public boolean isCustom() {
            return false;
        }

        @Override
        public String deferredPlacementKey() {
            return null;
        }

        @Override
        public PlatformBlockState placementBaseState() {
            return null;
        }

        @Override
        public boolean isFluid() {
            return false;
        }

        @Override
        public boolean isWater() {
            return false;
        }

        @Override
        public boolean isWaterLogged() {
            return false;
        }

        @Override
        public boolean isLit() {
            return false;
        }

        @Override
        public boolean isUpdatable() {
            return false;
        }

        @Override
        public boolean isFoliage() {
            return false;
        }

        @Override
        public boolean isTreeBlock() {
            return false;
        }

        @Override
        public boolean isFoliagePlantable() {
            return false;
        }

        @Override
        public boolean isDecorant() {
            return false;
        }

        @Override
        public boolean isStorage() {
            return false;
        }

        @Override
        public boolean isStorageChest() {
            return false;
        }

        @Override
        public boolean isOre() {
            return false;
        }

        @Override
        public boolean isDeepSlate() {
            return false;
        }

        @Override
        public boolean isVineBlock() {
            return false;
        }

        @Override
        public boolean canPlaceOnto(PlatformBlockState onto) {
            return false;
        }

        @Override
        public boolean matches(PlatformBlockState state) {
            return false;
        }

        @Override
        public boolean isAirOrFluid() {
            return false;
        }

        @Override
        public boolean hasTileEntity() {
            return false;
        }

        @Override
        public PlatformBlockState withProperty(String name, String value) {
            return null;
        }

        @Override
        public Object nativeHandle() {
            return null;
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final ExecutorService executor = Executors.newFixedThreadPool(3);
        private final List<String> completed = Collections.synchronizedList(new ArrayList<>());
        private final List<String> samples = Collections.synchronizedList(new ArrayList<>());
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
        private final int attempts;

        @SuppressWarnings("unchecked")
        private Fixture(boolean parallel, int attempts) {
            this.parallel = parallel;
            this.attempts = attempts;
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
            when(generator.getMinPerChunk()).thenReturn(attempts);
            when(generator.getMaxPerChunk()).thenReturn(attempts);
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
                assertTrue("Placement must release the first clump window before more are prepared",
                        samples.size() < 32 || output.get(0, 4, 0) != stone);
                if (parallel && attempts == 1 && name.equals("dimension")) {
                    assertTrue(regionReady.await(10, TimeUnit.SECONDS));
                } else if (parallel && attempts == 1 && name.equals("region")) {
                    assertTrue(biomeReady.await(10, TimeUnit.SECONDS));
                }
                RNG rng = invocation.getArgument(1);
                samples.add(name + ":" + rng.getSeed());
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
            try (MockedStatic<B> blocks = mockStatic(B.class, CALLS_REAL_METHODS)) {
                blocks.when(() -> B.toDeepSlateOre(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
                new IrisDepositModifier(engine).generateDeposits(output, 0, 0, parallel, context);
            }
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
