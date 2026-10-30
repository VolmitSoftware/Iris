package art.arcane.iris.pack.datapack.export.compile.terrain;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ExportTestPacks;
import art.arcane.iris.pack.datapack.export.compile.biome.BiomeCodes;
import art.arcane.iris.pack.datapack.export.compile.biome.BiomeIdSanitizer;
import art.arcane.iris.pack.datapack.export.compile.noise.NoiseRegistry;
import art.arcane.iris.pack.datapack.export.ir.Axis;
import art.arcane.iris.pack.datapack.export.ir.ClimateLayout;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.lower.ExportCapabilities;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;

public class OverworldTerrainProbeTest {
    private static final String PROPERTY = "iris.exportProbePack";
    private static final String DIMENSION = "overworld";
    private static final double CODE_SPAN = 0.9;

    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public ExportTestPacks.BoundPlatform platform = new ExportTestPacks.BoundPlatform();

    @Test
    public void overworldTerrainCompiles() {
        String pack = System.getProperty(PROPERTY);
        Assume.assumeTrue("Set -P" + PROPERTY + " to probe a real pack", pack != null && !pack.isBlank());
        doReturn(DataVersion.V26_3.getVersion()).when(IrisPlatforms.get()).minecraftVersion();
        doReturn(null).when(IrisPlatforms.get()).registries();
        IrisData data = IrisData.openRuntime(new File(pack));
        try {
            IrisDimension dimension = data.getDimensionLoader().load(DIMENSION, false);
            NoiseRegistry registry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);
            BiomeCodes codes = codes(dimension, data, registry);
            ExportCapabilities capabilities = ExportCapabilities.forTarget(DataVersion.V26_3);
            List<ExportDiagnostic> diagnostics = new ArrayList<>();

            TerrainHeight terrain = new HeightCompiler(TerrainTestSupport.noiseStyles(), diagnostics).compile(codes, dimension, data, registry, capabilities);
            Field height = terrain.height();
            Field volumetric = new VolumetricCompiler(TerrainTestSupport.noiseStyles(), diagnostics).compile(terrain, codes, dimension, data, registry, capabilities);

            assertFalse(height.axes().contains(Axis.Y));
            List<String> violations = new ArrayList<>(TerrainTestSupport.unguardedExpensiveBranches(height));
            violations.addAll(TerrainTestSupport.unguardedExpensiveBranches(volumetric));
            assertTrue(violations.toString(), violations.isEmpty());
            for (int sample = 0; sample < 8; sample++) {
                int x = sample * 977 - 3000;
                int z = sample * 613 - 2000;
                double value = TerrainTestSupport.sample(height, registry, DataVersion.V26_3, x, 0, z);
                assertTrue("height " + value, value >= dimension.getMinHeight() && value <= dimension.getMaxHeight());
                assertTrue(Double.isFinite(TerrainTestSupport.sample(volumetric, registry, DataVersion.V26_3, x, (int) value, z)));
            }
            Map<String, Integer> limits = new TreeMap<>();
            for (ExportDiagnostic diagnostic : diagnostics) {
                limits.merge(diagnostic.limit().name(), 1, Integer::sum);
            }
            System.out.println("[terrain probe] biomes " + codes.loadKeyByBiomeId().size() + " noises " + registry.definitions().size()
                    + " diagnostics " + limits);
            System.out.println("[terrain probe] height " + Shape.of(height));
            System.out.println("[terrain probe] volumetric " + Shape.of(volumetric));
        } finally {
            data.close();
        }
    }

    private static BiomeCodes codes(IrisDimension dimension, IrisData data, NoiseRegistry registry) {
        Map<String, IrisBiome> biomes = new TreeMap<>();
        for (IrisBiome biome : dimension.getReachableBiomes(() -> data)) {
            biomes.put(biome.getLoadKey(), biome);
        }
        int count = biomes.size();
        String selector = registry.register(TerrainTestSupport.SELECTOR);
        List<Double> thresholds = new ArrayList<>(count - 1);
        List<Field> leaves = new ArrayList<>(count);
        List<ClimateLayout.ClimateEntry> entries = new ArrayList<>(count);
        Map<String, String> loadKeys = new TreeMap<>();
        Set<String> taken = new HashSet<>();
        int index = 0;
        for (String loadKey : biomes.keySet()) {
            String id = BiomeIdSanitizer.sanitize(TerrainTestSupport.NAMESPACE, loadKey, taken);
            double center = -1.0 + (2.0 * index + 1.0) / count;
            double half = CODE_SPAN / count;
            entries.add(new ClimateLayout.ClimateEntry(id, ClimateLayout.ClimateAxis.TEMPERATURE, center - half, center + half));
            leaves.add(new Field.Const(center));
            loadKeys.put(id, loadKey);
            if (index > 0) {
                thresholds.add(-0.8 + 1.6 * index / count);
            }
            index++;
        }
        Field code = new Field.Partition(new Field.Noise(selector, 1.0, 0.0, null, null, null), thresholds, leaves);
        return new BiomeCodes(code, new Field.Const(0.0), new Field.Const(0.0), new ClimateLayout(entries), loadKeys);
    }

    private record Shape(long distinct, long emitted, long sharedFiles, int inlineDepth, int fileDepth, long gates, long muxes, long knots) {
        private static Shape of(Field root) {
            Map<Field, Long> sizes = new IdentityHashMap<>();
            Map<Field, Integer> depths = new IdentityHashMap<>();
            Map<Field, Integer> fileDepths = new IdentityHashMap<>();
            Set<Field> distinct = Collections.newSetFromMap(new IdentityHashMap<>());
            Set<String> shared = new HashSet<>();
            long[] counts = new long[3];
            collect(root, distinct, shared, counts);
            int deepestFile = fileDepth(root, fileDepths);
            for (Field field : distinct) {
                if (field instanceof Field.Shared sharedField) {
                    deepestFile = Math.max(deepestFile, fileDepth(sharedField.expr(), fileDepths));
                }
            }
            long emitted = root instanceof Field.Shared ? 0 : emitted(root, sizes);
            for (Field field : distinct) {
                if (field instanceof Field.Shared sharedField) {
                    emitted += emitted(sharedField.expr(), sizes);
                }
            }
            return new Shape(distinct.size(), emitted, shared.size(), depth(root, depths), deepestFile, counts[0], counts[1], counts[2]);
        }

        private static void collect(Field field, Set<Field> distinct, Set<String> shared, long[] counts) {
            if (!distinct.add(field)) {
                return;
            }
            switch (field) {
                case Field.Shared sharedField -> shared.add(sharedField.name());
                case Field.Gate ignored -> counts[0]++;
                case Field.Mux ignored -> counts[1]++;
                case Field.Curve curve -> counts[2] += curve.knots().size();
                default -> {
                }
            }
            for (Field child : FieldMacros.children(field)) {
                collect(child, distinct, shared, counts);
            }
        }

        private static long emitted(Field field, Map<Field, Long> sizes) {
            Long cached = sizes.get(field);
            if (cached != null) {
                return cached;
            }
            long total = 1;
            for (Field child : FieldMacros.children(field)) {
                total += child instanceof Field.Shared ? 1 : emitted(child, sizes);
            }
            sizes.put(field, total);
            return total;
        }

        private static int depth(Field field, Map<Field, Integer> depths) {
            Integer cached = depths.get(field);
            if (cached != null) {
                return cached;
            }
            int deepest = 0;
            for (Field child : FieldMacros.children(field)) {
                deepest = Math.max(deepest, depth(child, depths));
            }
            depths.put(field, deepest + 1);
            return deepest + 1;
        }

        private static int fileDepth(Field field, Map<Field, Integer> depths) {
            Integer cached = depths.get(field);
            if (cached != null) {
                return cached;
            }
            int deepest = 0;
            for (Field child : FieldMacros.children(field)) {
                deepest = Math.max(deepest, child instanceof Field.Shared ? 1 : fileDepth(child, depths));
            }
            depths.put(field, deepest + 1);
            return deepest + 1;
        }
    }
}
