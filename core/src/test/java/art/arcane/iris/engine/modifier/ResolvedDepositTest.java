package art.arcane.iris.engine.modifier;

import art.arcane.iris.builtin.deposit.DepositGenerator;
import art.arcane.iris.builtin.deposit.OreFrequencySettings;
import art.arcane.iris.builtin.deposit.OreSizeSettings;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.core.modules.PackResourceFixture;
import art.arcane.iris.engine.UsableBlockPlatform;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.math.RNG;
import com.google.gson.Gson;
import com.volmit.config.Desc;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A deposit generator made ready for an engine: its clump shapes, ore tuning, replaceable blocks and biome filters. */
public class ResolvedDepositTest {
    private static final long DEPOSIT_SEED = 41L;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    @SuppressWarnings("unchecked")
    public void thePaletteResolvesThroughThePacksBlocks() throws Exception {
        UsableBlockPlatform.bind();
        try {
            IrisData data = mock(IrisData.class);
            when(data.getDataFolder()).thenReturn(folder.newFolder("pack"));
            when(data.getBlockLoader()).thenReturn(mock(ResourceLoader.class));
            PackResourceFixture.serve(data);
            Engine engine = mock(Engine.class);
            SeedManager seeds = mock(SeedManager.class);
            when(engine.getData()).thenReturn(data);
            when(engine.getSeedManager()).thenReturn(seeds);
            when(seeds.getDeposit()).thenReturn(DEPOSIT_SEED);

            ResolvedDeposit deposit = ResolvedDeposit.of(generator("{\"minSize\": 1, \"maxSize\": 1, \"palette\": [{\"block\": \"iron_ore\"}]}"), engine);
            IrisObject clump = deposit.clump(new RNG(3));

            List<String> keys = new java.util.ArrayList<>();
            clump.getBlocks().values().forEachRemaining(block -> keys.add(block.key()));
            assertEquals(List.of("minecraft:iron_ore"), keys);
        } finally {
            UsableBlockPlatform.unbind();
        }
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
        ResolvedDeposit firstWorld = new ResolvedDeposit(generator, DEPOSIT_SEED, List.of(state("minecraft:granite", false)));
        ResolvedDeposit secondWorld = new ResolvedDeposit(generator, DEPOSIT_SEED + 1, List.of(state("minecraft:granite", false)));

        assertTrue("one variant: every pick is the same shape", firstWorld.clump(new RNG(1)) == firstWorld.clump(new RNG(2)));
        assertNotEquals(firstWorld.clump(new RNG(1)).getBlocks().keys().toString(), secondWorld.clump(new RNG(1)).getBlocks().keys().toString());
        assertTrue("scaled sizes are their own shapes", firstWorld.clump(new RNG(1)) != firstWorld.clump(new RNG(1), 2D));
    }

    @Test
    public void clumpSaltIsStableAcrossEquivalentConfigInstances() {
        ResolvedDeposit first = new ResolvedDeposit(new DepositGenerator(), DEPOSIT_SEED, List.of(state("minecraft:granite", false)));
        ResolvedDeposit second = new ResolvedDeposit(new DepositGenerator(), DEPOSIT_SEED, List.of(state("minecraft:granite", false)));

        assertEquals(first.stableClumpSalt(), second.stableClumpSalt());
    }

    @Test
    public void clumpSaltIncludesAuthoredConfigAndPalette() {
        ResolvedDeposit granite = new ResolvedDeposit(new DepositGenerator(), DEPOSIT_SEED, List.of(state("minecraft:granite", false)));
        ResolvedDeposit andesite = new ResolvedDeposit(new DepositGenerator(), DEPOSIT_SEED, List.of(state("minecraft:andesite", false)));
        ResolvedDeposit alteredShape = new ResolvedDeposit(generator("{\"shape\": \"VANILLA_SCATTERED\"}"), DEPOSIT_SEED,
                List.of(state("minecraft:granite", false)));

        assertEquals("the salt Iris deposits always had", 3112546198474861350L, granite.stableClumpSalt());
        assertNotEquals(granite.stableClumpSalt(), andesite.stableClumpSalt());
        assertNotEquals(granite.stableClumpSalt(), alteredShape.stableClumpSalt());
    }

