package art.arcane.iris.generation.hydrology.runtime;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.pack.validation.CompatStatus;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.generation.hydrology.HydrologyTerrainSample;
import art.arcane.iris.generation.hydrology.policy.RiverPolicyResolver;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.generation.hydrology.IrisRiverPolicy;
import art.arcane.iris.generation.hydrology.IrisRiverProfile;
import art.arcane.volmlib.util.collection.KList;
import com.github.benmanes.caffeine.cache.Cache;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
public class IrisHydrologyPolicyCacheTest {
    private static IrisSettings previousSettings;

    @BeforeClass
    public static void useDefaultSettings() {
        previousSettings = IrisSettings.settings;
        IrisSettings.settings = new IrisSettings();
    }

    @AfterClass
    public static void restoreSettings() {
        IrisSettings.settings = previousSettings;
    }

    @Test
    public void repeatedOwnersReuseFilteredPolicyAndProfilesAcrossCoordinates() throws Exception {
        Fixture fixture = new Fixture();
        fixture.dimension.setRiverPolicy(new IrisRiverPolicy().setSurfaceBiomes(new KList<>("river"))
                .setProfiles(new KList<>("water", "unknown", "water", "lava")));
        fixture.dimension.getHydrology().getRivers().setProfiles(new KList<>(
                new IrisRiverProfile().setId("water"), new IrisRiverProfile().setId("lava")));
        try (IrisHydrologyRuntime runtime = fixture.runtime()) {
            for (int index = 0; index < 128; index++) {
                HydrologyTerrainSample sample = sample(runtime, index * 17, -index * 13);
                assertEquals("river", sample.surfaceBiomeKey());
                assertEquals(List.of("lava", "water"), sample.preferredProfileKeys());
            }
            verify(fixture.loader, times(1)).load("river");
        }
    }

    @Test
    public void ownerIdentityAndExplicitEmptyOverridesRemainDistinct() throws Exception {
        Fixture fixture = new Fixture();
        fixture.dimension.setRiverPolicy(new IrisRiverPolicy().setSurfaceBiomes(new KList<>("river"))
                .setWidthMultiplier(2D));
        IrisRegion first = new IdentityRegion();
        first.setLoadKey("same-region");
        first.setRiverPolicy(new IrisRiverPolicy().setWidthMultiplier(3D));
        IrisBiome empty = new IdentityBiome();
        empty.setLoadKey("same-biome");
        empty.setRiverPolicy(new IrisRiverPolicy().setSurfaceBiomes(new KList<>()));
        fixture.region.set(first);
        fixture.biome.set(empty);
        try (IrisHydrologyRuntime runtime = fixture.runtime()) {
            assertEquals("same-biome", sample(runtime, 0, 0).surfaceBiomeKey());
            assertEquals(3D, sample(runtime, 1, 0).widthMultiplier(), 0D);
            IrisRegion second = new IdentityRegion();
            second.setLoadKey("same-region");
            second.setRiverPolicy(new IrisRiverPolicy().setWidthMultiplier(4D));
            fixture.region.set(second);
            IrisBiome inherited = new IdentityBiome();
            inherited.setLoadKey("same-biome");
            fixture.biome.set(inherited);
            assertEquals("river", sample(runtime, 2, 0).surfaceBiomeKey());
            assertEquals(4D, sample(runtime, 3, 0).widthMultiplier(), 0D);
        }
    }

    @Test
    public void selectedLoaderReplacementAndRuntimeReloadCannotReuseOldFiltering() throws Exception {
        Fixture fixture = new Fixture();
        fixture.dimension.setRiverPolicy(new IrisRiverPolicy().setSurfaceBiomes(new KList<>("river")));
        IrisData regionData = mock(IrisData.class);
        IrisData biomeData = mock(IrisData.class);
        ResourceLoader<IrisBiome> excludedLoader = loader(true);
        ResourceLoader<IrisBiome> replacementLoader = loader(false);
        when(regionData.getBiomeLoader()).thenReturn(excludedLoader);
        when(biomeData.getBiomeLoader()).thenReturn(excludedLoader);
        fixture.region.get().setLoader(regionData);
        fixture.biome.get().setLoader(biomeData);
        try (IrisHydrologyRuntime runtime = fixture.runtime()) {
            assertEquals("parent", sample(runtime, 0, 0).surfaceBiomeKey());
            when(biomeData.getBiomeLoader()).thenReturn(replacementLoader);
            assertEquals("river", sample(runtime, 1, 0).surfaceBiomeKey());
            fixture.biome.get().setLoader(null);
            assertEquals("parent", sample(runtime, 2, 0).surfaceBiomeKey());
            verify(excludedLoader, times(2)).load("river");
            fixture.region.get().setLoader(null);
            assertEquals("river", sample(runtime, 3, 0).surfaceBiomeKey());
        }
        fixture.dimension.getRiverPolicy().setWidthMultiplier(7D);
        try (IrisHydrologyRuntime replacement = fixture.runtime()) {
            assertEquals(7D, sample(replacement, 0, 0).widthMultiplier(), 0D);
        }
        fixture.dimension.getRiverPolicy().setWidthMultiplier(8D);
        assertEquals(8D, RiverPolicyResolver.resolve(fixture.dimension, fixture.region.get(), fixture.biome.get())
                .widthMultiplier(), 0D);
    }

