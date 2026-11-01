package art.arcane.iris.modded;

import art.arcane.iris.generation.mantle.EngineMantle;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedBlockState;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeBlockFaces;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.matter.Matter;
import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ModdedDecoratorHooksTest {
    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void faceFixingExcludesTheUpperBoundaryWhileKeepingTheLastRowAccessible() {
        NativeBlockState state = mock(NativeBlockState.class);
        NativeBlockState lastRow = mock(NativeBlockState.class);
        Hunk<NativeBlockState> hunk = mock(Hunk.class);
        when(hunk.getHeight()).thenReturn(4);
        when(hunk.get(1, 3, 2)).thenReturn(lastRow);
        when(hunk.get(1, 4, 2)).thenThrow(new IndexOutOfBoundsException("upper boundary"));
        try (MockedStatic<NativeBlockFaces> faces = mockStatic(NativeBlockFaces.class)) {
            faces.when(() -> NativeBlockFaces.fixFaces(eq(state), any(NativeBlockFaces.Neighbors.class)))
                    .thenAnswer(invocation -> {
                        NativeBlockFaces.Neighbors neighbors = invocation.getArgument(1);
                        assertNull(neighbors.secondary(0, 1, 0));
                        assertSame(lastRow, neighbors.secondary(0, 0, 0));
                        return state;
                    });

            assertSame(state, new ModdedDecoratorHooks().fixFaces(state, hunk, 1, 2, 99, 3, 108, null));

            faces.verify(() -> NativeBlockFaces.fixFaces(eq(state), any(NativeBlockFaces.Neighbors.class)));
        }
        verify(hunk, never()).get(1, 4, 2);
        verify(hunk).get(1, 3, 2);
    }

    @Test
    public void stackedVineCurtainHangsFromTheWallItsTopStrandFaces() {
        Hunk<NativeBlockState> hunk = Hunk.newArrayHunk(16, 8, 16);
        hunk.set(8, 6, 8, stone());
        hunk.set(8, 5, 7, stone());
        hunk.set(9, 4, 8, stone());
        NativeBlockState vine = state(Blocks.VINE, Direction.NORTH);

        for (int y = 5; y >= 3; y--) {
            hunk.set(8, y, 8, fix(vine, hunk, y));
        }

        assertEquals(EnumSet.of(Direction.NORTH, Direction.UP), faces(hunk.get(8, 5, 8)));
        assertEquals(EnumSet.of(Direction.NORTH), faces(hunk.get(8, 4, 8)));
        assertEquals(EnumSet.of(Direction.NORTH), faces(hunk.get(8, 3, 8)));
    }

    @Test
    public void unsupportedAuthoredSideFaceOnALoneVineIsDroppedAndReattached() {
        NativeBlockState vine = state(Blocks.VINE, Direction.NORTH);
        Hunk<NativeBlockState> walled = Hunk.newArrayHunk(16, 8, 16);
        walled.set(9, 3, 8, stone());

        assertEquals(EnumSet.of(Direction.EAST), faces(fix(vine, walled, 3)));
        assertEquals(EnumSet.of(Direction.UP), faces(fix(vine, Hunk.newArrayHunk(16, 8, 16), 3)));
    }

    @Test
    public void glowLichenSideFaceIsNotHeldByTheLichenAboveIt() {
        NativeBlockState lichen = state(Blocks.GLOW_LICHEN, Direction.NORTH);
        Hunk<NativeBlockState> hunk = Hunk.newArrayHunk(16, 8, 16);
        hunk.set(8, 2, 8, stone());
        hunk.set(8, 4, 8, state(Blocks.GLOW_LICHEN, Direction.NORTH));

        assertEquals(EnumSet.of(Direction.DOWN), faces(fix(lichen, hunk, 3)));
    }

    @Test
    public void supportedAuthoredSideFaceKeepsItsDirection() {
        NativeBlockState vine = state(Blocks.VINE, Direction.NORTH);
        Hunk<NativeBlockState> hunk = Hunk.newArrayHunk(16, 8, 16);
        hunk.set(8, 3, 7, stone());
        hunk.set(9, 3, 8, stone());

        assertEquals(EnumSet.of(Direction.NORTH), faces(fix(vine, hunk, 3)));
    }

    @Test
    public void vineWithoutAuthoredSideFaceReattachesToEverySturdyNeighbour() {
        NativeBlockState vine = state(Blocks.VINE, Direction.UP);
        Hunk<NativeBlockState> hunk = Hunk.newArrayHunk(16, 8, 16);
        hunk.set(7, 3, 8, stone());
        hunk.set(8, 3, 9, stone());
        hunk.set(8, 4, 8, stone());

        assertEquals(EnumSet.of(Direction.SOUTH, Direction.WEST, Direction.UP), faces(fix(vine, hunk, 3)));
    }

    @SuppressWarnings("unchecked")
    private static NativeBlockState fix(NativeBlockState state, Hunk<NativeBlockState> hunk, int y) {
        EngineMantle mantle = mock(EngineMantle.class);
        Mantle<Matter> storage = mock(Mantle.class);
        NativeBlockState air = ModdedBlockState.of(Blocks.AIR.defaultBlockState(), null);
        when(mantle.getMantle()).thenReturn(storage);
        when(storage.get(anyInt(), anyInt(), anyInt(), eq(NativeBlockState.class))).thenReturn(air);
        return new ModdedDecoratorHooks().fixFaces(state, hunk, 8, 8, 8, y, 8, mantle);
    }

    private static NativeBlockState stone() {
        return ModdedBlockState.of(Blocks.STONE.defaultBlockState(), null);
    }

    private static NativeBlockState state(Block block, Direction... faces) {
        BlockState state = block.defaultBlockState();
        for (Direction face : faces) {
            state = state.setValue(property(face), Boolean.TRUE);
        }
        return ModdedBlockState.of(state, null);
    }

    private static Set<Direction> faces(NativeBlockState state) {
        BlockState handle = (BlockState) state.nativeHandle();
        EnumSet<Direction> faces = EnumSet.noneOf(Direction.class);
        for (Direction face : Direction.values()) {
            BooleanProperty property = property(face);
            if (handle.hasProperty(property) && handle.getValue(property)) {
                faces.add(face);
            }
        }
        return faces;
    }

    private static BooleanProperty property(Direction face) {
        return switch (face) {
            case NORTH -> BlockStateProperties.NORTH;
            case EAST -> BlockStateProperties.EAST;
            case SOUTH -> BlockStateProperties.SOUTH;
            case WEST -> BlockStateProperties.WEST;
            case UP -> BlockStateProperties.UP;
            case DOWN -> BlockStateProperties.DOWN;
        };
    }
}
