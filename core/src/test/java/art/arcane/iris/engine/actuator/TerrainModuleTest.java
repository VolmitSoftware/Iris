package art.arcane.iris.engine.actuator;

import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.UsableBlockPlatform;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineMetrics;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.image.IrisImageMapRuntime;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.project.context.ChunkedDataCache;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.iris.builtin.terrain.HideOresSettings;
import art.arcane.iris.core.modules.BuiltInPack;
import org.junit.After;
import org.junit.Test;

import java.util.List;


import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Terrain fills a column from bedrock to its height with the biome's layers over rock, and the fluid above it up to the fluid height. */
public class TerrainModuleTest {
    private static final int HEIGHT = 20;
    private static final int FLUID_HEIGHT = 25;

    private final PlatformBlockState grass = mock(PlatformBlockState.class);
    private final PlatformBlockState dirt = mock(PlatformBlockState.class);
    private final PlatformBlockState rock = mock(PlatformBlockState.class);
    private final PlatformBlockState water = mock(PlatformBlockState.class);

    @After
    public void unbindPlatform() {
        UsableBlockPlatform.unbind();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void aColumnIsBedrockThenRockThenLayersThenFluid() {
        UsableBlockPlatform.bind();
        TerrainModule.TerrainShaper shaper = new TerrainModule.TerrainShaper(engine(true), mock(TerrainOres.class));
        Hunk<PlatformBlockState> blocks = Hunk.newArrayHunk(16, 64, 16);
        IrisBiome biome = mock(IrisBiome.class);
        KList<PlatformBlockState> layers = new KList<>();
        layers.add(grass, dirt);
        when(biome.generateLayers(any(), anyDouble(), anyDouble(), any(RNG.class), anyInt(), anyInt(), any(), any())).thenReturn(layers);
        when(biome.generateSeaLayers(anyDouble(), anyDouble(), any(RNG.class), anyInt(), any())).thenReturn(new KList<>());

        shaper.shape(0, 0, blocks, context(biome));

        PlatformBlockState floor = blocks.get(3, 0, 4);
        assertNotNull("bedrock at the bottom", floor);
        assertNotSame(rock, floor);
        assertSame("rock below the layers", rock, blocks.get(3, 5, 4));
        assertSame("the second layer", dirt, blocks.get(3, HEIGHT - 1, 4));
        assertSame("the first layer is the surface", grass, blocks.get(3, HEIGHT, 4));
        assertSame("fluid above the surface", water, blocks.get(3, HEIGHT + 1, 4));
        assertSame("fluid up to the fluid height", water, blocks.get(3, FLUID_HEIGHT, 4));
        assertNull("nothing above the fluid", blocks.get(3, FLUID_HEIGHT + 1, 4));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void oresOfTheBiomeThenTheRegionThenTheDimensionReplaceTheBlocksTheirBandsReach() {
        UsableBlockPlatform.bind();
        Engine engine = engine(false);
        IrisDimension dimension = engine.getDimension();
        IrisBiome biome = mock(IrisBiome.class);
        when(biome.generateLayers(any(), anyDouble(), anyDouble(), any(RNG.class), anyInt(), anyInt(), any(), any())).thenReturn(new KList<>());
        when(biome.generateSeaLayers(anyDouble(), anyDouble(), any(RNG.class), anyInt(), any())).thenReturn(new KList<>());
        ChunkContext context = context(biome);
        IrisRegion region = context.getRegion().get(0, 0);
        PlatformBlockState biomeOre = mock(PlatformBlockState.class);
        PlatformBlockState regionOre = mock(PlatformBlockState.class);
        PlatformBlockState dimensionOre = mock(PlatformBlockState.class);
        TerrainOres ores = mock(TerrainOres.class);
        ResolvedOres biomeOres = oresAt(2, biomeOre);
        ResolvedOres regionOres = oresAt(2, regionOre, 3, regionOre);
        ResolvedOres dimensionOres = oresAt(2, dimensionOre, 3, dimensionOre, 4, dimensionOre);
        when(ores.of(biome)).thenReturn(biomeOres);
        when(ores.of(region)).thenReturn(regionOres);
        when(ores.of(dimension)).thenReturn(dimensionOres);
        Hunk<PlatformBlockState> blocks = Hunk.newArrayHunk(16, 64, 16);

        new TerrainModule.TerrainShaper(engine, ores).shape(0, 0, blocks, context);

        assertSame("the biome's ore comes first", biomeOre, blocks.get(3, 2, 4));
        assertSame("then the region's", regionOre, blocks.get(3, 3, 4));
        assertSame("then the dimension's", dimensionOre, blocks.get(3, 4, 4));
        assertSame("rock where no ore reaches", rock, blocks.get(3, 5, 4));
    }

    /** Underground ores that give {@code block} at each listed height and nothing elsewhere. */
    private static ResolvedOres oresAt(Object... heightsAndBlocks) {
        ResolvedOre ore = mock(ResolvedOre.class);
        when(ore.generatesSurface()).thenReturn(false);
        when(ore.minY()).thenReturn(0D);
        when(ore.maxY()).thenReturn(10D);
        for (int index = 0; index < heightsAndBlocks.length; index += 2) {
            when(ore.generate(anyInt(), eq((int) heightsAndBlocks[index]), anyInt())).thenReturn((PlatformBlockState) heightsAndBlocks[index + 1]);
        }
        return new ResolvedOres(List.of(ore));
    }

    @SuppressWarnings("unchecked")
    private ChunkContext context(IrisBiome biome) {
        ChunkedDataCache<IrisBiome> biomes = mock(ChunkedDataCache.class);
        when(biomes.get(anyInt(), anyInt())).thenReturn(biome);
        ChunkedDataCache<IrisRegion> regions = mock(ChunkedDataCache.class);
        when(regions.get(anyInt(), anyInt())).thenReturn(mock(IrisRegion.class));
        ChunkedDataCache<PlatformBlockState> fluids = mock(ChunkedDataCache.class);
        when(fluids.get(anyInt(), anyInt())).thenReturn(water);
        ChunkedDataCache<PlatformBlockState> rocks = mock(ChunkedDataCache.class);
        when(rocks.get(anyInt(), anyInt())).thenReturn(rock);
        ChunkContext context = mock(ChunkContext.class);
        when(context.getBiome()).thenReturn(biomes);
        when(context.getRegion()).thenReturn(regions);
        when(context.getFluid()).thenReturn(fluids);
        when(context.getRock()).thenReturn(rocks);
        when(context.getRoundedHeight(anyInt(), anyInt())).thenReturn(HEIGHT);
        return context;
    }

    private Engine engine(boolean hidesOres) {
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.getFluidHeight()).thenReturn(FLUID_HEIGHT);
        when(dimension.isBedrock()).thenReturn(true);
        when(dimension.getModuleSettings()).thenReturn(BuiltInPack.settings(new HideOresSettings(hidesOres)));
        IrisComplex complex = mock(IrisComplex.class);
        when(complex.getImageMapRuntime()).thenReturn(mock(IrisImageMapRuntime.class));
        SeedManager seeds = mock(SeedManager.class);
        when(seeds.getTerrain()).thenReturn(1337L);
        Engine engine = mock(Engine.class);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getSeedManager()).thenReturn(seeds);
        when(engine.getMetrics()).thenReturn(new EngineMetrics(16));
        return engine;
    }
}
