package art.arcane.iris.structure.placement;

import art.arcane.iris.generation.runtime.Engine;

import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.world.loot.InventorySlotType;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.world.loot.IrisLootMode;
import art.arcane.iris.world.loot.IrisLootReference;
import art.arcane.iris.world.loot.IrisLootTable;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LootResolverTest {
    @Test
    public void inclusiveRollCanReachBothConfiguredBounds() {
        RNG rng = new RNG(417L);
        Set<Integer> results = new HashSet<>();

        for (int i = 0; i < 256; i++) {
            results.add(LootResolver.inclusive(rng, 1, 3));
        }

        assertEquals(Set.of(1, 2, 3), results);
    }

    @Test
    public void inclusiveRollNormalizesReversedBounds() {
        RNG rng = new RNG(912L);

        for (int i = 0; i < 64; i++) {
            int value = LootResolver.inclusive(rng, 7, 3);
            assertTrue(value >= 3 && value <= 7);
        }
    }

    @Test
    public void oneInUsesTheWholeConfiguredRange() {
        RNG success = new FixedRollRng(0);
        RNG failure = new FixedRollRng(7);

        assertTrue(LootResolver.oneIn(success, 8));
        assertFalse(LootResolver.oneIn(failure, 8));
        assertTrue(LootResolver.oneIn(failure, 1));
    }

    @Test
    public void spatialSeedsIncludeWorldTableAndAbsolutePosition() {
        IrisLootTable first = new IrisLootTable().setName("first");
        IrisLootTable second = new IrisLootTable().setName("second");

        long baseline = LootResolver.tableSeed(41L, first, 0, 64, 0);

        assertEquals(baseline, LootResolver.tableSeed(41L, first, 0, 64, 0));
        assertNotEquals(baseline, LootResolver.tableSeed(42L, first, 0, 64, 0));
        assertNotEquals(baseline, LootResolver.tableSeed(41L, second, 0, 64, 0));
        assertNotEquals(baseline, LootResolver.tableSeed(41L, first, 16, 64, 0));
    }

    @Test
    public void spatialRarityIsStableAndDistributed() {
        IrisLootTable table = new IrisLootTable().setName("distribution");
        int successes = 0;

        for (int x = 0; x < 65_536; x++) {
            if (LootResolver.spatialOneIn(91L, table, 3, x, 40, x * 31, 8L)) {
                successes++;
            }
        }

        assertTrue(successes > 7_600);
        assertTrue(successes < 8_800);
        assertEquals(
                LootResolver.spatialOneIn(91L, table, 3, 112, 40, 992, 8L),
                LootResolver.spatialOneIn(91L, table, 3, 112, 40, 992, 8L)
        );
    }

    @Test
    public void nativeOrObjectLootSuppressesFallbackReferences() {
        ResolutionFixture fixture = new ResolutionFixture();
        List<IrisLootTable> sources = new ArrayList<>();

        LootResolver.resolveEnvironmentSources(
                sources,
                fixture.engine,
                new RNG(19L),
                10,
                70,
                20,
                true,
                table -> table
        );

        assertTrue(sources.isEmpty());
    }

    @Test
    public void emptyPlacementUsesEachFallbackScopeOnce() {
        ResolutionFixture fixture = new ResolutionFixture();
        List<IrisLootTable> sources = new ArrayList<>();

        LootResolver.resolveEnvironmentSources(
                sources,
                fixture.engine,
                new RNG(19L),
                10,
                70,
                20,
                false,
                table -> table
        );

        assertEquals(List.of(fixture.dimensionTable, fixture.regionTable, fixture.surfaceTable), sources);
    }

    @Test
    public void sourceScalingCanSelectTheLastSource() {
        List<String> sources = new ArrayList<>(List.of("first", "last"));
        RNG rng = new FixedRollRng(1);

        LootResolver.scaleSources(sources, 1.5D, rng);

        assertEquals(List.of("first", "last", "last"), sources);
    }

    @Test
    public void sourceScalingRejectsUnsafeMultipliersBeforeMutation() {
        List<String> sources = new ArrayList<>(List.of("first", "second"));

        IllegalArgumentException nonFinite = assertThrows(IllegalArgumentException.class,
                () -> LootResolver.scaleSources(sources, Double.POSITIVE_INFINITY, new RNG(17L)));
        IllegalArgumentException negative = assertThrows(IllegalArgumentException.class,
                () -> LootResolver.scaleSources(sources, -1D, new RNG(17L)));

        assertTrue(nonFinite.getMessage().contains("must be finite"));
        assertTrue(negative.getMessage().contains("non-negative"));
        assertEquals(List.of("first", "second"), sources);
    }

    @Test
    public void sourceScalingRejectsExcessiveTargetAllocationBeforeMutation() {
        List<String> sources = new ArrayList<>();
        for (int index = 0; index < 17; index++) {
            sources.add("table-" + index);
        }

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> LootResolver.scaleSources(sources, IrisLootReference.MAX_MULTIPLIER, new RNG(17L)));

        assertTrue(failure.getMessage().contains("maximum of 256 sources"));
        assertEquals(17, sources.size());
    }

    @Test
    public void sourceScalingAllowsExactResolvedSourceBoundary() {
        List<String> sources = new ArrayList<>();
        for (int index = 0; index < 16; index++) {
            sources.add("table-" + index);
        }

        LootResolver.scaleSources(sources, IrisLootReference.MAX_MULTIPLIER, new RNG(17L));

        assertEquals(256, sources.size());
    }

    @Test
    public void sourceScalingRejectsNumericOverflowBeforeMutation() {
        List<String> sources = new ArrayList<>(List.of("first", "second"));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> LootResolver.scaleSources(sources, Double.MAX_VALUE, new RNG(17L)));

        assertTrue(failure.getMessage().contains("maximum of 256 sources"));
        assertEquals(List.of("first", "second"), sources);
    }

    @Test
    public void sourceScalingRejectsOversizedInputBeforeSnapshotAllocation() {
        List<String> sources = new ArrayList<>();
        for (int index = 0; index < 257; index++) {
            sources.add("table-" + index);
        }

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> LootResolver.scaleSources(sources, 0D, new RNG(17L)));

        assertTrue(failure.getMessage().contains("starts above the maximum of 256"));
        assertEquals(257, sources.size());
    }

    @Test
    public void wholeNumberScalingPreservesTableBalanceAndIsDeterministic() {
        List<String> first = new ArrayList<>(List.of("alpha", "beta", "gamma"));
        List<String> second = new ArrayList<>(first);

        LootResolver.scaleSources(first, 4D, new RNG(817L));
        LootResolver.scaleSources(second, 4D, new RNG(817L));

        assertEquals(first, second);
        assertEquals(4, Collections.frequency(first, "alpha"));
        assertEquals(4, Collections.frequency(first, "beta"));
        assertEquals(4, Collections.frequency(first, "gamma"));
    }

    @Test
    public void combinedMultiplierAboveReferenceCapRemainsUsableUnderResolvedSourceCap() {
        List<String> sources = new ArrayList<>(List.of("table"));

        LootResolver.scaleSources(sources, IrisLootReference.MAX_MULTIPLIER * 2D, new RNG(17L));

        assertEquals(32, sources.size());
    }

    @Test
    public void lootMultiplierCapIsPublishedInSchemaMetadata() throws Exception {
        Field multiplier = IrisLootReference.class.getDeclaredField("multiplier");
        MaxNumber maximum = multiplier.getAnnotation(MaxNumber.class);
        Description description = multiplier.getAnnotation(Description.class);

        assertEquals(IrisLootReference.MAX_MULTIPLIER, maximum.value(), 0D);
        assertTrue(description.value().contains("0 to 16"));
    }

    @Test
    public void emptyLootTableReturnsNoItems() {
        IrisLootTable table = new IrisLootTable().setName("empty");

        assertTrue(table.getLoot(false, 7L, InventorySlotType.STORAGE, null, 0, 64, 0).isEmpty());
    }

    @Test
    public void weightedSelectionRejectsNonPositivePoolsAndCanReachLastEntry() {
        List<WeightedChoice> invalid = List.of(new WeightedChoice("zero", 0), new WeightedChoice("negative", -4));
        List<WeightedChoice> valid = List.of(new WeightedChoice("first", 1), new WeightedChoice("last", 1));

        assertNull(LootResolver.pickWeighted(invalid, WeightedChoice::weight, new FixedRollRng(0)));
        assertEquals("last", LootResolver.pickWeighted(valid, WeightedChoice::weight, new FixedRollRng(1)).name());
    }

    private static final class ResolutionFixture {
        private final Engine engine = mock(Engine.class, RETURNS_DEEP_STUBS);
        private final IrisLootTable dimensionTable = new IrisLootTable().setName("dimension");
        private final IrisLootTable regionTable = new IrisLootTable().setName("region");
        private final IrisLootTable surfaceTable = new IrisLootTable().setName("surface");

        private ResolutionFixture() {
            IrisDimension dimension = mock(IrisDimension.class);
            IrisRegion region = mock(IrisRegion.class);
            IrisBiome surface = mock(IrisBiome.class);
            IrisComplex complex = engine.getComplex();
            IrisLootReference dimensionReference = reference(dimensionTable);
            IrisLootReference regionReference = reference(regionTable);
            IrisLootReference surfaceReference = reference(surfaceTable);

            when(engine.getDimension()).thenReturn(dimension);
            when(dimension.getLoot()).thenReturn(dimensionReference);
            when(region.getLoot()).thenReturn(regionReference);
            when(surface.getLoot()).thenReturn(surfaceReference);
            when(surface.getLoadKey()).thenReturn("surface");
            when(complex.getRegionStream().get(10, 20)).thenReturn(region);
            when(complex.getTrueBiomeStream().get(10, 20)).thenReturn(surface);
            when(complex.getHeightStream().get(10, 20)).thenReturn(64D);
            when(engine.getCaveBiome(10, 70, 20)).thenReturn(surface);
        }

        private IrisLootReference reference(IrisLootTable table) {
            IrisLootReference reference = mock(IrisLootReference.class);
            when(reference.getMode()).thenReturn(IrisLootMode.FALLBACK);
            when(reference.getMultiplier()).thenReturn(1D);
            when(reference.getLootTables(any())).thenReturn(new KList<>(table));
            return reference;
        }
    }

    private static final class FixedRollRng extends RNG {
        private final int roll;

        private FixedRollRng(int roll) {
            super(1L);
            this.roll = roll;
        }

        @Override
        public int nextInt(int bound) {
            return Math.min(roll, bound - 1);
        }

        @Override
        public long nextLong(long bound) {
            return Math.min(roll, bound - 1L);
        }
    }

    private record WeightedChoice(String name, int weight) {
    }
}
