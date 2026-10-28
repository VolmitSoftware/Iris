package art.arcane.iris.engine.modifier;

import art.arcane.iris.modules.deposit.DepositGenerator;
import art.arcane.iris.modules.deposit.OreFrequencySettings;
import art.arcane.iris.modules.deposit.OreSizeSettings;
import art.arcane.iris.engine.modifier.ResolvedDeposit.BiomeIdentity;
import art.arcane.volmlib.util.math.RNG;
import com.google.gson.Gson;
import com.volmit.block.BlockRegistry;
import com.volmit.config.Desc;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** A deposit generator made ready for an engine: its clump shapes, ore tuning, replaceable blocks and biome filters. */
public class ResolvedDepositTest {
    private static final long DEPOSIT_SEED = 41L;

    private static final BlockRegistry BLOCKS = ApiChunk.blockRegistry();

    @Test
    public void thePaletteResolvesThroughThePacksBlocks() {
        ResolvedDeposit deposit = new ResolvedDeposit(generator("{\"minSize\": 1, \"maxSize\": 1, \"palette\": [{\"block\": \"iron_ore\"}]}"), DEPOSIT_SEED, BLOCKS);
        DepositClump clump = deposit.clump(new RNG(3));

        List<String> keys = new java.util.ArrayList<>();
        clump.blocks().values().forEachRemaining(block -> keys.add(block.key()));
        assertEquals(List.of("minecraft:iron_ore"), keys);
    }

    @Test
    public void everyDepositEnumAndValueHasSchemaDescription() throws ReflectiveOperationException {
        for (Field modelField : DepositGenerator.class.getDeclaredFields()) {
            Class<?> enumType = modelField.getType();
            if (!enumType.isEnum()) {
                continue;
            }

            Desc typeDescription = enumType.getAnnotation(Desc.class);
            assertNotNull(enumType.getSimpleName(), typeDescription);
            assertFalse(typeDescription.value().isBlank());
            for (Object constant : enumType.getEnumConstants()) {
                String constantName = ((Enum<?>) constant).name();
                Desc constantDescription = enumType.getField(constantName).getAnnotation(Desc.class);
                assertNotNull(enumType.getSimpleName() + "." + constantName, constantDescription);
                assertFalse(constantDescription.value().isBlank());
            }
        }
    }

    @Test
    public void depositSizesScaleAndRemainWithinSchemaLimit() {
        assertEquals(8, ResolvedDeposit.scaledDepositSize(4, 2D));
        assertEquals(16, ResolvedDeposit.scaledDepositSize(8, 2D));
        assertEquals(0, ResolvedDeposit.scaledDepositSize(0, 2D));
        assertEquals(8192, ResolvedDeposit.scaledDepositSize(8192, 2D));
    }

    @Test
    public void biomeOreTuningDefaultsPreserveExistingGeneration() {
        assertEquals(1D, new OreFrequencySettings().multiplier(), 0D);
        assertEquals(1D, new OreSizeSettings().multiplier(), 0D);
    }

    @Test
    public void clumpShapesAreBuiltOncePerSizeFromTheWorldsDepositSeed() {
        DepositGenerator generator = generator("{\"minSize\": 4, \"maxSize\": 8, \"varience\": 1}");
        ResolvedDeposit firstWorld = new ResolvedDeposit(withPalette(generator, "granite"), DEPOSIT_SEED, BLOCKS);
        ResolvedDeposit secondWorld = new ResolvedDeposit(withPalette(generator, "granite"), DEPOSIT_SEED + 1, BLOCKS);

        assertTrue("one variant: every pick is the same shape", firstWorld.clump(new RNG(1)) == firstWorld.clump(new RNG(2)));
        assertNotEquals(firstWorld.clump(new RNG(1)).blocks().keys().toString(), secondWorld.clump(new RNG(1)).blocks().keys().toString());
        assertTrue("scaled sizes are their own shapes", firstWorld.clump(new RNG(1)) != firstWorld.clump(new RNG(1), 2D));
    }

    @Test
    public void clumpSaltIsStableAcrossEquivalentConfigInstances() {
        ResolvedDeposit first = new ResolvedDeposit(withPalette(new DepositGenerator(), "granite"), DEPOSIT_SEED, BLOCKS);
        ResolvedDeposit second = new ResolvedDeposit(withPalette(new DepositGenerator(), "granite"), DEPOSIT_SEED, BLOCKS);

        assertEquals(first.stableClumpSalt(), second.stableClumpSalt());
    }

    @Test
    public void clumpSaltIncludesAuthoredConfigAndPalette() {
        ResolvedDeposit granite = new ResolvedDeposit(withPalette(new DepositGenerator(), "granite"), DEPOSIT_SEED, BLOCKS);
        ResolvedDeposit andesite = new ResolvedDeposit(withPalette(new DepositGenerator(), "andesite"), DEPOSIT_SEED, BLOCKS);
        ResolvedDeposit alteredShape = new ResolvedDeposit(withPalette(generator("{\"shape\": \"VANILLA_SCATTERED\"}"), "granite"), DEPOSIT_SEED, BLOCKS);

        assertEquals("the salt Iris deposits always had", 3112546198474861350L, granite.stableClumpSalt());
        assertNotEquals(granite.stableClumpSalt(), andesite.stableClumpSalt());
        assertNotEquals(granite.stableClumpSalt(), alteredShape.stableClumpSalt());
    }

    @Test
    public void onlyOreDepositPalettesReceiveBiomeTuning() {
        assertTrue(new ResolvedDeposit(withPalette(new DepositGenerator(), "iron_ore"), DEPOSIT_SEED, BLOCKS).isOre());
        assertFalse(new ResolvedDeposit(withPalette(new DepositGenerator(), "stone"), DEPOSIT_SEED, BLOCKS).isOre());
    }

