package art.arcane.iris.pack.datapack.export.compile.biome;

import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ExportTestPacks;
import art.arcane.iris.pack.datapack.export.ExportTestStyles;
import art.arcane.iris.pack.datapack.export.compile.DimensionStage;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.ir.ClimateLayout;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.FieldEvaluator;
import art.arcane.iris.pack.datapack.export.ir.NoiseDef;
import art.arcane.iris.pack.datapack.export.lower.ExportCapabilities;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.CompatTestPlatform;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.Assert.assertTrue;

public class BiomeShareTest {
    private static final String PROBE_PACK_PROPERTY = "iris.exportProbePack";
    private static final String PROBE_DIMENSION = "overworld";
    private static final int SAMPLES_PER_AXIS = 1024;
    private static final double SHARE_TOLERANCE = 0.05;
    private static final long WORLD_SEED = 424_242L;
    private static final long IRIS_SEED = 9_876_543L;
    private static final int REPORTED_BIOMES = 20;

    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public ExportTestPacks.BoundPlatform platform = new ExportTestPacks.BoundPlatform();

    @Test
    public void biomeShareMatchesIrisWithin0_05() throws Exception {
        File pack = SelectionTestPacks.threeRegions(temporaryFolder.newFolder("pack"));
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = BiomeDecisionCompilerTest.context(data);
            Comparison comparison = compare(context, context.dimension(), data, 8);

            assertTrue(comparison.report(), comparison.maximumDelta() <= SHARE_TOLERANCE);
            assertTrue(comparison.report(), comparison.iris().size() >= 12);
        } finally {
            data.close();
        }
    }

    @Test
    public void overworldBiomeShareMatchesIrisWithin0_05() throws Exception {
        String probePack = System.getProperty(PROBE_PACK_PROPERTY);
        Assume.assumeTrue("Set -P" + PROBE_PACK_PROPERTY + " to run against a real pack", probePack != null && !probePack.isBlank());
        CompatTestPlatform unrestricted = CompatTestPlatform.bind(temporaryFolder.newFolder("platform"), List.of());
        try {
            IrisData data = IrisData.openRuntime(new File(probePack));
            try {
                IrisDimension dimension = data.getDimensionLoader().load(PROBE_DIMENSION, false);
                PackCompileContext context = PackCompileContext.builder()
                        .data(data)
                        .dimension(dimension)
                        .capabilities(ExportCapabilities.forTarget(DataVersion.V26_3))
                        .namespace("iris_export")
                        .styles(new ExportTestStyles())
                        .build();
                new DimensionStage().apply(context);
                Comparison comparison = compare(context, dimension, data, 48);
                System.out.println(comparison.report());

                assertTrue(comparison.report(), comparison.maximumDelta() <= SHARE_TOLERANCE);
            } finally {
                data.close();
            }
        } finally {
            unrestricted.unbind();
        }
    }

    private static Comparison compare(PackCompileContext context, IrisDimension dimension, IrisData data, int stride) {
        BiomeDecision decision = new BiomeDecisionCompiler(context.styles()).compile(context);
        Field height = BiomeDecisionCompilerTest.height(context);
        BiomeCodes codes = decision.bind(height);
        Map<String, NoiseDef> noises = BiomeDecisionCompilerTest.noises(context);
        IrisSelectionReference iris = new IrisSelectionReference(dimension, data, IRIS_SEED);
        double fluid = context.dimensionDef().fluidHeight();
        List<ClimateLayout.ClimateEntry> surface = new ArrayList<>();
        for (ClimateLayout.ClimateEntry entry : codes.layout().entries()) {
            if (entry.axis() == ClimateLayout.ClimateAxis.TEMPERATURE) {
                surface.add(entry);
            }
        }
        surface.sort(Comparator.comparingDouble(ClimateLayout.ClimateEntry::min));
        Map<String, Double> irisShares = new TreeMap<>();
        Map<String, Double> exportShares = new TreeMap<>();
        double weight = 1.0 / ((double) SAMPLES_PER_AXIS * SAMPLES_PER_AXIS);
        int origin = -SAMPLES_PER_AXIS / 2 * stride;
        for (int column = 0; column < SAMPLES_PER_AXIS; column++) {
            int x = origin + column * stride;
            for (int row = 0; row < SAMPLES_PER_AXIS; row++) {
                int z = origin + row * stride;
                double terrain = FieldEvaluator.sample(height, noises, WORLD_SEED, DataVersion.V26_3, x, 0, z);
                irisShares.merge(iris.surfaceBiome(x, z, terrain, fluid), weight, Double::sum);
                double code = FieldEvaluator.sample(codes.surfaceCode(), noises, WORLD_SEED, DataVersion.V26_3, x, 0, z);
                exportShares.merge(codes.loadKeyByBiomeId().get(entryAt(surface, code).biomeId()), weight, Double::sum);
            }
        }
        return new Comparison(irisShares, exportShares);
    }

    private static ClimateLayout.ClimateEntry entryAt(List<ClimateLayout.ClimateEntry> sorted, double code) {
        int low = 0;
        int high = sorted.size() - 1;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (sorted.get(middle).min() <= code) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return sorted.get(low);
    }

    private record Comparison(Map<String, Double> iris, Map<String, Double> export) {
        double maximumDelta() {
            double maximum = 0.0;
            for (String biome : biomes()) {
                maximum = Math.max(maximum, delta(biome));
            }
            return maximum;
        }

        String report() {
            List<String> biomes = new ArrayList<>(biomes());
            biomes.sort(Comparator.comparingDouble((String biome) -> -iris.getOrDefault(biome, 0.0)));
            StringBuilder report = new StringBuilder(String.format(Locale.ROOT, "biomes=%d maxDelta=%.4f%n", biomes.size(), maximumDelta()));
            for (String biome : biomes.subList(0, Math.min(REPORTED_BIOMES, biomes.size()))) {
                report.append(String.format(Locale.ROOT, "  %-40s iris=%.4f export=%.4f%n", biome, iris.getOrDefault(biome, 0.0), export.getOrDefault(biome, 0.0)));
            }
            return report.toString();
        }

        private TreeSet<String> biomes() {
            TreeSet<String> biomes = new TreeSet<>(iris.keySet());
            biomes.addAll(export.keySet());
            return biomes;
        }

        private double delta(String biome) {
            return Math.abs(iris.getOrDefault(biome, 0.0) - export.getOrDefault(biome, 0.0));
        }
    }
}
