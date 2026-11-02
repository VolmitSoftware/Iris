package art.arcane.iris.pack.datapack.export.compile.terrain;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.generation.decoration.IrisDecorationPart;
import art.arcane.iris.generation.decoration.IrisDecorator;
import art.arcane.iris.generation.hydrology.config.IrisBankStyle;
import art.arcane.iris.generation.hydrology.config.IrisFluidProfile;
import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyClimate;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyDeformation;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyPermission;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyPolicy;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyTier;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyUnderground;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.export.compile.noise.NoiseRegistry;
import art.arcane.iris.pack.datapack.export.ir.Axis;
import art.arcane.iris.pack.datapack.export.ir.CostClass;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.datapack.export.ir.Field;
import art.arcane.iris.pack.datapack.export.ir.FieldEvaluator;
import art.arcane.iris.pack.datapack.export.ir.FieldValidator;
import art.arcane.iris.pack.datapack.export.ir.NoiseDef;
import art.arcane.iris.pack.datapack.export.lower.ExportCapabilities;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class RiverCompilerTest {
    private static final int SEA_LEVEL = 63;
    private static final int WIDTH_REGION = 768;
    private static final int RUN_LIMIT = 96;
    private static final double HALF_WIDTH = 5.0;
    private static final double TOLERANCE = 1.0e-3;

    @Test
    public void riverWidthConstantAlongIsoline() {
        NoiseRegistry registry = registry();
        IrisHydrology hydrology = hydrology(tier(5000, HALF_WIDTH, HALF_WIDTH, 2, 8, IrisBankStyle.SMOOTH));
        Field carved = compile(new Field.Const(SEA_LEVEL + 2.0), hydrology, registry, DataVersion.V26_3, new ArrayList<>());
        Map<String, NoiseDef> noises = TerrainTestSupport.noises(registry);
        boolean[][] wet = new boolean[WIDTH_REGION][WIDTH_REGION];
        for (int x = 0; x < WIDTH_REGION; x++) {
            for (int z = 0; z < WIDTH_REGION; z++) {
                wet[x][z] = sample(carved, noises, DataVersion.V26_3, x, z) < SEA_LEVEL - 0.5;
            }
        }

        List<Double> widths = new ArrayList<>();
        for (int z = RUN_LIMIT; z < WIDTH_REGION - RUN_LIMIT; z += 2) {
            int x = 0;
            while (x < WIDTH_REGION) {
                if (!wet[x][z]) {
                    x++;
                    continue;
                }
                int start = x;
                while (x < WIDTH_REGION && wet[x][z]) {
                    x++;
                }
                if (start == 0 || x == WIDTH_REGION) {
                    continue;
                }
                double across = x - start;
                int middle = (start + x - 1) / 2;
                int low = z;
                int high = z;
                while (low > z - RUN_LIMIT && wet[middle][low - 1]) {
                    low--;
                }
                while (high < z + RUN_LIMIT && wet[middle][high + 1]) {
                    high++;
                }
                double along = low == z - RUN_LIMIT || high == z + RUN_LIMIT ? Double.POSITIVE_INFINITY : high - low + 1.0;
                widths.add(1.0 / Math.sqrt(1.0 / (across * across) + 1.0 / (along * along)));
            }
        }

        double mean = widths.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        double variance = widths.stream().mapToDouble(width -> (width - mean) * (width - mean)).average().orElseThrow();
        double variation = Math.sqrt(variance) / mean;
        assertTrue("only " + widths.size() + " channel crossings", widths.size() >= 100);
        assertTrue("mean width " + mean, Math.abs(mean - 2.0 * HALF_WIDTH) <= 2.0);
        assertTrue("width CV " + variation + " over " + widths.size() + " crossings", variation <= 0.15);
    }

    @Test
    public void riverBedNeverBelowSeaLevelMinusDepth() {
        NoiseRegistry registry = registry();
        Field natural = rollingHeight(registry, 30.0);
        Field carved = compile(natural, overworldHydrology(), registry, DataVersion.V26_3, new ArrayList<>());
        Map<String, NoiseDef> noises = TerrainTestSupport.noises(registry);
        double deepest = 0.5 * 18.0;
        int flooded = 0;
        for (int x = 0; x < 320; x += 2) {
            for (int z = 0; z < 320; z += 2) {
                double ground = sample(natural, noises, DataVersion.V26_3, x, z);
                double bed = sample(carved, noises, DataVersion.V26_3, x, z);
                assertTrue("bed " + bed + " under ground " + ground + " at " + x + "," + z, bed >= Math.min(ground, SEA_LEVEL - deepest) - TOLERANCE);
                assertTrue("bed " + bed + " raised above ground " + ground + " at " + x + "," + z, bed <= ground + TOLERANCE);
                if (ground > SEA_LEVEL && bed < SEA_LEVEL - 0.5) {
                    flooded++;
                }
            }
        }
        assertTrue("no lowland channel reaches the sea", flooded > 0);
    }

    @Test
    public void uplandChannelsAreDryGullies() {
        NoiseRegistry registry = registry();
        double ground = SEA_LEVEL + 80.0;
        Field carved = compile(new Field.Const(ground), overworldHydrology(), registry, DataVersion.V26_3, new ArrayList<>());
        Map<String, NoiseDef> noises = TerrainTestSupport.noises(registry);
        double gully = 3.0 + 0.5 * 9.0;
        int carvedColumns = 0;
        int columns = 0;
        for (int x = 0; x < 256; x += 2) {
            for (int z = 0; z < 256; z += 2) {
                double bed = sample(carved, noises, DataVersion.V26_3, x, z);
                columns++;
                assertTrue("upland bed " + bed + " at " + x + "," + z + " reaches the sea", bed > SEA_LEVEL + 0.5);
                assertTrue("gully " + (ground - bed) + " deeper than " + gully, ground - bed <= gully + TOLERANCE);
                if (bed < ground - 0.5) {
                    carvedColumns++;
                }
            }
        }
        assertTrue("only " + carvedColumns + " of " + columns + " upland columns carved", carvedColumns >= columns / 100);
    }

    @Test
    public void riverRootIsTwoDimensional() {
        DataVersion target = DataVersion.V26_3;
        NoiseRegistry registry = registry();
        Field carved = compile(rollingHeight(registry, 30.0), overworldHydrology(), registry, target, new ArrayList<>());

        assertEquals(RiverCompiler.CARVED_HEIGHT, ((Field.Shared) carved).name());
        assertEquals(EnumSet.of(Axis.X, Axis.Z), carved.axes());
        FieldValidator.requireTwoDimensional(carved, "height");
    }

    @Test
    public void noRiversWhenHydrologyDisabled() {
        Field natural = new Field.Const(SEA_LEVEL + 2.0);
        for (IrisHydrology hydrology : new IrisHydrology[]{null, overworldHydrology().setEnabled(false)}) {
            NoiseRegistry registry = registry();
            List<ExportDiagnostic> diagnostics = new ArrayList<>();

            Field result = compile(natural, hydrology, registry, DataVersion.V26_3, diagnostics);

            assertSame(natural, result);
            assertTrue(registry.definitions().isEmpty());
            assertTrue(diagnostics.isEmpty());
        }
    }

    @Test
    public void riversReportDrainageAndElevatedWaterLimits() {
        List<ExportDiagnostic> diagnostics = new ArrayList<>();

        compile(new Field.Const(SEA_LEVEL + 2.0), overworldHydrology(), registry(), DataVersion.V26_3, diagnostics);

        assertEquals(List.of(ExportLimit.ELEVATED_WATER, ExportLimit.RIVER_DRAINAGE, ExportLimit.SEA_INLET, ExportLimit.CAVE_POOL),
                diagnostics.stream().map(ExportDiagnostic::limit).toList());
        assertEquals("dimensions/rivers/hydrology", diagnostics.getFirst().resource());
    }

    @Test
    public void lavaRiversReportADiagnostic() {
        List<ExportDiagnostic> diagnostics = new ArrayList<>();
        IrisBiome volcano = biome("tropical/volcanoes", new IrisHydrologyPolicy().setFluid("lava"));
        List<RiverCompiler.Area> areas = List.of(
                new RiverCompiler.Area(region("tropical", null), List.of(volcano)),
                new RiverCompiler.Area(region("hot", null), List.of(volcano)),
                new RiverCompiler.Area(region("hot", null), List.of(biome("plains", null))));

        compile(new Field.Const(SEA_LEVEL + 2.0), withLava(overworldHydrology()), areas, diagnostics);

        List<ExportDiagnostic> fluids = limited(diagnostics, ExportLimit.RIVER_FLUID);
        assertEquals(diagnostics.toString(), 1, fluids.size());
        assertEquals("biomes/tropical/volcanoes", fluids.getFirst().resource());
        assertEquals(ExportDiagnostic.Severity.WARNING, fluids.getFirst().severity());
        assertTrue(fluids.getFirst().message(), fluids.getFirst().message().contains("'lava'"));
        assertTrue(fluids.getFirst().message(), fluids.getFirst().message().contains("minecraft:water"));
    }

    @Test
    public void childBiomesInheritTheParentLava() {
        List<ExportDiagnostic> diagnostics = new ArrayList<>();
        IrisBiome plains = biome("tropical/volcanic-plains", new IrisHydrologyPolicy().setFluid("lava"));
        IrisBiome cone = biome("tropical/cone", new IrisHydrologyPolicy().setRainfall(1.0));
        IrisBiome spring = biome("tropical/spring", new IrisHydrologyPolicy().setFluid("water"));

        compile(new Field.Const(SEA_LEVEL + 2.0), withLava(overworldHydrology()), List.of(
                new RiverCompiler.Area(region("tropical", null), List.of(cone, plains)),
                new RiverCompiler.Area(region("tropical", null), List.of(spring, plains))), diagnostics);

        List<ExportDiagnostic> fluids = limited(diagnostics, ExportLimit.RIVER_FLUID);
        assertEquals(diagnostics.toString(), 1, fluids.size());
        assertEquals("biomes/tropical/volcanic-plains", fluids.getFirst().resource());
        assertTrue(fluids.getFirst().message(), fluids.getFirst().message().contains("'lava'"));
    }

    @Test
    public void lavaTiersReportTheirFluidAndSeamOnTheHydrology() {
        List<ExportDiagnostic> diagnostics = new ArrayList<>();
        IrisHydrology hydrology = withLava(overworldHydrology());
        hydrology.getFluids().getLast().setSeam("obsidian");
        hydrology.getFluids().add(new IrisFluidProfile().setName("obsidian").setPalette(new IrisMaterialPalette().qclear().qadd("minecraft:obsidian")));
        for (IrisHydrologyTier tier : hydrology.getTiers()) {
            tier.setFluid("lava");
        }

        compile(new Field.Const(SEA_LEVEL + 2.0), hydrology, List.of(new RiverCompiler.Area(region("region", null), List.of(biome("plains", null)))), diagnostics);

        List<ExportDiagnostic> fluids = limited(diagnostics, ExportLimit.RIVER_FLUID);
        assertEquals(diagnostics.toString(), 1, fluids.size());
        assertEquals("dimensions/rivers/hydrology", fluids.getFirst().resource());
        assertTrue(fluids.getFirst().message(), fluids.getFirst().message().contains("'obsidian'"));
    }

    @Test
    public void waterSeamsReportADiagnostic() {
        List<ExportDiagnostic> diagnostics = new ArrayList<>();
        IrisHydrology hydrology = overworldHydrology().setFluids(new KList<>(
                new IrisFluidProfile().setSeam("mud"),
                new IrisFluidProfile().setName("mud").setPalette(new IrisMaterialPalette().qclear().qadd("minecraft:mud"))));

        compile(new Field.Const(SEA_LEVEL + 2.0), hydrology, List.of(new RiverCompiler.Area(region("region", null), List.of(biome("plains", null)))), diagnostics);

        List<ExportDiagnostic> fluids = limited(diagnostics, ExportLimit.RIVER_FLUID);
        assertEquals(diagnostics.toString(), 1, fluids.size());
        assertEquals("dimensions/rivers/hydrology", fluids.getFirst().resource());
        assertTrue(fluids.getFirst().message(), fluids.getFirst().message().contains("Seam 'mud' of fluid 'water'"));
    }

    @Test
    public void unknownPolicyFluidFailsTheExport() {
        IrisBiome acid = biome("acid-lake", new IrisHydrologyPolicy().setFluid("acid"));
        List<RiverCompiler.Area> areas = List.of(new RiverCompiler.Area(region("region", null), List.of(acid)));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> compile(new Field.Const(SEA_LEVEL + 2.0), overworldHydrology(), areas, new ArrayList<>()));

        assertTrue(failure.getMessage(), failure.getMessage().contains("'acid'"));
        assertTrue(failure.getMessage(), failure.getMessage().contains("biomes/acid-lake"));
    }

    @Test
    public void areasAreBuiltOnlyWhenRiversExport() {
        Supplier<List<RiverCompiler.Area>> refused = () -> {
            throw new AssertionError("areas built without exported rivers");
        };
        for (IrisHydrology hydrology : new IrisHydrology[]{null, overworldHydrology().setEnabled(false), overworldHydrology().setDensity(0.0)}) {
            List<ExportDiagnostic> diagnostics = new ArrayList<>();

            compile(new Field.Const(SEA_LEVEL + 2.0), dimension(hydrology, null), refused, registry(), DataVersion.V26_3, diagnostics);

            assertTrue(diagnostics.toString(), limited(diagnostics, ExportLimit.RIVER_DRAINAGE).isEmpty());
        }
    }

    @Test
    public void fluidsMatchingTheExportedFluidReportNothing() {
        List<ExportDiagnostic> water = new ArrayList<>();
        IrisBiome wet = biome("plains", new IrisHydrologyPolicy().setFluid("water"));
        compile(new Field.Const(SEA_LEVEL + 2.0), withLava(overworldHydrology()), List.of(new RiverCompiler.Area(region("region", null), List.of(wet))), water);

        List<ExportDiagnostic> lava = new ArrayList<>();
        IrisHydrology molten = withLava(overworldHydrology());
        for (IrisHydrologyTier tier : molten.getTiers()) {
            tier.setFluid("lava");
        }
        IrisDimension nether = dimension(molten, new IrisHydrologyPolicy().setFluid("lava"));
        nether.setFluidPalette(new IrisMaterialPalette().qclear().qadd("lava"));
        compile(new Field.Const(SEA_LEVEL + 2.0), nether, List.of(new RiverCompiler.Area(region("region", null), List.of(biome("plains", null)))),
                registry(), DataVersion.V26_3, lava);

        assertTrue(water.toString(), limited(water, ExportLimit.RIVER_FLUID).isEmpty());
        assertTrue(lava.toString(), limited(lava, ExportLimit.RIVER_FLUID).isEmpty());
    }

    @Test
    public void rainfallAndBlockedStreamsReportPolicyDiagnostics() {
        List<ExportDiagnostic> diagnostics = new ArrayList<>();
        IrisRegion hot = region("hot", new IrisHydrologyPolicy().setRainfall(1.4));
        IrisBiome deep = biome("ocean/deep", new IrisHydrologyPolicy().setStreams(IrisHydrologyPermission.BLOCK));
        IrisBiome neutral = biome("plains", new IrisHydrologyPolicy().setRainfall(1.0));
        IrisDimension dimension = dimension(overworldHydrology(), new IrisHydrologyPolicy().setRainfall(1.2));

        compile(new Field.Const(SEA_LEVEL + 2.0), dimension, List.of(new RiverCompiler.Area(hot, List.of(deep)), new RiverCompiler.Area(hot, List.of(neutral))),
                registry(), DataVersion.V26_3, diagnostics);

        List<ExportDiagnostic> policies = limited(diagnostics, ExportLimit.RIVER_POLICY);
        assertEquals(List.of("regions/hot", "biomes/ocean/deep"), policies.stream().map(ExportDiagnostic::resource).toList());
        assertTrue(policies.getFirst().message(), policies.getFirst().message().contains("rainfall"));
        assertTrue(policies.getLast().message(), policies.getLast().message().contains("BLOCK"));
    }

    @Test
    public void wetAndDryZonesReportAClimateDiagnostic() {
        List<ExportDiagnostic> zoned = new ArrayList<>();
        IrisHydrology hydrology = overworldHydrology().setClimate(new IrisHydrologyClimate().setEnabled(true));
        compile(new Field.Const(SEA_LEVEL + 2.0), hydrology, List.of(new RiverCompiler.Area(region("region", null), List.of(biome("plains", null)))), zoned);

        List<ExportDiagnostic> exempt = new ArrayList<>();
        IrisRegion everywhere = region("region", new IrisHydrologyPolicy().setClimateZones(false));
        compile(new Field.Const(SEA_LEVEL + 2.0), hydrology, List.of(new RiverCompiler.Area(everywhere, List.of(biome("plains", null)))), exempt);

        List<ExportDiagnostic> climate = limited(zoned, ExportLimit.RIVER_CLIMATE);
        assertEquals(zoned.toString(), 1, climate.size());
        assertEquals("dimensions/rivers/hydrology", climate.getFirst().resource());
        assertTrue(exempt.toString(), limited(exempt, ExportLimit.RIVER_CLIMATE).isEmpty());
    }

    @Test
    public void shoreBiomesWithAWidthReportADiagnostic() {
        List<ExportDiagnostic> diagnostics = new ArrayList<>();
        IrisDimension dimension = dimension(overworldHydrology(), new IrisHydrologyPolicy().setShoreBiomes(new KList<>("shore/beach")));
        List<RiverCompiler.Area> areas = List.of(
                new RiverCompiler.Area(region("banked", new IrisHydrologyPolicy().setShoreBiomeWidth(4)), List.of(biome("plains", null))),
                new RiverCompiler.Area(region("bare", null), List.of(biome("plains", null))));

        compile(new Field.Const(SEA_LEVEL + 2.0), dimension, areas, registry(), DataVersion.V26_3, diagnostics);

        List<ExportDiagnostic> widthless = new ArrayList<>();
        compile(new Field.Const(SEA_LEVEL + 2.0), dimension, List.of(areas.getLast()), registry(), DataVersion.V26_3, widthless);

        assertEquals(List.of("dimensions/rivers"), limited(diagnostics, ExportLimit.RIVER_SHORE_BIOME).stream().map(ExportDiagnostic::resource).toList());
        assertTrue(widthless.toString(), limited(widthless, ExportLimit.RIVER_SHORE_BIOME).isEmpty());
    }

    @Test
    public void waterDecoratorsReportADiagnostic() {
        List<ExportDiagnostic> diagnostics = new ArrayList<>();
        IrisHydrology hydrology = overworldHydrology();
        hydrology.getTiers().getFirst().setDecorators(new KList<>(seagrass()));
        IrisBiome reeds = biome("swamp", new IrisHydrologyPolicy().setDecorators(new KList<>(seagrass())));
        IrisBiome barren = biome("desert", new IrisHydrologyPolicy().setDecorators(new KList<>()));
        IrisRegion region = region("region", null);

        compile(new Field.Const(SEA_LEVEL + 2.0), hydrology, List.of(new RiverCompiler.Area(region, List.of(reeds)), new RiverCompiler.Area(region, List.of(barren))), diagnostics);

        List<ExportDiagnostic> plain = new ArrayList<>();
        compile(new Field.Const(SEA_LEVEL + 2.0), hydrology, List.of(new RiverCompiler.Area(region, List.of(biome("plains", null)))), plain);

        assertEquals(List.of("biomes/swamp"), limited(diagnostics, ExportLimit.RIVER_DECORATOR).stream().map(ExportDiagnostic::resource).toList());
        assertEquals(List.of("dimensions/rivers/hydrology"), limited(plain, ExportLimit.RIVER_DECORATOR).stream().map(ExportDiagnostic::resource).toList());
    }

    @Test
    public void seaInletsReportADiagnostic() {
        List<ExportDiagnostic> diagnostics = new ArrayList<>();
        IrisHydrology hydrology = overworldHydrology();
        hydrology.getUnderground().getCavePools().setEnabled(false);

        compile(new Field.Const(SEA_LEVEL + 2.0), hydrology, registry(), DataVersion.V26_3, diagnostics);

        assertEquals(1, limited(diagnostics, ExportLimit.SEA_INLET).size());
        assertEquals("dimensions/rivers/hydrology", limited(diagnostics, ExportLimit.SEA_INLET).getFirst().resource());
        assertTrue(diagnostics.toString(), limited(diagnostics, ExportLimit.CAVE_POOL).isEmpty());
    }

    @Test
    public void cavePoolsReportADiagnostic() {
        List<ExportDiagnostic> diagnostics = new ArrayList<>();
        IrisHydrology hydrology = overworldHydrology();
        hydrology.getUnderground().getSeaInlets().setEnabled(false);

        compile(new Field.Const(SEA_LEVEL + 2.0), hydrology, registry(), DataVersion.V26_3, diagnostics);

        assertEquals(1, limited(diagnostics, ExportLimit.CAVE_POOL).size());
        assertEquals("dimensions/rivers/hydrology", limited(diagnostics, ExportLimit.CAVE_POOL).getFirst().resource());
        assertTrue(diagnostics.toString(), limited(diagnostics, ExportLimit.SEA_INLET).isEmpty());
    }

    @Test
    public void unusedHydrologyFeaturesReportNoLimits() {
        List<ExportDiagnostic> plain = new ArrayList<>();
        IrisHydrology hydrology = withLava(overworldHydrology()).setUnderground(underground(false));
        compile(new Field.Const(SEA_LEVEL + 2.0), hydrology, registry(), DataVersion.V26_3, plain);

        List<ExportDiagnostic> dry = new ArrayList<>();
        IrisHydrology still = overworldHydrology().setDensity(0.0).setClimate(new IrisHydrologyClimate().setEnabled(true));
        still.getTiers().getFirst().setDecorators(new KList<>(seagrass()));
        compile(new Field.Const(SEA_LEVEL + 2.0), still, registry(), DataVersion.V26_3, dry);

        assertEquals(List.of(ExportLimit.ELEVATED_WATER, ExportLimit.RIVER_DRAINAGE), plain.stream().map(ExportDiagnostic::limit).toList());
        assertEquals(List.of(ExportLimit.ELEVATED_WATER), dry.stream().map(ExportDiagnostic::limit).toList());
    }

    @Test
    public void limitOrderIgnoresAreaOrder() {
        IrisHydrology hydrology = withLava(overworldHydrology()).setClimate(new IrisHydrologyClimate().setEnabled(true));
        hydrology.getTiers().getFirst().setDecorators(new KList<>(seagrass()));
        IrisRegion hot = region("hot", new IrisHydrologyPolicy().setRainfall(0.5).setShoreBiomes(new KList<>("hot/beach")).setShoreBiomeWidth(3));
        IrisRegion wet = region("wet", new IrisHydrologyPolicy().setRainfall(2.0));
        List<RiverCompiler.Area> areas = List.of(
                new RiverCompiler.Area(hot, List.of(biome("volcano", new IrisHydrologyPolicy().setFluid("lava")))),
                new RiverCompiler.Area(hot, List.of(biome("deep", new IrisHydrologyPolicy().setStreams(IrisHydrologyPermission.BLOCK)))),
                new RiverCompiler.Area(wet, List.of(biome("marsh", new IrisHydrologyPolicy().setDecorators(new KList<>(seagrass()))))),
                new RiverCompiler.Area(wet, List.of(biome("plains", null))));
        List<RiverCompiler.Area> reversed = new ArrayList<>(areas);
        Collections.reverse(reversed);
        List<ExportDiagnostic> forward = new ArrayList<>();
        List<ExportDiagnostic> backward = new ArrayList<>();

        compile(new Field.Const(SEA_LEVEL + 2.0), hydrology, areas, forward);
        compile(new Field.Const(SEA_LEVEL + 2.0), hydrology, reversed, backward);

        assertEquals(forward, backward);
        assertEquals(List.of(ExportLimit.ELEVATED_WATER, ExportLimit.RIVER_DRAINAGE,
                ExportLimit.RIVER_FLUID,
                ExportLimit.RIVER_POLICY, ExportLimit.RIVER_POLICY, ExportLimit.RIVER_POLICY,
                ExportLimit.RIVER_CLIMATE,
                ExportLimit.RIVER_SHORE_BIOME,
                ExportLimit.RIVER_DECORATOR, ExportLimit.RIVER_DECORATOR,
                ExportLimit.SEA_INLET, ExportLimit.CAVE_POOL), forward.stream().map(ExportDiagnostic::limit).toList());
        assertEquals(List.of("regions/hot", "regions/wet", "biomes/deep"),
                limited(forward, ExportLimit.RIVER_POLICY).stream().map(ExportDiagnostic::resource).toList());
        assertEquals(List.of("dimensions/rivers/hydrology", "biomes/marsh"),
                limited(forward, ExportLimit.RIVER_DECORATOR).stream().map(ExportDiagnostic::resource).toList());
    }

    @Test
    public void expensiveChildrenOnlyBehindMuxOrGate() {
        IrisHydrology hydrology = hydrology(
                tier(900, 0.5, 2.5, 1, 8, IrisBankStyle.SMOOTH),
                tier(2000, 1.0, 4.0, 1, 12, IrisBankStyle.LINEAR),
                tier(5000, 2.5, 9.0, 2, 32, IrisBankStyle.CONCAVE),
                tier(20000, 3.0, 10.0, 2, 24, IrisBankStyle.TERRACED),
                tier(60000, 4.0, 12.0, 3, 16, IrisBankStyle.CLIFF),
                tier(200000, 9.0, 18.0, 3, 0, IrisBankStyle.SMOOTH));
        DataVersion target = DataVersion.V26_3;
        NoiseRegistry registry = registry();
        Field natural = rollingHeight(registry, 30.0);
        assertEquals(CostClass.EXPENSIVE, natural.cost());

        Field carved = compile(natural, hydrology, registry, target, new ArrayList<>());

        List<String> violations = TerrainTestSupport.unguardedExpensiveBranches(carved);
        assertTrue(target + " " + violations, violations.isEmpty());
    }

    @Test
    public void riverCompileIsDeterministic() {
        List<Field> fields = new ArrayList<>();
        List<List<NoiseDef>> definitions = new ArrayList<>();
        List<Double> samples = new ArrayList<>();
        for (int run = 0; run < 2; run++) {
            NoiseRegistry registry = registry();
            Field carved = compile(rollingHeight(registry, 30.0), overworldHydrology(), registry, DataVersion.V26_3, new ArrayList<>());
            fields.add(carved);
            definitions.add(registry.definitions());
            samples.add(sample(carved, TerrainTestSupport.noises(registry), DataVersion.V26_3, 137, -411));
        }

        assertEquals(fields.getFirst(), fields.getLast());
        assertEquals(definitions.getFirst(), definitions.getLast());
        assertEquals(samples.getFirst(), samples.getLast());
    }

    private static Field compile(Field natural, IrisHydrology hydrology, NoiseRegistry registry, DataVersion target, List<ExportDiagnostic> diagnostics) {
        return compile(natural, dimension(hydrology, null), List.of(new RiverCompiler.Area(region("region", null), List.of(biome("plains", null)))),
                registry, target, diagnostics);
    }

    private static Field compile(Field natural, IrisHydrology hydrology, List<RiverCompiler.Area> areas, List<ExportDiagnostic> diagnostics) {
        return compile(natural, dimension(hydrology, null), areas, registry(), DataVersion.V26_3, diagnostics);
    }

    private static Field compile(Field natural, IrisDimension dimension, List<RiverCompiler.Area> areas, NoiseRegistry registry, DataVersion target,
                                 List<ExportDiagnostic> diagnostics) {
        return compile(natural, dimension, () -> areas, registry, target, diagnostics);
    }

    private static Field compile(Field natural, IrisDimension dimension, Supplier<List<RiverCompiler.Area>> areas, NoiseRegistry registry,
                                 DataVersion target, List<ExportDiagnostic> diagnostics) {
        return new RiverCompiler(diagnostics).compile(natural, dimension, areas, registry, ExportCapabilities.forTarget(target));
    }

    private static IrisDimension dimension(IrisHydrology hydrology, IrisHydrologyPolicy policy) {
        IrisDimension dimension = new IrisDimension();
        dimension.setLoadKey("rivers");
        dimension.setFluidHeight(SEA_LEVEL);
        dimension.setHydrology(hydrology);
        dimension.setHydrologyPolicy(policy);
        return dimension;
    }

    private static IrisRegion region(String key, IrisHydrologyPolicy policy) {
        IrisRegion region = new IrisRegion();
        region.setLoadKey(key);
        region.setHydrologyPolicy(policy);
        return region;
    }

    private static IrisBiome biome(String key, IrisHydrologyPolicy policy) {
        IrisBiome biome = new IrisBiome();
        biome.setLoadKey(key);
        biome.setHydrologyPolicy(policy);
        return biome;
    }

    private static List<ExportDiagnostic> limited(List<ExportDiagnostic> diagnostics, ExportLimit limit) {
        return diagnostics.stream().filter(diagnostic -> diagnostic.limit() == limit).toList();
    }

    private static IrisDecorator seagrass() {
        return new IrisDecorator().setPartOf(IrisDecorationPart.SEA_FLOOR).setPalette(new KList<>(new IrisBlockData("minecraft:seagrass")));
    }

    private static IrisHydrologyUnderground underground(boolean enabled) {
        IrisHydrologyUnderground underground = new IrisHydrologyUnderground();
        underground.getSeaInlets().setEnabled(enabled);
        underground.getCavePools().setEnabled(enabled);
        return underground;
    }

    private static IrisHydrology withLava(IrisHydrology hydrology) {
        IrisFluidProfile lava = new IrisFluidProfile().setName("lava").setPalette(new IrisMaterialPalette().qclear().qadd("minecraft:lava"));
        return hydrology.setFluids(new KList<>(new IrisFluidProfile(), lava));
    }

    private static double sample(Field field, Map<String, NoiseDef> noises, DataVersion target, int x, int z) {
        return FieldEvaluator.sample(field, noises, TerrainTestSupport.SEED, target, x, 0, z);
    }

    private static NoiseRegistry registry() {
        return new NoiseRegistry(TerrainTestSupport.NAMESPACE);
    }

    private static Field rollingHeight(NoiseRegistry registry, double amplitude) {
        String warp = registry.register(new NoiseDef("test/height_warp", -8, List.of(1.0)));
        String hills = registry.register(new NoiseDef("test/height", -7, List.of(1.0, 0.5)));
        Field shift = FieldMacros.scale(new Field.Noise(warp, 1.0, 0.0, null, null, null), 0.5);
        Field noise = new Field.Noise(hills, 1.0, 0.0, shift, null, shift);
        return new Field.Shared(HeightCompiler.HEIGHT, FieldMacros.sum(List.of(new Field.Const(SEA_LEVEL), FieldMacros.scale(noise, amplitude))));
    }

    private static IrisHydrology overworldHydrology() {
        return hydrology(
                tier(900, 0.5, 2.5, 1, 8, IrisBankStyle.SMOOTH).setDepthRatio(0.4),
                tier(5000, 2.5, 9.0, 2, 32, IrisBankStyle.SMOOTH),
                tier(200000, 9.0, 18.0, 3, 48, IrisBankStyle.SMOOTH).setDepthRatio(0.5));
    }

    private static IrisHydrology hydrology(IrisHydrologyTier... tiers) {
        return new IrisHydrology()
                .setEnabled(true)
                .setDensity(0.5)
                .setTiers(new KList<>(tiers))
                .setDeformation(new IrisHydrologyDeformation().setBankSlope(0.2).setBankSteepFactor(3.5).setBankNoise(0.2));
    }

    private static IrisHydrologyTier tier(double catchment, double minHalfWidth, double maxHalfWidth, int inset, int bankBand, IrisBankStyle style) {
        return new IrisHydrologyTier()
                .setName("tier_" + (long) catchment)
                .setMinCatchment(catchment)
                .setHalfWidth(new KList<>(minHalfWidth, maxHalfWidth))
                .setWidthFactor(1.2)
                .setDepthRatio(0.45)
                .setInset(inset)
                .setBankBand(bankBand)
                .setBankStyle(style);
    }
}
