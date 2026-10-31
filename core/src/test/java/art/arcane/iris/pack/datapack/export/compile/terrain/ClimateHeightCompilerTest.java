package art.arcane.iris.pack.datapack.export.compile.terrain;

import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ExportTestPacks;
import art.arcane.iris.pack.datapack.export.GenerationCost;
import art.arcane.iris.pack.datapack.export.compile.biome.BiomeCodes;
import art.arcane.iris.pack.datapack.export.compile.noise.NoiseRegistry;
import art.arcane.iris.pack.datapack.export.compile.noise.StyleCompiler;
import art.arcane.iris.pack.datapack.export.ir.AquiferDef;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.FieldValidator;
import art.arcane.iris.pack.datapack.export.ir.NoiseDef;
import art.arcane.iris.pack.datapack.export.ir.RouterDef;
import art.arcane.iris.pack.datapack.export.lower.ExportCapabilities;
import art.arcane.iris.pack.datapack.export.lower.GenerationCostCounter;
import art.arcane.iris.pack.datapack.export.lower.VanillaBounds;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClimateHeightCompilerTest {
    private static final ExportCapabilities CAPABILITIES = ExportCapabilities.forTarget(DataVersion.V26_3);
    private static final int SEA_LEVEL = 63;

    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public ExportTestPacks.BoundPlatform platform = new ExportTestPacks.BoundPlatform();

    @Test
    public void climateHeightIsTheUnblendedTerrainHeight() throws Exception {
        IrisData data = IrisData.openRuntime(pack());
        try {
            NoiseRegistry registry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);
            BiomeCodes codes = TerrainTestSupport.twoBiomeCodes(registry, 0.0);
            IrisDimension dimension = data.getDimensionLoader().load(TerrainTestPacks.DIMENSION, false);
            Field generator = new GeneratorCompiler(TerrainTestSupport.noiseStyles(), registry, CAPABILITIES, new ArrayList<>())
                    .compile(data.getGeneratorLoader().load("hills", false), "height");
            Field selector = new Field.Noise(registry.namespace() + ":" + TerrainTestSupport.SELECTOR_ID, 1.0, 0.0, null, null, null);

            Field climate = new ClimateHeightCompiler(TerrainTestSupport.noiseStyles()).compile(codes, dimension, data, registry, CAPABILITIES);

            assertEquals(ClimateHeightCompiler.CLIMATE_HEIGHT, ((Field.Shared) climate).name());
            FieldValidator.requireTwoDimensional(climate, "climate height");
            assertFalse(contains(climate, Field.Grid.class));
            for (int x = -2000; x <= 2000; x += 97) {
                for (int z = -2000; z <= 2000; z += 89) {
                    double mean = TerrainTestSupport.sample(generator, registry, DataVersion.V26_3, x, 0, z);
                    boolean low = TerrainTestSupport.sample(selector, registry, DataVersion.V26_3, x, 0, z) < 0.0;
                    double expected = SEA_LEVEL + (low ? 20.0 * mean : 60.0 + 40.0 * mean);
                    double actual = TerrainTestSupport.sample(climate, registry, DataVersion.V26_3, x, 0, z);
                    assertEquals(x + "," + z, expected, actual, 1.0e-3);
                }
            }
        } finally {
            data.close();
        }
    }

    @Test
    public void climateHeightCostsAFractionOfTheTerrainHeightUncached() throws Exception {
        IrisData data = IrisData.openRuntime(pack());
        try {
            NoiseRegistry registry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);
            BiomeCodes codes = TerrainTestSupport.twoBiomeCodes(registry, 0.0);
            IrisDimension dimension = data.getDimensionLoader().load(TerrainTestPacks.DIMENSION, false);
            Field terrain = new HeightCompiler(TerrainTestSupport.noiseStyles(), new ArrayList<>()).compile(codes, dimension, data, registry, CAPABILITIES).height();
            Field climate = new ClimateHeightCompiler(TerrainTestSupport.noiseStyles()).compile(codes, dimension, data, registry, CAPABILITIES);

            double terrainCost = worstCase(terrain, registry);
            double climateCost = worstCase(climate, registry);

            assertTrue(climateCost + " vs " + terrainCost, climateCost * 10.0 < terrainCost);
            assertTrue(String.valueOf(climateCost), climateCost <= 30.0);
        } finally {
            data.close();
        }
    }

    @Test
    public void expensiveGeneratorsEnterAsTheirCalibratedMean() throws Exception {
        IrisData data = IrisData.openRuntime(pack());
        try {
            NoiseRegistry registry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);
            BiomeCodes codes = TerrainTestSupport.twoBiomeCodes(registry, 0.0);
            IrisDimension dimension = data.getDimensionLoader().load(TerrainTestPacks.DIMENSION, false);
            StyleCompiler expensive = (style, seedSalt, styleRegistry, capabilities) -> {
                String id = styleRegistry.register(new NoiseDef("test/expensive", -5, List.of(1.0)));
                Field value = new Field.Binary(Field.BinaryOp.ADD, new Field.Const(0.5), new Field.Binary(Field.BinaryOp.MUL, new Field.Const(0.5),
                        new Field.Noise(id, 1.0 / style.getZoom(), 0.0, null, null, null)));
                for (int level = 0; level < 12; level++) {
                    value = new Field.Shared("test/expensive/" + level, new Field.Binary(Field.BinaryOp.MIN, value, value));
                }
                return value;
            };

            Field climate = new ClimateHeightCompiler(expensive).compile(codes, dimension, data, registry, CAPABILITIES);

            assertTrue(String.valueOf(worstCase(climate, registry)), worstCase(climate, registry) <= 20.0);
            Field selector = new Field.Noise(registry.namespace() + ":" + TerrainTestSupport.SELECTOR_ID, 1.0, 0.0, null, null, null);
            for (int x = -2000; x <= 2000; x += 211) {
                double value = TerrainTestSupport.sample(climate, registry, DataVersion.V26_3, x, 0, 37);
                boolean low = TerrainTestSupport.sample(selector, registry, DataVersion.V26_3, x, 0, 37) < 0.0;
                assertTrue(x + " " + value, low ? value > SEA_LEVEL && value < SEA_LEVEL + 20.0 : value > SEA_LEVEL + 60.0 && value < SEA_LEVEL + 100.0);
            }
        } finally {
            data.close();
        }
    }

    @Test
    public void flatBiomesGiveAConstantLeafSelection() throws Exception {
        File pack = TerrainTestPacks.twoBiomes(temporaryFolder.newFolder("pack"),
                TerrainTestPacks.link("hills", 10, 10), TerrainTestPacks.link("hills", 50, 50));
        TerrainTestPacks.generator(pack, "hills", TerrainTestPacks.twoLayers("BILINEAR_STARCAST_9", 12));
        IrisData data = IrisData.openRuntime(pack);
        try {
            NoiseRegistry registry = new NoiseRegistry(TerrainTestSupport.NAMESPACE);
            BiomeCodes codes = TerrainTestSupport.twoBiomeCodes(registry, 0.0);
            IrisDimension dimension = data.getDimensionLoader().load(TerrainTestPacks.DIMENSION, false);

            Field climate = new ClimateHeightCompiler(TerrainTestSupport.noiseStyles()).compile(codes, dimension, data, registry, CAPABILITIES);

            assertFalse(contains(climate, Field.Mux.class));
            assertTrue(contains(climate, Field.Partition.class));
            assertEquals(1, registry.definitions().size());
        } finally {
            data.close();
        }
    }

    private File pack() throws Exception {
        File pack = TerrainTestPacks.twoBiomes(temporaryFolder.newFolder("pack"),
                TerrainTestPacks.link("hills", 0, 20), TerrainTestPacks.link("hills", 60, 100));
        TerrainTestPacks.generator(pack, "hills", TerrainTestPacks.singleLayer("BILINEAR_STARCAST_9", 12, "SIMPLEX"));
        return pack;
    }

    private static double worstCase(Field field, NoiseRegistry registry) {
        Field zero = new Field.Const(0.0);
        RouterDef router = new RouterDef(field, zero, zero, zero, zero, zero, zero, zero, AquiferDef.dry(zero));
        return GenerationCostCounter.count(router, VanillaBounds.of(registry.namespace(), registry.definitions()), DataVersion.V26_3)
                .root(GenerationCost.Root.TEMPERATURE).orElseThrow().worstCaseNodes();
    }

    private static boolean contains(Field root, Class<? extends Field> type) {
        Set<Field> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Field> pending = new ArrayDeque<>();
        pending.push(root);
        while (!pending.isEmpty()) {
            Field field = pending.pop();
            if (!visited.add(field)) {
                continue;
            }
            if (type.isInstance(field)) {
                return true;
            }
            for (Field child : FieldMacros.children(field)) {
                pending.push(child);
            }
        }
        return false;
    }
}
