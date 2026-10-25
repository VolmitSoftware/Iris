package art.arcane.iris.generation.stage;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.block.B;
import art.arcane.iris.generation.concurrent.BurstExecutor;
import art.arcane.iris.generation.concurrent.MultiBurst;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.context.ChunkedDataCache;
import art.arcane.iris.generation.decoration.IrisDepositGenerator;
import art.arcane.iris.generation.decoration.IrisDepositHeightDistribution;
import art.arcane.iris.generation.decoration.IrisDepositPlacementScope;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.testsupport.KeyedBlockState;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.stream.interpolation.Interpolated;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class IrisDepositModifierPoolGuardTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    private final NativeBlockState stone = new KeyedBlockState("minecraft:stone");
    private final NativeBlockState gravel = new KeyedBlockState("minecraft:gravel");

    @Before
    public void bindPlatform() {
        IrisPlatforms.unbind();
        PlatformRegistries registries = mock(PlatformRegistries.class, withSettings().stubOnly());
        when(registries.block(anyString())).thenReturn(stone);
        IrisPlatform platform = mock(IrisPlatform.class, withSettings().stubOnly());
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);
    }

    @After
    public void unbindPlatform() {
        IrisPlatforms.unbind();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void gravelDepositsNeverReplacePoolWallsFloorsOrWaterCells() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(1);
        try {
            Engine engine = mock(Engine.class, withSettings().stubOnly().defaultAnswer(RETURNS_DEEP_STUBS));
            ChunkContext context = mock(ChunkContext.class, withSettings().stubOnly().defaultAnswer(RETURNS_DEEP_STUBS));
            IrisBiome biome = mock(IrisBiome.class, withSettings().stubOnly());
            IrisRegion region = mock(IrisRegion.class, withSettings().stubOnly());
            when(context.getBiome()).thenReturn(new ChunkedDataCache<>(
                    ProceduralStream.of((x, z) -> biome, Interpolated.of(value -> 0D, value -> null)), 0, 0));
            when(context.getRegion()).thenReturn(new ChunkedDataCache<>(
                    ProceduralStream.of((x, z) -> region, Interpolated.of(value -> 0D, value -> null)), 0, 0));
            when(context.getGenerationSessionId()).thenReturn(11L);
            when(engine.getHeight()).thenReturn(16);
            when(engine.getCaveBiome(anyInt(), anyInt(), anyInt(), any())).thenReturn(null);
            when(engine.getDimension().getDepositVariants()).thenReturn(new KList<>());
            when(region.getDepositVariants()).thenReturn(new KList<>());
            when(region.getDeposits()).thenReturn(new KList<>());
            when(biome.getDeposits()).thenReturn(new KList<>());
            IrisDepositGenerator gravelDeposit = gravelDeposit();
            when(engine.getDimension().getDeposits()).thenReturn(new KList<>(gravelDeposit));
            MantleChunk<Matter> chunk = mock(MantleChunk.class);
            when(chunk.get(5, 4, 5, HydrologyCaveCell.class)).thenReturn(HydrologyCaveCell.of(HydrologyCaveAction.SEAL_GUARD));
            when(chunk.get(6, 4, 6, HydrologyCaveCell.class)).thenReturn(new HydrologyCaveCell(HydrologyCaveAction.POOL_SOURCE, "water", ""));
            Mantle<Matter> mantle = engine.getMantle().getMantle();
            doReturn(chunk).when(mantle).useChunk(0, 0);
            MultiBurst pool = mock(MultiBurst.class, withSettings().stubOnly());
            BurstExecutor burst = new BurstExecutor(executor, 1);
            burst.setMulticore(false);
            when(engine.burst()).thenReturn(pool);
            when(pool.burst(false)).thenReturn(burst);
            Hunk<NativeBlockState> output = Hunk.newArrayHunk(16, 16, 16);
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    output.set(x, 4, z, stone);
                }
            }

            try (MockedStatic<B> blocks = mockStatic(B.class, withSettings().stubOnly().defaultAnswer(CALLS_REAL_METHODS))) {
                blocks.when(() -> B.toDeepSlateOre(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
                new IrisDepositModifier(engine).generateDeposits(output, 0, 0, false, context);
            }

            assertSame("the pool wall guard keeps its rock", stone, output.get(5, 4, 5));
            assertSame("a pool water cell is never replaced", stone, output.get(6, 4, 6));
            assertSame(gravel, output.get(7, 4, 7));
            assertSame(gravel, output.get(5, 4, 6));
            verify(chunk).release();
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private IrisDepositGenerator gravelDeposit() {
        IrisDepositGenerator generator = mock(IrisDepositGenerator.class, withSettings().stubOnly());
        when(generator.getSpawnChance()).thenReturn(1D);
        when(generator.getPerClumpSpawnChance()).thenReturn(1D);
        when(generator.getMinPerChunk()).thenReturn(1);
        when(generator.getMaxPerChunk()).thenReturn(1);
        when(generator.getMinHeight()).thenReturn(4);
        when(generator.getMaxHeight()).thenReturn(4);
        when(generator.getPlacementScope()).thenReturn(IrisDepositPlacementScope.FULL_HEIGHT);
        when(generator.getHeightDistribution()).thenReturn(IrisDepositHeightDistribution.UNIFORM);
        when(generator.matchesBiome(any(), any())).thenReturn(true);
        when(generator.canReplace(stone)).thenReturn(true);
        IrisObject clump = new IrisObject(33, 1, 33);
        for (int x = -16; x <= 16; x++) {
            for (int z = -16; z <= 16; z++) {
                clump.getBlocks().put(new IrisBlockVector(x, 0, z), gravel);
            }
        }
        when(generator.getClump(any(), any(), any())).thenReturn(clump);
        return generator;
    }
}
