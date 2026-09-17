package art.arcane.iris.engine.object.terrain.deposit;

import art.arcane.iris.engine.object.material.IrisBlockData;
import art.arcane.iris.engine.object.terrain.deposit.IrisDepositBiomeScope;
import art.arcane.iris.engine.object.terrain.deposit.IrisDepositGenerator;
import art.arcane.iris.engine.object.terrain.deposit.IrisDepositShape;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisDepositTuningTest {
    @Test
    public void everyDepositEnumAndValueHasSchemaDescription() throws ReflectiveOperationException {
        for (Field modelField : IrisDepositGenerator.class.getDeclaredFields()) {
            Class<?> enumType = modelField.getType();
            if (!enumType.isEnum()) {
                continue;
            }

            Desc typeDescription = enumType.getAnnotation(Desc.class);
            assertNotNull(enumType.getSimpleName(), typeDescription);
            assertFalse(typeDescription.value().isBlank());

            Object[] constants = enumType.getEnumConstants();
            for (Object constant : constants) {
                String constantName = ((Enum<?>) constant).name();
                Desc constantDescription = enumType.getField(constantName).getAnnotation(Desc.class);
                assertNotNull(enumType.getSimpleName() + "." + constantName, constantDescription);
                assertFalse(constantDescription.value().isBlank());
            }
        }
    }

    @Test
    public void depositSizesScaleAndRemainWithinSchemaLimit() {
        assertEquals(8, IrisDepositGenerator.scaledDepositSize(4, 2D));
        assertEquals(16, IrisDepositGenerator.scaledDepositSize(8, 2D));
        assertEquals(0, IrisDepositGenerator.scaledDepositSize(0, 2D));
        assertEquals(8192, IrisDepositGenerator.scaledDepositSize(8192, 2D));
    }

    @Test
    public void biomeOreTuningDefaultsPreserveExistingGeneration() {
        IrisBiome biome = new IrisBiome();

        assertEquals(1D, biome.getOreDepositFrequencyMultiplier(), 0D);
        assertEquals(1D, biome.getOreDepositSizeMultiplier(), 0D);
    }

    @Test
    public void clumpCachesAreScopedByWorldSeedAndSize() {
        IrisDepositGenerator.ClumpCacheKey firstWorld =
                new IrisDepositGenerator.ClumpCacheKey(41L, 4, 8);
        IrisDepositGenerator.ClumpCacheKey secondWorld =
                new IrisDepositGenerator.ClumpCacheKey(42L, 4, 8);
        IrisDepositGenerator.ClumpCacheKey largerVein =
                new IrisDepositGenerator.ClumpCacheKey(41L, 8, 16);

        assertNotEquals(firstWorld, secondWorld);
        assertNotEquals(firstWorld, largerVein);
        assertEquals(firstWorld, new IrisDepositGenerator.ClumpCacheKey(41L, 4, 8));
    }

    @Test
    public void clumpSaltIsStableAcrossEquivalentConfigInstances() {
        IrisData data = mock(IrisData.class);
        IrisDepositGenerator first = generatorWithState(data, false, "minecraft:granite");
        IrisDepositGenerator second = generatorWithState(data, false, "minecraft:granite");

        assertEquals(first.stableClumpSalt(data), second.stableClumpSalt(data));
    }

    @Test
    public void clumpSaltIncludesAuthoredConfigAndPalette() {
        IrisData data = mock(IrisData.class);
        IrisDepositGenerator granite = generatorWithState(data, false, "minecraft:granite");
        IrisDepositGenerator andesite = generatorWithState(data, false, "minecraft:andesite");
        IrisDepositGenerator alteredShape = generatorWithState(data, false, "minecraft:granite");
        alteredShape.setShape(IrisDepositShape.VANILLA_SCATTERED);

        assertEquals(3112546198474861350L, granite.stableClumpSalt(data));
        assertNotEquals(granite.stableClumpSalt(data), andesite.stableClumpSalt(data));
        assertNotEquals(granite.stableClumpSalt(data), alteredShape.stableClumpSalt(data));
    }

    @Test
    public void onlyOreDepositPalettesReceiveBiomeTuning() {
        IrisData data = mock(IrisData.class);
        IrisDepositGenerator oreGenerator = generatorWithState(data, true);
        IrisDepositGenerator stoneGenerator = generatorWithState(data, false);

        assertTrue(oreGenerator.isOre(data));
        assertFalse(stoneGenerator.isOre(data));
    }

    @Test
    public void vanillaEllipsoidSizeControlsGeometryRatherThanExactBlockCount() {
        IrisData data = mock(IrisData.class);
        IrisDepositGenerator generator = generatorWithState(data, true);
        long smallBlocks = 0L;
        long mediumBlocks = 0L;
        long largeBlocks = 0L;
        int samples = 500;
        for (int i = 0; i < samples; i++) {
            smallBlocks += generator.generateVanillaEllipsoid(new RNG(i), data, 4).getBlocks().size();
            mediumBlocks += generator.generateVanillaEllipsoid(new RNG(i), data, 8).getBlocks().size();
            largeBlocks += generator.generateVanillaEllipsoid(new RNG(i), data, 17).getBlocks().size();
        }

        assertTrue(smallBlocks > 0L);
        assertTrue(mediumBlocks > smallBlocks);
        assertTrue(largeBlocks > mediumBlocks);
        assertNotEquals(17L * samples, largeBlocks);
    }

    @Test
    public void vanillaScatteredSizeIsAnUpperCandidateBound() {
        IrisData data = mock(IrisData.class);
        IrisDepositGenerator generator = generatorWithState(data, true);
        boolean foundEmpty = false;
        boolean foundNonEmpty = false;
        RNG rng = new RNG(991L);
        for (int i = 0; i < 200; i++) {
            int blocks = generator.generateVanillaScattered(rng, data, 3).getBlocks().size();
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
        IrisDepositGenerator generator = new IrisDepositGenerator();
        generator.setBiomeScope(IrisDepositBiomeScope.SURFACE);
        generator.getIncludedBiomes().add("minecraft:stony_peaks");

        assertTrue(generator.matchesBiome(mountain, plains));
        assertFalse(generator.matchesBiome(plains, mountain));
        assertFalse(generator.usesCaveBiomeFilter());

        generator.getIncludedBiomes().clear();
        generator.getExcludedBiomes().add("custom/mountain");
        assertFalse(generator.matchesBiome(mountain, plains));
        assertTrue(generator.matchesBiome(plains, mountain));

        generator.setBiomeScope(IrisDepositBiomeScope.CAVE);
        assertTrue(generator.usesCaveBiomeFilter());
    }

    private IrisDepositGenerator generatorWithState(IrisData data, boolean ore) {
        return generatorWithState(data, ore, ore ? "minecraft:iron_ore" : "minecraft:stone");
    }

    private IrisDepositGenerator generatorWithState(IrisData data, boolean ore, String key) {
        IrisBlockData block = mock(IrisBlockData.class);
        PlatformBlockState state = mock(PlatformBlockState.class);
        when(block.getBlockData(data)).thenReturn(state);
        when(state.isOre()).thenReturn(ore);
        when(state.key()).thenReturn(key);
        IrisDepositGenerator generator = new IrisDepositGenerator();
        generator.getPalette().add(block);
        return generator;
    }
}
