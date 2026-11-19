package art.arcane.iris.generation.terrain;

import art.arcane.iris.generation.image.IrisImageMapRuntime;
import art.arcane.iris.generation.stream.RarityStream;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.pack.validation.PackCompatReport;
import art.arcane.iris.testsupport.PackCompatFixtures;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.stream.ProceduralStream;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static art.arcane.iris.testsupport.PackCompatFixtures.excludeBlock;
import static art.arcane.iris.testsupport.PackCompatFixtures.region;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RegionSelectionWeightedTest {
    private static final int NOISE_STEPS = 1000;

    private final Map<String, IrisRegion> regions = new HashMap<>();
    private final IrisData data = dataWithRegions();
    private final double[] noise = new double[1];
    private final ProceduralStream<Double> style = ProceduralStream.ofDouble((x, z) -> noise[0]);

    @Test
    public void weightedRegionsPickBySlotAndSkipExcludedRegions() {
        IrisRegion first = addRegion("first");
        addRegion("excluded");
        regions.put("excluded", excludeBlock(regions.get("excluded")));
        IrisRegion last = addRegion("last");
        IrisDimension dimension = new IrisDimension().setRegions(new KList<>(
                new IrisRegionEntry("first", 2, true), IrisRegionEntry.plain("excluded"), IrisRegionEntry.plain("last")));
        ProceduralStream<IrisRegion> selected = RegionSelection.select(options(dimension));

        noise[0] = 2D / 3D - 0.01D;
        assertSame(first, selected.get(0D, 0D));
        noise[0] = 2D / 3D + 0.01D;
        assertSame(last, selected.get(0D, 0D));
    }

    @Test
    public void weightedRegionsThatAllFailToLoadPickNothing() {
        IrisDimension dimension = new IrisDimension().setRegions(new KList<>(new IrisRegionEntry("missing", 2, true)));

        assertNull(RegionSelection.select(options(dimension)).get(0D, 0D));
    }

    @Test
    public void regionListsWithoutObjectsMatchTheRarityStreamOverTheSameNoise() {
        IrisRegion rare = addRegion("rare");
        rare.setRarity(6);
        IrisRegion common = addRegion("common");
        IrisRegion middle = addRegion("middle");
        middle.setRarity(3);
        IrisDimension dimension = new IrisDimension().setRegions(IrisRegionEntry.plainList("rare", "common", "middle"));
        ProceduralStream<IrisRegion> selected = RegionSelection.select(options(dimension));
        RarityStream<IrisRegion> legacy = new RarityStream<>(style, new KList<>(rare, common, middle));

        for (int step = -50; step <= NOISE_STEPS + 50; step++) {
            noise[0] = step / (double) NOISE_STEPS;
            assertSame("noise " + noise[0], legacy.get(3D, 4D), selected.get(3D, 4D));
        }
    }

    private RegionSelection.Options options(IrisDimension dimension) {
        return new RegionSelection.Options(dimension, data, style, mock(IrisImageMapRuntime.class), null);
    }

    private IrisRegion addRegion(String key) {
        IrisRegion added = region(key);
        regions.put(key, added);
        return added;
    }

    @SuppressWarnings("unchecked")
    private IrisData dataWithRegions() {
        IrisData mocked = PackCompatFixtures.data(new PackCompatReport());
        ResourceLoader<IrisRegion> loader = mock(ResourceLoader.class);
        when(mocked.getRegionLoader()).thenReturn(loader);
        when(loader.load(anyString())).thenAnswer(call -> regions.get(call.<String>getArgument(0)));
        when(loader.loadAll(any(KList.class))).thenAnswer(call -> {
            KList<IrisRegion> found = new KList<>();
            for (String key : call.<KList<String>>getArgument(0)) {
                IrisRegion value = regions.get(key);
                if (value != null) {
                    found.add(value);
                }
            }
            return found;
        });
        return mocked;
    }
}
