package art.arcane.iris.generation.stage;

import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EngineMetrics;
import art.arcane.iris.generation.runtime.SeedManager;
import art.arcane.iris.world.history.TransitionGenerationPlan;
import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBiomeWriter;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.context.ChunkedDataCache;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterBiomeInject;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class IrisBiomeActuatorCoordinateTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @After
    public void unbindPlatform() {
        IrisPlatforms.unbind();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void speculativeTerrainDoesNotPublishPersistentMetadata() {
        Engine engine = mock(Engine.class);
        ChunkContext context = mock(ChunkContext.class);
        when(context.isSpeculativeTerrain()).thenReturn(true);

        IrisBiomeActuator.publishNaturalMetadata(engine, 0, 0, mock(Hunk.class), context);

        verifyNoMoreInteractions(engine);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void derivativeScatterUsesEachWorldColumnCoordinate() {
        bindPlatform();
        IrisComplex complex = mock(IrisComplex.class);
        when(complex.allowsNewDiscreteContentAt(anyInt(), anyInt())).thenReturn(true);
        Engine engine = engine(complex);
        IrisBiomeActuator actuator = new IrisBiomeActuator(engine);
        Hunk<PlatformBiome> output = mock(Hunk.class);
        when(output.getWidth()).thenReturn(2);
        when(output.getDepth()).thenReturn(2);
        when(output.getHeight()).thenReturn(1);

        List<String> samples = new ArrayList<>();
        IrisBiome biome = mock(IrisBiome.class);
        when(biome.getSkyBiomeKey(any(RNG.class), same(engine), anyDouble(), anyDouble(), anyDouble()))
                .thenAnswer(invocation -> {
                    double worldX = invocation.getArgument(2);
                    double worldZ = invocation.getArgument(4);
                    samples.add((int) worldX + "," + (int) worldZ);
                    return "minecraft:plains";
                });

        ChunkedDataCache<IrisBiome> biomeCache = mock(ChunkedDataCache.class);
        when(biomeCache.get(anyInt(), anyInt())).thenReturn(biome);
        ChunkContext context = mock(ChunkContext.class);
        when(context.getComplex()).thenReturn(complex);
        when(context.getBiome()).thenReturn(biomeCache);

        actuator.onActuate(100, -200, output, false, context);

        assertEquals(List.of("100,-200", "100,-199", "101,-200", "101,-199"), samples);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void stackedBiomeReplacementRemovesEveryExistingMarkerWithItsTypedSlice() {
        Mantle<Matter> mantle = mock(Mantle.class);

        IrisBiomeActuator.clearBiomeMatterRange(mantle, 100, -200, 4, 6);

        verify(mantle).remove(100, 4, -200, MatterBiomeInject.class);
        verify(mantle).remove(100, 5, -200, MatterBiomeInject.class);
        verify(mantle).remove(100, 6, -200, MatterBiomeInject.class);
        verifyNoMoreInteractions(mantle);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void transitionColumnOverlaysFrozenPhysicalKeyAfterCurrentBiome() {
        bindPlatform();
        IrisComplex complex = mock(IrisComplex.class);
        when(complex.allowsNewDiscreteContentAt(100, -200)).thenReturn(false);
        TransitionGenerationPlan transitionPlan = mock(TransitionGenerationPlan.class);
        TransitionGenerationPlan.TerrainSample terrainSample = mock(TransitionGenerationPlan.TerrainSample.class);
        when(complex.getTransitionGenerationPlan()).thenReturn(transitionPlan);
        when(transitionPlan.terrainSampleAt(100, -200)).thenReturn(terrainSample);
        when(transitionPlan.historicalPhysicalBiomeKeyAt(100, 0, -200, terrainSample))
                .thenReturn(Optional.of("iris:old-physical"));
        Engine engine = engine(complex);
        IrisBiomeActuator actuator = new IrisBiomeActuator(engine);
        Hunk<PlatformBiome> output = mock(Hunk.class);
        when(output.getWidth()).thenReturn(1);
        when(output.getDepth()).thenReturn(1);
        when(output.getHeight()).thenReturn(1);
        ChunkedDataCache<IrisBiome> biomeCache = currentBiomeCache(engine);
        ChunkContext context = mock(ChunkContext.class);
        when(context.getComplex()).thenReturn(complex);
        when(context.getBiome()).thenReturn(biomeCache);

        actuator.onActuate(100, -200, output, false, context);

        verify(biomeCache).get(0, 0);
        verify(IrisPlatforms.get().registries()).biome("iris:old-physical");
        verify(IrisPlatforms.get().biomeWriter(), never()).biomeIdFor("iris:old-physical");
        Mantle<Matter> mantle = engine.getMantle().getMantle();
        verify(mantle).set(eq(100), eq(0), eq(-200), argThat(value -> {
            MatterBiomeInject injection = (MatterBiomeInject) value;
            return !injection.isCustom() && "iris:old-physical".equals(injection.getBiomeKey());
        }));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void transitionColumnRestoresEveryFrozenVerticalBiomeRange() {
        bindPlatform();
        IrisComplex complex = mock(IrisComplex.class);
        when(complex.allowsNewDiscreteContentAt(100, -200)).thenReturn(false);
        TransitionGenerationPlan transitionPlan = mock(TransitionGenerationPlan.class);
        TransitionGenerationPlan.TerrainSample terrainSample = mock(TransitionGenerationPlan.TerrainSample.class);
        when(complex.getTransitionGenerationPlan()).thenReturn(transitionPlan);
        when(transitionPlan.terrainSampleAt(100, -200)).thenReturn(terrainSample);
        when(transitionPlan.historicalPhysicalBiomeKeyAt(eq(100), anyInt(), eq(-200), same(terrainSample))).thenAnswer(invocation ->
                Optional.of(invocation.<Integer>getArgument(1) < -60
                        ? "iris:old-bottom"
                        : "iris:old-top"));
        Engine engine = engine(complex);
        when(engine.getMinHeight()).thenReturn(-64);
        IrisBiomeActuator actuator = new IrisBiomeActuator(engine);
        Hunk<PlatformBiome> output = mock(Hunk.class);
        when(output.getWidth()).thenReturn(1);
        when(output.getDepth()).thenReturn(1);
        when(output.getHeight()).thenReturn(8);
        ChunkContext context = mock(ChunkContext.class);
        when(context.getComplex()).thenReturn(complex);
        ChunkedDataCache<IrisBiome> biomeCache = currentBiomeCache(engine);
        when(context.getBiome()).thenReturn(biomeCache);

        actuator.onActuate(100, -200, output, false, context);

        verify(transitionPlan, times(1)).terrainSampleAt(100, -200);
        verify(transitionPlan, never()).newEpochWeightAt(anyInt(), anyInt());
        verify(transitionPlan, never()).historicalPhysicalBiomeKeyAt(anyInt(), anyInt(), anyInt());
        verify(output).set(eq(0), eq(0), eq(0), eq(0), eq(3), eq(0), any(PlatformBiome.class));
        verify(output).set(eq(0), eq(4), eq(0), eq(0), eq(7), eq(0), any(PlatformBiome.class));
        Mantle<Matter> mantle = engine.getMantle().getMantle();
        verify(mantle).set(eq(100), eq(0), eq(-200), argThat(value -> {
            MatterBiomeInject injection = (MatterBiomeInject) value;
            return "iris:old-bottom".equals(injection.getBiomeKey());
        }));
        verify(mantle).set(eq(100), eq(4), eq(-200), argThat(value -> {
            MatterBiomeInject injection = (MatterBiomeInject) value;
            return "iris:old-top".equals(injection.getBiomeKey());
        }));
    }

    @SuppressWarnings("unchecked")
    private ChunkedDataCache<IrisBiome> currentBiomeCache(Engine engine) {
        IrisBiome biome = mock(IrisBiome.class);
        when(biome.getSkyBiomeKey(any(RNG.class), same(engine), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn("minecraft:plains");
        ChunkedDataCache<IrisBiome> cache = mock(ChunkedDataCache.class);
        when(cache.get(anyInt(), anyInt())).thenReturn(biome);
        return cache;
    }

    @SuppressWarnings("unchecked")
    private Engine engine(IrisComplex complex) {
        Mantle<Matter> mantle = mock(Mantle.class);
        EngineMantle engineMantle = mock(EngineMantle.class);
        when(engineMantle.getMantle()).thenReturn(mantle);
        SeedManager seedManager = mock(SeedManager.class);
        when(seedManager.getBiome()).thenReturn(1337L);
        Engine engine = mock(Engine.class);
        when(engine.getCacheID()).thenReturn(1);
        when(engine.getSeedManager()).thenReturn(seedManager);
        when(engine.getMantle()).thenReturn(engineMantle);
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getMetrics()).thenReturn(new EngineMetrics(16));
        return engine;
    }

    private void bindPlatform() {
        IrisPlatforms.unbind();
        PlatformBiome biome = mock(PlatformBiome.class);
        PlatformBlockState block = mock(PlatformBlockState.class);
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(registries.biome(anyString())).thenReturn(biome);
        when(registries.block(anyString())).thenReturn(block);
        PlatformBiomeWriter biomeWriter = mock(PlatformBiomeWriter.class);
        when(biomeWriter.biomeIdFor(anyString())).thenReturn(1);
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        when(platform.biomeWriter()).thenReturn(biomeWriter);
        IrisPlatforms.bind(platform);
    }
}