    @Test
    public void vanillaEllipsoidSizeControlsGeometryRatherThanExactBlockCount() {
        ResolvedDeposit deposit = new ResolvedDeposit(withPalette(new DepositGenerator(), "iron_ore"), DEPOSIT_SEED, BLOCKS);
        long smallBlocks = 0L;
        long mediumBlocks = 0L;
        long largeBlocks = 0L;
        int samples = 500;
        for (int i = 0; i < samples; i++) {
            smallBlocks += deposit.vanillaEllipsoid(new RNG(i), 4).blocks().size();
            mediumBlocks += deposit.vanillaEllipsoid(new RNG(i), 8).blocks().size();
            largeBlocks += deposit.vanillaEllipsoid(new RNG(i), 17).blocks().size();
        }

        assertTrue(smallBlocks > 0L);
        assertTrue(mediumBlocks > smallBlocks);
        assertTrue(largeBlocks > mediumBlocks);
        assertNotEquals(17L * samples, largeBlocks);
    }

    @Test
    public void vanillaScatteredSizeIsAnUpperCandidateBound() {
        ResolvedDeposit deposit = new ResolvedDeposit(withPalette(new DepositGenerator(), "iron_ore"), DEPOSIT_SEED, BLOCKS);
        boolean foundEmpty = false;
        boolean foundNonEmpty = false;
        RNG rng = new RNG(991L);
        for (int i = 0; i < 200; i++) {
            int blocks = deposit.vanillaScattered(rng, 3).blocks().size();
            assertTrue(blocks <= 3);
            foundEmpty |= blocks == 0;
            foundNonEmpty |= blocks > 0;
        }

        assertTrue(foundEmpty);
        assertTrue(foundNonEmpty);
    }

    @Test
    public void biomeFiltersAcceptResourceKeysAndVanillaDerivatives() {
        BiomeIdentity mountain = new BiomeIdentity("custom/mountain", "minecraft:stony_peaks", "minecraft:stony_peaks");
        BiomeIdentity plains = new BiomeIdentity("custom/plains", "minecraft:plains", "minecraft:plains");

        DepositGenerator included = generator("{\"biomeScope\": \"SURFACE\", \"includedBiomes\": [\"minecraft:stony_peaks\"]}");
        ResolvedDeposit surface = new ResolvedDeposit(included, DEPOSIT_SEED, BLOCKS);
        assertTrue(surface.matchesBiome(() -> mountain, () -> plains));
        assertFalse(surface.matchesBiome(() -> plains, () -> mountain));
        assertFalse(included.usesCaveBiomeFilter());

        DepositGenerator excluded = generator("{\"biomeScope\": \"SURFACE\", \"excludedBiomes\": [\"custom/mountain\"]}");
        ResolvedDeposit excluding = new ResolvedDeposit(excluded, DEPOSIT_SEED, BLOCKS);
        assertFalse(excluding.matchesBiome(() -> mountain, () -> plains));
        assertTrue(excluding.matchesBiome(() -> plains, () -> mountain));

        assertTrue(generator("{\"excludedBiomes\": [\"custom/mountain\"]}").usesCaveBiomeFilter());
    }

    @Test
    public void aBiomeThatIsNoneMatchesNoKey() {
        ResolvedDeposit including = new ResolvedDeposit(generator("{\"biomeScope\": \"CAVE\", \"includedBiomes\": [\"carving/drip\"]}"), DEPOSIT_SEED, BLOCKS);
        ResolvedDeposit excluding = new ResolvedDeposit(generator("{\"biomeScope\": \"CAVE\", \"excludedBiomes\": [\"carving/drip\"]}"), DEPOSIT_SEED, BLOCKS);

        assertFalse(including.matchesBiome(() -> null, () -> null));
        assertTrue(excluding.matchesBiome(() -> null, () -> null));
    }

    @Test
    public void noFilterAsksNoBiome() {
        ResolvedDeposit unfiltered = new ResolvedDeposit(new DepositGenerator(), DEPOSIT_SEED, BLOCKS);

        assertTrue(unfiltered.matchesBiome(() -> {
            throw new AssertionError("The surface biome is asked for only when a filter reads it");
        }, () -> {
            throw new AssertionError("The cave biome is asked for only when a filter reads it");
        }));
    }

    @Test
    public void onlyTheBlocksTheServerKnowsAreReplaceable() {
        ResolvedDeposit deposit = new ResolvedDeposit(generator("{\"replaceableBlocks\": [\"stone\", \"" + ApiChunk.UNKNOWN_BLOCK + "\"]}"), DEPOSIT_SEED, BLOCKS);

        assertTrue(deposit.canReplace(new com.volmit.block.BlockState("minecraft:stone")));
        assertFalse(deposit.canReplace(new com.volmit.block.BlockState("minecraft:dirt")));
        assertTrue("a deposit that names no blocks replaces any", new ResolvedDeposit(new DepositGenerator(), DEPOSIT_SEED, BLOCKS)
                .canReplace(new com.volmit.block.BlockState("minecraft:dirt")));
    }

    private static DepositGenerator generator(String json) {
        return new Gson().fromJson(json, DepositGenerator.class);
    }

    /** {@code generator} with a palette of {@code block}, as its JSON would have it. */
    private static DepositGenerator withPalette(DepositGenerator generator, String block) {
        return art.arcane.iris.engine.object.TestSettings.with(generator, "palette", List.of(com.volmit.block.BlockSpec.of(block)));
    }
}
