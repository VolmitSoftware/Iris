package art.arcane.iris.engine.actuator;

import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.history.BoundaryColumnGeometry;
import art.arcane.iris.engine.history.BoundaryGeometryInfluence;
import art.arcane.iris.engine.history.NativeTerrainReceipt;
import art.arcane.iris.engine.history.SavedTerrainChunk;
import art.arcane.iris.engine.history.TransitionGenerationPlan;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.project.hunk.Hunk;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class IrisTransitionGeometryActuatorTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    private IrisPlatform previous;
    private PlatformRegistries registries;
    private PlatformBlockState stone;
    private PlatformBlockState air;

    @Before
    public void bind() {
        previous = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatforms.unbind();
        registries = mock(PlatformRegistries.class);
        stone = state("minecraft:stone", false);
        air = state("minecraft:air", true);
        when(registries.air()).thenReturn(air);
        when(registries.blockOrNull("minecraft:stone")).thenReturn(stone);
        when(registries.blockOrNull("minecraft:air")).thenReturn(air);
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);
    }

    @After
    public void restore() {
        IrisPlatforms.unbind();
        if (previous != null) {
            IrisPlatforms.bind(previous);
        }
    }

    @Test
    public void boundaryReceiptRetainsCustomKeyAndCarrierPhysics() throws Exception {
        String key = "itemsadder:rocks/ruby_ore";
        PlatformBlockState carrier = state("minecraft:oak_slab[type=bottom,waterlogged=true]", false);
        when(carrier.isWaterLogged()).thenReturn(true);
        PlatformBlockState custom = state(key, false);
        when(custom.isCustom()).thenReturn(true);
        when(custom.placementBaseState()).thenReturn(carrier);
        Hunk<PlatformBlockState> blocks = filled();
        blocks.setRaw(0, 3, 0, custom);
        Hunk<PlatformBiome> biomes = Hunk.newArrayHunk(16, 16, 16);
        PlatformBiome biome = mock(PlatformBiome.class);
        when(biome.key()).thenReturn("minecraft:plains");
        biomes.fill(biome);
        ChunkContext context = mock(ChunkContext.class);
        when(context.getRoundedHeight(anyInt(), anyInt())).thenReturn(3);

        SavedTerrainChunk captured = IrisTransitionGeometryActuator.capture(0, 0, blocks, biomes, 0, context, true);
        byte[] encoded = NativeTerrainReceipt.encode(captured, 7, "epoch");
        SavedTerrainChunk restored = NativeTerrainReceipt.decode(encoded, "minecraft:noise").terrain();
        BoundaryColumnGeometry.Voxel voxel = restored.column(0, 0).geometry().voxelAt(3);

        assertEquals(key, voxel.stateKey());
        assertEquals(BoundaryColumnGeometry.Phase.SOLID, voxel.phase());
        assertEquals("minecraft:water[level=0]", voxel.fluidStateKey());
        assertTrue(voxel.protectedContent());
    }

    @Test
    public void appliesCaveAndIslandGeometryThenUpdatesDecorationHeight() {
        Hunk<PlatformBlockState> blocks = filled();
        ChunkContext context = context("minecraft:stone");
        Engine engine = mock(Engine.class);

        new IrisTransitionGeometryActuator(engine).generate(16, 0, blocks,
                Hunk.newArrayHunk(16, 16, 16), false, context);

        assertSame(stone, blocks.getRaw(0, 0, 0));
        assertSame(air, blocks.getRaw(0, 1, 0));
        assertSame(air, blocks.getRaw(0, 2, 0));
        assertSame(stone, blocks.getRaw(0, 3, 0));
        assertSame(air, blocks.getRaw(0, 4, 0));
        verify(context).setTerrainHeight(0, 0, 3);
    }

    @Test
    public void refusesUnresolvableRetainedMaterialInsteadOfReplacingItWithAir() {
        Engine engine = mock(Engine.class);
        ChunkContext context = context("removed:stone");
        Hunk<PlatformBlockState> blocks = filled();
        Hunk<PlatformBiome> biomes = Hunk.newArrayHunk(16, 16, 16);

        assertThrows(IllegalStateException.class, () -> new IrisTransitionGeometryActuator(engine)
                .generate(16, 0, blocks, biomes, false, context));
    }

    private ChunkContext context(String material) {
        ArrayList<BoundaryColumnGeometry.Voxel> voxels = new ArrayList<>(16);
        for (int y = 0; y < 16; y++) {
            boolean solid = y == 0 || y == 3;
            voxels.add(new BoundaryColumnGeometry.Voxel(solid ? material : "minecraft:air",
                    solid ? BoundaryColumnGeometry.Phase.SOLID : BoundaryColumnGeometry.Phase.AIR, "", false));
        }
        BoundaryGeometryInfluence influence = new BoundaryGeometryInfluence(0D, 0D,
                List.of(new BoundaryGeometryInfluence.Contribution(BoundaryColumnGeometry.fromVoxels(0, voxels), 1D)));
        TransitionGenerationPlan plan = mock(TransitionGenerationPlan.class);
        when(plan.geometryAt(anyInt(), anyInt())).thenReturn(influence);
        when(plan.hasTransitionAtChunk(anyInt(), anyInt())).thenReturn(true);
        TransitionGenerationPlan.TerrainSample sample = mock(TransitionGenerationPlan.TerrainSample.class);
        when(sample.historicalOceanFloorHeight()).thenReturn(3D);
        when(plan.terrainSampleAt(anyInt(), anyInt())).thenReturn(sample);
        IrisComplex complex = mock(IrisComplex.class);
        when(complex.getTransitionGenerationPlan()).thenReturn(plan);
        ChunkContext context = mock(ChunkContext.class);
        when(context.getComplex()).thenReturn(complex);
        return context;
    }

    private Hunk<PlatformBlockState> filled() {
        Hunk<PlatformBlockState> blocks = Hunk.newArrayHunk(16, 16, 16);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < 16; y++) {
                    blocks.setRaw(x, y, z, stone);
                }
            }
        }
        return blocks;
    }

    private static PlatformBlockState state(String key, boolean isAir) {
        PlatformBlockState state = mock(PlatformBlockState.class);
        when(state.key()).thenReturn(key);
        when(state.isAir()).thenReturn(isAir);
        when(state.isSolid()).thenReturn(!isAir);
        return state;
    }
}
