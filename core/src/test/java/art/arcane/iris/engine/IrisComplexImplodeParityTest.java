package art.arcane.iris.engine;

import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class IrisComplexImplodeParityTest {
    private static Method childSelectionCreateMethod;
    private static Method childSelectionSelectMethod;

    @BeforeClass
    public static void setup() throws Exception {
        Class<?> childSelectionClass = Class.forName("art.arcane.iris.engine.IrisComplex$ChildSelectionPlan");
        childSelectionCreateMethod = childSelectionClass.getDeclaredMethod("create", KList.class);
        childSelectionCreateMethod.setAccessible(true);
        childSelectionSelectMethod = childSelectionClass.getDeclaredMethod("select", CNG.class, double.class, double.class);
        childSelectionSelectMethod.setAccessible(true);
    }

    @Test
    public void selectionPlanMatchesLegacyFitRarityAcrossSeedAndCoordinateGrid() throws Exception {
        List<KList<IrisBiome>> scenarios = buildScenarios();
        for (int scenarioIndex = 0; scenarioIndex < scenarios.size(); scenarioIndex++) {
            KList<IrisBiome> options = scenarios.get(scenarioIndex);
            Object selectionPlan = childSelectionCreateMethod.invoke(null, options);
            for (long seed = 1L; seed <= 7L; seed++) {
                CNG generator = new CNG(new RNG(seed), 4);
                for (int x = -512; x <= 512; x += 37) {
                    for (int z = -512; z <= 512; z += 41) {
                        IrisBiome expected = generator.fitRarity(options, x, z);
                        IrisBiome actual = (IrisBiome) childSelectionSelectMethod.invoke(selectionPlan, generator, (double) x, (double) z);
                        assertSame("scenario=" + scenarioIndex + " seed=" + seed + " x=" + x + " z=" + z, expected, actual);
                    }
                }
            }
        }
    }

    @Test
    public void extremeCountsKeepTheRareBiomeReachableAcrossTheIntIndexBoundary() {
        IrisBiome rare = createBiome(Integer.MAX_VALUE);
        IrisBiome common = createBiome(1);
        CNG generator = mock(CNG.class);
        doReturn(1_073_741_824).when(generator).fit2D(0, Integer.MAX_VALUE, 12D, -32D);
        IrisComplex.ChildSelectionPlan pair = IrisComplex.ChildSelectionPlan.create(new KList<>(rare, common));

        assertSame(rare, pair.select(generator, 12D, -32D));
        verify(generator).fit2D(0, Integer.MAX_VALUE, 12D, -32D);
        verify(generator, never()).noiseFast2D(anyDouble(), anyDouble());

        CNG largeGenerator = mock(CNG.class);
        doReturn(0.5D).when(largeGenerator).noiseFast2D(12D, -32D);
        IrisComplex.ChildSelectionPlan large = IrisComplex.ChildSelectionPlan.create(
                new KList<>(rare, common, createBiome(2)));
        assertSame(rare, large.select(largeGenerator, 12D, -32D));
        verify(largeGenerator).noiseFast2D(12D, -32D);
        verify(largeGenerator, never()).fit2D(anyInt(), anyInt(), anyDouble(), anyDouble());
    }

    @Test
    public void emptySelectionPlanMatchesLegacyEmptyBehavior() throws Exception {
        KList<IrisBiome> options = new KList<>();
        CNG generator = new CNG(new RNG(9L), 2);
        Object selectionPlan = childSelectionCreateMethod.invoke(null, options);
        IrisBiome expected = generator.fitRarity(options, 12D, -32D);
        IrisBiome actual = (IrisBiome) childSelectionSelectMethod.invoke(selectionPlan, generator, 12D, -32D);
        assertNull(expected);
        assertNull(actual);
    }

    private List<KList<IrisBiome>> buildScenarios() {
        List<KList<IrisBiome>> scenarios = new ArrayList<>();

        KList<IrisBiome> scenarioA = new KList<>();
        scenarioA.add(createBiome(1));
        scenarioA.add(createBiome(3));
        scenarioA.add(createBiome(5));
        scenarioA.add(createBiome(2));
        scenarios.add(scenarioA);

        KList<IrisBiome> scenarioB = new KList<>();
        scenarioB.add(createBiome(7));
        scenarioB.add(createBiome(2));
        scenarioB.add(createBiome(2));
        scenarioB.add(createBiome(6));
        scenarioB.add(createBiome(1));
        scenarios.add(scenarioB);

        KList<IrisBiome> scenarioC = new KList<>();
        scenarioC.add(createBiome(4));
        scenarios.add(scenarioC);

        return scenarios;
    }

    private IrisBiome createBiome(int rarity) {
        IrisBiome biome = mock(IrisBiome.class);
        doReturn(rarity).when(biome).getRarity();
        return biome;
    }
}
