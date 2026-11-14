package art.arcane.iris.generation.runtime;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomeEntry;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.pack.validation.PackCompatReport;
import art.arcane.iris.testsupport.PackCompatFixtures;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;
import org.junit.Test;

import static art.arcane.iris.testsupport.PackCompatFixtures.biome;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyDouble;

public class ChildSelectionPlanWeightedTest {
    private final IrisBiome parent = biome("parent");
    private final IrisBiome first = biome("first");
    private final IrisBiome second = biome("second");
    private final IrisData data = dataWithBiomes(parent, first, second);

    @Test
    public void weightedChildrenDoNotIncludeTheParentUnlessListed() {
        parent.setChildren(new KList<>(new IrisBiomeEntry("first", 1, true), IrisBiomeEntry.plain("second")));
        IrisComplex.ChildSelectionPlan plan = IrisComplex.ChildSelectionPlan.of(parent, () -> data);

        for (int step = -20; step <= 1020; step++) {
            double noise = step / 1000D;
            IrisBiome picked = plan.select(noiseOf(noise), 5D, 6D);
            assertSame("noise " + noise, noise < 0.5D ? first : second, picked);
        }
    }

    @Test
    public void weightedChildrenGoToTheLaterEntryOnAnEdge() {
        parent.setChildren(new KList<>(new IrisBiomeEntry("first", 1, true), IrisBiomeEntry.plain("second")));
        IrisComplex.ChildSelectionPlan plan = IrisComplex.ChildSelectionPlan.of(parent, () -> data);

        assertSame(second, plan.select(noiseOf(0.5D), 1D, 2D));
        assertSame(first, plan.select(noiseOf(Math.nextDown(0.5D)), 1D, 2D));
    }

    @Test
    public void weightedChildrenReadTheChildNoiseWithoutTheLegacyFit() {
        parent.setChildren(new KList<>(new IrisBiomeEntry("first", 1, true)));
        CNG childNoise = noiseOf(0.3D);

        IrisComplex.ChildSelectionPlan.of(parent, () -> data).select(childNoise, 4D, -4D);

        verify(childNoise).noiseFast2D(4D, -4D);
        verify(childNoise, never()).fit2D(anyInt(), anyInt(), anyDouble(), anyDouble());
    }

    @Test
    public void aListedParentTakesPartInTheWeightedPick() {
        parent.setChildren(new KList<>(new IrisBiomeEntry("parent", 3, true), IrisBiomeEntry.plain("first")));
        IrisComplex.ChildSelectionPlan plan = IrisComplex.ChildSelectionPlan.of(parent, () -> data);

        assertSame(parent, plan.select(noiseOf(0.74D), 0D, 0D));
        assertSame(first, plan.select(noiseOf(0.76D), 0D, 0D));
    }

    @Test
    public void weightsSplitTheChildNoiseInListOrder() {
        parent.setChildren(new KList<>(new IrisBiomeEntry("first", 3, true), new IrisBiomeEntry("second", 1, true),
                new IrisBiomeEntry("first", 4, true)));
        IrisComplex.ChildSelectionPlan plan = IrisComplex.ChildSelectionPlan.of(parent, () -> data);

        assertSame(first, plan.select(noiseOf(0.37D), 0D, 0D));
        assertSame(second, plan.select(noiseOf(0.4D), 0D, 0D));
        assertSame(first, plan.select(noiseOf(0.6D), 0D, 0D));
    }

    @Test
    public void childrenWrittenOnlyAsStringsKeepTheRarityPlanWithTheParentAppended() {
        first.setRarity(3);
        second.setRarity(7);
        parent.setRarity(2);
        parent.setChildren(IrisBiomeEntry.plainList("first", "second"));
        KList<IrisBiome> legacyOptions = new KList<>(first, second, parent);
        IrisComplex.ChildSelectionPlan legacy = IrisComplex.ChildSelectionPlan.create(legacyOptions);
        IrisComplex.ChildSelectionPlan plan = IrisComplex.ChildSelectionPlan.of(parent, () -> data);

        for (long seed = 1L; seed <= 5L; seed++) {
            CNG generator = new CNG(new RNG(seed), 4);
            for (int x = -300; x <= 300; x += 29) {
                for (int z = -300; z <= 300; z += 31) {
                    assertSame("seed=" + seed + " x=" + x + " z=" + z,
                            legacy.select(generator, x, z), plan.select(generator, x, z));
                    assertSame(generator.fitRarity(legacyOptions, x, z), plan.select(generator, x, z));
                }
            }
        }
    }

    @Test
    public void weightedChildrenThatDoNotLoadLeaveTheParentInPlace() {
        parent.setChildren(new KList<>(new IrisBiomeEntry("missing", 2, true)));
        IrisComplex.ChildSelectionPlan plan = IrisComplex.ChildSelectionPlan.of(parent, () -> data);

        assertSame(parent, plan.select(noiseOf(0.9D), 0D, 0D));
    }

    private static CNG noiseOf(double value) {
        CNG generator = mock(CNG.class);
        doReturn(value).when(generator).noiseFast2D(anyDouble(), anyDouble());
        return generator;
    }

    @SuppressWarnings("unchecked")
    private static IrisData dataWithBiomes(IrisBiome... biomes) {
        IrisData mocked = PackCompatFixtures.data(new PackCompatReport());
        ResourceLoader<IrisBiome> loader = mock(ResourceLoader.class);
        when(mocked.getBiomeLoader()).thenReturn(loader);
        when(loader.load(anyString())).thenReturn(null);
        for (IrisBiome biome : biomes) {
            when(loader.load(biome.getLoadKey())).thenReturn(biome);
        }
        return mocked;
    }
}
