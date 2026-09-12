package art.arcane.iris.generation.biome;

import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisDimensionCarvingEntry;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.testsupport.BukkitTestServer;
import art.arcane.volmlib.util.collection.KList;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class IrisFloatingChildBiomesCarvingResolutionTest {
    @BeforeClass
    public static void setupBukkit() {
        BukkitTestServer.install();
    }

    @Test
    public void resolveCarvingBiome_loadsBiomeKeyWhenEntryIdMissing() {
        IrisBiome biome = mock(IrisBiome.class);
        Fixture fixture = createFixture(Map.of(), Map.of("carving/mushroom", biome));

        IrisBiome resolved = IrisFloatingChildBiomes.resolveCarvingBiome("carving/mushroom", fixture.engine, fixture.data);

        assertSame(biome, resolved);
    }

    @Test
    public void resolveCarvingBiome_prefersDimensionCarvingEntryId() {
        IrisBiome entryBiome = mock(IrisBiome.class);
        IrisBiome sameKeyBiome = mock(IrisBiome.class);
        IrisDimensionCarvingEntry entry = new IrisDimensionCarvingEntry();
        entry.setId("global-deepdark-band");
        entry.setBiome("carving/standard-deepdark");

        Map<String, IrisDimensionCarvingEntry> entries = new HashMap<>();
        entries.put("global-deepdark-band", entry);

        Map<String, IrisBiome> biomes = new HashMap<>();
        biomes.put("carving/standard-deepdark", entryBiome);
        biomes.put("global-deepdark-band", sameKeyBiome);

        Fixture fixture = createFixture(entries, biomes);

        IrisBiome resolved = IrisFloatingChildBiomes.resolveCarvingBiome("global-deepdark-band", fixture.engine, fixture.data);

        assertSame(entryBiome, resolved);
    }

    private Fixture createFixture(Map<String, IrisDimensionCarvingEntry> entries, Map<String, IrisBiome> biomes) {
        @SuppressWarnings("unchecked")
        ResourceLoader<IrisBiome> biomeLoader = mock(ResourceLoader.class);
        for (Map.Entry<String, IrisBiome> biome : biomes.entrySet()) {
            doReturn(biome.getValue()).when(biomeLoader).load(biome.getKey());
        }

        IrisData data = mock(IrisData.class);
        doReturn(biomeLoader).when(data).getBiomeLoader();

        IrisDimension dimension = mock(IrisDimension.class);
        doReturn(entries).when(dimension).getCarvingEntryIndex();
        doReturn(new KList<>(entries.values())).when(dimension).getCarving();

        Engine engine = mock(Engine.class);
        doReturn(data).when(engine).getData();
        doReturn(dimension).when(engine).getDimension();

        return new Fixture(engine, data);
    }

    private static final class Fixture {
        private final Engine engine;
        private final IrisData data;

        private Fixture(Engine engine, IrisData data) {
            this.engine = engine;
            this.data = data;
        }
    }
}
