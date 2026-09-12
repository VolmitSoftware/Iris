package art.arcane.iris.generation.decoration;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.block.IrisBlockData;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.block.B;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import com.google.gson.Gson;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class IrisDepositTuningTest {
    @Test
    public void everyDepositEnumAndValueHasSchemaDescription() throws ReflectiveOperationException {
        for (Field modelField : IrisDepositGenerator.class.getDeclaredFields()) {
            Class<?> enumType = modelField.getType();
            if (!enumType.isEnum()) {
                continue;
            }

            Description typeDescription = enumType.getAnnotation(Description.class);
            assertNotNull(enumType.getSimpleName(), typeDescription);
            assertFalse(typeDescription.value().isBlank());

            Object[] constants = enumType.getEnumConstants();
            for (Object constant : constants) {
                String constantName = ((Enum<?>) constant).name();
                Description constantDescription = enumType.getField(constantName).getAnnotation(Description.class);
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
        assertNull(biome.getSurfaceOreReplaceableBlocks());
    }

    @Test
    public void surfaceHostFilterDefaultsToUnrestricted() {
        IrisDepositGenerator generator = new IrisDepositGenerator();

        assertTrue(generator.getSurfaceReplaceableBlocks().isEmpty());
        assertTrue(generator.canReplaceSurface(mock(PlatformBlockState.class)));
    }

    @Test
    public void surfaceHostFilterAllowsOnlyConfiguredMaterials() {
        IrisDepositGenerator generator = new IrisDepositGenerator();
        generator.setSurfaceReplaceableBlocks(new KList<String>().qadd("minecraft:stone"));
        PlatformBlockState stone = mock(PlatformBlockState.class);
        PlatformBlockState dirt = mock(PlatformBlockState.class);
        when(stone.key()).thenReturn("minecraft:stone");
        when(dirt.key()).thenReturn("minecraft:dirt");

        try (MockedStatic<B> blocks = mockStatic(B.class)) {
            blocks.when(() -> B.getStateOrNull("minecraft:stone", false)).thenReturn(stone);
            blocks.when(() -> B.getStateOrNull("minecraft:dirt", false)).thenReturn(dirt);

            assertTrue(generator.canReplaceSurface(stone));
            assertFalse(generator.canReplaceSurface(dirt));

            generator.setSurfaceReplaceableBlocks(new KList<String>().qadd("minecraft:dirt"));
            assertFalse(generator.canReplaceSurface(stone));
            assertTrue(generator.canReplaceSurface(dirt));
        }
    }

    @Test
    public void biomeSurfaceHostPolicyOverridesDepositPolicy() {
        IrisDepositGenerator generator = new IrisDepositGenerator();
        generator.setSurfaceReplaceableBlocks(new KList<String>().qadd("minecraft:stone"));
        IrisBiome inherited = new IrisBiome();
        IrisBiome desert = new IrisBiome();
        desert.setSurfaceOreReplaceableBlocks(
                new KList<String>().qadd("minecraft:stone").qadd("minecraft:sand"));
        IrisBiome disabled = new IrisBiome();
        disabled.setSurfaceOreReplaceableBlocks(new KList<>());
        PlatformBlockState stone = mock(PlatformBlockState.class);
        PlatformBlockState sand = mock(PlatformBlockState.class);
        when(stone.key()).thenReturn("minecraft:stone");
        when(sand.key()).thenReturn("minecraft:sand");

        try (MockedStatic<B> blocks = mockStatic(B.class)) {
            blocks.when(() -> B.getStateOrNull("minecraft:stone", false)).thenReturn(stone);
            blocks.when(() -> B.getStateOrNull("minecraft:sand", false)).thenReturn(sand);

            assertTrue(generator.canReplaceSurface(stone, inherited));
            assertFalse(generator.canReplaceSurface(sand, inherited));
            assertTrue(generator.canReplaceSurface(sand, desert));
            assertTrue(generator.hasSurfaceReplaceableBlocks(disabled));
            assertFalse(generator.canReplaceSurface(stone, disabled));
        }
    }

    @Test
    public void biomeSurfaceHostPolicyDistinguishesOmittedAndEmptyJson() {
        Gson gson = new Gson();
        IrisBiome inherited = gson.fromJson("{\"name\":\"Inherited\"}", IrisBiome.class);
        IrisBiome disabled = gson.fromJson(
                "{\"name\":\"Disabled\",\"surfaceOreReplaceableBlocks\":[]}", IrisBiome.class);

        assertNull(inherited.getSurfaceOreReplaceableBlocks());
        assertNotNull(disabled.getSurfaceOreReplaceableBlocks());
        assertTrue(disabled.getSurfaceOreReplaceableBlocks().isEmpty());
        assertFalse(inherited.hasSurfaceOreReplaceableBlocks());
        assertTrue(disabled.hasSurfaceOreReplaceableBlocks());
    }

    @Test
    public void biomeSurfaceHostPolicySetterInvalidatesResolvedHosts() {
        IrisBiome biome = new IrisBiome();
        PlatformBlockState stone = mock(PlatformBlockState.class);
        PlatformBlockState sand = mock(PlatformBlockState.class);
        when(stone.key()).thenReturn("minecraft:stone");
        when(sand.key()).thenReturn("minecraft:sand");

        try (MockedStatic<B> blocks = mockStatic(B.class)) {
            blocks.when(() -> B.getStateOrNull("minecraft:stone", false)).thenReturn(stone);
            blocks.when(() -> B.getStateOrNull("minecraft:sand", false)).thenReturn(sand);

            biome.setSurfaceOreReplaceableBlocks(new KList<String>().qadd("minecraft:stone"));
            assertTrue(biome.canReplaceSurfaceOre(stone));
            assertFalse(biome.canReplaceSurfaceOre(sand));

            biome.setSurfaceOreReplaceableBlocks(new KList<String>().qadd("minecraft:sand"));
            assertFalse(biome.canReplaceSurfaceOre(stone));
            assertTrue(biome.canReplaceSurfaceOre(sand));
        }
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
