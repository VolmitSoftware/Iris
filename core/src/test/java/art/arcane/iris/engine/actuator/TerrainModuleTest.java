package art.arcane.iris.engine.actuator;

import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineMetrics;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.image.IrisImageMapRuntime;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.project.context.ChunkedDataCache;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import org.junit.After;
import org.junit.Test;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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
        IrisPlatforms.unbind();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void aColumnIsBedrockThenRockThenLayersThenFluid() {
        bindPlatform();
        TerrainModule.TerrainShaper shaper = new TerrainModule.TerrainShaper(engine());
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

    private Engine engine() {
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.getFluidHeight()).thenReturn(FLUID_HEIGHT);
        when(dimension.isBedrock()).thenReturn(true);
        when(dimension.isHideOresForHiddenOre()).thenReturn(true);
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

    private void bindPlatform() {
        IrisPlatforms.unbind();
        PlatformRegistries registries = mock(PlatformRegistries.class);
        // B keeps the first registries any test binds, and the shaper reads its bedrock from it once, so every answer must be usable by the tests that run after this one.
        Map<String, PlatformBlockState> states = new HashMap<>();
        when(registries.block(anyString())).thenAnswer(invocation -> states.computeIfAbsent(
                invocation.<String>getArgument(0).toLowerCase(Locale.ROOT), this::usableBlock));
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);
    }

    private PlatformBlockState usableBlock(String name) {
        String key = name.contains(":") ? name : "minecraft:" + name;
        PlatformBlockState block = mock(PlatformBlockState.class);
        when(block.key()).thenReturn(key);
        when(block.materialKey()).thenReturn(key.split("\\[", 2)[0]);
        when(block.isSolid()).thenReturn(!key.contains("air"));
        when(block.isOccluding()).thenReturn(!key.contains("air"));
        return block;
    }
}