    @Test
    public void unresolvedReferencesAreRecheckedInsteadOfFreezingAFirstLoadFailure() throws Exception {
        Fixture fixture = new Fixture();
        fixture.dimension.setRiverPolicy(new IrisRiverPolicy().setSurfaceBiomes(new KList<>("river")));
        when(fixture.loader.load("river")).thenReturn(null);
        when(fixture.loader.isLoaded("river")).thenReturn(false);
        try (IrisHydrologyRuntime runtime = fixture.runtime()) {
            assertEquals("river", sample(runtime, 0, 0).surfaceBiomeKey());
            IrisBiome excluded = new IrisBiome();
            excluded.setCompat(CompatStatus.excludedBy(List.of()));
            when(fixture.loader.load("river")).thenReturn(excluded);
            when(fixture.loader.isLoaded("river")).thenReturn(true);
            assertEquals("parent", sample(runtime, 1, 0).surfaceBiomeKey());
            assertEquals("parent", sample(runtime, 2, 0).surfaceBiomeKey());
            verify(fixture.loader, times(2)).load("river");
        }
    }

    @Test
    public void aConcurrentLoadAfterTheResolutionMissCannotMakeThatResultCacheable() throws Exception {
        Fixture fixture = new Fixture();
        fixture.dimension.setRiverPolicy(new IrisRiverPolicy().setSurfaceBiomes(new KList<>("river")));
        IrisBiome excluded = new IrisBiome();
        excluded.setCompat(CompatStatus.excludedBy(List.of()));
        when(fixture.loader.load("river")).thenReturn(null, excluded);
        when(fixture.loader.isLoaded("river")).thenReturn(true);
        try (IrisHydrologyRuntime runtime = fixture.runtime()) {
            assertEquals("river", sample(runtime, 0, 0).surfaceBiomeKey());
            assertEquals("parent", sample(runtime, 1, 0).surfaceBiomeKey());
            assertEquals("parent", sample(runtime, 2, 0).surfaceBiomeKey());
            verify(fixture.loader, times(2)).load("river");
        }
    }

    @Test
    public void policyEntriesRemainBoundedAndCloseClearsThem() throws Exception {
        Fixture fixture = new Fixture();
        IrisHydrologyRuntime runtime = fixture.runtime();
        Cache<?, ?> policies = policies(runtime);
        try {
            for (int index = 0; index < 2048; index++) {
                IrisBiome biome = new IdentityBiome();
                biome.setLoadKey("parent-" + index);
                fixture.biome.set(biome);
                assertEquals("parent-" + index, sample(runtime, index, 0).parentBiomeKey());
            }
            policies.cleanUp();
            assertTrue(policies.estimatedSize() <= 1024);
            assertTrue(policies.estimatedSize() > 0);
        } finally {
            runtime.close();
        }
        assertEquals(0L, policies.estimatedSize());
    }

    private static HydrologyTerrainSample sample(IrisHydrologyRuntime runtime, int x, int z) throws Exception {
        Method method = IrisHydrologyRuntime.class.getDeclaredMethod("createTerrainBasis", int.class, int.class, double.class);
        method.setAccessible(true);
        try {
            return ((IrisHydrologyRoutingTerrainSampler.TerrainBasis) method.invoke(runtime, x, z, 180D)).terrain();
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Error error) {
                throw error;
            }
            if (failure.getCause() instanceof Exception exception) {
                throw exception;
            }
            throw failure;
        }
    }

    private static Cache<?, ?> policies(IrisHydrologyRuntime runtime) throws ReflectiveOperationException {
        Field field = IrisHydrologyRuntime.class.getDeclaredField("resolvedPolicies");
        field.setAccessible(true);
        return (Cache<?, ?>) field.get(runtime);
    }

    private static ResourceLoader<IrisBiome> loader(boolean excluded) {
        ResourceLoader<IrisBiome> loader = mock(ResourceLoader.class);
        IrisBiome river = new IrisBiome();
        river.setLoadKey("river");
        river.setCompat(excluded ? CompatStatus.excludedBy(List.of()) : CompatStatus.OK);
        when(loader.load("river")).thenReturn(river);
        when(loader.isLoaded("river")).thenReturn(true);
        when(loader.getPossibleKeys()).thenReturn(new String[0]);
        when(loader.loadAll(any(String[].class))).thenReturn(new KList<>());
        return loader;
    }

    private static final class Fixture {
        private final IrisDimension dimension = new IrisDimension().setRegions(new KList<>());
        private final IrisData data = mock(IrisData.class);
        private final ResourceLoader<IrisBiome> loader = loader(false);
        private final AtomicReference<IrisRegion> region = new AtomicReference<>(new IrisRegion());
        private final AtomicReference<IrisBiome> biome = new AtomicReference<>(new IrisBiome());

        private Fixture() {
            dimension.setLoadKey("dimension");
            dimension.setLoader(data);
            region.get().setLoadKey("region");
            biome.get().setLoadKey("parent");
            when(data.getBiomeLoader()).thenReturn(loader);
        }

        private IrisHydrologyRuntime runtime() {
            return new IrisHydrologyRuntime(new IrisHydrologyRuntimeContext(17L, 768, dimension, data,
                    (x, z, height) -> new IrisHydrologyNaturalSample(height, false, biome.get(), region.get()),
                    (x, z) -> 180D, (x, z) -> "constant", (x, z) -> false, footprint -> null, () -> false));
        }
    }

    private static final class IdentityRegion extends IrisRegion {
        @Override
        public boolean equals(Object compared) {
            throw new AssertionError("Policy cache must use region identity");
        }

        @Override
        public int hashCode() {
            throw new AssertionError("Policy cache must use region identity");
        }
    }

    private static final class IdentityBiome extends IrisBiome {
        @Override
        public boolean equals(Object compared) {
            throw new AssertionError("Policy cache must use biome identity");
        }

        @Override
        public int hashCode() {
            throw new AssertionError("Policy cache must use biome identity");
        }
    }
}
