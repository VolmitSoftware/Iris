package art.arcane.iris.engine.terrain;

import art.arcane.iris.engine.hydrology.HydrologyColumnLayer;
import art.arcane.iris.engine.hydrology.HydrologyColumnSample;
import art.arcane.iris.engine.hydrology.HydrologyColumnSnapshot;
import art.arcane.iris.engine.hydrology.HydrologyFeatureRef;
import art.arcane.iris.engine.hydrology.HydrologyFeatureType;
import art.arcane.iris.engine.object.IrisRiverBank3DConfig;
import art.arcane.iris.engine.object.IrisGeneratorStyle;
import art.arcane.iris.engine.object.NoiseStyle;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HydrologyBankTerrainRuntimeTest {
    @Test
    public void unavailableSupportColumnsDoNotPoisonLaterAcceptedBankGeometry() {
        AtomicBoolean ready = new AtomicBoolean();
        Terrain3DColumn natural = Terrain3DColumn.unshaped(80, 256);
        HydrologyBankTerrainRuntime runtime = HydrologyBankTerrainRuntime.withNoise(
                new HydrologyBankTerrainRuntime.Sources((x, z) -> natural,
                        (x, z) -> x == 0 && z == 0 || ready.get()
                                ? HydrologyColumnSnapshot.ready(bank(50, false, true))
                                : HydrologyColumnSnapshot.unavailable()),
                options(4096), (x, y, z) -> StrictMath.sin((y - 50D) * StrictMath.PI / 4D));

        assertTrue(runtime.columnIfReady(0, 0).isEmpty());
        assertSame(natural, runtime.column(0, 0));
        assertTrue(runtime.columnIfReady(0, 0).isEmpty());

        ready.set(true);
        Terrain3DColumn accepted = runtime.column(0, 0);
        HydrologyBankTerrainRuntime fresh = runtime(4096, (x, z) -> bank(50, false, true), (x, z) -> natural);
        assertEquals(fresh.column(0, 0), accepted);
        assertEquals(1, accepted.spanCount());
        assertTrue(runtime.columnIfReady(0, 0).isPresent());
        assertFalse(accepted.isSolid(59));
    }

    @Test
    public void anUnplannedBankIsNotCachedAsAPlannedNaturalColumn() {
        AtomicBoolean ready = new AtomicBoolean();
        Terrain3DColumn natural = Terrain3DColumn.unshaped(80, 256);
        HydrologyBankTerrainRuntime runtime = HydrologyBankTerrainRuntime.withNoise(
                new HydrologyBankTerrainRuntime.Sources((x, z) -> natural,
                        (x, z) -> ready.get() ? HydrologyColumnSnapshot.ready(bank(50, true, true))
                                : HydrologyColumnSnapshot.unavailable()), options(16), (x, y, z) -> 0D);

        assertSame(natural, runtime.column(0, 0));
        ready.set(true);
        assertEquals(50, runtime.column(0, 0).topY());
    }

    @Test
    public void clearingDuringAColumnSampleCannotRepublishItsOldGeometry() {
        AtomicReference<HydrologyBankTerrainRuntime> reference = new AtomicReference<>();
        AtomicBoolean clear = new AtomicBoolean(true);
        AtomicInteger bed = new AtomicInteger(50);
        HydrologyBankTerrainRuntime runtime = HydrologyBankTerrainRuntime.withNoise(
                new HydrologyBankTerrainRuntime.Sources((x, z) -> {
                    if (clear.compareAndSet(true, false)) {
                        reference.get().clear();
                        bed.set(60);
                    }
                    return Terrain3DColumn.unshaped(80, 256);
                }, (x, z) -> HydrologyColumnSnapshot.ready(bank(bed.get(), true, true))),
                options(16), (x, y, z) -> 0D);
        reference.set(runtime);

        assertTrue(runtime.columnIfReady(0, 0).isEmpty());
        assertEquals(60, runtime.column(0, 0).topY());
    }

    @Test
    public void configuredProductionNoiseChangesVolumesWhenOnlyVerticalScaleChanges() {
        HydrologyBankTerrainRuntime.Sources sources = new HydrologyBankTerrainRuntime.Sources(
                (x, z) -> Terrain3DColumn.unshaped(80, 256),
                (x, z) -> HydrologyColumnSnapshot.ready(x < 2 ? bank(50, false, true) : null));
        IrisRiverBank3DConfig shortLayers = new IrisRiverBank3DConfig().setAmplitude(16)
                .setHorizontalScale(24).setVerticalScale(4)
                .setDensityStyle(new IrisGeneratorStyle(NoiseStyle.SIMPLEX));
        IrisRiverBank3DConfig tallLayers = new IrisRiverBank3DConfig().setAmplitude(16)
                .setHorizontalScale(24).setVerticalScale(64)
                .setDensityStyle(new IrisGeneratorStyle(NoiseStyle.SIMPLEX));
        HydrologyBankTerrainRuntime shortRuntime = new HydrologyBankTerrainRuntime(sources,
                new HydrologyBankTerrainRuntime.Options(812, 256, 4096, null, shortLayers));
        HydrologyBankTerrainRuntime tallRuntime = new HydrologyBankTerrainRuntime(sources,
                new HydrologyBankTerrainRuntime.Options(812, 256, 4096, null, tallLayers));
        int changed = 0;
        int overhangs = 0;
        for (int z = -32; z <= 32; z++) {
            Terrain3DColumn shortColumn = shortRuntime.column(0, z);
            Terrain3DColumn tallColumn = tallRuntime.column(0, z);
            changed += shortColumn.equals(tallColumn) ? 0 : 1;
            overhangs += shortColumn.spanCount() > 1 ? 1 : 0;
            assertTrue(shortColumn.topY() <= 66);
            assertTrue(shortColumn.isSolid(50));
        }
        assertTrue("Changing only Y scale must change configured XYZ terrain", changed > 0);
        assertTrue("Configured XYZ noise must produce supported covered shelves", overhangs > 0);
    }

    @Test
    public void disabledAndZeroAmplitudeFieldsDoNotCompileNoise() {
        HydrologyBankTerrainRuntime.Sources sources = new HydrologyBankTerrainRuntime.Sources(
                (x, z) -> Terrain3DColumn.unshaped(80, 256),
                (x, z) -> HydrologyColumnSnapshot.ready(bank(50, false, true)));
        for (IrisRiverBank3DConfig config : List.of(
                new IrisRiverBank3DConfig().setEnabled(false), new IrisRiverBank3DConfig().setAmplitude(0))) {
            config.setDensityStyle(new FailingStyle());
            HydrologyBankTerrainRuntime runtime = new HydrologyBankTerrainRuntime(sources,
                    new HydrologyBankTerrainRuntime.Options(812, 256, 16, null, config));
            Terrain3DColumn column = runtime.column(0, 0);
            assertEquals(50, column.topY());
            assertEquals(1, column.spanCount());
        }
    }

    @Test
    public void overlappingBanksPreserveTheirHighestRequiredContainmentFloor() {
        HydrologyColumnSample low = bank(50, false, true);
        HydrologyColumnSample high = bank(55, false, true);
        HydrologyColumnSample overlapping = new HydrologyColumnSample(0, 0, 80, 32, false, "plains",
                List.of(low.layers().getFirst(), high.layers().getFirst()));
        HydrologyBankTerrainRuntime runtime = runtime(16, (x, z) -> overlapping,
                (x, z) -> Terrain3DColumn.unshaped(80, 256));

        Terrain3DColumn column = runtime.column(0, 0);

        for (int y = 0; y <= 55; y++) {
            assertTrue("Overlapping bank floor " + y, column.isSolid(y));
        }
    }

    @Test
    public void xyzDensityLeavesSupportedShelvesAboveAnIntactWaterline() {
        HydrologyBankTerrainRuntime runtime = runtime(4096,
                (x, z) -> x < 2 ? bank(50, false, true) : null,
                (x, z) -> Terrain3DColumn.unshaped(80, 256));

        Terrain3DColumn column = runtime.column(0, 0);

        assertTrue(column.shaped());
        assertTrue(column.spanCount() > 1);
        assertFalse(column.isSolid(56));
        assertTrue(column.isSolid(59));
        assertTrue(column.isSolid(60));
        assertFalse(column.isSolid(66));
        for (int y = 0; y <= 50; y++) {
            assertTrue("Protected bank block " + y, column.isSolid(y));
        }
        for (int y = 81; y < 256; y++) {
            assertFalse(column.isSolid(y));
        }
    }

    @Test
    public void detachedShelvesAreRemovedWithoutRequestingAnUnboundedNeighbourhood() {
        AtomicInteger sampled = new AtomicInteger();
        HydrologyBankTerrainRuntime runtime = runtime(4096,
                (x, z) -> {
                    sampled.incrementAndGet();
                    assertTrue(Math.abs(x) <= 8 && Math.abs(z) <= 8);
                    return bank(50, false, true);
                }, (x, z) -> Terrain3DColumn.unshaped(80, 256));

        Terrain3DColumn column = runtime.column(0, 0);

        assertEquals(1, column.spanCount());
        assertFalse(column.isSolid(58));
        assertTrue(sampled.get() <= 33);
    }

    @Test
    public void separateNaturalUpperSpansSurviveWhenTheyStillJoinTheBank() {
        Terrain3DColumn natural = Terrain3DColumnFixtures.spans(70, 0, 70, 82, 88);
        HydrologyBankTerrainRuntime runtime = runtime(4096,
                (x, z) -> x < 2 ? bank(50, false, true) : null,
                (x, z) -> x < 2 ? natural : Terrain3DColumn.unshaped(100, 256));

        Terrain3DColumn column = runtime.column(0, 0);

        assertEquals(88, column.topY());
        for (int y = 82; y <= 88; y++) {
            assertTrue(column.isSolid(y));
        }
        assertFalse(column.isSolid(81));
    }

    @Test
    public void unchangedBiomeBandsAndNaturalColumnsSkipDensityAndKeepTheirSpans() {
        Terrain3DColumn natural = Terrain3DColumnFixtures.spans(70, 0, 70, 82, 88);
        HydrologyBankTerrainRuntime runtime = HydrologyBankTerrainRuntime.withNoise(
                new HydrologyBankTerrainRuntime.Sources((x, z) -> natural,
                        (x, z) -> HydrologyColumnSnapshot.ready(x == 0 ? bank(70, false, false) : null)),
                options(16), (x, y, z) -> {
                    throw new AssertionError("Density sampled outside an eroded bank");
                });

        assertSame(natural, runtime.column(0, 0));
        assertSame(natural, runtime.column(1, 0));
    }

    @Test
    public void wetChannelsRemainOpenAndDoNotSampleBankNoise() {
        HydrologyBankTerrainRuntime runtime = HydrologyBankTerrainRuntime.withNoise(
                new HydrologyBankTerrainRuntime.Sources((x, z) -> Terrain3DColumn.unshaped(80, 256),
                        (x, z) -> HydrologyColumnSnapshot.ready(bank(50, true, true))),
                options(16), (x, y, z) -> {
                    throw new AssertionError("Density sampled inside the wet core");
                });

        Terrain3DColumn column = runtime.column(0, 0);

        assertEquals(50, column.topY());
        assertTrue(column.isSolid(50));
        for (int y = 51; y <= 80; y++) {
            assertFalse(column.isSolid(y));
        }
    }

    @Test
    public void negativeBoundariesEvictionAndConcurrentQueriesProduceTheSameVolumes() throws Exception {
        BiFunction<Integer, Integer, HydrologyColumnSample> hydrology = (x, z) -> Math.floorMod(x, 8) < 6
                ? bank(50, false, true) : null;
        HydrologyBankTerrainRuntime.NaturalSource natural = (x, z) -> Terrain3DColumn.unshaped(80, 256);
        HydrologyBankTerrainRuntime reference = runtime(4096, hydrology, natural);
        HydrologyBankTerrainRuntime evicted = runtime(2, hydrology, natural);
        List<Callable<Terrain3DColumn>> queries = new ArrayList<>();
        for (int x = -18; x <= 18; x++) {
            int sampleX = x;
            queries.add(() -> evicted.column(sampleX, -17));
        }
        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            List<Future<Terrain3DColumn>> columns = executor.invokeAll(queries);
            for (int index = 0; index < columns.size(); index++) {
                assertEquals(reference.column(index - 18, -17), columns.get(index).get());
            }
        }
        evicted.clear();
        for (int x = 18; x >= -18; x--) {
            assertEquals(reference.column(x, -17), evicted.column(x, -17));
        }
    }

    @Test
    public void nonfiniteDensityAndInvalidConfigurationFailBeforePublishingGeometry() {
        HydrologyBankTerrainRuntime.Sources sources = new HydrologyBankTerrainRuntime.Sources(
                (x, z) -> Terrain3DColumn.unshaped(80, 256),
                (x, z) -> HydrologyColumnSnapshot.ready(bank(50, false, true)));
        HydrologyBankTerrainRuntime invalidNoise = HydrologyBankTerrainRuntime.withNoise(
                sources, options(16), (x, y, z) -> Double.NaN);
        assertThrows(IllegalStateException.class, () -> invalidNoise.column(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new IrisRiverBank3DConfig().setAmplitude(-1).validate());
        assertThrows(IllegalArgumentException.class, () -> new IrisRiverBank3DConfig().setVerticalScale(Double.NaN).validate());
        assertThrows(IllegalArgumentException.class, () -> new IrisRiverBank3DConfig().setMaximumOverhang(17).validate());
    }

    private static HydrologyBankTerrainRuntime runtime(int cacheSize,
                                                      BiFunction<Integer, Integer, HydrologyColumnSample> hydrology,
                                                      HydrologyBankTerrainRuntime.NaturalSource natural) {
        return HydrologyBankTerrainRuntime.withNoise(new HydrologyBankTerrainRuntime.Sources(natural,
                        (x, z) -> HydrologyColumnSnapshot.ready(hydrology.apply(x, z))),
                options(cacheSize), (x, y, z) -> StrictMath.sin((y - 50D) * StrictMath.PI / 4D));
    }

    private static HydrologyBankTerrainRuntime.Options options(int cacheSize) {
        IrisRiverBank3DConfig config = new IrisRiverBank3DConfig().setAmplitude(16)
                .setHorizontalScale(64).setVerticalScale(64).setMaximumOverhang(8);
        return new HydrologyBankTerrainRuntime.Options(812L, 256, cacheSize, null, config);
    }

    private static HydrologyColumnSample bank(int height, boolean channel, boolean owned) {
        HydrologyFeatureRef feature = new HydrologyFeatureRef(1L, HydrologyFeatureType.SURFACE_POOL,
                2L, 3L, 0, height, 0, 1, 0, false);
        HydrologyColumnLayer layer = new HydrologyColumnLayer(feature, height, height, height,
                channel, false, !channel, channel, false, false, owned, channel, false,
                "default", "river", "mouth", "shore", "bank", "cave");
        return new HydrologyColumnSample(0, 0, 80, 32, false, "plains", List.of(layer));
    }

    private static final class FailingStyle extends IrisGeneratorStyle {
        @Override
        public CNG createNoCache(RNG rng, IrisData data) {
            throw new AssertionError("Disabled bank geometry compiled a density style");
        }
    }
}
