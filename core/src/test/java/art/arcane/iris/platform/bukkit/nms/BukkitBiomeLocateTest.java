package art.arcane.iris.platform.bukkit.nms;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomeCustom;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.runtime.GenerationSessionLease;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.world.IrisWorld;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.volmlib.nativelib.terrain.NativeBiomeRegistry;
import art.arcane.volmlib.nativelib.terrain.NativeBiomeSourcePolicy;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.stream.interpolation.Interpolated;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BukkitBiomeLocateTest {
    @Test
    public void locateResolvesVisibleBiomesOnlyInColumnsThatCanHoldTheTarget() throws Exception {
        IrisBiome isles = new IrisBiome().setCustomDerivitives(new KList<>(new IrisBiomeCustom().setId("mirage")));
        isles.setLoadKey("isles");
        IrisBiome meadow = new IrisBiome().setDerivative("minecraft:plains");
        meadow.setLoadKey("meadow");
        IrisRegion main = new IrisRegion().setLandBiomes(new KList<>("meadow"));
        main.setLoadKey("main");
        IrisRegion archipelago = new IrisRegion().setLandBiomes(new KList<>("isles"));
        archipelago.setLoadKey("archipelago");
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.getReachableBiomes(any())).thenReturn(new KList<>(meadow, isles));
        when(dimension.getAllRegions(any())).thenReturn(new KList<>(main, archipelago));
        when(dimension.getCarving()).thenReturn(new KList<>());
        when(dimension.getSubterrainFeatures()).thenReturn(new KList<>());
        when(dimension.getCustomBiomeKey("mirage")).thenReturn("pack:mirage");
        @SuppressWarnings("unchecked")
        ResourceLoader<IrisBiome> biomes = (ResourceLoader<IrisBiome>) mock(ResourceLoader.class);
        when(biomes.load("meadow")).thenReturn(meadow);
        when(biomes.load("isles")).thenReturn(isles);
        IrisData data = mock(IrisData.class);
        when(data.getBiomeLoader()).thenReturn(biomes);
        when(data.customBiomeResourceKey(any(IrisDimension.class), anyString()))
                .thenAnswer(invocation -> "pack:" + invocation.getArgument(1));
        IrisComplex complex = mock(IrisComplex.class);
        when(complex.getRegionStream()).thenReturn(ProceduralStream.of(
                (x, z) -> x >= 4096D ? archipelago : main, Interpolated.of(value -> 0D, value -> main)));
        when(complex.getLandBiomeStream()).thenReturn(ProceduralStream.of(
                (x, z) -> x >= 4096D ? isles : meadow, Interpolated.of(value -> 0D, value -> meadow)));
        when(complex.getSeaBiomeStream()).thenReturn(ProceduralStream.of(
                (x, z) -> meadow, Interpolated.of(value -> 0D, value -> meadow)));
        when(complex.getShoreBiomeStream()).thenReturn(ProceduralStream.of(
                (x, z) -> meadow, Interpolated.of(value -> 0D, value -> meadow)));
        when(complex.historicalPhysicalBiomeKeyAt(anyInt(), anyInt(), anyInt())).thenReturn(Optional.empty());
        GenerationHistoryRuntimeRouter.CoordinateScope scope = mock(GenerationHistoryRuntimeRouter.CoordinateScope.class);
        IrisEngine engine = mock(IrisEngine.class);
        when(engine.openGenerationHistoryCoordinateScope(anyInt(), anyInt())).thenReturn(scope);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getData()).thenReturn(data);
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getWorld()).thenReturn(IrisWorld.builder().minHeight(-64).maxHeight(320).build());
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getCacheID()).thenReturn(1);
        when(engine.acquireGenerationLease(anyString())).thenReturn(GenerationSessionLease.noop());
        when(engine.answersFromNaturalTerrain(anyInt(), anyInt())).thenReturn(true);
        AtomicInteger heightSamples = new AtomicInteger();
        when(engine.getHostSurfaceBiome(anyInt(), anyInt())).thenAnswer(invocation -> {
            heightSamples.incrementAndGet();
            return invocation.<Integer>getArgument(0) >= 4096 ? isles : meadow;
        });
        @SuppressWarnings("unchecked")
        NativeBiomeRegistry<String, String> registry = mock(NativeBiomeRegistry.class);
        when(registry.lookup("minecraft:plains")).thenReturn("minecraft:plains");
        when(registry.lookupCustom("pack:mirage")).thenReturn("pack:mirage");
        BukkitBiomePolicy<String, String> policy = policy(engine, registry, "minecraft:plains");
        int[] columns = {0, 1024, 2048, 3072, 4096, 5120};

        NativeBiomeSourcePolicy.BiomeLocation<String> found = policy.findClosestBiome3d(
                new NativeBiomeSourcePolicy.ClosestQuery<>("pack:mirage"::equals, (candidates, resolver) -> {
                    for (int x : columns) {
                        String biome = resolver.resolve(x, 0).sample(100);
                        if (candidates.contains(biome)) {
                            return new NativeBiomeSourcePolicy.BiomeLocation<>(x, 100, 0, biome);
                        }
                    }
                    return null;
                }));

        assertEquals(new NativeBiomeSourcePolicy.BiomeLocation<>(4096, 100, 0, "pack:mirage"), found);
        assertEquals(1, heightSamples.get());
        verify(engine).openGenerationHistoryCoordinateScope(4096, 0);
        verify(engine, times(1)).openGenerationHistoryCoordinateScope(anyInt(), anyInt());
        verify(scope).close();
    }

    @SuppressWarnings("unchecked")
    private static BukkitBiomePolicy<String, String> policy(Engine engine, NativeBiomeRegistry<String, String> registry,
                                                            String fallback) throws Exception {
        BukkitBiomePolicy<String, String> policy = mock(BukkitBiomePolicy.class, CALLS_REAL_METHODS);
        set(policy, "engine", engine);
        set(policy, "biomeCustomRegistry", registry);
        set(policy, "biomeRegistry", registry);
        set(policy, "fallbackBiome", fallback);
        set(policy, "runtimeBiomeStates", new ConcurrentHashMap<>());
        return policy;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = BukkitBiomePolicy.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
