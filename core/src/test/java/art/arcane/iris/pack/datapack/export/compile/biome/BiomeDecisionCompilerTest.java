package art.arcane.iris.pack.datapack.export.compile.biome;

import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.ExportTestPacks;
import art.arcane.iris.pack.datapack.export.ExportTestStyles;
import art.arcane.iris.pack.datapack.export.compile.DimensionStage;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.compile.noise.NoiseRegistry;
import art.arcane.iris.pack.datapack.export.compile.noise.StyleCompiler;
import art.arcane.iris.pack.datapack.export.compile.terrain.FieldMacros;
import art.arcane.iris.pack.datapack.export.ir.Axis;
import art.arcane.iris.pack.datapack.export.ir.ClimateLayout;
import art.arcane.iris.pack.datapack.export.ir.CostClass;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.FieldEvaluator;
import art.arcane.iris.pack.datapack.export.ir.NoiseDef;
import art.arcane.iris.pack.datapack.export.lower.ExportCapabilities;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BiomeDecisionCompilerTest {
    private static final long WORLD_SEED = 7_331L;
    private static final int GRID = 96;
    private static final int STRIDE = 24;

    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Rule
    public ExportTestPacks.BoundPlatform platform = new ExportTestPacks.BoundPlatform();

    @Test
    public void childrenAppliedUpToFourHops() throws Exception {
        File pack = SelectionTestPacks.childChain(temporaryFolder.newFolder("pack"));
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = context(data);
            BiomeDecision decision = new BiomeDecisionCompiler(context.styles()).compile(context);
            BiomeCodes codes = decision.bind(new Field.Const(context.dimensionDef().fluidHeight() + 100.0));

            assertEquals(Set.of("a", "b", "c", "d", "e", "sea"), Set.copyOf(codes.loadKeyByBiomeId().values()));
            Map<String, Double> shares = shares(context, codes);
            assertEquals(shares.toString(), 0.5, shares.getOrDefault("a", 0.0), 0.15);
            assertEquals(shares.toString(), 0.5, shares.getOrDefault("e", 0.0), 0.15);
            assertEquals(shares.toString(), 1.0, shares.getOrDefault("a", 0.0) + shares.getOrDefault("e", 0.0), 1.0e-9);
        } finally {
            data.close();
        }
    }

    @Test
    public void selectorsCompileThroughTheSelectorStyles() throws Exception {
        File pack = SelectionTestPacks.childChain(temporaryFolder.newFolder("pack"));
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = context(data);
            StyleCompiler selectorsOnly = new StyleCompiler() {
                @Override
                public Field compile(IrisGeneratorStyle style, long seedSalt, NoiseRegistry registry, ExportCapabilities capabilities) {
                    throw new AssertionError("Biome selectors compile through the selector styles");
                }

                @Override
                public StyleCompiler selectors() {
                    return context.styles();
                }
            };

            BiomeCodes codes = new BiomeDecisionCompiler(selectorsOnly).compile(context).base();

            assertFalse(codes.surfaceCode() instanceof Field.Const);
        } finally {
            data.close();
        }
    }

    @Test
    public void eachChildHopReadsThePreviousHopOnce() throws Exception {
        File pack = SelectionTestPacks.childChainBesideStillBiome(temporaryFolder.newFolder("pack"));
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = context(data);
            Field base = new BiomeDecisionCompiler(context.styles()).compile(context).base().surfaceCode();
            Map<String, Field.Shared> shared = new TreeMap<>();
            collectShared(base, shared, Collections.newSetFromMap(new IdentityHashMap<>()));

            assertTrue(shared.keySet().toString(), shared.containsKey("biome/land/hop2"));
            assertEquals(1, references(shared.get("biome/land/hop1").expr(), shared.get("biome/land/pick")));
            assertEquals(1, references(shared.get("biome/land/hop2").expr(), shared.get("biome/land/hop1")));
        } finally {
            data.close();
        }
    }

    @Test
    public void codeRootsAreTwoDimensional() throws Exception {
        File pack = SelectionTestPacks.threeRegions(temporaryFolder.newFolder("pack"));
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = context(data);
            BiomeDecision decision = new BiomeDecisionCompiler(context.styles()).compile(context);
            BiomeCodes bound = decision.bind(height(context));

            for (BiomeCodes codes : List.of(decision.base(), bound)) {
                assertFalse(codes.surfaceCode().axes().contains(Axis.Y));
                assertFalse(codes.caveCode().axes().contains(Axis.Y));
            }
            assertEquals(EnumSet.of(Axis.X, Axis.Z), bound.surfaceCode().axes());
            assertEquals(EnumSet.of(Axis.X, Axis.Z), bound.caveCode().axes());
            assertTrue(bound.class3d().axes().contains(Axis.Y));
        } finally {
            data.close();
        }
    }

    @Test
    public void expensiveSubtreesSitUnderMuxOrGate() throws Exception {
        File pack = SelectionTestPacks.threeRegions(temporaryFolder.newFolder("pack"));
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = context(data);
            BiomeDecision decision = new BiomeDecisionCompiler(context.styles()).compile(context);
            BiomeCodes bound = decision.bind(height(context));

            List<String> violations = new ArrayList<>();
            for (Field root : List.of(bound.surfaceCode(), bound.caveCode(), bound.class3d(), decision.base().surfaceCode())) {
                collectEagerExpensive(root, violations);
            }
            assertTrue(violations.toString(), violations.isEmpty());
            assertEquals(CostClass.EXPENSIVE, bound.surfaceCode().cost());
        } finally {
            data.close();
        }
    }

    @Test
    public void compilationIsDeterministic() throws Exception {
        File pack = SelectionTestPacks.threeRegions(temporaryFolder.newFolder("pack"));
        IrisData first = IrisData.openRuntime(pack);
        IrisData second = IrisData.openRuntime(pack);
        try {
            PackCompileContext firstContext = context(first);
            PackCompileContext secondContext = context(second);
            BiomeDecision firstDecision = new BiomeDecisionCompiler(firstContext.styles()).compile(firstContext);
            BiomeDecision secondDecision = new BiomeDecisionCompiler(secondContext.styles()).compile(secondContext);

            assertEquals(firstDecision.base(), secondDecision.base());
            assertEquals(firstDecision.bind(height(firstContext)), secondDecision.bind(height(secondContext)));
            assertEquals(firstContext.noises().definitions(), secondContext.noises().definitions());
        } finally {
            first.close();
            second.close();
        }
    }

    @Test
    public void layoutPutsSurfaceCodesOnTemperatureAndUndergroundCodesOnDepth() throws Exception {
        File pack = SelectionTestPacks.threeRegions(temporaryFolder.newFolder("pack"));
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = context(data);
            BiomeCodes codes = new BiomeDecisionCompiler(context.styles()).compile(context).base();

            Map<String, ClimateLayout.ClimateAxis> axes = new TreeMap<>();
            for (ClimateLayout.ClimateEntry entry : codes.layout().entries()) {
                axes.put(codes.loadKeyByBiomeId().get(entry.biomeId()), entry.axis());
                assertTrue(entry.toString(), entry.axis() == ClimateLayout.ClimateAxis.TEMPERATURE
                        ? entry.min() >= -1.0 && entry.max() <= 1.0
                        : entry.min() >= 1.25 && entry.max() <= 2.0);
            }
            assertEquals(ClimateLayout.ClimateAxis.TEMPERATURE, axes.get("birch_forest"));
            assertEquals(ClimateLayout.ClimateAxis.DEPTH, axes.get("ice_cave"));
            assertEquals(ClimateLayout.ClimateAxis.DEPTH, axes.get("deep_dark"));
            assertEquals(18, codes.layout().entries().size());
        } finally {
            data.close();
        }
    }

    @Test
    public void shoreRelabelFollowsHeight() throws Exception {
        File pack = SelectionTestPacks.threeRegions(temporaryFolder.newFolder("pack"));
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = context(data);
            BiomeDecision decision = new BiomeDecisionCompiler(context.styles()).compile(context);
            double fluid = context.dimensionDef().fluidHeight();

            Set<String> deep = Set.copyOf(shares(context, decision.bind(new Field.Const(fluid - 20.0))).keySet());
            Set<String> coast = Set.copyOf(shares(context, decision.bind(new Field.Const(fluid))).keySet());
            Set<String> high = Set.copyOf(shares(context, decision.bind(new Field.Const(fluid + 40.0))).keySet());

            assertTrue(deep.toString(), Set.of("frozen_ocean", "warm_ocean", "ocean", "deep_ocean").containsAll(deep));
            assertTrue(coast.toString(), Set.of("snowy_beach", "beach").containsAll(coast));
            assertTrue(high.toString(), Set.of("tundra", "taiga", "desert", "savanna", "mesa", "plains", "forest", "birch_forest").containsAll(high));
            assertTrue(high.toString(), high.contains("birch_forest"));
        } finally {
            data.close();
        }
    }

    @Test
    public void boundCodesReadTheHeightOncePerComparison() throws Exception {
        File pack = SelectionTestPacks.threeRegions(temporaryFolder.newFolder("pack"));
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = context(data);
            BiomeDecision decision = new BiomeDecisionCompiler(context.styles()).compile(context);
            Field height = height(context);

            BiomeCodes bound = decision.bind(height);

            assertEquals(2, references(bound.surfaceCode(), height));
            assertEquals(1, references(bound.class3d(), height));
            assertEquals(0, references(bound.surfaceCode(), decision.base().surfaceCode()));
        } finally {
            data.close();
        }
    }

    @Test
    public void caveGateAndCarvingBandSelectUndergroundBiomes() throws Exception {
        File pack = SelectionTestPacks.threeRegions(temporaryFolder.newFolder("pack"));
        IrisData data = IrisData.openRuntime(pack);
        try {
            PackCompileContext context = context(data);
            BiomeCodes codes = new BiomeDecisionCompiler(context.styles()).compile(context).bind(new Field.Const(60.0));
            Map<String, NoiseDef> noises = noises(context);

            Set<String> carving = new TreeSet<>();
            Set<String> caves = new TreeSet<>();
            Set<String> shallow = new TreeSet<>();
            for (int x = 0; x < GRID; x++) {
                for (int z = 0; z < GRID; z++) {
                    carving.add(biomeAt(codes, noises, x * STRIDE, -48, z * STRIDE));
                    caves.add(biomeAt(codes, noises, x * STRIDE, -20, z * STRIDE));
                    shallow.add(biomeAt(codes, noises, x * STRIDE, 20, z * STRIDE));
                }
            }
            assertEquals(Set.of("deep_dark"), carving);
            assertTrue(caves.toString(), caves.containsAll(Set.of("ice_cave", "lush_cave", "dripstone_cave")));
            assertFalse(caves.toString(), caves.contains("deep_dark"));
            assertTrue(shallow.toString(), shallow.stream().noneMatch(Set.of("ice_cave", "lush_cave", "dripstone_cave", "deep_dark")::contains));
        } finally {
            data.close();
        }
    }

    static PackCompileContext context(IrisData data) {
        PackCompileContext context = ExportTestPacks.contextBuilder(data, DataVersion.V26_3).styles(new ExportTestStyles()).build();
        new DimensionStage().apply(context);
        return context;
    }

    static Field height(PackCompileContext context) {
        String noise = context.noises().register(new NoiseDef("test/height", 0, List.of(1.0)));
        return new Field.Binary(Field.BinaryOp.ADD, new Field.Const(context.dimensionDef().fluidHeight()),
                new Field.Binary(Field.BinaryOp.MUL, new Field.Const(30.0), new Field.Noise(noise, 1.0 / 300.0, 0.0, null, null, null)));
    }

    static Map<String, NoiseDef> noises(PackCompileContext context) {
        Map<String, NoiseDef> noises = new HashMap<>();
        for (NoiseDef definition : context.noises().definitions()) {
            noises.put(context.namespace() + ":" + definition.id(), definition);
        }
        return noises;
    }

    static String biomeAt(BiomeCodes codes, Map<String, NoiseDef> noises, int x, int y, int z) {
        double surface = FieldEvaluator.sample(codes.surfaceCode(), noises, WORLD_SEED, DataVersion.V26_3, x, y, z);
        double humidity = FieldEvaluator.sample(codes.caveCode(), noises, WORLD_SEED, DataVersion.V26_3, x, y, z);
        double depth = FieldEvaluator.sample(codes.class3d(), noises, WORLD_SEED, DataVersion.V26_3, x, y, z);
        String best = null;
        double bestFitness = Double.POSITIVE_INFINITY;
        for (ClimateLayout.ClimateEntry entry : codes.layout().entries()) {
            double fitness = switch (entry.axis()) {
                case TEMPERATURE -> square(gap(surface, entry)) + square(humidity) + square(depth);
                case HUMIDITY -> square(surface) + square(gap(humidity, entry)) + square(depth);
                case DEPTH -> square(surface) + square(humidity) + square(gap(depth, entry));
            };
            if (fitness < bestFitness) {
                bestFitness = fitness;
                best = entry.biomeId();
            }
        }
        return codes.loadKeyByBiomeId().get(best);
    }

    private static Map<String, Double> shares(PackCompileContext context, BiomeCodes codes) {
        Map<String, NoiseDef> noises = noises(context);
        Map<String, Double> shares = new TreeMap<>();
        double weight = 1.0 / (GRID * GRID);
        for (int x = 0; x < GRID; x++) {
            for (int z = 0; z < GRID; z++) {
                shares.merge(biomeAt(codes, noises, x * STRIDE, 100, z * STRIDE), weight, Double::sum);
            }
        }
        return shares;
    }

    private static void collectEagerExpensive(Field field, List<String> violations) {
        switch (field) {
            case Field.Partition partition -> {
                for (Field leaf : partition.leaves()) {
                    requireNotExpensive(leaf, "partition leaf", violations);
                }
                collectEagerExpensive(partition.input(), violations);
            }
            case Field.RangeIf rangeIf -> {
                requireNotExpensive(rangeIf.inRange(), "range choice branch", violations);
                requireNotExpensive(rangeIf.outOfRange(), "range choice branch", violations);
                collectEagerExpensive(rangeIf.input(), violations);
            }
            case Field.Binary binary -> {
                if (binary.op() == Field.BinaryOp.MUL) {
                    requireNotExpensive(binary.left(), "mul operand", violations);
                    requireNotExpensive(binary.right(), "mul operand", violations);
                }
                collectEagerExpensive(binary.left(), violations);
                collectEagerExpensive(binary.right(), violations);
            }
            case Field.Lerp lerp -> {
                requireNotExpensive(lerp.first(), "lerp branch", violations);
                requireNotExpensive(lerp.second(), "lerp branch", violations);
                collectEagerExpensive(lerp.alpha(), violations);
            }
            case Field.Mux mux -> {
                collectEagerExpensive(mux.index(), violations);
                for (Field selected : mux.cases()) {
                    collectEagerExpensive(selected, violations);
                }
            }
            case Field.Gate gate -> {
                collectEagerExpensive(gate.condition(), violations);
                collectEagerExpensive(gate.expr(), violations);
            }
            case Field.Shared shared -> collectEagerExpensive(shared.expr(), violations);
            case Field.Curve curve -> collectEagerExpensive(curve.input(), violations);
            case Field.Unary unary -> collectEagerExpensive(unary.input(), violations);
            case Field.Clamp clamp -> collectEagerExpensive(clamp.input(), violations);
            case Field.Grid grid -> collectEagerExpensive(grid.expr(), violations);
            default -> {
            }
        }
    }

    private static void requireNotExpensive(Field field, String role, List<String> violations) {
        if (field.cost() == CostClass.EXPENSIVE) {
            violations.add(role + " " + field.getClass().getSimpleName());
        }
    }

    private static void collectShared(Field field, Map<String, Field.Shared> found, Set<Field> visited) {
        if (!visited.add(field)) {
            return;
        }
        if (field instanceof Field.Shared shared) {
            found.put(shared.name(), shared);
        }
        for (Field child : FieldMacros.children(field)) {
            collectShared(child, found, visited);
        }
    }

    private static int references(Field field, Field target) {
        if (field == target) {
            return 1;
        }
        int count = 0;
        for (Field child : FieldMacros.children(field)) {
            count += references(child, target);
        }
        return count;
    }

    private static double gap(double value, ClimateLayout.ClimateEntry entry) {
        return value < entry.min() ? entry.min() - value : value > entry.max() ? value - entry.max() : 0.0;
    }

    private static double square(double value) {
        return value * value;
    }
}
