package art.arcane.iris.engine.decorator;

import art.arcane.iris.builtin.decorant.DecorationPart;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.host.ModuleLogs;
import org.bukkit.block.BlockSupport;
import org.bukkit.block.data.BlockData;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
        ResolvedDecorator result = DecoratorCore.pickDecorator(new ResolvedDecorator[0], new RNG(2L), 0.0, 0.0, ModuleLogs.discarding());
        assertNull(result);
    }

    @Test
    public void pickDecorator_nonePassChanceGate_returnsNull() {
        ResolvedDecorator d = mock(ResolvedDecorator.class);
        when(d.passesChanceGate(anyDouble(), anyDouble())).thenReturn(false);

        ResolvedDecorator result = DecoratorCore.pickDecorator(new ResolvedDecorator[]{d}, new RNG(2L), 0.0, 0.0, ModuleLogs.discarding());
        assertNull(result);
    }

    @Test
    public void pickDecorator_singleCandidate_alwaysReturnsThat() {
        ResolvedDecorator d = mock(ResolvedDecorator.class);
        when(d.passesChanceGate(anyDouble(), anyDouble())).thenReturn(true);

        for (int t = 0; t < 50; t++) {
            ResolvedDecorator result = DecoratorCore.pickDecorator(new ResolvedDecorator[]{d}, new RNG(t * 13L + 7), 0.0, 0.0, ModuleLogs.discarding());
            assertSame(d, result);
        }
    }

    @Test
    public void pickDecorator_multiplePassingCandidates_selectsUniformly() {
        int n = 4;
        ResolvedDecorator[] decorators = new ResolvedDecorator[n];
        for (int i = 0; i < n; i++) {
            ResolvedDecorator d = mock(ResolvedDecorator.class);
            when(d.passesChanceGate(anyDouble(), anyDouble())).thenReturn(true);
            decorators[i] = d;
        }

        int[] counts = new int[n];
        int trials = 2000;

        for (int t = 0; t < trials; t++) {
            ResolvedDecorator picked = DecoratorCore.pickDecorator(decorators, new RNG(t * 31L + 3), 0.0, 0.0, ModuleLogs.discarding());
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
        ResolvedDecorator decorator = mock(ResolvedDecorator.class);
        PlatformBlockState support = sturdyState();
        PlatformBlockState air = mock(PlatformBlockState.class);
        PlatformBlockState decorant = mock(PlatformBlockState.class);
        when(air.isAir()).thenReturn(true);
        when(decorant.key()).thenReturn("minecraft:stone");
        when(decorant.canPlaceOnto(support)).thenReturn(true);
        when(decorator.stackHeight(anyDouble(), anyDouble())).thenReturn(1);
        when(decorator.pickTopBlock(anyDouble(), anyDouble())).thenReturn(decorant);

        Hunk<PlatformBlockState> output = Hunk.newArrayHunk(1, 4, 1);
        output.set(0, 1, 0, support);
        output.set(0, 2, 0, air);
        DecoratorCore.PlaceOpts opts = new DecoratorCore.PlaceOpts();

        DecoratorCore.placeStackUp(decorator, 0, 0, 0, 0, 1, 3, output, opts);

        assertSame(support, output.get(0, 1, 0));
        assertSame(decorant, output.get(0, 2, 0));
    }

    @Test
    public void singleStackDoesNotOverwriteOccupiedTarget() {
        ResolvedDecorator decorator = mock(ResolvedDecorator.class);
        PlatformBlockState support = sturdyState();
        PlatformBlockState occupied = mock(PlatformBlockState.class);
        PlatformBlockState decorant = mock(PlatformBlockState.class);
        when(occupied.isAir()).thenReturn(false);
        when(occupied.isFluid()).thenReturn(false);
        when(decorator.stackHeight(anyDouble(), anyDouble())).thenReturn(1);
        when(decorator.pickTopBlock(anyDouble(), anyDouble())).thenReturn(decorant);

        Hunk<PlatformBlockState> output = Hunk.newArrayHunk(1, 4, 1);
        output.set(0, 1, 0, support);
        output.set(0, 2, 0, occupied);
        DecoratorCore.PlaceOpts opts = new DecoratorCore.PlaceOpts();

        DecoratorCore.placeStackUp(decorator, 0, 0, 0, 0, 1, 3, output, opts);

        assertSame(support, output.get(0, 1, 0));
        assertSame(occupied, output.get(0, 2, 0));
    }

    @Test
    public void multiStackStopsBeforeOccupiedTarget() {
        ResolvedDecorator decorator = mock(ResolvedDecorator.class);
        PlatformBlockState support = sturdyState();
        PlatformBlockState air = mock(PlatformBlockState.class);
        PlatformBlockState occupied = mock(PlatformBlockState.class);
        PlatformBlockState decorant = mock(PlatformBlockState.class);
        when(air.isAir()).thenReturn(true);
        when(occupied.isAir()).thenReturn(false);
        when(occupied.isFluid()).thenReturn(false);
        when(decorant.key()).thenReturn("minecraft:tall_grass");
        when(decorant.canPlaceOnto(support)).thenReturn(true);
        when(decorator.stackHeight(anyDouble(), anyDouble())).thenReturn(3);
        when(decorator.topThreshold()).thenReturn(0.75);
        when(decorator.pickBlock(anyDouble(), anyDouble())).thenReturn(decorant);
        when(decorator.pickTopBlock(anyDouble(), anyDouble())).thenReturn(decorant);

        Hunk<PlatformBlockState> output = Hunk.newArrayHunk(1, 5, 1);
        output.set(0, 1, 0, support);
        output.set(0, 2, 0, air);
        output.set(0, 3, 0, occupied);
        output.set(0, 4, 0, air);
        DecoratorCore.PlaceOpts opts = new DecoratorCore.PlaceOpts();

        DecoratorCore.placeStackUp(decorator, 0, 0, 0, 0, 1, 4, output, opts);

        assertSame(decorant, output.get(0, 2, 0));
        assertSame(occupied, output.get(0, 3, 0));
        assertSame(air, output.get(0, 4, 0));
    }

    @Test
    public void descendingStackStopsAtLocalWorldFloor() {
        ResolvedDecorator decorator = mock(ResolvedDecorator.class);
        PlatformBlockState decorant = mock(PlatformBlockState.class);
        when(decorant.key()).thenReturn("minecraft:stone");
        when(decorator.stackHeight(anyDouble(), anyDouble())).thenReturn(2);
        when(decorator.topThreshold()).thenReturn(1.0);
        when(decorator.pickBlock(anyDouble(), anyDouble())).thenReturn(decorant);
        when(decorator.pickTopBlock(anyDouble(), anyDouble())).thenReturn(decorant);

        Hunk<PlatformBlockState> output = Hunk.newArrayHunk(1, 2, 1);
        DecoratorCore.PlaceOpts opts = new DecoratorCore.PlaceOpts();

        DecoratorCore.placeStackDown(
                decorator, 0, 0, 0, 0, 0, -64, output, 2, opts, null);

        assertSame(decorant, output.get(0, 0, 0));
    }

    @Test
    public void singleDescendingStackPreservesTargetWhenPickIsNull() {
        ResolvedDecorator decorator = mock(ResolvedDecorator.class);
        PlatformBlockState existing = mock(PlatformBlockState.class);
        when(decorator.stackHeight(anyDouble(), anyDouble())).thenReturn(1);

        Hunk<PlatformBlockState> output = Hunk.newArrayHunk(1, 2, 1);
        output.set(0, 0, 0, existing);
        DecoratorCore.PlaceOpts opts = new DecoratorCore.PlaceOpts();

        DecoratorCore.placeStackDown(
                decorator, 0, 0, 0, 0, 0, 0, output, 1, opts, null);

        assertSame(existing, output.get(0, 0, 0));
    }

    @Test
    public void multiDescendingStackStopsWhenPickIsNull() {
        ResolvedDecorator decorator = mock(ResolvedDecorator.class);
        PlatformBlockState lowerExisting = mock(PlatformBlockState.class);
        PlatformBlockState upperExisting = mock(PlatformBlockState.class);
        when(decorator.stackHeight(anyDouble(), anyDouble())).thenReturn(2);
        when(decorator.topThreshold()).thenReturn(1.0);

        Hunk<PlatformBlockState> output = Hunk.newArrayHunk(1, 2, 1);
        output.set(0, 0, 0, lowerExisting);
        output.set(0, 1, 0, upperExisting);
        DecoratorCore.PlaceOpts opts = new DecoratorCore.PlaceOpts();

        DecoratorCore.placeStackDown(
                decorator, 0, 0, 0, 0, 1, 0, output, 2, opts, null);

        assertSame(lowerExisting, output.get(0, 0, 0));
        assertSame(upperExisting, output.get(0, 1, 0));
    }

    @Test
    public void floatingStackStopsAtHunkCeiling() {
        ResolvedDecorator decorator = mock(ResolvedDecorator.class);
        PlatformBlockState decorant = mock(PlatformBlockState.class);
        when(decorant.key()).thenReturn("minecraft:stone");
        when(decorator.stackHeight(anyDouble(), anyDouble())).thenReturn(3);
        when(decorator.topThreshold()).thenReturn(1.0);
        when(decorator.pickBlock(anyDouble(), anyDouble())).thenReturn(decorant);
        when(decorator.pickTopBlock(anyDouble(), anyDouble())).thenReturn(decorant);

        Hunk<PlatformBlockState> output = Hunk.newArrayHunk(1, 2, 1);
        int placed = DecoratorCore.placeFloatingStacked(
                decorator, 0, 0, 0, 0, 0, 3, output);

        assertEquals(1, placed);
        assertSame(decorant, output.get(0, 1, 0));
    }

    @Test
    public void tallSurfacePlantDoesNotPlaceWhenUpperTargetIsOccupied() {
        ResolvedDecorator decorator = mock(ResolvedDecorator.class);
        PlatformBlockState support = sturdyState();
        PlatformBlockState air = airState();
        PlatformBlockState occupied = mock(PlatformBlockState.class);
        PlatformBlockState plant = tallPlantState();
        when(decorator.pickBlock(anyDouble(), anyDouble())).thenReturn(plant);

        Hunk<PlatformBlockState> output = Hunk.newArrayHunk(1, 4, 1);
        output.set(0, 0, 0, support);
        output.set(0, 1, 0, air);
        output.set(0, 2, 0, occupied);

        DecoratorCore.placeSurfaceSingle(
                decorator, 0, 0, 0, 0, 0, output, false, false, null, ModuleLogs.discarding());

        assertSame(air, output.get(0, 1, 0));
        assertSame(occupied, output.get(0, 2, 0));
    }

    @Test
    public void tallFloatingPlantDoesNotPlaceWhenUpperTargetIsOccupied() {
        ResolvedDecorator decorator = mock(ResolvedDecorator.class);
        PlatformBlockState air = airState();
        PlatformBlockState occupied = mock(PlatformBlockState.class);
        PlatformBlockState plant = tallPlantState();
        when(decorator.pickBlock(anyDouble(), anyDouble())).thenReturn(plant);

        Hunk<PlatformBlockState> output = Hunk.newArrayHunk(1, 4, 1);
        output.set(0, 1, 0, air);
        output.set(0, 2, 0, occupied);

        DecoratorCore.placeFloatingSimple(
                decorator, 0, 0, 0, 0, 0, 3, output, ModuleLogs.discarding());

        assertSame(air, output.get(0, 1, 0));
        assertSame(occupied, output.get(0, 2, 0));
    }

    @Test
    public void tallSurfacePlantPlacesBothHalvesTogether() {
        ResolvedDecorator decorator = mock(ResolvedDecorator.class);
        PlatformBlockState support = sturdyState();
        PlatformBlockState lowerAir = airState();
        PlatformBlockState upperAir = airState();
        PlatformBlockState lower = mock(PlatformBlockState.class);
        PlatformBlockState upper = mock(PlatformBlockState.class);
        PlatformBlockState plant = tallPlantState(lower, upper);
        when(plant.canPlaceOnto(support)).thenReturn(true);
        when(decorator.pickBlock(anyDouble(), anyDouble())).thenReturn(plant);

        Hunk<PlatformBlockState> output = Hunk.newArrayHunk(1, 4, 1);
        output.set(0, 0, 0, support);
        output.set(0, 1, 0, lowerAir);
        output.set(0, 2, 0, upperAir);

        DecoratorCore.placeSurfaceSingle(
                decorator, 0, 0, 0, 0, 0, output, false, false, null, ModuleLogs.discarding());

        assertSame(lower, output.get(0, 1, 0));
        assertSame(upper, output.get(0, 2, 0));
    }

    @Test
    public void surfaceDecorantRejectsInvalidNativeSupport() {
        ResolvedDecorator decorator = mock(ResolvedDecorator.class);
        PlatformBlockState support = sturdyState();
        PlatformBlockState air = airState();
        PlatformBlockState decorant = mock(PlatformBlockState.class);
        when(decorant.canPlaceOnto(support)).thenReturn(false);
        when(decorator.pickBlock(anyDouble(), anyDouble())).thenReturn(decorant);

        Hunk<PlatformBlockState> output = Hunk.newArrayHunk(1, 3, 1);
        output.set(0, 0, 0, support);
        output.set(0, 1, 0, air);

        DecoratorCore.placeSurfaceSingle(
                decorator, 0, 0, 0, 0, 0, output, false, false, null, ModuleLogs.discarding());

        assertSame(air, output.get(0, 1, 0));
    }

    @Test
    public void forcedSurfaceBlockStillPlacesDecorantAboveIt() {
        ResolvedDecorator decorator = mock(ResolvedDecorator.class);
        PlatformBlockState support = sturdyState();
        PlatformBlockState farmland = sturdyState();
        PlatformBlockState air = airState();
        PlatformBlockState wheat = mock(PlatformBlockState.class);
        when(wheat.key()).thenReturn("minecraft:wheat[age=7]");
        when(decorator.forceBlock()).thenReturn(farmland);
        when(decorator.pickBlock(anyDouble(), anyDouble())).thenReturn(wheat);

        Hunk<PlatformBlockState> output = Hunk.newArrayHunk(1, 3, 1);
        output.set(0, 0, 0, support);
        output.set(0, 1, 0, air);

        DecoratorCore.placeSurfaceSingle(
                decorator, 0, 0, 0, 0, 0, output, false, false, null, ModuleLogs.discarding());

        assertSame(farmland, output.get(0, 0, 0));
        assertSame(wheat, output.get(0, 1, 0));
    }

    @Test
    public void descendingWeepingVinesUsePlantBodiesAndOneFreeEndTip() {
        PlatformBlockState vine = mock(PlatformBlockState.class);
        when(vine.key()).thenReturn("minecraft:weeping_vines");

        assertEquals("minecraft:weeping_vines_plant", DecoratorCore.stackedVineKey(vine, 3, 0));
        assertEquals("minecraft:weeping_vines_plant", DecoratorCore.stackedVineKey(vine, 3, 1));
        assertEquals("minecraft:weeping_vines", DecoratorCore.stackedVineKey(vine, 3, 2));
    }

    @Test
    public void ascendingTwistingVinesUsePlantBodiesAndOneFreeEndTip() {
        PlatformBlockState vine = mock(PlatformBlockState.class);
        when(vine.key()).thenReturn("minecraft:twisting_vines_plant");

        assertEquals("minecraft:twisting_vines_plant", DecoratorCore.stackedVineKey(vine, 3, 0));
        assertEquals("minecraft:twisting_vines_plant", DecoratorCore.stackedVineKey(vine, 3, 1));
        assertEquals("minecraft:twisting_vines", DecoratorCore.stackedVineKey(vine, 3, 2));
    }

    @Test
    public void boundDecoratorHooksWinWhenBukkitClassesArePresent() {
        PlatformBlockState vine = mock(PlatformBlockState.class);
        PlatformBlockState fixed = mock(PlatformBlockState.class);
        PlatformBlockState decorator = mock(PlatformBlockState.class);
        PlatformBlockState surface = mock(PlatformBlockState.class);
        Hunk<PlatformBlockState> output = Hunk.newArrayHunk(1, 1, 1);
        DecoratorPlatformHooks.FaceFixer faceFixer = mock(DecoratorPlatformHooks.FaceFixer.class);
        DecoratorPlatformHooks.SurfaceSturdiness sturdiness = mock(DecoratorPlatformHooks.SurfaceSturdiness.class);
        when(vine.isVineBlock()).thenReturn(true);
        when(faceFixer.fixFaces(vine, output, 0, 0, 0, 0, 0, null)).thenReturn(fixed);
        when(decorator.canPlaceOnto(surface)).thenReturn(true);
        when(sturdiness.canGoOn(surface)).thenReturn(true);
        DecoratorPlatformHooks.Bindings previous = DecoratorPlatformHooks.bind(faceFixer, sturdiness);
        try {
            assertSame(fixed, DecoratorCore.fixFacesForHunk(vine, output, 0, 0, 0, 0, 0, null));
            assertTrue(DecoratorCore.canGoOn(decorator, surface));
        } finally {
            DecoratorPlatformHooks.restore(previous);
        }
    }

    private PlatformBlockState airState() {
        PlatformBlockState air = mock(PlatformBlockState.class);
        when(air.isAir()).thenReturn(true);
        return air;
    }

    private PlatformBlockState tallPlantState() {
        return tallPlantState(mock(PlatformBlockState.class), mock(PlatformBlockState.class));
    }

    private PlatformBlockState tallPlantState(PlatformBlockState lower, PlatformBlockState upper) {
        PlatformBlockState plant = mock(PlatformBlockState.class);
        when(plant.key()).thenReturn("minecraft:tall_grass[half=lower]");
        when(plant.withProperty("half", "lower")).thenReturn(lower);
        when(plant.withProperty("half", "upper")).thenReturn(upper);
        return plant;
    }

    private PlatformBlockState sturdyState() {
        PlatformBlockState support = mock(PlatformBlockState.class);
        BlockData blockData = mock(BlockData.class);
        when(support.nativeHandle()).thenReturn(blockData);
        when(blockData.isFaceSturdy(any(), eq(BlockSupport.FULL))).thenReturn(true);
        return support;
    }
}
