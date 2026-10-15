package art.arcane.iris.modded;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.DimensionStackLayout;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.subterrain.IrisSubterrainFeature;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.iris.generation.runtime.GenerationSessionException;
import art.arcane.iris.generation.runtime.GenerationSessionLease;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.world.history.GenerationHistoryRuntimeRouter;
import art.arcane.volmlib.nativelib.terrain.NativeBiomeSourceAccess;
import art.arcane.volmlib.nativelib.terrain.NativeBiomeSourcePolicy;
import art.arcane.volmlib.nativelib.terrain.NativeModdedBiomePolicy;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.util.stream.interpolation.Interpolated;
import org.junit.Test;

import java.util.ArrayList;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class ModdedBiomePolicyTest {
    @Test
    public void authoredBiomeWinsAboveSurfaceGateAndBeforeStackOwnership() throws Exception {
        Engine engine = mock(Engine.class);
        IrisBiome feature = mock(IrisBiome.class);
        IrisDimension host = new IrisDimension().setSubterrainFeatures(new KList<>(
                new IrisSubterrainFeature().setId("room")));
        IrisData data = mock(IrisData.class);
        when(data.getDataFolder()).thenReturn(new File("synthetic-pack"));
        when(engine.getData()).thenReturn(data);
        when(engine.getDimension()).thenReturn(host);
        when(engine.getComplex()).thenReturn(mock(IrisComplex.class));
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getSubterrainBiome(12, 224, -20)).thenReturn(feature);
        assertFalse(ModdedBiomePolicy.isGuaranteedSurfaceBiome(engine, 40));
        ModdedBiomePolicy<BiomeToken, Object> policy = boundTo(engine);
        DimensionStackLayout stack = mock(DimensionStackLayout.class);
        Method method = ModdedBiomePolicy.class.getDeclaredMethod("resolveBiomeResolution", Engine.class,
                int.class, int.class, int.class, boolean.class, DimensionStackLayout.class);
        method.setAccessible(true);
        Object resolution = method.invoke(policy, engine, 3, 40, -5, true, stack);
        Field biome = resolution.getClass().getDeclaredField("irisBiome");
        biome.setAccessible(true);
        assertSame(feature, biome.get(resolution));
        Field dimension = resolution.getClass().getDeclaredField("dimension");
        dimension.setAccessible(true);
        assertSame(host, dimension.get(resolution));
        verifyNoInteractions(stack);
    }

    @Test
    public void registryOrderAndHolderIdentitySurviveThePolicyBoundaryAndRefreshOnRepoint() {
        BiomeToken ocean = new BiomeToken("minecraft:ocean");
        BiomeToken forest = new BiomeToken("minecraft:forest");
        List<BiomeToken> entries = new ArrayList<>(List.of(forest, ocean));
        AtomicReference<Set<String>> configured = new AtomicReference<>(Set.of(ocean.key(), forest.key()));
        ModdedBiomePolicy<BiomeToken, Object> policy = policy(entries, configured);

        List<BiomeToken> first = policy.orderedPossibleBiomes();
        assertSame(forest, first.getFirst());
        assertSame(ocean, first.getLast());
        assertEquals(first, new ArrayList<>(policy.possibleBiomes()));

        BiomeToken desert = new BiomeToken("minecraft:desert");
        entries.clear();
        entries.add(desert);
        configured.set(Set.of(desert.key()));
        assertSame(first, policy.orderedPossibleBiomes());

        policy.clearCaches();

        assertEquals(1L, policy.packGeneration());
        assertEquals(1, policy.orderedPossibleBiomes().size());
        assertSame(desert, policy.orderedPossibleBiomes().getFirst());
    }

    @Test
    public void missingRequiredStructureBiomesFailBeforePublishingAPartialSet() {
        AtomicReference<Set<String>> configured = new AtomicReference<>(Set.of("example:missing"));
        ModdedBiomePolicy<BiomeToken, Object> policy = policy(
                new ArrayList<>(List.of(new BiomeToken("minecraft:plains"))), configured);

        IllegalStateException failure = assertThrows(IllegalStateException.class, policy::possibleBiomes);

        assertTrue(failure.getMessage().contains("example:missing"));
    }

    @Test
    public void structureRingBiomesResolveFromTheNaturalSurfaceWithoutPlanningHydrology() throws Exception {
        BiomeToken forest = new BiomeToken("minecraft:forest");
        IrisBiome natural = mock(IrisBiome.class);
        when(natural.getStructureDerivativeKey()).thenReturn(forest.key());
        IrisComplex complex = mock(IrisComplex.class);
        when(complex.getNaturalTrueBiomeStream()).thenReturn(
                ProceduralStream.of((x, z) -> natural, Interpolated.of(value -> 0D, value -> null)));
        when(complex.getTrueBiomeStream()).thenThrow(
                new AssertionError("Stronghold ring lookups must not plan hydrology"));
        Engine engine = mock(Engine.class);
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.acquireGenerationLease(anyString())).thenReturn(GenerationSessionLease.noop());
        ModdedBiomePolicy<BiomeToken, Object> policy = new ModdedBiomePolicy<>(
                new Access(new ArrayList<>(List.of(forest))));
        policy.bind(new ModdedBiomePolicy.RuntimeCallbacks(() -> engine, () -> engine, bound -> true,
                () -> Set.of(forest.key()), bound -> Set.of(), () -> 0L));

        assertSame(forest, policy.requiredStructureBiome(6000, 0, -6000, null));
        assertSame(forest, policy.requiredStructureBiome(6000, -30, -6000, null));
    }

    /**
     * Stacked initial mob spawning asks for the visible surface biome. A null answer made it spawn from the chunk's
     * top biome instead, so a sealed or unready engine has to fail the chunk.
     */
    @Test
    public void surfaceSpawnBiomeRefusesAnEngineInTransitionInsteadOfAnsweringNothing() throws Exception {
        Engine sealed = mock(Engine.class);
        when(sealed.isClosing()).thenReturn(true);
        when(sealed.acquireGenerationLease(anyString())).thenThrow(new GenerationSessionException("sealed", true));
        Engine unready = mock(Engine.class);
        when(unready.acquireGenerationLease(anyString())).thenReturn(GenerationSessionLease.noop());

        assertThrows(IllegalStateException.class, () -> boundTo(sealed).getVisibleSurfaceBiome(8, 8));
        assertThrows(IllegalStateException.class, () -> boundTo(unready).getVisibleSurfaceBiome(8, 8));
    }

    /**
     * Vanilla /locate biome samples a 6400 block radius every 32 blocks. Each Iris surface sample needs the column's
     * terrain height, so the search may only resolve columns whose region and biome streams can hold the target, and
     * it must answer with the visible biome a chunk is filled with.
     */
    @Test
    public void locateResolvesVisibleBiomesOnlyInColumnsThatCanHoldTheTarget() throws Exception {
        BiomeToken mirage = new BiomeToken("terralith:mirage_isles");
        BiomeToken plains = new BiomeToken("minecraft:plains");
        IrisBiome isles = new IrisBiome().setDerivative("terralith:mirage_isles");
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
        @SuppressWarnings("unchecked")
        ResourceLoader<IrisBiome> biomes = (ResourceLoader<IrisBiome>) mock(ResourceLoader.class);
        when(biomes.load("meadow")).thenReturn(meadow);
        when(biomes.load("isles")).thenReturn(isles);
        IrisData data = mock(IrisData.class);
        when(data.getDataFolder()).thenReturn(new File("synthetic-pack"));
        when(data.getBiomeLoader()).thenReturn(biomes);
        when(data.physicalBiomeResourceKey(any(), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
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
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        GenerationHistoryRuntimeRouter.CoordinateScope scope = mock(GenerationHistoryRuntimeRouter.CoordinateScope.class);
        when(router.openCoordinateScope(anyInt(), anyInt())).thenReturn(scope);
        IrisEngine engine = mock(IrisEngine.class);
        when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(router));
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getData()).thenReturn(data);
        when(engine.getComplex()).thenReturn(complex);
        when(engine.getMinHeight()).thenReturn(-64);
        when(engine.getCacheID()).thenReturn(1);
        when(engine.acquireGenerationLease(anyString())).thenReturn(GenerationSessionLease.noop());
        when(engine.answersFromNaturalTerrain(anyInt(), anyInt())).thenReturn(true);
        AtomicInteger heightSamples = new AtomicInteger();
        when(engine.getHostSurfaceBiome(anyInt(), anyInt())).thenAnswer(invocation -> {
            heightSamples.incrementAndGet();
            return invocation.<Integer>getArgument(0) >= 4096 ? isles : meadow;
        });
        ModdedBiomePolicy<BiomeToken, Object> policy = new ModdedBiomePolicy<>(
                new Access(new ArrayList<>(List.of(plains, mirage))));
        policy.bind(new ModdedBiomePolicy.RuntimeCallbacks(() -> engine, () -> engine, bound -> false,
                () -> Set.of(plains.key(), mirage.key()), bound -> Set.of(), () -> 0L));
        int[] columns = {0, 1024, 2048, 3072, 4096, 5120};

        NativeBiomeSourcePolicy.BiomeLocation<BiomeToken> found = policy.findClosestBiome3d(
                new NativeModdedBiomePolicy.ClosestQuery<>(mirage::equals, null,
                        () -> {
                            throw new AssertionError("A bound engine must not use the unbound search");
                        },
                        (candidates, resolver) -> {
                            for (int x : columns) {
                                NativeBiomeSourcePolicy.Column<BiomeToken> column = resolver.resolve(x, 0);
                                BiomeToken biome = column.sample(100);
                                if (candidates.contains(biome)) {
                                    return new NativeBiomeSourcePolicy.BiomeLocation<>(x, 100, 0, biome);
                                }
                            }
                            return null;
                        }));

        assertEquals(new NativeBiomeSourcePolicy.BiomeLocation<>(4096, 100, 0, mirage), found);
        assertEquals(1, heightSamples.get());
        verify(router).openCoordinateScope(4096, 0);
        verify(router, times(1)).openCoordinateScope(anyInt(), anyInt());
        verify(scope).close();
    }

    private static ModdedBiomePolicy<BiomeToken, Object> boundTo(Engine engine) {
        ModdedBiomePolicy<BiomeToken, Object> policy = new ModdedBiomePolicy<>(new Access(new ArrayList<>()));
        policy.bind(new ModdedBiomePolicy.RuntimeCallbacks(() -> engine, () -> engine, bound -> true,
                Set::of, bound -> Set.of(), () -> 0L));
        return policy;
    }

    private static ModdedBiomePolicy<BiomeToken, Object> policy(List<BiomeToken> entries,
                                                               AtomicReference<Set<String>> configured) {
        ModdedBiomePolicy<BiomeToken, Object> policy = new ModdedBiomePolicy<>(new Access(entries));
        policy.bind(new ModdedBiomePolicy.RuntimeCallbacks(() -> null, () -> null, engine -> false,
                configured::get, engine -> Set.of(), () -> 0L));
        return policy;
    }

    private record BiomeToken(String key) {
    }

    private record Access(List<BiomeToken> entries) implements NativeBiomeSourceAccess<BiomeToken, Object>,
            NativeBiomeSourceAccess.RegistryView<BiomeToken> {
        @Override
        public RegistryView<BiomeToken> registry() {
            return this;
        }

        @Override
        public Set<BiomeToken> serializedBiomes() {
            throw new AssertionError("A loaded registry must not use serialized biomes");
        }

        @Override
        public BiomeToken serializedNoise(int x, int y, int z, Object sampler) {
            throw new AssertionError("Listing biome holders must not sample terrain");
        }

        @Override
        public String holderKey(BiomeToken holder) {
            return holder.key();
        }

        @Override
        public void forEach(Consumer<? super BiomeToken> consumer) {
            entries.forEach(consumer);
        }

        @Override
        public BiomeToken lookup(String key) {
            for (BiomeToken biome : entries) {
                if (biome.key().equals(key)) {
                    return biome;
                }
            }
            return null;
        }
    }
}
