package art.arcane.iris.generation.decoration;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.iris.generation.block.B;
import art.arcane.volmlib.util.math.RNG;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockSupport;
import org.bukkit.block.data.BlockData;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class DecoratorCoreTest {

    @Test
    public void partSeed_differsByPartOrdinal() {
        long base = 123456789L;
        long s0 = DecoratorCore.partSeed(base, 0);
        long s1 = DecoratorCore.partSeed(base, 1);
        long s2 = DecoratorCore.partSeed(base, 2);
        assertNotEquals(s0, s1);
        assertNotEquals(s1, s2);
    }

    @Test
    public void partSeed_isDeterministic() {
        long base = 987654321L;
        assertEquals(DecoratorCore.partSeed(base, 0), DecoratorCore.partSeed(base, 0));
        assertEquals(DecoratorCore.partSeed(base, 3), DecoratorCore.partSeed(base, 3));
    }

    @Test
    public void placeOpts_resetClearsAllFields() {
        DecoratorCore.PlaceOpts opts = DecoratorCore.SCRATCH_OPTS.get();
        opts.caveSkipFluid = true;
        opts.underwater = true;
        opts.fluidHeight = 99;
        opts.reset();
        assertFalse(opts.caveSkipFluid);
        assertFalse(opts.underwater);
        assertEquals(0, opts.fluidHeight);
    }

    @Test
    public void scratchOpts_sameInstanceReturnedWithinThread() {
        DecoratorCore.PlaceOpts a = DecoratorCore.SCRATCH_OPTS.get();
        DecoratorCore.PlaceOpts b = DecoratorCore.SCRATCH_OPTS.get();
        assertSame(a, b);
    }

    @Test
    public void pickDecorator_emptyBucket_returnsNull() {
        IrisBiome biome = mock(IrisBiome.class);
        IrisData data = mock(IrisData.class);
        when(biome.getDecoratorBucket(IrisDecorationPart.NONE)).thenReturn(new IrisDecorator[0]);

        IrisDecorator result = DecoratorCore.pickDecorator(
                biome, IrisDecorationPart.NONE, new RNG(1L), new RNG(2L), data, 0.0, 0.0);
        assertNull(result);
    }

    @Test
    public void pickDecorator_nonePassChanceGate_returnsNull() {
        IrisBiome biome = mock(IrisBiome.class);
        IrisData data = mock(IrisData.class);
        IrisDecorator d = mock(IrisDecorator.class);
        when(d.passesChanceGate(any(RNG.class), anyDouble(), anyDouble(), any(IrisData.class))).thenReturn(false);
        when(biome.getDecoratorBucket(IrisDecorationPart.NONE)).thenReturn(new IrisDecorator[]{d});

        IrisDecorator result = DecoratorCore.pickDecorator(
                biome, IrisDecorationPart.NONE, new RNG(1L), new RNG(2L), data, 0.0, 0.0);
        assertNull(result);
    }

    @Test
    public void pickDecorator_singleCandidate_alwaysReturnsThat() {
        IrisBiome biome = mock(IrisBiome.class);
        IrisData data = mock(IrisData.class);
        IrisDecorator d = mock(IrisDecorator.class);
        when(d.passesChanceGate(any(RNG.class), anyDouble(), anyDouble(), any(IrisData.class))).thenReturn(true);
        when(biome.getDecoratorBucket(IrisDecorationPart.NONE)).thenReturn(new IrisDecorator[]{d});

        RNG gRNG = new RNG(42L);
        for (int t = 0; t < 50; t++) {
            IrisDecorator result = DecoratorCore.pickDecorator(
                    biome, IrisDecorationPart.NONE, gRNG, new RNG(t * 13L + 7), data, 0.0, 0.0);
            assertSame(d, result);
        }
    }

    @Test
    public void pickDecorator_multiplePassingCandidates_selectsUniformly() {
        IrisBiome biome = mock(IrisBiome.class);
        IrisData data = mock(IrisData.class);

        int n = 4;
        IrisDecorator[] decorators = new IrisDecorator[n];
        for (int i = 0; i < n; i++) {
            IrisDecorator d = mock(IrisDecorator.class);
            when(d.passesChanceGate(any(RNG.class), anyDouble(), anyDouble(), any(IrisData.class))).thenReturn(true);
            decorators[i] = d;
        }
        when(biome.getDecoratorBucket(IrisDecorationPart.NONE)).thenReturn(decorators);

        RNG gRNG = new RNG(99L);
        int[] counts = new int[n];
        int trials = 2000;

        for (int t = 0; t < trials; t++) {
            IrisDecorator picked = DecoratorCore.pickDecorator(
                    biome, IrisDecorationPart.NONE, gRNG, new RNG(t * 31L + 3), data, 0.0, 0.0);
            assertNotNull(picked);
            for (int i = 0; i < n; i++) {
                if (picked == decorators[i]) {
                    counts[i]++;
                    break;
                }
            }
        }

        double expected = trials / (double) n;
        for (int i = 0; i < n; i++) {
            double deviation = Math.abs(counts[i] - expected) / expected;
            assertTrue("Decorator " + i + " selected " + counts[i] + " times; expected ~" + (int) expected
                    + " (deviation " + String.format("%.0f%%", deviation * 100) + ")", deviation < 0.20);
        }
    }

    @Test
    public void singleStackTargetsBlockAboveSupport() {
        IrisDecorator decorator = mock(IrisDecorator.class);
        IrisData data = mock(IrisData.class);
        NativeBlockState support = sturdyState();
        NativeBlockState air = mock(NativeBlockState.class);
        NativeBlockState decorant = mock(NativeBlockState.class);
        when(air.isAir()).thenReturn(true);
        when(decorant.key()).thenReturn("minecraft:stone");
        when(decorant.canPlaceOnto(support)).thenReturn(true);
        when(decorator.getHeight(any(RNG.class), anyDouble(), anyDouble(), eq(data))).thenReturn(1);
        when(decorator.pickBlockDataTop(any(RNG.class), eq(data), anyDouble(), anyDouble())).thenReturn(decorant);

        Hunk<NativeBlockState> output = Hunk.newArrayHunk(1, 4, 1);
        output.set(0, 1, 0, support);
        output.set(0, 2, 0, air);
        DecoratorCore.PlaceOpts opts = new DecoratorCore.PlaceOpts();

        DecoratorCore.placeStackUp(decorator, 0, 0, 0, 0, 1, 3, output, new RNG(1L), data, opts);

        assertSame(support, output.get(0, 1, 0));
        assertSame(decorant, output.get(0, 2, 0));
    }

    @Test
    public void singleStackDoesNotOverwriteOccupiedTarget() {
        IrisDecorator decorator = mock(IrisDecorator.class);
        IrisData data = mock(IrisData.class);
        NativeBlockState support = sturdyState();
        NativeBlockState occupied = mock(NativeBlockState.class);
        NativeBlockState decorant = mock(NativeBlockState.class);
        when(occupied.isAir()).thenReturn(false);
        when(occupied.isFluid()).thenReturn(false);
        when(decorator.getHeight(any(RNG.class), anyDouble(), anyDouble(), eq(data))).thenReturn(1);
        when(decorator.pickBlockDataTop(any(RNG.class), eq(data), anyDouble(), anyDouble())).thenReturn(decorant);

        Hunk<NativeBlockState> output = Hunk.newArrayHunk(1, 4, 1);
        output.set(0, 1, 0, support);
        output.set(0, 2, 0, occupied);
        DecoratorCore.PlaceOpts opts = new DecoratorCore.PlaceOpts();

        DecoratorCore.placeStackUp(decorator, 0, 0, 0, 0, 1, 3, output, new RNG(1L), data, opts);

        assertSame(support, output.get(0, 1, 0));
        assertSame(occupied, output.get(0, 2, 0));
    }

    @Test
    public void multiStackStopsBeforeOccupiedTarget() {
        IrisDecorator decorator = mock(IrisDecorator.class);
        IrisData data = mock(IrisData.class);
        NativeBlockState support = sturdyState();
        NativeBlockState air = mock(NativeBlockState.class);
        NativeBlockState occupied = mock(NativeBlockState.class);
        NativeBlockState decorant = mock(NativeBlockState.class);
        when(air.isAir()).thenReturn(true);
        when(occupied.isAir()).thenReturn(false);
        when(occupied.isFluid()).thenReturn(false);
        when(decorant.key()).thenReturn("minecraft:tall_grass");
        when(decorant.canPlaceOnto(support)).thenReturn(true);
        when(decorator.getHeight(any(RNG.class), anyDouble(), anyDouble(), eq(data))).thenReturn(3);
        when(decorator.getTopThreshold()).thenReturn(0.75);
        when(decorator.pickBlockData(any(RNG.class), eq(data), anyDouble(), anyDouble())).thenReturn(decorant);
        when(decorator.pickBlockDataTop(any(RNG.class), eq(data), anyDouble(), anyDouble())).thenReturn(decorant);

        Hunk<NativeBlockState> output = Hunk.newArrayHunk(1, 5, 1);
        output.set(0, 1, 0, support);
        output.set(0, 2, 0, air);
        output.set(0, 3, 0, occupied);
        output.set(0, 4, 0, air);
        DecoratorCore.PlaceOpts opts = new DecoratorCore.PlaceOpts();

        DecoratorCore.placeStackUp(decorator, 0, 0, 0, 0, 1, 4, output, new RNG(1L), data, opts);

        assertSame(decorant, output.get(0, 2, 0));
        assertSame(occupied, output.get(0, 3, 0));
        assertSame(air, output.get(0, 4, 0));
    }

    @Test
    public void descendingStackStopsAtLocalWorldFloor() {
        IrisDecorator decorator = mock(IrisDecorator.class);
        IrisData data = mock(IrisData.class);
        NativeBlockState decorant = mock(NativeBlockState.class);
        when(decorant.key()).thenReturn("minecraft:stone");
        when(decorator.getHeight(any(RNG.class), anyDouble(), anyDouble(), eq(data))).thenReturn(2);
        when(decorator.getTopThreshold()).thenReturn(1.0);
        when(decorator.pickBlockData(any(RNG.class), eq(data), anyDouble(), anyDouble())).thenReturn(decorant);
        when(decorator.pickBlockDataTop(any(RNG.class), eq(data), anyDouble(), anyDouble())).thenReturn(decorant);

        Hunk<NativeBlockState> output = Hunk.newArrayHunk(1, 2, 1);
        DecoratorCore.PlaceOpts opts = new DecoratorCore.PlaceOpts();

        DecoratorCore.placeStackDown(
                decorator, 0, 0, 0, 0, 0, -64, output, new RNG(1L), data, 2, opts, null);

        assertSame(decorant, output.get(0, 0, 0));
    }

    @Test
    public void singleDescendingStackPreservesTargetWhenPickIsNull() {
        IrisDecorator decorator = mock(IrisDecorator.class);
        IrisData data = mock(IrisData.class);
        NativeBlockState existing = mock(NativeBlockState.class);
        when(decorator.getHeight(any(RNG.class), anyDouble(), anyDouble(), eq(data))).thenReturn(1);

        Hunk<NativeBlockState> output = Hunk.newArrayHunk(1, 2, 1);
        output.set(0, 0, 0, existing);
        DecoratorCore.PlaceOpts opts = new DecoratorCore.PlaceOpts();

        DecoratorCore.placeStackDown(
                decorator, 0, 0, 0, 0, 0, 0, output, new RNG(1L), data, 1, opts, null);

        assertSame(existing, output.get(0, 0, 0));
    }

    @Test
    public void multiDescendingStackStopsWhenPickIsNull() {
        IrisDecorator decorator = mock(IrisDecorator.class);
        IrisData data = mock(IrisData.class);
        NativeBlockState lowerExisting = mock(NativeBlockState.class);
        NativeBlockState upperExisting = mock(NativeBlockState.class);
        when(decorator.getHeight(any(RNG.class), anyDouble(), anyDouble(), eq(data))).thenReturn(2);
        when(decorator.getTopThreshold()).thenReturn(1.0);

        Hunk<NativeBlockState> output = Hunk.newArrayHunk(1, 2, 1);
        output.set(0, 0, 0, lowerExisting);
        output.set(0, 1, 0, upperExisting);
        DecoratorCore.PlaceOpts opts = new DecoratorCore.PlaceOpts();

        DecoratorCore.placeStackDown(
                decorator, 0, 0, 0, 0, 1, 0, output, new RNG(1L), data, 2, opts, null);

        assertSame(lowerExisting, output.get(0, 0, 0));
        assertSame(upperExisting, output.get(0, 1, 0));
    }

    @Test
    public void floatingStackStopsAtHunkCeiling() {
        IrisDecorator decorator = mock(IrisDecorator.class);
        IrisData data = mock(IrisData.class);
        NativeBlockState decorant = mock(NativeBlockState.class);
        when(decorant.key()).thenReturn("minecraft:stone");
        when(decorator.getHeight(any(RNG.class), anyDouble(), anyDouble(), eq(data))).thenReturn(3);
        when(decorator.getTopThreshold()).thenReturn(1.0);
        when(decorator.pickBlockData(any(RNG.class), eq(data), anyDouble(), anyDouble())).thenReturn(decorant);
        when(decorator.pickBlockDataTop(any(RNG.class), eq(data), anyDouble(), anyDouble())).thenReturn(decorant);

        Hunk<NativeBlockState> output = Hunk.newArrayHunk(1, 2, 1);
        int placed = DecoratorCore.placeFloatingStacked(
                decorator, 0, 0, 0, 0, 0, 3, output, new RNG(1L), data, null);

        assertEquals(1, placed);
        assertSame(decorant, output.get(0, 1, 0));
    }

    @Test
    public void tallSurfacePlantDoesNotPlaceWhenUpperTargetIsOccupied() {
        IrisDecorator decorator = mock(IrisDecorator.class);
        IrisData data = mock(IrisData.class);
        NativeBlockState support = sturdyState();
        NativeBlockState air = airState();
        NativeBlockState occupied = mock(NativeBlockState.class);
        NativeBlockState plant = tallPlantState();
        when(decorator.pickBlockData(any(RNG.class), eq(data), anyDouble(), anyDouble())).thenReturn(plant);

        Hunk<NativeBlockState> output = Hunk.newArrayHunk(1, 4, 1);
        output.set(0, 0, 0, support);
        output.set(0, 1, 0, air);
        output.set(0, 2, 0, occupied);

        DecoratorCore.placeSurfaceSingle(
                decorator, 0, 0, 0, 0, 0, output, new RNG(1L), data, false, false, null);

        assertSame(air, output.get(0, 1, 0));
        assertSame(occupied, output.get(0, 2, 0));
    }

    @Test
    public void tallFloatingPlantDoesNotPlaceWhenUpperTargetIsOccupied() {
        IrisDecorator decorator = mock(IrisDecorator.class);
        IrisData data = mock(IrisData.class);
        NativeBlockState air = airState();
        NativeBlockState occupied = mock(NativeBlockState.class);
        NativeBlockState plant = tallPlantState();
        when(decorator.pickBlockData(any(RNG.class), eq(data), anyDouble(), anyDouble())).thenReturn(plant);

        Hunk<NativeBlockState> output = Hunk.newArrayHunk(1, 4, 1);
        output.set(0, 1, 0, air);
        output.set(0, 2, 0, occupied);

        DecoratorCore.placeFloatingSimple(
                decorator, 0, 0, 0, 0, 0, 3, output, new RNG(1L), data, null);

        assertSame(air, output.get(0, 1, 0));
        assertSame(occupied, output.get(0, 2, 0));
    }

    @Test
    public void tallSurfacePlantPlacesBothHalvesTogether() {
        IrisDecorator decorator = mock(IrisDecorator.class);
        IrisData data = mock(IrisData.class);
        NativeBlockState support = sturdyState();
        NativeBlockState lowerAir = airState();
        NativeBlockState upperAir = airState();
        NativeBlockState lower = mock(NativeBlockState.class);
        NativeBlockState upper = mock(NativeBlockState.class);
        NativeBlockState plant = tallPlantState(lower, upper);
        when(plant.canPlaceOnto(support)).thenReturn(true);
        when(decorator.pickBlockData(any(RNG.class), eq(data), anyDouble(), anyDouble())).thenReturn(plant);

        Hunk<NativeBlockState> output = Hunk.newArrayHunk(1, 4, 1);
        output.set(0, 0, 0, support);
        output.set(0, 1, 0, lowerAir);
        output.set(0, 2, 0, upperAir);

        DecoratorCore.placeSurfaceSingle(
                decorator, 0, 0, 0, 0, 0, output, new RNG(1L), data, false, false, null);

        assertSame(lower, output.get(0, 1, 0));
        assertSame(upper, output.get(0, 2, 0));
    }

    @Test
    public void surfaceDecorantRejectsInvalidNativeSupport() {
        IrisDecorator decorator = mock(IrisDecorator.class);
        IrisData data = mock(IrisData.class);
        NativeBlockState support = sturdyState();
        NativeBlockState air = airState();
        NativeBlockState decorant = mock(NativeBlockState.class);
        when(decorant.canPlaceOnto(support)).thenReturn(false);
        when(decorator.pickBlockData(any(RNG.class), eq(data), anyDouble(), anyDouble())).thenReturn(decorant);

        Hunk<NativeBlockState> output = Hunk.newArrayHunk(1, 3, 1);
        output.set(0, 0, 0, support);
        output.set(0, 1, 0, air);

        DecoratorCore.placeSurfaceSingle(
                decorator, 0, 0, 0, 0, 0, output, new RNG(1L), data, false, false, null);

        assertSame(air, output.get(0, 1, 0));
    }

    @Test
    public void forcedSurfaceBlockStillPlacesDecorantAboveIt() {
        IrisDecorator decorator = mock(IrisDecorator.class);
        IrisBlockData forcedBlock = mock(IrisBlockData.class);
        IrisData data = mock(IrisData.class);
        NativeBlockState support = sturdyState();
        NativeBlockState farmland = sturdyState();
        NativeBlockState air = airState();
        NativeBlockState wheat = mock(NativeBlockState.class);
        when(wheat.key()).thenReturn("minecraft:wheat[age=7]");
        when(decorator.getForceBlock()).thenReturn(forcedBlock);
        when(forcedBlock.getBlockData(data)).thenReturn(farmland);
        when(decorator.pickBlockData(any(RNG.class), eq(data), anyDouble(), anyDouble())).thenReturn(wheat);

        Hunk<NativeBlockState> output = Hunk.newArrayHunk(1, 3, 1);
        output.set(0, 0, 0, support);
        output.set(0, 1, 0, air);

        DecoratorCore.placeSurfaceSingle(
                decorator, 0, 0, 0, 0, 0, output, new RNG(1L), data, false, false, null);

        assertSame(farmland, output.get(0, 0, 0));
        assertSame(wheat, output.get(0, 1, 0));
    }

    @Test
    public void descendingWeepingVinesUsePlantBodiesAndOneFreeEndTip() {
        NativeBlockState vine = mock(NativeBlockState.class);
        when(vine.key()).thenReturn("minecraft:weeping_vines");

        assertEquals("minecraft:weeping_vines_plant", DecoratorCore.stackedVineKey(vine, 3, 0));
        assertEquals("minecraft:weeping_vines_plant", DecoratorCore.stackedVineKey(vine, 3, 1));
        assertEquals("minecraft:weeping_vines", DecoratorCore.stackedVineKey(vine, 3, 2));
    }

    @Test
    public void ascendingTwistingVinesUsePlantBodiesAndOneFreeEndTip() {
        NativeBlockState vine = mock(NativeBlockState.class);
        when(vine.key()).thenReturn("minecraft:twisting_vines_plant");

        assertEquals("minecraft:twisting_vines_plant", DecoratorCore.stackedVineKey(vine, 3, 0));
        assertEquals("minecraft:twisting_vines_plant", DecoratorCore.stackedVineKey(vine, 3, 1));
        assertEquals("minecraft:twisting_vines", DecoratorCore.stackedVineKey(vine, 3, 2));
    }

    @Test
    public void sulfurStalagmiteKeepsSulfurMaterialAndTapersToOneTip() {
        Hunk<NativeBlockState> output = placeSpikeColumn("minecraft:sulfur_spike", 5, true, false);

        assertSpikeColumn(output, "minecraft:sulfur_spike", true, false,
                "base", "middle", "middle", "frustum", "tip");
    }

    @Test
    public void sulfurStalactiteKeepsWaterloggingAndPointsDown() {
        Hunk<NativeBlockState> output = placeSpikeColumn("minecraft:sulfur_spike", 5, false, true);

        assertSpikeColumn(output, "minecraft:sulfur_spike", false, true,
                "base", "middle", "middle", "frustum", "tip");
    }

    @Test
    public void shortSulfurSpikesHaveCorrectTipsInBothDirections() {
        for (boolean upward : new boolean[]{true, false}) {
            Hunk<NativeBlockState> single = placeSpikeColumn("minecraft:sulfur_spike", 1, upward, false);
            Hunk<NativeBlockState> pair = placeSpikeColumn("minecraft:sulfur_spike", 2, upward, false);

            assertSpikeColumn(single, "minecraft:sulfur_spike", upward, false, "tip");
            assertSpikeColumn(pair, "minecraft:sulfur_spike", upward, false, "frustum", "tip");
        }
    }

    @Test
    public void pointedDripstoneRetainsWaterloggingWhenStacked() {
        Hunk<NativeBlockState> output = placeSpikeColumn("minecraft:pointed_dripstone", 4, true, true);

        assertSpikeColumn(output, "minecraft:pointed_dripstone", true, true,
                "base", "middle", "frustum", "tip");
    }

    @Test
    public void clippedSpikesRebuildTheirFreeEndWithoutOverwritingObstructions() {
        for (boolean upward : new boolean[]{true, false}) {
            SpikeFixture fixture = new SpikeFixture(5, upward);
            NativeBlockState obstruction = sturdyState();
            fixture.output.set(0, upward ? 4 : 2, 0, obstruction);
            fixture.place();

            assertSame(obstruction, fixture.output.get(0, upward ? 4 : 2, 0));
            assertEquals("tip", fixture.property(3, "thickness"));
            assertEquals("frustum", fixture.property(upward ? 2 : 4, "thickness"));
            assertEquals("base", fixture.property(upward ? 1 : 5, "thickness"));
        }
    }

    @Test
    public void ceilingSpikesRequireTheUndersideOfTheirSupport() {
        SpikeFixture fixture = new SpikeFixture(3, false);
        BlockData support = (BlockData) fixture.output.get(0, 4, 0).nativeHandle();
        when(support.isFaceSturdy(BlockFace.DOWN, BlockSupport.FULL)).thenReturn(false);
        fixture.place();

        assertTrue(fixture.output.get(0, 3, 0).isAir());
    }

    @Test
    public void spikeStacksHonorSurfacePalettesInBothDirections() {
        for (boolean upward : new boolean[]{true, false}) {
            SpikeFixture fixture = new SpikeFixture(3, upward);
            NativeBlockState allowed = sturdyState();
            when(fixture.decorator.getWhitelist()).thenReturn(new KList<>());
            when(fixture.decorator.getWhitelistArray(fixture.data)).thenReturn(new NativeBlockState[]{allowed});
            fixture.place();
            assertTrue(fixture.output.get(0, upward ? 1 : 3, 0).isAir());

            NativeBlockState support = fixture.output.get(0, upward ? 0 : 4, 0);
            when(support.matches(allowed)).thenReturn(true);
            fixture.place();
            assertEquals("tip", fixture.property(upward ? 3 : 1, "thickness"));

            when(fixture.decorator.getBlacklist()).thenReturn(new KList<>());
            when(fixture.decorator.getBlacklistArray(fixture.data)).thenReturn(new NativeBlockState[]{allowed});
            fixture.output.set(0, upward ? 1 : 3, 0, airState());
            fixture.place();
            assertTrue(fixture.output.get(0, upward ? 1 : 3, 0).isAir());
        }
    }

    @Test
    public void submergedSpikesWaterlogAndStopAtLava() {
        for (boolean upward : new boolean[]{true, false}) {
            SpikeFixture fixture = new SpikeFixture(4, upward);
            NativeBlockState water = mock(NativeBlockState.class);
            NativeBlockState lava = mock(NativeBlockState.class);
            when(water.isFluid()).thenReturn(true);
            when(water.isWater()).thenReturn(true);
            when(lava.isFluid()).thenReturn(true);
            int first = upward ? 1 : 4;
            int second = upward ? 2 : 3;
            fixture.output.set(0, first, 0, water);
            fixture.output.set(0, second, 0, lava);
            fixture.opts.underwater = true;
            fixture.opts.fluidHeight = 4;
            fixture.place();

            assertEquals("true", fixture.property(first, "waterlogged"));
            assertEquals("tip", fixture.property(first, "thickness"));
            assertSame(lava, fixture.output.get(0, second, 0));
        }
    }

    @Test
    public void oppositeSulfurTipsMergeWithoutReplacingEitherColumn() {
        for (boolean upward : new boolean[]{true, false}) {
            SpikeFixture fixture = new SpikeFixture(5, upward);
            int oppositeY = 3;
            NativeBlockState opposite = spikeState("minecraft:sulfur_spike", Map.of(
                    "thickness", "tip", "vertical_direction", upward ? "down" : "up", "waterlogged", "false"));
            fixture.output.set(0, oppositeY, 0, opposite);
            fixture.place();

            assertEquals("tip_merge", fixture.property(oppositeY, "thickness"));
            assertEquals(upward ? "down" : "up", fixture.property(oppositeY, "vertical_direction"));
            assertEquals("tip_merge", fixture.property(upward ? 2 : 4, "thickness"));
            assertEquals("frustum", fixture.property(upward ? 1 : 5, "thickness"));
        }
    }

    @Test
    public void sulfurTipsDoNotMergeWithPointedDripstone() {
        SpikeFixture fixture = new SpikeFixture(4, true);
        NativeBlockState dripstone = spikeState("minecraft:pointed_dripstone", Map.of(
                "thickness", "tip", "vertical_direction", "down", "waterlogged", "false"));
        fixture.output.set(0, 3, 0, dripstone);
        fixture.place();

        assertEquals("tip", fixture.property(2, "thickness"));
        assertSame(dripstone, fixture.output.get(0, 3, 0));
    }

    @Test
    public void singleDecoratorsNormalizeNativeSpikeDirectionAndThickness() {
        for (boolean upward : new boolean[]{true, false}) {
            SpikeFixture fixture = new SpikeFixture(1, upward);
            if (upward) {
                DecoratorCore.placeSurfaceSingle(fixture.decorator, 0, 0, 0, 0, 0,
                        fixture.output, new RNG(1L), fixture.data, false, false, null);
            } else {
                DecoratorCore.placeSingleAt(fixture.decorator, 0, 0, 0, 1, 0,
                        fixture.output, new RNG(1L), fixture.data, true, null);
            }
            assertEquals("tip", fixture.property(1, "thickness"));
            assertEquals(upward ? "up" : "down", fixture.property(1, "vertical_direction"));
        }
    }

    @Test
    public void extendingSpikeRepairsTheExistingTipBehindIt() {
        SpikeFixture fixture = new SpikeFixture(3, true);
        NativeBlockState spike = spikeState("minecraft:sulfur_spike", Map.of(
                "thickness", "tip", "vertical_direction", "up", "waterlogged", "false"));
        fixture.output.set(0, 0, 0, spike);
        fixture.place();

        assertEquals("base", fixture.property(0, "thickness"));
        assertEquals("middle", fixture.property(1, "thickness"));
        assertEquals("frustum", fixture.property(2, "thickness"));
        assertEquals("tip", fixture.property(3, "thickness"));
    }

    @Test
    public void singleSpikeChecksPaletteBeforeReplacingItsSupport() {
        SpikeFixture fixture = new SpikeFixture(1, true);
        NativeBlockState original = fixture.output.get(0, 0, 0);
        NativeBlockState replacement = sturdyState();
        IrisBlockData forced = mock(IrisBlockData.class);
        when(forced.getBlockData(fixture.data)).thenReturn(replacement);
        when(fixture.decorator.getForceBlock()).thenReturn(forced);
        when(fixture.decorator.getWhitelist()).thenReturn(new KList<>());
        when(fixture.decorator.getWhitelistArray(fixture.data)).thenReturn(new NativeBlockState[]{original});

        DecoratorCore.placeSurfaceSingle(fixture.decorator, 0, 0, 0, 0, 0,
                fixture.output, new RNG(1L), fixture.data, false, false, null);

        assertSame(replacement, fixture.output.get(0, 0, 0));
        assertEquals("tip", fixture.property(1, "thickness"));
    }

    @Test
    public void clippedCeilingVinesRetainTheirGrowingTip() {
        SpikeFixture fixture = new SpikeFixture(5, false);
        NativeBlockState plant = mock(NativeBlockState.class);
        NativeBlockState tip = mock(NativeBlockState.class);
        NativeBlockState obstruction = sturdyState();
        when(plant.key()).thenReturn("minecraft:weeping_vines_plant");
        when(tip.key()).thenReturn("minecraft:weeping_vines");
        when(fixture.decorator.pickBlockData(any(RNG.class), eq(fixture.data), anyDouble(), anyDouble())).thenReturn(tip);
        when(fixture.decorator.pickBlockDataTop(any(RNG.class), eq(fixture.data), anyDouble(), anyDouble())).thenReturn(tip);
        fixture.output.set(0, 3, 0, obstruction);
        try (MockedStatic<B> blocks = mockStatic(B.class, CALLS_REAL_METHODS)) {
            blocks.when(() -> B.getState("minecraft:weeping_vines_plant")).thenReturn(plant);
            blocks.when(() -> B.getState("minecraft:weeping_vines")).thenReturn(tip);
            fixture.place();
        }

        assertSame(plant, fixture.output.get(0, 5, 0));
        assertSame(tip, fixture.output.get(0, 4, 0));
        assertSame(obstruction, fixture.output.get(0, 3, 0));
    }

    @Test
    public void boundDecoratorHooksWinWhenBukkitClassesArePresent() {
        NativeBlockState vine = mock(NativeBlockState.class);
        NativeBlockState fixed = mock(NativeBlockState.class);
        NativeBlockState decorator = mock(NativeBlockState.class);
        NativeBlockState surface = mock(NativeBlockState.class);
        Hunk<NativeBlockState> output = Hunk.newArrayHunk(1, 1, 1);
        DecoratorPlatformHooks.FaceFixer faceFixer = mock(DecoratorPlatformHooks.FaceFixer.class);
        DecoratorPlatformHooks.SurfaceSturdiness sturdiness = mock(DecoratorPlatformHooks.SurfaceSturdiness.class);
        when(vine.isVineBlock()).thenReturn(true);
        when(faceFixer.fixFaces(vine, output, 0, 0, 0, 0, 0, null)).thenReturn(fixed);
        when(decorator.canPlaceOnto(surface)).thenReturn(true);
        when(sturdiness.canGoOn(surface, true)).thenReturn(true);
        DecoratorPlatformHooks.Bindings previous = DecoratorPlatformHooks.bind(faceFixer, sturdiness);
        try {
            assertSame(fixed, DecoratorCore.fixFacesForHunk(vine, output, 0, 0, 0, 0, 0, null));
            assertTrue(DecoratorCore.canGoOn(decorator, surface));
        } finally {
            DecoratorPlatformHooks.restore(previous);
        }
    }

    private NativeBlockState airState() {
        NativeBlockState air = mock(NativeBlockState.class);
        when(air.isAir()).thenReturn(true);
        return air;
    }

    private Hunk<NativeBlockState> placeSpikeColumn(String material, int height, boolean upward, boolean waterlogged) {
        IrisDecorator decorator = mock(IrisDecorator.class);
        IrisData data = mock(IrisData.class);
        NativeBlockState spike = spikeState(material, Map.of(
                "thickness", "tip_merge", "vertical_direction", upward ? "down" : "up",
                "waterlogged", Boolean.toString(waterlogged)));
        when(decorator.getHeight(any(RNG.class), anyDouble(), anyDouble(), eq(data))).thenReturn(height);
        when(decorator.getTopThreshold()).thenReturn(0.75);
        when(decorator.pickBlockData(any(RNG.class), eq(data), anyDouble(), anyDouble())).thenReturn(spike);
        when(decorator.pickBlockDataTop(any(RNG.class), eq(data), anyDouble(), anyDouble())).thenReturn(spike);

        Hunk<NativeBlockState> output = Hunk.newArrayHunk(1, height + 2, 1);
        for (int y = 1; y <= height; y++) {
            output.set(0, y, 0, airState());
        }
        NativeBlockState support = sturdyState();
        output.set(0, upward ? 0 : height + 1, 0, support);
        DecoratorCore.PlaceOpts opts = new DecoratorCore.PlaceOpts();
        if (upward) {
            DecoratorCore.placeStackUp(decorator, 0, 0, 0, 0, 0, height, output, new RNG(1L), data, opts);
        } else {
            DecoratorCore.placeStackDown(decorator, 0, 0, 0, 0, height, 1, output, new RNG(1L), data, height, opts, null);
        }
        assertSame(support, output.get(0, upward ? 0 : height + 1, 0));
        return output;
    }

    private void assertSpikeColumn(Hunk<NativeBlockState> output, String material,
                                   boolean upward, boolean waterlogged, String... thicknesses) {
        for (int index = 0; index < thicknesses.length; index++) {
            int y = upward ? index + 1 : thicknesses.length - index;
            assertEquals(material + "[thickness=" + thicknesses[index]
                            + ",vertical_direction=" + (upward ? "up" : "down")
                            + ",waterlogged=" + waterlogged + "]",
                    output.get(0, y, 0).key());
        }
    }

    private NativeBlockState spikeState(String material, Map<String, String> properties) {
        NativeBlockState state = mock(NativeBlockState.class);
        when(state.key()).thenReturn(material + "[thickness=" + properties.get("thickness")
                + ",vertical_direction=" + properties.get("vertical_direction")
                + ",waterlogged=" + properties.get("waterlogged") + "]");
        when(state.canPlaceOnto(any(NativeBlockState.class))).thenReturn(true);
        when(state.withProperty(anyString(), anyString())).thenAnswer(invocation -> {
            Map<String, String> updated = new LinkedHashMap<>(properties);
            updated.put(invocation.getArgument(0), invocation.getArgument(1));
            return spikeState(material, updated);
        });
        return state;
    }

    private NativeBlockState tallPlantState() {
        return tallPlantState(mock(NativeBlockState.class), mock(NativeBlockState.class));
    }

    private NativeBlockState tallPlantState(NativeBlockState lower, NativeBlockState upper) {
        NativeBlockState plant = mock(NativeBlockState.class);
        when(plant.key()).thenReturn("minecraft:tall_grass[half=lower]");
        when(plant.withProperty("half", "lower")).thenReturn(lower);
        when(plant.withProperty("half", "upper")).thenReturn(upper);
        return plant;
    }

    private NativeBlockState sturdyState() {
        NativeBlockState support = mock(NativeBlockState.class);
        BlockData blockData = mock(BlockData.class);
        when(support.nativeHandle()).thenReturn(blockData);
        when(blockData.isFaceSturdy(any(), eq(BlockSupport.FULL))).thenReturn(true);
        return support;
    }
    private class SpikeFixture {
        private final IrisDecorator decorator = mock(IrisDecorator.class);
        private final IrisData data = mock(IrisData.class);
        private final DecoratorCore.PlaceOpts opts = new DecoratorCore.PlaceOpts();
        private final Hunk<NativeBlockState> output;
        private final int height;
        private final boolean upward;

        private SpikeFixture(int height, boolean upward) {
            this.height = height;
            this.upward = upward;
            output = Hunk.newArrayHunk(1, height + 2, 1);
            for (int y = 0; y < output.getHeight(); y++) {
                output.set(0, y, 0, airState());
            }
            output.set(0, upward ? 0 : height + 1, 0, sturdyState());
            NativeBlockState spike = spikeState("minecraft:sulfur_spike", Map.of(
                    "thickness", "tip_merge", "vertical_direction", upward ? "down" : "up", "waterlogged", "false"));
            when(decorator.getHeight(any(RNG.class), anyDouble(), anyDouble(), eq(data))).thenReturn(height);
            when(decorator.getTopThreshold()).thenReturn(0.75);
            when(decorator.pickBlockData(any(RNG.class), eq(data), anyDouble(), anyDouble())).thenReturn(spike);
            when(decorator.pickBlockDataTop(any(RNG.class), eq(data), anyDouble(), anyDouble())).thenReturn(spike);
        }

        private void place() {
            if (upward) {
                DecoratorCore.placeStackUp(decorator, 0, 0, 0, 0, 0, height, output, new RNG(1L), data, opts);
            } else {
                DecoratorCore.placeStackDown(decorator, 0, 0, 0, 0, height, 1, output, new RNG(1L), data, height, opts, null);
            }
        }

        private String property(int y, String name) {
            return IrisProceduralBlocks.propertyValue(output.get(0, y, 0), name);
        }
    }

}
