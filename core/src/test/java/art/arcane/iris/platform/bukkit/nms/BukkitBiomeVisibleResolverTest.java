package art.arcane.iris.platform.bukkit.nms;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomeCustom;
import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.GenerationSessionLease;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisDimensionCarvingResolver;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.world.IrisWorld;
import art.arcane.volmlib.nativelib.terrain.NativeBiomeRegistry;
import art.arcane.volmlib.nativelib.terrain.NativeBiomeSourcePolicy;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertEquals;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BukkitBiomeVisibleResolverTest {
    @Test
    public void chunkResolverMatchesThePerQuartResolution() throws Exception {
        IrisBiome surface = customBiome("surface", "meadow");
        IrisBiome cave = customBiome("cave", "grotto");
        IrisBiome custom = customBiome("custom", "mirage");
        IrisBiome feature = customBiome("feature", "bloom");
        IrisDimension dimension = mock(IrisDimension.class);
        when(dimension.getReachableBiomes(any())).thenReturn(new KList<>(surface, cave, custom, feature));
        when(dimension.getCustomBiomeKey(anyString())).thenAnswer(invocation -> "pack:" + invocation.getArgument(0));
        IrisData data = mock(IrisData.class);
        when(data.customBiomeResourceKey(any(IrisDimension.class), anyString()))
                .thenAnswer(invocation -> "pack:" + invocation.getArgument(1));
        IrisComplex complex = mock(IrisComplex.class);
        when(complex.historicalPhysicalBiomeKeyAt(anyInt(), anyInt(), anyInt())).thenReturn(Optional.empty());
        EngineMantle mantle = mock(EngineMantle.class);
        when(mantle.trueHeight(anyInt(), anyInt())).thenAnswer(invocation ->
                120 + Math.floorMod(invocation.<Integer>getArgument(0) * 7 + invocation.<Integer>getArgument(1) * 3, 160));
        IrisEngine engine = mock(IrisEngine.class);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getData()).thenReturn(data);
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getMantle()).thenReturn(mantle);
        when(engine.getWorld()).thenReturn(IrisWorld.builder().minHeight(-64).maxHeight(320).build());
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getCacheID()).thenReturn(1);
        when(engine.acquireGenerationLease(anyString())).thenReturn(GenerationSessionLease.noop());
        when(engine.answersFromNaturalTerrain(anyInt(), anyInt())).thenReturn(false);
        when(engine.getHostSurfaceBiome(anyInt(), anyInt())).thenAnswer(invocation ->
                Math.floorMod(invocation.<Integer>getArgument(0) + invocation.<Integer>getArgument(1), 8) == 0 ? custom : surface);
        when(engine.getCaveBiome(anyInt(), anyInt(), anyInt(), any(IrisDimensionCarvingResolver.State.class)))
                .thenAnswer(invocation -> invocation.<Integer>getArgument(1) % 3 == 0 ? custom : cave);
        when(engine.getSubterrainBiome(anyInt(), anyInt(), anyInt())).thenAnswer(invocation ->
                invocation.<Integer>getArgument(0) == 4 && invocation.<Integer>getArgument(1) == 40 ? feature : null);
        when(engine.subterrainAbsentInChunk(1, 0)).thenReturn(true);
        @SuppressWarnings("unchecked")
        NativeBiomeRegistry<String, String> registry = mock(NativeBiomeRegistry.class);
        when(registry.lookupCustom(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        BukkitBiomePolicy<String, String> policy = policy(engine, registry, "fallback");

        for (int chunkX = 0; chunkX <= 1; chunkX++) {
            NativeBiomeSourcePolicy.VisibleResolver<String> resolver = policy.visibleResolver();
            for (int quartY = -16; quartY < 80; quartY++) {
                for (int localX = 0; localX < 4; localX++) {
                    for (int localZ = 0; localZ < 4; localZ++) {
                        int quartX = (chunkX << 2) + localX;
                        String expected = policy.getVisibleNoiseBiomeWithActiveGenerationLease(
                                quartX, quartY, localZ, new IrisDimensionCarvingResolver.State());
                        assertEquals("quart " + quartX + "," + quartY + "," + localZ, expected,
                                resolver.biome(quartX, quartY, localZ));
                    }
                }
            }
        }
    }

    private static IrisBiome customBiome(String key, String customId) {
        IrisBiome biome = new IrisBiome().setCustomDerivitives(new KList<>(new IrisBiomeCustom().setId(customId)));
        biome.setLoadKey(key);
        return biome;
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
        set(policy, "noiseBiomeCache", new ConcurrentHashMap<>());
        return policy;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = BukkitBiomePolicy.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
