package art.arcane.iris.generation.locator;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyPolicy;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class HydrologyShoreTargetsTest {
    @Test
    @SuppressWarnings("unchecked")
    public void listsWithoutWidthPlaceNothing() {
        IrisDimension dimension = new IrisDimension()
                .setRegions(new KList<>("coast"))
                .setHydrology(new IrisHydrology().setEnabled(true))
                .setHydrologyPolicy(new IrisHydrologyPolicy().setShoreBiomes(new KList<>("beach")));
        IrisRegion coast = new IrisRegion()
                .setLandBiomes(new KList<>("plains"))
                .setHydrologyPolicy(new IrisHydrologyPolicy().setShoreBiomes(new KList<>("dune")));
        coast.setLoadKey("coast");
        IrisData data = mock(IrisData.class);
        ResourceLoader<IrisRegion> regionLoader = mock(ResourceLoader.class);
        ResourceLoader<IrisBiome> biomeLoader = mock(ResourceLoader.class);
        when(data.getRegionLoader()).thenReturn(regionLoader);
        when(data.getBiomeLoader()).thenReturn(biomeLoader);
        when(regionLoader.load("coast")).thenReturn(coast);
        when(biomeLoader.load("plains")).thenReturn(biome("plains"));
        when(biomeLoader.load("beach")).thenReturn(biome("beach"));
        when(biomeLoader.load("dune")).thenReturn(biome("dune"));
        Engine engine = mock(Engine.class);
        when(engine.getDimension()).thenReturn(dimension);
        when(engine.getData()).thenReturn(data);

        assertFalse(HydrologyShoreTargets.placesAny(engine, Set.of("beach")));
        assertFalse(HydrologyShoreTargets.placesAny(engine, Set.of("dune")));

        dimension.getHydrologyPolicy().setShoreBiomeWidth(3);
        assertTrue(HydrologyShoreTargets.placesAny(engine, Set.of("dune")));
        assertFalse(HydrologyShoreTargets.placesAny(engine, Set.of("beach")));

        coast.getHydrologyPolicy().setShoreBiomeWidth(0);
        assertFalse(HydrologyShoreTargets.placesAny(engine, Set.of("dune")));
    }

    private static IrisBiome biome(String key) {
        IrisBiome biome = new IrisBiome();
        biome.setLoadKey(key);
        return biome;
    }
}
