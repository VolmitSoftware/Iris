package art.arcane.iris.probe;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public final class DensityCostAnalysisTest {
    @ClassRule
    public static final PaperRegistryIsolation registryIsolation = new PaperRegistryIsolation();

    private static final double EPSILON = 1.0E-9;
    private static final String LEAF = "{\"type\":\"noise\",\"noise\":\"minecraft:temperature\",\"xz_scale\":1.0,\"y_scale\":0.0}";

    private static HeadlessNativeRegistries registries;

    @BeforeClass
    public static void loadGraphs() throws IOException {
        Map<String, String> definitions = new LinkedHashMap<>();
        definitions.put(path("leaf"), LEAF);
        definitions.put(path("pair"), "{\"type\":\"add\",\"left\":\"t:leaf\",\"right\":\"t:leaf\"}");
        definitions.put(path("quad"), "{\"type\":\"mul\",\"left\":\"t:pair\",\"right\":\"t:pair\"}");
        definitions.put(path("cached"), "{\"type\":\"cache\",\"input\":\"t:pair\"}");
        definitions.put(path("twice"), "{\"type\":\"add\",\"left\":\"t:cached\",\"right\":\"t:cached\"}");
        definitions.put(path("select"), "{\"type\":\"interval_select\",\"input\":\"t:leaf\",\"thresholds\":[0.0],\"functions\":[\"t:quad\",\"t:pair\"]}");
        definitions.put(path("spline"), "{\"type\":\"spline\",\"spline\":{\"coordinate\":\"t:leaf\",\"points\":["
                + "{\"location\":-1.0,\"value\":" + nested("t:quad") + ",\"derivative\":0.0},"
                + "{\"location\":0.0,\"value\":0.0,\"derivative\":0.0},"
                + "{\"location\":1.0,\"value\":" + nested("t:quad") + ",\"derivative\":0.0}]}}");
        definitions.put(path("grid"), "{\"type\":\"interpolated\",\"input\":\"t:pair\",\"cell_size_xz\":4,\"cell_size_y\":8}");
        registries = HeadlessNativeRegistries.load(definitions);
    }

    @AfterClass
    public static void closeRegistries() throws IOException {
        if (registries != null) {
            registries.close();
        }
    }

    @Test
    public void referencesExpandWhileTheDagSharesThem() {
        DensityCostAnalysis.RootCost quad = analysis().analyze("quad", function("quad"));
        assertEquals(3L, quad.dagNodes());
        assertEquals(7.0, quad.expandedNodes(), EPSILON);
        assertEquals(7.0, quad.cachedNodes(), EPSILON);
        assertEquals(2, quad.namedSubtrees());
        assertEquals("t:leaf", quad.exclusive().getFirst().name());
        assertEquals(4.0, quad.exclusive().getFirst().multiplicity(), EPSILON);
        assertEquals(4.0, quad.exclusive().getFirst().contribution(), EPSILON);
        assertEquals("t:pair", quad.inclusive().getFirst().name());
        assertEquals(6.0, quad.inclusive().getFirst().inclusive(), EPSILON);
        assertEquals(1.0 / 3.0, quad.dagShare().get("leaf"), EPSILON);
        assertEquals(1.0 / 3.0, quad.dagShare().get("quad"), EPSILON);
    }

    @Test
    public void cachesEvaluateTheirBodyOnceWhenEnabled() {
        DensityCostAnalysis.RootCost twice = analysis().analyze("twice", function("twice"));
        assertEquals(4L, twice.dagNodes());
        assertEquals(9.0, twice.expandedNodes(), EPSILON);
        assertEquals(6.0, twice.cachedNodes(), EPSILON);
    }

    @Test
    public void uncachedPointEvaluationTakesOneSelectedBranch() {
        DensityCostAnalysis analysis = analysis();
        DensityCostAnalysis.RootCost select = analysis.analyze("select", function("select"));
        assertEquals(12.0, select.expandedNodes(), EPSILON);
        assertEquals(9.0, select.uncachedPointWorst(), EPSILON);
        DensityCostAnalysis.RootCost spline = analysis.analyze("spline", function("spline"));
        assertEquals(19.0, spline.expandedNodes(), EPSILON);
        assertEquals(11.0, spline.uncachedPointWorst(), EPSILON);
    }

    @Test
    public void offLatticeGridPointsSampleEightCorners() {
        DensityCostAnalysis.RootCost grid = analysis().analyze("grid", function("grid"));
        assertEquals(4.0, grid.expandedNodes(), EPSILON);
        assertEquals(25.0, grid.uncachedPointWorst(), EPSILON);
    }

    private static DensityCostAnalysis analysis() {
        return new DensityCostAnalysis(id -> DensityJsonBreakdown.category("overworld", id), 10);
    }

    private static DensityFunction function(String name) {
        return registries.registries().lookupOrThrow(Registries.DENSITY_FUNCTION)
                .getOrThrow(ResourceKey.create(Registries.DENSITY_FUNCTION, Identifier.parse("t:" + name))).value();
    }

    private static String nested(String coordinate) {
        return "{\"coordinate\":\"" + coordinate + "\",\"points\":[{\"location\":0.0,\"value\":1.0,\"derivative\":0.0},"
                + "{\"location\":1.0,\"value\":2.0,\"derivative\":0.0}]}";
    }

    private static String path(String name) {
        return "data/t/worldgen/density_function/" + name + ".json";
    }
}
