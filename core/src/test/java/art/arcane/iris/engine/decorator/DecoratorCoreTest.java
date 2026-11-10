package art.arcane.iris.engine.decorator;

import art.arcane.iris.engine.modifier.ApiChunk;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.block.BlockState;
import com.volmit.host.ArrayHunk;
import com.volmit.host.ModuleLogs;
import com.volmit.hunk.Hunk;
import com.volmit.geometry.BlockBox;
import com.volmit.mantle.MantleLayer;
import com.volmit.mantle.MantleVisitor;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DecoratorCoreTest {
    private static final BlockState AIR = ApiChunk.AIR;
    private static final BlockState GRASS_BLOCK = new BlockState("minecraft:grass_block");
    private static final BlockState STONE = new BlockState("minecraft:stone");
    private static final BlockState POPPY = new BlockState("minecraft:poppy");
    private static final BlockState TALL_GRASS = BlockState.parse("minecraft:tall_grass[half=lower]");

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
    public void pickDecorator_contentFailure_skipsThatDecorator() {
        ResolvedDecorator broken = mock(ResolvedDecorator.class);
        when(broken.passesChanceGate(anyDouble(), anyDouble())).thenThrow(new IllegalArgumentException("malformed decorator"));
        ResolvedDecorator healthy = mock(ResolvedDecorator.class);
        when(healthy.passesChanceGate(anyDouble(), anyDouble())).thenReturn(true);

        ResolvedDecorator result = DecoratorCore.pickDecorator(new ResolvedDecorator[]{broken, healthy}, new RNG(2L), 0.0, 0.0, ModuleLogs.discarding());
        assertSame(healthy, result);
    }

    @Test
    public void pickDecorator_engineFailure_endsTheChunk() {
        ResolvedDecorator closed = mock(ResolvedDecorator.class);
        RejectedExecutionException failure = new RejectedExecutionException("pool shut down");
        when(closed.passesChanceGate(anyDouble(), anyDouble())).thenThrow(failure);

        assertSame(failure, assertThrows(RejectedExecutionException.class, () ->
                DecoratorCore.pickDecorator(new ResolvedDecorator[]{closed}, new RNG(2L), 0.0, 0.0, ModuleLogs.discarding())));
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
        when(decorator.stackHeight(anyDouble(), anyDouble())).thenReturn(1);
        when(decorator.pickTopBlock(anyDouble(), anyDouble())).thenReturn(POPPY);

        Hunk<BlockState> output = hunk(1, 4, 1);
        output.set(0, 1, 0, GRASS_BLOCK);
        DecoratorCore.PlaceOpts opts = new DecoratorCore.PlaceOpts();

        DecoratorCore.placeStackUp(decorator, scene(output), 0, 0, 0, 0, 1, 3, opts);

        assertSame(GRASS_BLOCK, output.get(0, 1, 0));
        assertSame(POPPY, output.get(0, 2, 0));
    }

    @Test
    public void singleStackDoesNotOverwriteOccupiedTarget() {
        ResolvedDecorator decorator = mock(ResolvedDecorator.class);
        when(decorator.stackHeight(anyDouble(), anyDouble())).thenReturn(1);
        when(decorator.pickTopBlock(anyDouble(), anyDouble())).thenReturn(POPPY);

        Hunk<BlockState> output = hunk(1, 4, 1);
        output.set(0, 1, 0, GRASS_BLOCK);
        output.set(0, 2, 0, STONE);

        DecoratorCore.placeStackUp(decorator, scene(output), 0, 0, 0, 0, 1, 3, new DecoratorCore.PlaceOpts());

        assertSame(GRASS_BLOCK, output.get(0, 1, 0));
        assertSame(STONE, output.get(0, 2, 0));
    }

    @Test
    public void multiStackStopsBeforeOccupiedTarget() {
        ResolvedDecorator decorator = mock(ResolvedDecorator.class);
        BlockState decorant = new BlockState("minecraft:tall_grass");
        when(decorator.stackHeight(anyDouble(), anyDouble())).thenReturn(3);
        when(decorator.topThreshold()).thenReturn(0.75);
        when(decorator.pickBlock(anyDouble(), anyDouble())).thenReturn(decorant);
        when(decorator.pickTopBlock(anyDouble(), anyDouble())).thenReturn(decorant);

        Hunk<BlockState> output = hunk(1, 5, 1);
        output.set(0, 1, 0, GRASS_BLOCK);
        output.set(0, 3, 0, STONE);

        DecoratorCore.placeStackUp(decorator, scene(output), 0, 0, 0, 0, 1, 4, new DecoratorCore.PlaceOpts());

        assertSame(decorant, output.get(0, 2, 0));
        assertSame(STONE, output.get(0, 3, 0));
        assertSame(AIR, output.get(0, 4, 0));
    }

    @Test
    public void descendingStackStopsAtLocalWorldFloor() {
        ResolvedDecorator decorator = mock(ResolvedDecorator.class);
        when(decorator.stackHeight(anyDouble(), anyDouble())).thenReturn(2);
        when(decorator.topThreshold()).thenReturn(1.0);
        when(decorator.pickBlock(anyDouble(), anyDouble())).thenReturn(STONE);
        when(decorator.pickTopBlock(anyDouble(), anyDouble())).thenReturn(STONE);

        Hunk<BlockState> output = hunk(1, 2, 1);

        DecoratorCore.placeStackDown(decorator, scene(output), 0, 0, 0, 0, 0, -64, 2, new DecoratorCore.PlaceOpts());

        assertSame(STONE, output.get(0, 0, 0));
    }

    @Test
    public void singleDescendingStackPreservesTargetWhenPickIsNull() {
        ResolvedDecorator decorator = mock(ResolvedDecorator.class);
        when(decorator.stackHeight(anyDouble(), anyDouble())).thenReturn(1);

        Hunk<BlockState> output = hunk(1, 2, 1);
        output.set(0, 0, 0, STONE);

        DecoratorCore.placeStackDown(decorator, scene(output), 0, 0, 0, 0, 0, 0, 1, new DecoratorCore.PlaceOpts());

        assertSame(STONE, output.get(0, 0, 0));
    }

    @Test
    public void multiDescendingStackStopsWhenPickIsNull() {
        ResolvedDecorator decorator = mock(ResolvedDecorator.class);
        when(decorator.stackHeight(anyDouble(), anyDouble())).thenReturn(2);
        when(decorator.topThreshold()).thenReturn(1.0);

        Hunk<BlockState> output = hunk(1, 2, 1);
        output.set(0, 0, 0, STONE);
        output.set(0, 1, 0, GRASS_BLOCK);

        DecoratorCore.placeStackDown(decorator, scene(output), 0, 0, 0, 0, 1, 0, 2, new DecoratorCore.PlaceOpts());

        assertSame(STONE, output.get(0, 0, 0));
        assertSame(GRASS_BLOCK, output.get(0, 1, 0));
    }

    @Test
    public void floatingStackStopsAtHunkCeiling() {
        ResolvedDecorator decorator = mock(ResolvedDecorator.class);
        when(decorator.stackHeight(anyDouble(), anyDouble())).thenReturn(3);
        when(decorator.topThreshold()).thenReturn(1.0);
        when(decorator.pickBlock(anyDouble(), anyDouble())).thenReturn(STONE);
        when(decorator.pickTopBlock(anyDouble(), anyDouble())).thenReturn(STONE);

        Hunk<BlockState> output = hunk(1, 2, 1);
        int placed = DecoratorCore.placeFloatingStacked(decorator, scene(output), 0, 0, 0, 0, 0, 3);

        assertEquals(1, placed);
        assertSame(STONE, output.get(0, 1, 0));
    }

    @Test
    public void tallSurfacePlantDoesNotPlaceWhenUpperTargetIsOccupied() {
        ResolvedDecorator decorator = mock(ResolvedDecorator.class);
        when(decorator.pickBlock(anyDouble(), anyDouble())).thenReturn(TALL_GRASS);

        Hunk<BlockState> output = hunk(1, 4, 1);
        output.set(0, 0, 0, GRASS_BLOCK);
        output.set(0, 2, 0, STONE);

        DecoratorCore.placeSurfaceSingle(decorator, scene(output), 0, 0, 0, 0, 0, false, false);

        assertSame(AIR, output.get(0, 1, 0));
        assertSame(STONE, output.get(0, 2, 0));
    }

    @Test
    public void tallFloatingPlantDoesNotPlaceWhenUpperTargetIsOccupied() {
        ResolvedDecorator decorator = mock(ResolvedDecorator.class);
        when(decorator.pickBlock(anyDouble(), anyDouble())).thenReturn(TALL_GRASS);

        Hunk<BlockState> output = hunk(1, 4, 1);
        output.set(0, 2, 0, STONE);

        DecoratorCore.placeFloatingSimple(decorator, scene(output), 0, 0, 0, 0, 0, 3);

        assertSame(AIR, output.get(0, 1, 0));
        assertSame(STONE, output.get(0, 2, 0));
    }

    @Test
    public void tallSurfacePlantPlacesBothHalvesTogether() {
        ResolvedDecorator decorator = mock(ResolvedDecorator.class);
        when(decorator.pickBlock(anyDouble(), anyDouble())).thenReturn(TALL_GRASS);

        Hunk<BlockState> output = hunk(1, 4, 1);
        output.set(0, 0, 0, GRASS_BLOCK);

        DecoratorCore.placeSurfaceSingle(decorator, scene(output), 0, 0, 0, 0, 0, false, false);

        assertEquals(BlockState.parse("minecraft:tall_grass[half=lower]"), output.get(0, 1, 0));
        assertEquals(BlockState.parse("minecraft:tall_grass[half=upper]"), output.get(0, 2, 0));
    }

    @Test
    public void surfaceDecorantRejectsInvalidSupport() {
        ResolvedDecorator decorator = mock(ResolvedDecorator.class);
        when(decorator.pickBlock(anyDouble(), anyDouble())).thenReturn(POPPY);

        Hunk<BlockState> output = hunk(1, 3, 1);
        output.set(0, 0, 0, STONE);

        DecoratorCore.placeSurfaceSingle(decorator, scene(output), 0, 0, 0, 0, 0, false, false);

        assertSame(AIR, output.get(0, 1, 0));
    }

    @Test
    public void forcedSurfaceBlockStillPlacesDecorantAboveIt() {
        ResolvedDecorator decorator = mock(ResolvedDecorator.class);
        BlockState farmland = new BlockState("minecraft:farmland");
        BlockState wheat = BlockState.parse("minecraft:wheat[age=7]");
        when(decorator.forceBlock()).thenReturn(farmland);
        when(decorator.pickBlock(anyDouble(), anyDouble())).thenReturn(wheat);

        Hunk<BlockState> output = hunk(1, 3, 1);
        output.set(0, 0, 0, STONE);

        DecoratorCore.placeSurfaceSingle(decorator, scene(output), 0, 0, 0, 0, 0, false, false);

        assertSame(farmland, output.get(0, 0, 0));
        assertSame(wheat, output.get(0, 1, 0));
    }

    @Test
    public void descendingWeepingVinesUsePlantBodiesAndOneFreeEndTip() {
        BlockState vine = new BlockState("minecraft:weeping_vines");

        assertEquals("minecraft:weeping_vines_plant", DecoratorCore.stackedVineMaterial(vine, 3, 0).toString());
        assertEquals("minecraft:weeping_vines_plant", DecoratorCore.stackedVineMaterial(vine, 3, 1).toString());
        assertEquals("minecraft:weeping_vines", DecoratorCore.stackedVineMaterial(vine, 3, 2).toString());
    }

    @Test
    public void ascendingTwistingVinesUsePlantBodiesAndOneFreeEndTip() {
        BlockState vine = new BlockState("minecraft:twisting_vines_plant");

        assertEquals("minecraft:twisting_vines_plant", DecoratorCore.stackedVineMaterial(vine, 3, 0).toString());
        assertEquals("minecraft:twisting_vines_plant", DecoratorCore.stackedVineMaterial(vine, 3, 1).toString());
        assertEquals("minecraft:twisting_vines", DecoratorCore.stackedVineMaterial(vine, 3, 2).toString());
        assertNull(DecoratorCore.stackedVineMaterial(STONE, 3, 2));
    }

    @Test
    public void aVineFacesTheNeighboursThatCanHoldIt() {
        Hunk<BlockState> output = hunk(3, 3, 3);
        output.set(2, 1, 1, STONE);

        BlockState fixed = DecoratorCore.fixFacesForHunk(scene(output), vine(), 1, 1, 1, 1, 1);

        assertEquals("true", fixed.properties().get("east"));
        for (String face : new String[]{"north", "south", "west", "up", "down"}) {
            assertEquals(face, "false", fixed.properties().get(face));
        }
    }

    @Test
    public void aVineHeldByBlocksThePlannedMantleHoldsOutsideTheChunkFacesThem() {
        Hunk<BlockState> output = hunk(3, 3, 3);
        Map<String, BlockState> planned = new HashMap<>();
        planned.put("-1,1,1", STONE);

        BlockState fixed = DecoratorCore.fixFacesForHunk(scene(output, plannedLayer(planned)), vine(), 0, 1, 0, 1, 1);

        assertEquals("true", fixed.properties().get("west"));
        assertEquals("false", fixed.properties().get("east"));
    }

    @Test
    public void aVineNothingHoldsFacesDown() {
        BlockState fixed = DecoratorCore.fixFacesForHunk(scene(hunk(3, 3, 3)), vine(), 1, 1, 1, 1, 1);

        assertEquals("true", fixed.properties().get("down"));
        assertEquals("false", fixed.properties().get("up"));
    }

    @Test
    public void otherBlocksAreNotFaced() {
        assertSame(STONE, DecoratorCore.fixFacesForHunk(scene(hunk(1, 1, 1)), STONE, 0, 0, 0, 0, 0));
    }

    @Test
    public void aCurtainHangsFromTheWallItsTopStrandFaces() {
        Hunk<BlockState> output = hunk(16, 8, 16);
        output.set(8, 6, 8, STONE);
        output.set(8, 5, 7, STONE);
        output.set(9, 4, 8, STONE);

        placeCurtain(output);

        assertEquals(Set.of("north", "up"), setFaces(output.get(8, 5, 8)));
        assertEquals(Set.of("north"), setFaces(output.get(8, 4, 8)));
        assertEquals(Set.of("north"), setFaces(output.get(8, 3, 8)));
    }

    @Test
    public void aCurtainHangsUnderAnOpenCeiling() {
        Hunk<BlockState> output = hunk(16, 8, 16);
        output.set(8, 6, 8, STONE);

        placeCurtain(output);

        assertEquals(Set.of("north", "up"), setFaces(output.get(8, 5, 8)));
        assertEquals(Set.of("north"), setFaces(output.get(8, 4, 8)));
        assertEquals(Set.of("north"), setFaces(output.get(8, 3, 8)));
    }

    @Test
    public void anAuthoredSideFaceNothingHoldsIsDroppedAndTheVineReattaches() {
        Hunk<BlockState> walled = hunk(16, 8, 16);
        walled.set(9, 3, 8, STONE);

        assertEquals(Set.of("east"), setFaces(DecoratorCore.fixFacesForHunk(scene(walled), authoredVine("north"), 8, 8, 8, 3, 8)));
        assertEquals(Set.of("up"), setFaces(DecoratorCore.fixFacesForHunk(scene(hunk(16, 8, 16)), authoredVine("north"), 8, 8, 8, 3, 8)));
    }

    @Test
    public void anAuthoredSideFaceAWallHoldsKeepsItsDirection() {
        Hunk<BlockState> output = hunk(16, 8, 16);
        output.set(8, 3, 7, STONE);
        output.set(9, 3, 8, STONE);

        assertEquals(Set.of("north"), setFaces(DecoratorCore.fixFacesForHunk(scene(output), authoredVine("north"), 8, 8, 8, 3, 8)));
    }

    @Test
    public void aVineWithoutAnAuthoredSideFaceReattachesToEverySturdyNeighbour() {
        Hunk<BlockState> output = hunk(16, 8, 16);
        output.set(7, 3, 8, STONE);
        output.set(8, 3, 9, STONE);
        output.set(8, 4, 8, STONE);

        BlockState fixed = DecoratorCore.fixFacesForHunk(scene(output), authoredVine("up"), 8, 8, 8, 3, 8);

        assertEquals(Set.of("south", "west", "up"), setFaces(fixed));
    }

    @Test
    public void glowLichenSideFaceIsNotHeldByTheLichenAboveIt() {
        BlockState lichen = BlockState.parse("minecraft:glow_lichen[down=false,east=false,north=true,south=false,up=false,west=false]");
        Hunk<BlockState> output = hunk(16, 8, 16);
        output.set(8, 2, 8, STONE);
        output.set(8, 4, 8, lichen);

        assertEquals(Set.of("down"), setFaces(DecoratorCore.fixFacesForHunk(scene(output), lichen, 8, 8, 8, 3, 8)));
    }

    private static void placeCurtain(Hunk<BlockState> output) {
        ResolvedDecorator decorator = mock(ResolvedDecorator.class);
        BlockState vine = authoredVine("north");
        when(decorator.stackHeight(anyDouble(), anyDouble())).thenReturn(3);
        when(decorator.topThreshold()).thenReturn(1.0);
        when(decorator.pickBlock(anyDouble(), anyDouble())).thenReturn(vine);
        when(decorator.pickTopBlock(anyDouble(), anyDouble())).thenReturn(vine);

        DecoratorCore.placeStackDown(decorator, scene(output), 8, 8, 8, 8, 5, 0, 3, new DecoratorCore.PlaceOpts());
    }

    private static BlockState authoredVine(String face) {
        return BlockState.parse("minecraft:vine[east=false,north=false,south=false,up=false,west=false]").withProperty(face, "true");
    }

    private static Set<String> setFaces(BlockState state) {
        return state.properties().entrySet().stream()
                .filter(property -> "true".equals(property.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private static BlockState vine() {
        return BlockState.parse("minecraft:vine[down=false,east=false,north=false,south=false,up=false,west=false]");
    }

    private static Hunk<BlockState> hunk(int width, int height, int depth) {
        return new ArrayHunk<>(BlockState.class, width, height, depth, AIR);
    }

    private static DecorationScene scene(Hunk<BlockState> blocks) {
        return scene(blocks, null);
    }

    private static DecorationScene scene(Hunk<BlockState> blocks, MantleLayer<BlockState> planned) {
        return new DecorationScene(blocks, ApiChunk.blockRegistry(), null, planned, null, 1L, ModuleLogs.discarding());
    }

    /** Blocks planned at "x,y,z" in world coordinates; everywhere else reads none. */
    private static MantleLayer<BlockState> plannedLayer(Map<String, BlockState> planned) {
        return new MantleLayer<>() {
            @Override
            public BlockState get(int x, int y, int z) {
                return planned.get(x + "," + y + "," + z);
            }

            @Override
            public void set(int x, int y, int z, BlockState value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void remove(int x, int y, int z) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void visit(BlockBox area, MantleVisitor<BlockState> visitor) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void clear(BlockBox area) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
