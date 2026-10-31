package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.InferredType;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.volmlib.util.interpolation.NoiseBounds;
import org.junit.Test;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class RegionBoundsTest {
    private static final NoiseBounds FLAT = new NoiseBounds(0D, 0D);
    private static final NoiseBounds HILLS = new NoiseBounds(4D, 30D);

    @Test
    public void groupsEveryReachableBiomeAgreesOnAreConstantForTheRegion() {
        IrisBiome plains = new IrisBiome();
        IrisBiome forest = new IrisBiome();
        IrisBiome ocean = new IrisBiome();
        IrisRegion region = new IrisRegion();
        Map<IrisBiome, NoiseBounds[]> bounds = new IdentityHashMap<>();
        bounds.put(plains, new NoiseBounds[]{FLAT, HILLS});
        bounds.put(forest, new NoiseBounds[]{new NoiseBounds(0D, 0D), new NoiseBounds(2D, 30D)});
        bounds.put(ocean, new NoiseBounds[]{FLAT, HILLS});

        RegionBounds compiled = RegionBounds.compile(List.of(region), 2,
                new Fixed(Map.of(InferredType.LAND, List.of(plains, forest), InferredType.SEA, List.of(ocean)), Map.of()),
                (group, biome) -> bounds.get(biome)[group]);
        RegionBounds.Constants constants = compiled.of(region);

        assertBits(FLAT, constants.any(0));
        assertBits(FLAT, constants.typed(InferredType.LAND, 0));
        assertBits(FLAT, constants.typed(InferredType.SEA, 0));
        assertNull(constants.any(1));
        assertNull(constants.typed(InferredType.LAND, 1));
        assertSame(HILLS, constants.typed(InferredType.SEA, 1));
        assertSame(HILLS, compiled.pick(plains, 1));
        assertBits(new NoiseBounds(2D, 30D), compiled.pick(forest, 1));
    }

    @Test
    public void picksAreConstantOnlyWhenEveryDescendantAgrees() {
        IrisBiome steppe = new IrisBiome();
        IrisBiome meadow = new IrisBiome();
        IrisBiome mesa = new IrisBiome();
        IrisBiome canyon = new IrisBiome();
        IrisRegion region = new IrisRegion();
        Map<IrisBiome, NoiseBounds> bounds = new IdentityHashMap<>();
        bounds.put(steppe, FLAT);
        bounds.put(meadow, new NoiseBounds(0D, 0D));
        bounds.put(mesa, FLAT);
        bounds.put(canyon, HILLS);

        RegionBounds compiled = RegionBounds.compile(List.of(region), 1,
                new Fixed(Map.of(InferredType.LAND, List.of(steppe, mesa), InferredType.SEA, List.of()),
                        descendants(steppe, List.of(steppe, meadow), mesa, List.of(mesa, canyon))),
                (group, biome) -> bounds.get(biome));

        assertNull(compiled.of(region).typed(InferredType.LAND, 0));
        assertSame(FLAT, compiled.pick(steppe, 0));
        assertNull(compiled.pick(mesa, 0));
        assertNull(compiled.pick(canyon, 0));
    }

    @Test
    public void landAndSeaConstantsOnlyMergeWhenTheirBitsMatch() {
        IrisBiome beach = new IrisBiome();
        IrisBiome reef = new IrisBiome();
        IrisRegion region = new IrisRegion();
        Map<IrisBiome, NoiseBounds> bounds = new IdentityHashMap<>();
        bounds.put(beach, new NoiseBounds(0D, 0D));
        bounds.put(reef, new NoiseBounds(-0D, 0D));

        RegionBounds.Constants constants = RegionBounds.compile(List.of(region), 1,
                new Fixed(Map.of(InferredType.LAND, List.of(beach), InferredType.SEA, List.of(reef)), Map.of()),
                (group, biome) -> bounds.get(biome)).of(region);

        assertNull(constants.any(0));
        assertSame(bounds.get(beach), constants.typed(InferredType.LAND, 0));
        assertSame(bounds.get(reef), constants.typed(InferredType.SEA, 0));
    }

    @Test
    public void emptyBiomeListsAndUnknownRegionsHaveNoConstant() {
        IrisRegion region = new IrisRegion();
        IrisBiome only = new IrisBiome();
        RegionBounds compiled = RegionBounds.compile(List.of(region), 1,
                new Fixed(Map.of(InferredType.LAND, List.of(only), InferredType.SEA, List.of()), Map.of()),
                (group, biome) -> FLAT);
        RegionBounds.Constants constants = compiled.of(region);

        assertSame(FLAT, constants.typed(InferredType.LAND, 0));
        assertNull(constants.typed(InferredType.SEA, 0));
        assertNull(constants.any(0));
        assertNull(constants.typed(InferredType.SHORE, 0));
        assertNull(constants.typed(InferredType.CAVE, 0));
        assertNull(compiled.of(new IrisRegion()));
        assertNull(compiled.of(null));
        assertNull(compiled.pick(new IrisBiome(), 0));
        assertNull(compiled.pick(null, 0));
    }

    private static Map<IrisBiome, List<IrisBiome>> descendants(IrisBiome first, List<IrisBiome> firstDescendants,
                                                               IrisBiome second, List<IrisBiome> secondDescendants) {
        Map<IrisBiome, List<IrisBiome>> descendants = new IdentityHashMap<>();
        descendants.put(first, firstDescendants);
        descendants.put(second, secondDescendants);
        return descendants;
    }

    private static void assertBits(NoiseBounds expected, NoiseBounds actual) {
        assertEquals(Double.doubleToRawLongBits(expected.min()), Double.doubleToRawLongBits(actual.min()));
        assertEquals(Double.doubleToRawLongBits(expected.max()), Double.doubleToRawLongBits(actual.max()));
    }

    private record Fixed(Map<InferredType, List<IrisBiome>> picks, Map<IrisBiome, List<IrisBiome>> descendants)
            implements RegionBounds.Reach {
        @Override
        public List<IrisBiome> picks(IrisRegion region, InferredType type) {
            return picks.get(type);
        }

        @Override
        public Collection<IrisBiome> descendants(IrisBiome biome) {
            List<IrisBiome> known = descendants.get(biome);
            return known == null ? List.of(biome) : known;
        }
    }
}
