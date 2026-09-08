package art.arcane.iris.engine;

import art.arcane.iris.engine.actuator.IrisDimensionStackActuator;
import art.arcane.iris.engine.actuator.IrisTerrainNormalActuator;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisRegion;
import art.arcane.iris.engine.object.IrisBiomePaletteLayer;
import art.arcane.iris.engine.object.IrisSlopeClip;
import art.arcane.iris.engine.image.IrisImageMapRuntime;
import art.arcane.iris.engine.terrain.Terrain3DColumn;
import art.arcane.iris.engine.terrain.Terrain3DColumnFixtures;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.project.context.ChunkedDataCache;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.iris.util.project.stream.ProceduralStream;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.volmlib.util.collection.KList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DimensionTerrain3DCompositionTest {
    private final Map<String, PlatformBlockState> states = new HashMap<>();
    private IrisPlatform previousPlatform;

    @Before
    public void bindPlatform() {
        previousPlatform = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        IrisPlatforms.unbind();
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(registries.block(anyString())).thenAnswer(invocation -> state(invocation.getArgument(0)));
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);
    }

    @After
    public void restorePlatform() {
        IrisPlatforms.unbind();
        if (previousPlatform != null) {
            IrisPlatforms.bind(previousPlatform);
        }
    }

    @Test
    public void actualStackPassClearsGapsAndPaintsEveryExposedFloor() {
        Terrain3DColumn terrain = Terrain3DColumnFixtures.spans(6, 0, 3, 7, 9);
        DimensionStackLayout layout = DimensionStackLayout.create(32, 0,
                List.of(input(10, 0, null), input(9, 0, terrain)), new int[]{0});
        Hunk<PlatformBlockState> blocks = render(layout, 32);
        assertSame(state("surface"), blocks.getRaw(0, 14, 0));
        assertSame(state("rock"), blocks.getRaw(0, 13, 0));
        assertTrue(blocks.getRaw(0, 15, 0).isAir());
        assertTrue(blocks.getRaw(0, 17, 0).isAir());
        assertSame(state("surface"), blocks.getRaw(0, 20, 0));
        for (int y = 11; y <= 20; y++) {
            assertEquals("y=" + y, layout.isSolid(y), !blocks.getRaw(0, y, 0).isAir());
        }
    }

    @Test
    public void clippedWorldCeilingFindsTheActualFloorBelowAnUndercut() {
        Terrain3DColumn terrain = Terrain3DColumnFixtures.spans(6, 0, 3, 7, 9);
        DimensionStackLayout layout = DimensionStackLayout.create(17, 0,
                List.of(input(10, 0, null), input(9, 0, terrain)), new int[]{0});
        assertEquals(14, layout.clippedStackTerrainTopY());
        assertEquals(14, layout.clippedStackTopY());
        assertEquals(14, layout.layersBottomToTop().get(1).clippedSurfaceY());
        assertSame(layout.layersBottomToTop().get(1), layout.topTerrainLayer());
        assertTrue(render(layout, 17).getRaw(0, 16, 0).isAir());
    }

    @Test
    public void laterLayerAirOverwritesLowerTerrainInAnOverlappingSeam() {
        Terrain3DColumn terrain = Terrain3DColumnFixtures.spans(6, 0, 2, 8, 10);
        DimensionStackLayout layout = DimensionStackLayout.create(32, 0,
                List.of(input(20, 0, null), input(10, 0, terrain)), new int[]{-11});
        Hunk<PlatformBlockState> blocks = render(layout, 32);
        assertFalse(layout.isSolid(15));
        assertTrue(blocks.getRaw(0, 15, 0).isAir());
        assertTrue(layout.isHostFeatureProtectedY(15));
        assertTrue(layout.isSolid(18));
    }

    @Test
    public void referencedStackSelectsEachLedgePaletteFromItsSourceSlope() {
        Terrain3DColumn terrain = Terrain3DColumnFixtures.spans(15, 0, 5, 10, 21);
        IrisBiome biome = paletteBiome(false, new IrisSlopeClip(0D, 1D), "grass");
        DimensionStackLayout.LayerInput upper = materialInput(terrain, biome, 5D);
        DimensionStackLayout layout = DimensionStackLayout.create(64, 0,
                List.of(input(10, 0, null), upper), new int[]{0});
        Hunk<PlatformBlockState> blocks = render(layout, 64);
        assertSame(state("grass"), blocks.getRaw(0, 16, 0));
        assertSame(state("rock"), blocks.getRaw(0, 32, 0));
    }

    @Test
    public void referencedStackLockedPalettePhaseUsesEachSourceFloorHeight() {
        Terrain3DColumn terrain = Terrain3DColumnFixtures.spans(15, 0, 5, 10, 21);
        IrisBiome biome = paletteBiome(true, new IrisSlopeClip(), "a", "b", "c");
        DimensionStackLayout layout = DimensionStackLayout.create(64, 0,
                List.of(input(10, 0, null), materialInput(terrain, biome, 0D)), new int[]{0});
        Hunk<PlatformBlockState> blocks = render(layout, 64);
        assertSame(state("a"), blocks.getRaw(0, 16, 0));
        assertSame(state("c"), blocks.getRaw(0, 15, 0));
        assertSame(state("c"), blocks.getRaw(0, 32, 0));
    }

    @Test
    public void mirroredUpperPalettesUseSourceSlopesAndLockedLayerPhases() {
        Terrain3DColumn terrain = Terrain3DColumnFixtures.spans(15, 0, 5, 10, 21);
        UpperDimensionContext upper = materialUpper(terrain,
                paletteBiome(false, new IrisSlopeClip(0D, 1D), "grass"), 5D);
        Hunk<PlatformBlockState> slopeBlocks = renderUpper(upper);
        assertSame(state("grass"), slopeBlocks.getRaw(0, 58, 0));
        assertSame(state("rock"), slopeBlocks.getRaw(0, 42, 0));

        upper = materialUpper(terrain, paletteBiome(true, new IrisSlopeClip(), "a", "b", "c"), 0D);
        Hunk<PlatformBlockState> lockedBlocks = renderUpper(upper);
        assertSame(state("a"), lockedBlocks.getRaw(0, 58, 0));
        assertSame(state("c"), lockedBlocks.getRaw(0, 59, 0));
        assertSame(state("c"), lockedBlocks.getRaw(0, 42, 0));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void actualUpperPassMirrorsGapsAndPaintsEveryInvertedFace() {
        PlatformBlockState upperRock = state("upper-rock");
        PlatformBlockState upperSurface = state("upper-surface");
        Terrain3DColumn terrain = Terrain3DColumnFixtures.spans(15, 0, 5, 10, 20);
        UpperDimensionContext.Column upperColumn = new UpperDimensionContext.Column(64, 43, terrain);
        UpperDimensionContext upper = mock(UpperDimensionContext.class);
        when(upper.sampleColumn(0, 0)).thenReturn(upperColumn);
        when(upper.getRockBlock(0, 0)).thenReturn(upperRock);
        when(upper.getSurfaceBlock(0, 0)).thenReturn(upperSurface);
        Hunk<PlatformBlockState> blocks = renderUpper(upper);

        assertSame(state("upper-surface"), blocks.getRaw(0, 43, 0));
        assertSame(state("upper-rock"), blocks.getRaw(0, 44, 0));
        assertTrue(blocks.getRaw(0, 54, 0).isAir());
        assertTrue(blocks.getRaw(0, 57, 0).isAir());
        assertSame(state("upper-surface"), blocks.getRaw(0, 58, 0));
        for (int y = 43; y < 64; y++) {
            assertEquals("y=" + y, upperColumn.isSolid(y), !blocks.getRaw(0, y, 0).isAir());
        }
    }

    @Test
    public void clippedOverlappingStacksMatchIndependentVoxelComposition() {
        Random random = new Random(718223L);
        Terrain3DColumn first = Terrain3DColumnFixtures.spans(12, 0, 4, 10, 14, 21, 24);
        Terrain3DColumn second = Terrain3DColumnFixtures.spans(9, 0, 2, 7, 10, 14, 18);
        for (int iteration = 0; iteration < 150; iteration++) {
            int height = 8 + random.nextInt(56);
            DimensionStackLayout layout = DimensionStackLayout.create(height, random.nextInt(10),
                    List.of(input(24, 0, first), input(18, 22, second), input(24, 0, first)),
                    new int[]{random.nextInt(71) - 50, random.nextInt(71) - 50});
            boolean[] solid = new boolean[height];
            boolean[] content = new boolean[height];
            List<DimensionStackLayout.Layer> layers = layout.layersBottomToTop();
            for (int index = 0; index < layers.size(); index++) {
                DimensionStackLayout.Layer layer = layers.get(index);
                for (int y = 0; y < height; y++) {
                    if (index > 0 && y > layers.get(index - 1).contentTopY() && y < layer.localBaseY()) {
                        solid[y] = false;
                        content[y] = false;
                    }
                    if (y >= layer.renderMinY() && y <= layer.renderMaxY()) {
                        int sourceY = y - layer.localBaseY();
                        solid[y] = layer.terrainColumn().isSolid(sourceY);
                        content[y] = solid[y]
                                || sourceY > layer.normalTerrainHeight() && sourceY <= layer.fluidHeight();
                    }
                }
            }
            int highestSolid = 0;
            int highestContent = 0;
            for (int y = 0; y < height; y++) {
                assertEquals("iteration=" + iteration + ",y=" + y, solid[y], layout.isSolid(y));
                if (solid[y]) {
                    highestSolid = y;
                }
                if (content[y]) {
                    highestContent = y;
                }
            }
            assertEquals("iteration=" + iteration, highestSolid, layout.clippedStackTerrainTopY());
            assertEquals("iteration=" + iteration, highestContent, layout.clippedStackTopY());
        }
    }

    @Test
    public void composedSupportRespectsUpperAndStackOwnershipAboveRootTerrain() {
        Engine engine = mock(Engine.class, CALLS_REAL_METHODS);
        IrisComplex complex = mock(IrisComplex.class);
        doReturn(complex).when(engine).getComplex();
        doReturn(64).when(engine).getHeight();
        when(complex.terrainColumn(0, 0)).thenReturn(Terrain3DColumnFixtures.spans(12, 0, 4, 15, 20));
        UpperDimensionContext upper = mock(UpperDimensionContext.class);
        UpperDimensionContext.Column upperColumn = new UpperDimensionContext.Column(64, 40,
                Terrain3DColumnFixtures.spans(12, 0, 5, 10, 23));
        doReturn(upper).when(engine).getUpperContext();
        when(upper.sampleColumn(0, 0)).thenReturn(upperColumn);
        when(upper.getEffectiveSurfaceY(0, 0)).thenReturn(40);
        assertTrue(engine.isTerrainSurfaceSolid(0, 50, 0));
        assertFalse(engine.isTerrainSurfaceSolid(0, 56, 0));
        assertFalse(engine.isTerrainSurfaceSolid(0, 30, 0));

        DimensionStackLayout layout = DimensionStackLayout.create(64, 0,
                List.of(input(20, 0, null), input(30, 0,
                        Terrain3DColumnFixtures.spans(10, 0, 3, 28, 30))), new int[]{0});
        DimensionStackContext stack = mock(DimensionStackContext.class);
        doReturn(stack).when(engine).getDimensionStackContext();
        when(stack.getLayout(0, 0)).thenReturn(layout);
        assertFalse(engine.isTerrainSurfaceSolid(0, 45, 0));
        assertTrue(engine.isTerrainSurfaceSolid(0, 50, 0));
    }

    @Test
    public void stackedHostKeepsOutdoorLedgesInTheSurfaceBiome() {
        Engine engine = mock(Engine.class, CALLS_REAL_METHODS);
        IrisComplex complex = mock(IrisComplex.class);
        doReturn(complex).when(engine).getComplex();
        DimensionStackContext stack = mock(DimensionStackContext.class);
        doReturn(stack).when(engine).getDimensionStackContext();
        DimensionStackLayout.LayerInput bottom = input(24, 0,
                Terrain3DColumnFixtures.spans(12, 0, 4, 20, 24));
        DimensionStackLayout layout = DimensionStackLayout.create(64, 10,
                List.of(bottom, input(9, 0, null)), new int[]{0});
        when(stack.getLayout(0, 0)).thenReturn(layout);
        when(complex.isTerrain3DSurface(0, 4, 0)).thenReturn(true);
        IrisBiome cave = new IrisBiome();
        doReturn(cave).when(engine).getCaveBiome(0, 2, 0);
        doReturn(cave).when(engine).getCaveOrMantleBiome(0, 2, 0);
        assertSame(bottom.biome(), engine.getBiome(0, 4, 0));
        assertSame(bottom.biome(), engine.getBiomeOrMantle(0, 4, 0));
        assertSame(cave, engine.getBiome(0, 2, 0));
        assertSame(cave, engine.getBiomeOrMantle(0, 2, 0));
    }

    @SuppressWarnings("unchecked")
    private Hunk<PlatformBlockState> renderUpper(UpperDimensionContext upper) {
        PlatformBlockState rootRock = state("root");
        Engine engine = mock(Engine.class);
        IrisDimension dimension = new IrisDimension().setBedrock(false).setHideOresForHiddenOre(true);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getSeedManager()).thenReturn(mock(SeedManager.class));
        when(engine.getUpperContext()).thenReturn(upper);
        IrisComplex complex = mock(IrisComplex.class);
        when(engine.getComplex()).thenReturn(complex);
        when(complex.getRiverWaterSurfaceStream()).thenReturn(ProceduralStream.ofDouble((x, z) -> 8D));
        when(complex.getImageMapRuntime()).thenReturn(mock(IrisImageMapRuntime.class));
        IrisBiome biome = mock(IrisBiome.class);
        when(biome.generateLayers(any(), anyDouble(), anyDouble(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(new KList<>());
        ChunkedDataCache<IrisBiome> biomes = mock(ChunkedDataCache.class);
        when(biomes.get(0, 0)).thenReturn(biome);
        ChunkedDataCache<IrisRegion> regions = mock(ChunkedDataCache.class);
        ChunkedDataCache<PlatformBlockState> rocks = mock(ChunkedDataCache.class);
        when(rocks.get(0, 0)).thenReturn(rootRock);
        ChunkContext context = mock(ChunkContext.class);
        when(context.getBiome()).thenReturn(biomes);
        when(context.getRegion()).thenReturn(regions);
        when(context.getRock()).thenReturn(rocks);
        when(context.getRoundedHeight(0, 0)).thenReturn(8);
        Hunk<PlatformBlockState> blocks = Hunk.newArrayHunk(1, 64, 1);
        new IrisTerrainNormalActuator(engine).terrainSliver(0, 0, 0, blocks, context);
        return blocks;
    }

    private Hunk<PlatformBlockState> render(DimensionStackLayout layout, int height) {
        Engine engine = mock(Engine.class);
        when(engine.getSeedManager()).thenReturn(mock(SeedManager.class));
        when(engine.getDimensionStackContext()).thenReturn(mock(DimensionStackContext.class));
        ChunkContext context = mock(ChunkContext.class);
        when(context.isSpeculativeTerrain()).thenReturn(true);
        when(context.getDimensionStackLayout(0, 0)).thenReturn(layout);
        Hunk<PlatformBlockState> output = Hunk.newArrayHunk(1, height, 1);
        for (int y = 0; y < height; y++) {
            output.setRaw(0, y, 0, state("root"));
        }
        new IrisDimensionStackActuator(engine).onActuate(0, 0, output, false, context);
        return output;
    }

    private DimensionStackLayout.LayerInput input(int height, int fluidHeight, Terrain3DColumn terrain) {
        DimensionTerrainContext context = mock(DimensionTerrainContext.class);
        when(context.getDimension()).thenReturn(new IrisDimension().setBedrock(false));
        return new DimensionStackLayout.LayerInput(context, null, null, state("rock"), state("water"),
                state("surface"), height, fluidHeight, terrain);
    }

    private DimensionStackLayout.LayerInput materialInput(Terrain3DColumn terrain, IrisBiome biome, double capSlope) {
        DimensionStackLayout.LayerInput input = input(terrain.topY(), 0, terrain);
        when(input.terrainContext().getSlopeStream()).thenReturn(ProceduralStream.ofDouble((x, z) -> capSlope));
        when(input.terrainContext().getSurfaceSlopeStream(anyInt())).thenAnswer(invocation ->
                ProceduralStream.ofDouble((x, z) -> (int) invocation.getArgument(0) == terrain.topY() ? capSlope : 0D));
        return new DimensionStackLayout.LayerInput(input.terrainContext(), biome, null,
                input.rockBlock(), input.fluidBlock(), null, terrain.topY(), 0, terrain);
    }

    private UpperDimensionContext materialUpper(Terrain3DColumn terrain, IrisBiome biome, double capSlope) {
        PlatformBlockState rock = state("rock");
        UpperDimensionContext upper = mock(UpperDimensionContext.class);
        when(upper.sampleColumn(0, 0)).thenReturn(new UpperDimensionContext.Column(64, 63 - terrain.topY(), terrain));
        when(upper.getRockBlock(0, 0)).thenReturn(rock);
        when(upper.getUpperBiome(0, 0)).thenReturn(biome);
        when(upper.getDimension()).thenReturn(new IrisDimension().setBedrock(false));
        when(upper.getSurfaceSlopeStream(anyInt())).thenAnswer(invocation ->
                ProceduralStream.ofDouble((x, z) -> (int) invocation.getArgument(0) == terrain.topY() ? capSlope : 0D));
        return upper;
    }

    private IrisBiome paletteBiome(boolean locked, IrisSlopeClip slope, String... keys) {
        KList<IrisBiomePaletteLayer> layers = new KList<>();
        for (String key : keys) {
            PlatformBlockState block = state(key);
            IrisBiomePaletteLayer layer = mock(IrisBiomePaletteLayer.class);
            CNG height = mock(CNG.class);
            when(layer.getZoom()).thenReturn(1D);
            when(layer.getMinHeight()).thenReturn(1);
            when(layer.getMaxHeight()).thenReturn(1);
            when(layer.getSlopeCondition()).thenReturn(slope);
            when(layer.getHeightGenerator(any(), any())).thenReturn(height);
            when(height.fit(anyInt(), anyInt(), anyDouble(), anyDouble())).thenReturn(1);
            when(layer.get(any(), anyInt(), anyDouble(), anyDouble(), anyDouble(), any())).thenReturn(block);
            layers.add(layer);
        }
        return new IrisBiome().setLockLayers(locked).setLayers(layers);
    }

    private PlatformBlockState state(String key) {
        return states.computeIfAbsent(key, value -> {
            PlatformBlockState block = mock(PlatformBlockState.class);
            when(block.key()).thenReturn(value);
            when(block.isAir()).thenReturn(value.equalsIgnoreCase("air") || value.equals("minecraft:air"));
            return block;
        });
    }
}
