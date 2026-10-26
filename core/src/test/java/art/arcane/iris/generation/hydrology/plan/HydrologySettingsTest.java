package art.arcane.iris.generation.hydrology.plan;

import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.generation.decoration.IrisDecorationPart;
import art.arcane.iris.generation.decoration.IrisDecorator;
import art.arcane.iris.generation.hydrology.config.IrisHydrology;
import art.arcane.iris.generation.hydrology.config.IrisHydrologyMaterials;
import art.arcane.iris.generation.hydrology.water.HydrologyTier;
import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.util.collection.KList;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class HydrologySettingsTest {
    private static final long SEED = 1337L;
    private static final int SEA_LEVEL = 62;
    private static final int MIN_Y = -64;
    private static final int MAX_Y = 320;
    private static final long DEFAULT_FINGERPRINT = 8818472145657699942L;

    @Test
    public void densityIsBoundedAndChangesThePlanIdentity() {
        HydrologySettings full = compile(new IrisHydrology());
        HydrologySettings half = compile(new IrisHydrology().setDensity(0.5D));
        assertEquals(1f, full.density(), 0f);
        assertEquals(0.5f, half.density(), 0f);
        assertTrue(full.fingerprint() != half.fingerprint());
        assertEquals(full.tiers(), half.tiers());
        for (double invalid : new double[]{-0.01D, 1.01D, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertRejected(new IrisHydrology().setDensity(invalid), "density");
        }
    }

    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void tierForPicksHighestTierBelowCatchment() {
        HydrologySettings settings = compile(new IrisHydrology());
        assertEquals("stream", settings.tierFor(1500f).name());
        assertEquals("stream", settings.tierFor(19_999f).name());
        assertEquals("river", settings.tierFor(20_000f).name());
        assertEquals("river", settings.tierFor(319_999f).name());
        assertEquals("major", settings.tierFor(320_000f).name());
        assertEquals("major", settings.tierFor(4_194_304f).name());
        assertEquals("stream", settings.tierFor(10f).name());
    }

    @Test
    public void tiersCompileInCatchmentOrder() {
        IrisHydrology reversed = new IrisHydrology();
        Collections.reverse(reversed.getTiers());
        HydrologySettings settings = compile(reversed);
        assertEquals(List.of("stream", "river", "major"), names(settings.tiers()));
        assertEquals(compile(new IrisHydrology()).fingerprint(), settings.fingerprint());
    }

    @Test
    public void majorTierIsHighestCatchmentTier() {
        IrisHydrology reversed = new IrisHydrology();
        Collections.reverse(reversed.getTiers());
        assertEquals("major", compile(reversed).majorTier().name());
        IrisHydrology single = new IrisHydrology();
        single.getTiers().removeIf(tier -> !"stream".equals(tier.getName()));
        assertEquals("stream", compile(single).majorTier().name());
    }

    @Test
    public void halfWidthFollowsSqrtCatchmentAndClamps() {
        HydrologySettings settings = compile(new IrisHydrology());
        HydrologyTier stream = settings.tiers().get(0);
        HydrologyTier river = settings.tiers().get(1);
        assertEquals(0.5f + (float) Math.sqrt(0.6D), stream.halfWidth(1500f), 1e-5f);
        assertEquals(2f, river.halfWidth(0f), 0f);
        assertEquals(2f, river.halfWidth(-50f), 0f);
        assertEquals(2f + (float) Math.sqrt(8D), river.halfWidth(20_000f), 1e-5f);
        assertEquals(8f, river.halfWidth(90_000f), 1e-5f);
        assertEquals(8f, river.halfWidth(1_000_000f), 0f);

        IrisHydrology wide = new IrisHydrology();
        wide.getTiers().get(1).setWidthFactor(2D);
        HydrologyTier wideRiver = compile(wide).tiers().get(1);
        assertEquals(6f, wideRiver.halfWidth(10_000f), 1e-5f);
        assertEquals(8f, wideRiver.halfWidth(40_000f), 1e-5f);
    }

    @Test
    public void depthScalesHalfWidthWithOneBlockFloor() {
        HydrologySettings settings = compile(new IrisHydrology());
        assertEquals(1f, settings.tiers().get(0).depth(2f), 0f);
        assertEquals(1.8f, settings.tiers().get(1).depth(4f), 1e-5f);
        assertEquals(8f, settings.tiers().get(2).depth(16f), 1e-5f);
    }

    @Test
    public void fingerprintChangesWhenTierPondOrDrainageFieldChanges() {
        List<Long> fingerprints = new ArrayList<>();
        fingerprints.add(compile(new IrisHydrology()).fingerprint());

        IrisHydrology tierInset = new IrisHydrology();
        tierInset.getTiers().get(1).setInset(3);
        fingerprints.add(compile(tierInset).fingerprint());

        IrisHydrology tierName = new IrisHydrology();
        tierName.getTiers().get(0).setName("creek");
        fingerprints.add(compile(tierName).fingerprint());

        IrisHydrology tierHalfWidth = new IrisHydrology();
        tierHalfWidth.getTiers().get(2).setHalfWidth(new KList<>(8D, 20D));
        fingerprints.add(compile(tierHalfWidth).fingerprint());

        IrisHydrology tierMaterials = new IrisHydrology();
        tierMaterials.getTiers().get(1).getMaterials().setBed(new IrisMaterialPalette().qclear().qadd("minecraft:gravel"));
        fingerprints.add(compile(tierMaterials).fingerprint());

        IrisHydrology pondDensity = new IrisHydrology();
        pondDensity.getPonds().getSeeded().setDensity(5D);
        fingerprints.add(compile(pondDensity).fingerprint());

        IrisHydrology pondFreeboard = new IrisHydrology();
        pondFreeboard.getPonds().setFreeboard(new KList<>(1, 4));
        fingerprints.add(compile(pondFreeboard).fingerprint());

        IrisHydrology pondDams = new IrisHydrology();
        pondDams.getPonds().getDams().setEnabled(true);
        fingerprints.add(compile(pondDams).fingerprint());

        IrisHydrology drainageBreach = new IrisHydrology();
        drainageBreach.getDrainage().setBreachDepth(25);
        fingerprints.add(compile(drainageBreach).fingerprint());

        IrisHydrology drainageThinning = new IrisHydrology();
        drainageThinning.getDrainage().setWetnessThinning(false);
        fingerprints.add(compile(drainageThinning).fingerprint());

        IrisHydrology drainageRelief = new IrisHydrology();
        drainageRelief.getDrainage().setHeadRelief(2D);
        fingerprints.add(compile(drainageRelief).fingerprint());

        IrisHydrology fluidPalette = new IrisHydrology();
        fluidPalette.getFluids().get(0).getPalette().qclear().qadd("minecraft:lava");
        fingerprints.add(compile(fluidPalette).fingerprint());

        IrisHydrology cavePools = new IrisHydrology();
        cavePools.getUnderground().getCavePools().setMaxDepth(5);
        fingerprints.add(compile(cavePools).fingerprint());

        IrisHydrology lakes = new IrisHydrology();
        lakes.getLakes().setMinArea(131072);
        fingerprints.add(compile(lakes).fingerprint());

        IrisHydrology deformation = new IrisHydrology();
        deformation.getDeformation().setBankNoise(0.3D);
        fingerprints.add(compile(deformation).fingerprint());

        IrisHydrology seaInlets = new IrisHydrology();
        seaInlets.getUnderground().getSeaInlets().setSpacing(256);
        fingerprints.add(compile(seaInlets).fingerprint());

        IrisHydrology shoreDepth = new IrisHydrology();
        shoreDepth.getPonds().getMaterials().setShoreDepth(3);
        fingerprints.add(compile(shoreDepth).fingerprint());

        IrisHydrology gravityBlocks = new IrisHydrology();
        gravityBlocks.getTiers().get(0).getMaterials().setAllowGravityBlocks(false);
        fingerprints.add(compile(gravityBlocks).fingerprint());

        fingerprints.add(HydrologySettings.compile(new IrisHydrology(), SEED + 1L, SEA_LEVEL, MIN_Y, MAX_Y).fingerprint());
        fingerprints.add(HydrologySettings.compile(new IrisHydrology(), SEED, SEA_LEVEL + 1, MIN_Y, MAX_Y).fingerprint());

        Set<Long> distinct = new HashSet<>(fingerprints);
        assertEquals(fingerprints.size(), distinct.size());
    }

    @Test
    public void fingerprintStableAcrossCompiles() {
        long first = compile(new IrisHydrology()).fingerprint();
        long second = compile(new IrisHydrology()).fingerprint();
        assertEquals(first, second);
        assertEquals("fingerprint must be identical in every JVM run", DEFAULT_FINGERPRINT, first);
    }

    @Test
    public void compileRejectsUnknownFluidProfile() {
        IrisHydrology config = new IrisHydrology();
        config.getTiers().get(2).setFluid("lava");
        assertRejected(config, "lava");
    }

    @Test
    public void compileRejectsMalformedRange() {
        IrisHydrology inverted = new IrisHydrology();
        inverted.getTiers().get(1).setHalfWidth(new KList<>(8D, 2D));
        assertRejected(inverted, "tiers[1].halfWidth");

        IrisHydrology threeValues = new IrisHydrology();
        threeValues.getPonds().getSeeded().setRadius(new KList<>(6, 12, 24));
        assertRejected(threeValues, "ponds.seeded.radius");

        IrisHydrology zeroWidth = new IrisHydrology();
        zeroWidth.getTiers().get(0).setHalfWidth(new KList<>(0D, 2D));
        assertRejected(zeroWidth, "tiers[0].halfWidth");

        IrisHydrology zeroRadius = new IrisHydrology();
        zeroRadius.getPonds().getSeeded().setRadius(new KList<>(0, 24));
        assertRejected(zeroRadius, "ponds.seeded.radius");

        IrisHydrology negativeFreeboard = new IrisHydrology();
        negativeFreeboard.getPonds().setFreeboard(new KList<>(-1, 3));
        assertRejected(negativeFreeboard, "ponds.freeboard");

        IrisHydrology negativeCaveFreeboard = new IrisHydrology();
        negativeCaveFreeboard.getUnderground().getCavePools().setFreeboard(new KList<>(-2, 1));
        assertRejected(negativeCaveFreeboard, "underground.cavePools.freeboard");

        IrisHydrology zeroFreeboard = new IrisHydrology();
        zeroFreeboard.getPonds().setFreeboard(new KList<>(0, 0));
        assertEquals(0, compile(zeroFreeboard).ponds().freeboard().min());
    }

    @Test
    public void compileRejectsInvertedAreaPairs() {
        IrisHydrology lakes = new IrisHydrology();
        lakes.getLakes().setMinArea(5_000_000);
        assertRejected(lakes, "lakes.minArea");

        IrisHydrology natural = new IrisHydrology();
        natural.getPonds().getNatural().setMaxArea(128);
        assertRejected(natural, "ponds.natural.minArea");

        IrisHydrology cavePools = new IrisHydrology();
        cavePools.getUnderground().getCavePools().setMinArea(500);
        assertRejected(cavePools, "underground.cavePools.minArea");
    }

    @Test
    public void compileRejectsCavePoolsThatReachPastTheNeighbouringChunks() {
        IrisHydrology wide = new IrisHydrology();
        wide.getUnderground().getCavePools().setRadius(HydrologySettings.MAX_CAVE_POOL_RADIUS + 1);
        assertRejected(wide, "underground.cavePools.radius");

        IrisHydrology flat = new IrisHydrology();
        flat.getUnderground().getCavePools().setMaxDepth(0);
        assertRejected(flat, "underground.cavePools.maxDepth");

        IrisHydrology widest = new IrisHydrology();
        widest.getUnderground().getCavePools().setRadius(HydrologySettings.MAX_CAVE_POOL_RADIUS);
        compile(widest);

        IrisHydrology disabled = new IrisHydrology();
        disabled.getUnderground().getCavePools().setEnabled(false).setRadius(64).setMaxDepth(0);
        compile(disabled);
    }

    @Test
    public void compileRejectsNegativeBankBand() {
        IrisHydrology config = new IrisHydrology();
        config.getTiers().get(1).setBankBand(-1);
        assertRejected(config, "tiers[1].bankBand");
    }

    @Test
    public void compileRejectsBankBandAbove64() {
        IrisHydrology config = new IrisHydrology();
        config.getTiers().get(1).setBankBand(65);
        assertRejected(config, "tiers[1].bankBand");

        IrisHydrology widest = new IrisHydrology();
        widest.getTiers().get(1).setBankBand(64);
        assertEquals(64, compile(widest).tiers().get(1).bankBand());
    }

    @Test
    public void compileRejectsHalfWidthAbove64() {
        IrisHydrology config = new IrisHydrology();
        config.getTiers().get(2).setHalfWidth(new KList<>(8D, 65D));
        assertRejected(config, "tiers[2].halfWidth");

        IrisHydrology widest = new IrisHydrology();
        widest.getTiers().get(2).setHalfWidth(new KList<>(8D, 64D));
        assertEquals(64f, compile(widest).tiers().get(2).maxHalfWidth(), 0f);
    }

    @Test
    public void compileRejectsDuplicateTierCatchment() {
        IrisHydrology config = new IrisHydrology();
        config.getTiers().get(2).setMinCatchment(20000D);
        assertRejected(config, "minCatchment");
    }

    @Test
    public void compileRejectsFluidWithoutPalette() throws Exception {
        IrisData data = IrisData.openDatapackCompiler(temporary.newFolder("pack"));
        try {
            IrisHydrology stringPalette = data.getGson().fromJson(
                    "{\"fluids\":[{\"name\":\"water\",\"palette\":\"minecraft:water\"}]}", IrisHydrology.class);
            assertNull(stringPalette.getFluids().get(0).getPalette());
            assertRejected(stringPalette, "fluids[0].palette");

            IrisHydrology emptyPalette = data.getGson().fromJson(
                    "{\"fluids\":[{\"name\":\"water\",\"palette\":{\"palette\":[]}}]}", IrisHydrology.class);
            assertRejected(emptyPalette, "fluids[0].palette");
        } finally {
            data.close();
        }
    }

    @Test
    public void compileRejectsEmptyOrMisspeltPaletteObjects() throws Exception {
        IrisData data = IrisData.openDatapackCompiler(temporary.newFolder("pack"));
        try {
            assertMissingBlocks(data.getGson().fromJson("{\"fluids\":[{\"name\":\"water\",\"palette\":{}}]}", IrisHydrology.class),
                    "fluids[0].palette");
            assertMissingBlocks(data.getGson().fromJson(
                    "{\"fluids\":[{\"name\":\"water\",\"palette\":{\"blocks\":[{\"block\":\"minecraft:water\"}]}}]}",
                    IrisHydrology.class), "fluids[0].palette");
            IrisHydrology tierBed = new IrisHydrology();
            tierBed.getTiers().get(2).setMaterials(data.getGson().fromJson("{\"bed\":{}}",
                    IrisHydrologyMaterials.class));
            assertMissingBlocks(tierBed, "tiers[2].materials.bed");
            IrisHydrology pondShore = new IrisHydrology();
            pondShore.getPonds().setMaterials(data.getGson().fromJson("{\"shore\":{\"palete\":[{\"block\":\"minecraft:sand\"}]}}",
                    IrisHydrologyMaterials.class));
            assertMissingBlocks(pondShore, "ponds.materials.shore");
            IrisHydrology pondBank = new IrisHydrology();
            pondBank.getPonds().getMaterials().setBank(new IrisMaterialPalette().qclear());
            assertMissingBlocks(pondBank, "ponds.materials.bank");
            IrisHydrology unsetBank = new IrisHydrology();
            unsetBank.getPonds().getMaterials().setBank(new IrisMaterialPalette());
            assertMissingBlocks(unsetBank, "ponds.materials.bank");
            IrisHydrology emptyList = new IrisHydrology();
            emptyList.getTiers().get(0).setMaterials(data.getGson().fromJson("{\"shore\":{\"palette\":[]}}", IrisHydrologyMaterials.class));
            assertMissingBlocks(emptyList, "tiers[0].materials.shore");

            for (String stone : List.of("STONE", "stone", "Stone", "minecraft:stone")) {
                IrisHydrology written = new IrisHydrology();
                written.getPonds().setMaterials(data.getGson().fromJson("{\"bed\":{\"palette\":[{\"block\":\"" + stone + "\"}]}}",
                        IrisHydrologyMaterials.class));
                written.getTiers().get(1).setMaterials(data.getGson().fromJson("{\"bank\":{\"palette\":[{\"block\":\"" + stone + "\"}]}}",
                        IrisHydrologyMaterials.class));
                written.getFluids().get(0).setPalette(data.getGson().fromJson("{\"palette\":[{\"block\":\"" + stone + "\"}]}",
                        IrisMaterialPalette.class));
                HydrologySettings settings = compile(written);
                assertEquals(stone, settings.ponds().materials().getBed().getPalette().get(0).getBlock());
                assertEquals(stone, settings.tiers().get(1).materials().getBank().getPalette().get(0).getBlock());
                assertEquals(List.of(stone), blocks(settings.fluids().get("water")));
            }
        } finally {
            data.close();
        }
    }

    @Test
    public void compiledSettingsKeepTheirOwnCopyOfMaterialsAndPalettes() {
        IrisHydrology config = new IrisHydrology();
        config.getTiers().get(1).getMaterials().setBed(new IrisMaterialPalette().qclear().qadd("minecraft:gravel"));
        config.getPonds().getMaterials().setShore(new IrisMaterialPalette().qclear().qadd("minecraft:sand"));
        HydrologySettings settings = compile(config);

        config.getTiers().get(1).getMaterials().getBed().qadd("minecraft:dirt");
        config.getTiers().get(1).getMaterials().setShoreDepth(7);
        config.getPonds().getMaterials().getShore().qclear().qadd("minecraft:red_sand");
        config.getFluids().get(0).getPalette().qclear().qadd("minecraft:lava");

        HydrologyTier river = settings.tiers().get(1);
        assertEquals(List.of("minecraft:gravel"), blocks(river.materials().getBed()));
        assertEquals(2, river.materials().getShoreDepth());
        assertEquals(List.of("minecraft:sand"), blocks(settings.ponds().materials().getShore()));
        assertEquals(List.of("minecraft:water"), blocks(settings.fluids().get("water")));
        assertNotSame(config.getTiers().get(1).getMaterials(), river.materials());
        assertNotSame(config.getFluids().get(0).getPalette(), settings.fluids().get("water"));
    }

    @Test
    public void waterDecoratorsCompileAsTheirOwnCopiesWithoutChangingTheFingerprint() throws Exception {
        IrisData data = IrisData.openDatapackCompiler(temporary.newFolder("decorators"));
        try {
            IrisHydrology config = data.getGson().fromJson("""
                    {"tiers": [{"name": "stream", "decorators": [
                        {"partOf": "SEA_FLOOR", "chance": 0.2, "stackMin": 2, "stackMax": 6,
                         "palette": [{"block": "minecraft:kelp_plant"}], "topPalette": [{"block": "minecraft:kelp"}]},
                        {"partOf": "SHORE_LINE", "chance": 0.1, "palette": [{"block": "minecraft:sugar_cane", "data": {"age": 0}}]}]}],
                     "ponds": {"decorators": [{"partOf": "SEA_SURFACE", "chance": 0.05, "palette": [{"block": "minecraft:lily_pad"}]}]}}
                    """, IrisHydrology.class);
            HydrologySettings settings = compile(config);

            List<IrisDecorator> stream = settings.tiers().get(0).decorators();
            assertEquals(2, stream.size());
            assertEquals(IrisDecorationPart.SEA_FLOOR, stream.get(0).getPartOf());
            assertEquals("minecraft:kelp", stream.get(0).getTopPalette().get(0).getBlock());
            assertEquals("minecraft:sugar_cane[age=0]", stream.get(1).getPalette().get(0).stateKey());
            assertEquals(List.of(IrisDecorationPart.SEA_SURFACE), settings.ponds().decorators().stream().map(IrisDecorator::getPartOf).toList());
            assertEquals("minecraft:lily_pad", settings.ponds().decorators().get(0).getPalette().get(0).getBlock());
            assertNotSame(config.getTiers().get(0).getDecorators().get(0), stream.get(0));

            config.getTiers().get(0).getDecorators().get(0).setStackMax(40);
            config.getPonds().getDecorators().clear();
            assertEquals(6, stream.get(0).getStackMax());
            assertEquals(1, settings.ponds().decorators().size());
            assertEquals(List.of(), compile(new IrisHydrology()).ponds().decorators());
            assertEquals(List.of(), compile(new IrisHydrology()).tiers().get(2).decorators());
            config.getTiers().get(0).getDecorators().clear();
            assertEquals(settings.fingerprint(), compile(config).fingerprint());
            IrisHydrology planted = new IrisHydrology();
            planted.getPonds().setDecorators(new KList<>(decorator(IrisDecorationPart.SEA_SURFACE, "minecraft:lily_pad")));
            assertEquals(DEFAULT_FINGERPRINT, compile(planted).fingerprint());
        } finally {
            data.close();
        }
    }

    @Test
    public void anOmittedOrMisspeltDecoratorPaletteIsRejectedInsteadOfGrowingGrass() throws Exception {
        IrisData data = IrisData.openDatapackCompiler(temporary.newFolder("omitted"));
        try {
            for (String json : List.of("{\"partOf\": \"SHORE_LINE\"}", "{\"partOf\": \"SEA_SURFACE\", \"palete\": [{\"block\": \"minecraft:lily_pad\"}]}")) {
                IrisHydrology config = new IrisHydrology();
                config.getPonds().setDecorators(new KList<>(data.getGson().fromJson(json, IrisDecorator.class)));
                IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> compile(config));
                assertTrue(failure.getMessage(), failure.getMessage().startsWith("Hydrology ponds.decorators[0].palette is missing or misspelt"));
            }
        } finally {
            data.close();
        }
    }

    @Test
    public void compileRejectsWaterDecoratorsThatCannotGrowSafely() {
        assertDecoratorRejected(decorator(IrisDecorationPart.NONE, "minecraft:fern"), "tiers[1].decorators[0].partOf is NONE");
        assertDecoratorRejected(decorator(IrisDecorationPart.CEILING, "minecraft:fern"), "tiers[1].decorators[0].partOf is CEILING");
        assertDecoratorRejected(decorator(IrisDecorationPart.SHORE_LINE).setPalette(new KList<>()), "tiers[1].decorators[0].palette has no blocks");
        assertDecoratorRejected(decorator(IrisDecorationPart.SHORE_LINE).setPalette(null), "tiers[1].decorators[0].palette has no blocks");
        assertDecoratorRejected(decorator(IrisDecorationPart.SHORE_LINE, " "), "tiers[1].decorators[0].palette[0] names no block");
        assertDecoratorRejected(decorator(IrisDecorationPart.SEA_FLOOR, "minecraft:kelp_plant").setTopPalette(new KList<>(new IrisBlockData(""))),
                "tiers[1].decorators[0].topPalette[0] names no block");
        assertDecoratorRejected(decorator(IrisDecorationPart.SHORE_LINE, "minecraft:fern").setForceBlock(new IrisBlockData("minecraft:air")),
                "tiers[1].decorators[0].forceBlock is not allowed");
        assertDecoratorRejected(decorator(IrisDecorationPart.SHORE_LINE, "minecraft:fern").setChance(1.5D), "tiers[1].decorators[0].chance");
        assertDecoratorRejected(decorator(IrisDecorationPart.SEA_FLOOR, "minecraft:kelp_plant").setStackMin(4).setStackMax(2),
                "tiers[1].decorators[0] needs 1 <= stackMin <= stackMax");
        assertDecoratorRejected(decorator(IrisDecorationPart.SHORE_LINE, "grass"), "tiers[1].decorators[0].palette is missing or misspelt");
        KList<IrisDecorator> holed = new KList<>();
        holed.add((IrisDecorator) null);
        IrisHydrology ponds = new IrisHydrology();
        ponds.getPonds().setDecorators(holed);
        assertRejected(ponds, "ponds.decorators[0] is empty");
    }

    private static void assertDecoratorRejected(IrisDecorator decorator, String message) {
        IrisHydrology config = new IrisHydrology();
        config.getTiers().get(1).setDecorators(new KList<>(decorator));
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> compile(config));
        assertTrue(failure.getMessage(), failure.getMessage().startsWith("Hydrology " + message));
    }

    private static IrisDecorator decorator(IrisDecorationPart part, String... blocks) {
        KList<IrisBlockData> palette = new KList<>();
        for (String block : blocks) {
            palette.add(new IrisBlockData(block));
        }
        return new IrisDecorator().setPartOf(part).setPalette(palette);
    }

    private static void assertRejected(IrisHydrology config, String field) {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> compile(config));
        assertTrue(failure.getMessage(), failure.getMessage().contains(field));
    }

    @Test
    public void seaInletRangesAreBounded() {
        compile(inlets(new KList<>(1D, 16D), new KList<>(2, 16)));
        for (IrisHydrology rejected : List.of(inlets(new KList<>(0.5D, 5D), new KList<>(2, 5)), inlets(new KList<>(2D, 17D), new KList<>(2, 5)),
                inlets(new KList<>(2D, 5D), new KList<>(2, 17)), inlets(new KList<>(2D, 5D), new KList<>(1, 5)))) {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> compile(rejected));
            assertTrue(failure.getMessage(), failure.getMessage().contains("underground.seaInlets"));
        }
        IllegalArgumentException belowWorld = assertThrows(IllegalArgumentException.class,
                () -> HydrologySettings.compile(inlets(new KList<>(2D, 5D), new KList<>(2, 16)), SEED, MIN_Y + 18, MIN_Y, MAX_Y));
        assertTrue(belowWorld.getMessage(), belowWorld.getMessage().contains("world bottom"));
    }

    private static IrisHydrology inlets(KList<Double> halfWidth, KList<Integer> depth) {
        IrisHydrology config = new IrisHydrology();
        config.getUnderground().getSeaInlets().setHalfWidth(halfWidth).setDepth(depth);
        return config;
    }

    private static void assertMissingBlocks(IrisHydrology config, String field) {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> compile(config));
        assertTrue(failure.getMessage(), failure.getMessage().contains(field));
        assertTrue(failure.getMessage(), failure.getMessage().contains("\"palette\" list is missing, misspelt or empty"));
    }

    private static HydrologySettings compile(IrisHydrology config) {
        return HydrologySettings.compile(config, SEED, SEA_LEVEL, MIN_Y, MAX_Y);
    }

    private static List<String> blocks(IrisMaterialPalette palette) {
        List<String> blocks = new ArrayList<>();
        for (IrisBlockData block : palette.getPalette()) {
            blocks.add(block.getBlock());
        }
        return blocks;
    }

    private static List<String> names(List<HydrologyTier> tiers) {
        List<String> names = new ArrayList<>(tiers.size());
        for (HydrologyTier tier : tiers) {
            names.add(tier.name());
        }
        return names;
    }
}
