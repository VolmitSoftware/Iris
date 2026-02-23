package art.arcane.iris.engine;

import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.data.BlockData;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class IrisComplexImplodeParityTest {
    private static Method childSelectionCreateMethod;
    private static Method childSelectionSelectMethod;

    @BeforeClass
    public static void setup() throws Exception {
        if (Bukkit.getServer() == null) {
            Server server = mock(Server.class);
            BlockData emptyBlockData = mock(BlockData.class);
            doReturn(Logger.getLogger("IrisTest")).when(server).getLogger();
            doReturn("IrisTestServer").when(server).getName();
            doReturn("1.0").when(server).getVersion();
            doReturn("1.0").when(server).getBukkitVersion();
            doReturn(emptyBlockData).when(server).createBlockData(any(Material.class));
            doReturn(emptyBlockData).when(server).createBlockData(anyString());
            Bukkit.setServer(server);
        }

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