    @Test
    public void onlyOreDepositPalettesReceiveBiomeTuning() {
        assertTrue(new ResolvedDeposit(new DepositGenerator(), DEPOSIT_SEED, List.of(state("minecraft:iron_ore", true))).isOre());
        assertFalse(new ResolvedDeposit(new DepositGenerator(), DEPOSIT_SEED, List.of(state("minecraft:stone", false))).isOre());
    }

    @Test
    public void vanillaEllipsoidSizeControlsGeometryRatherThanExactBlockCount() {
        ResolvedDeposit deposit = new ResolvedDeposit(new DepositGenerator(), DEPOSIT_SEED, List.of(state("minecraft:iron_ore", true)));
        long smallBlocks = 0L;
        long mediumBlocks = 0L;
        long largeBlocks = 0L;
        int samples = 500;
        for (int i = 0; i < samples; i++) {
            smallBlocks += deposit.vanillaEllipsoid(new RNG(i), 4).getBlocks().size();
            mediumBlocks += deposit.vanillaEllipsoid(new RNG(i), 8).getBlocks().size();
            largeBlocks += deposit.vanillaEllipsoid(new RNG(i), 17).getBlocks().size();
        }

        assertTrue(smallBlocks > 0L);
        assertTrue(mediumBlocks > smallBlocks);
        assertTrue(largeBlocks > mediumBlocks);
        assertNotEquals(17L * samples, largeBlocks);
    }

    @Test
    public void vanillaScatteredSizeIsAnUpperCandidateBound() {
        ResolvedDeposit deposit = new ResolvedDeposit(new DepositGenerator(), DEPOSIT_SEED, List.of(state("minecraft:iron_ore", true)));
        boolean foundEmpty = false;
        boolean foundNonEmpty = false;
        RNG rng = new RNG(991L);
        for (int i = 0; i < 200; i++) {
            int blocks = deposit.vanillaScattered(rng, 3).getBlocks().size();
            assertTrue(blocks <= 3);
            foundEmpty |= blocks == 0;
            foundNonEmpty |= blocks > 0;
        }

        assertTrue(foundEmpty);
        assertTrue(foundNonEmpty);
    }

    @Test
    public void biomeFiltersAcceptResourceKeysAndVanillaDerivatives() {
        IrisBiome mountain = new IrisBiome();
        mountain.setLoadKey("custom/mountain");
        mountain.setDerivative("minecraft:stony_peaks");
        IrisBiome plains = new IrisBiome();
        plains.setLoadKey("custom/plains");
        plains.setDerivative("minecraft:plains");

        DepositGenerator included = generator("{\"biomeScope\": \"SURFACE\", \"includedBiomes\": [\"minecraft:stony_peaks\"]}");
        ResolvedDeposit surface = new ResolvedDeposit(included, DEPOSIT_SEED, List.of());
        assertTrue(surface.matchesBiome(mountain, plains));
        assertFalse(surface.matchesBiome(plains, mountain));
        assertFalse(included.usesCaveBiomeFilter());

        DepositGenerator excluded = generator("{\"biomeScope\": \"SURFACE\", \"excludedBiomes\": [\"custom/mountain\"]}");
        ResolvedDeposit excluding = new ResolvedDeposit(excluded, DEPOSIT_SEED, List.of());
        assertFalse(excluding.matchesBiome(mountain, plains));
        assertTrue(excluding.matchesBiome(plains, mountain));

        assertTrue(generator("{\"excludedBiomes\": [\"custom/mountain\"]}").usesCaveBiomeFilter());
    }

    private static DepositGenerator generator(String json) {
        return new Gson().fromJson(json, DepositGenerator.class);
    }

    private static PlatformBlockState state(String key, boolean ore) {
        PlatformBlockState state = mock(PlatformBlockState.class);
        when(state.isOre()).thenReturn(ore);
        when(state.key()).thenReturn(key);
        return state;
    }
}
