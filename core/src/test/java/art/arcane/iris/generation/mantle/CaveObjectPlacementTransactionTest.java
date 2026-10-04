/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.generation.mantle;

import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.subterrain.SubterrainCell;
import art.arcane.iris.generation.subterrain.SubterrainRoom;
import art.arcane.iris.generation.subterrain.IrisSubterrainFamily;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.structure.object.IObjectPlacer;
import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CaveObjectPlacementTransactionTest {
    @Test
    public void validPlacementCommitsBlocksTilesAndMetadataTogether() {
        IObjectPlacer delegate = createPlacer(128, 80, 20, 60);
        NativeBlockState state = mock(NativeBlockState.class);
        TileData tile = mock(TileData.class);
        CaveObjectPlacementTransaction transaction = new CaveObjectPlacementTransaction(delegate, 20, 10);

        transaction.set(4, 30, 7, state);
        transaction.setTile(4, 30, 7, tile);
        transaction.setData(4, 30, 7, "object@1");

        assertSame(state, transaction.get(4, 30, 7));
        assertEquals("object@1", transaction.getData(4, 30, 7, String.class));
        verify(delegate, never()).set(anyInt(), anyInt(), anyInt(), any());
        verify(delegate, never()).setTile(anyInt(), anyInt(), anyInt(), any());
        verify(delegate, never()).setData(anyInt(), anyInt(), anyInt(), any());

        assertEquals(CaveObjectPlacementTransaction.CommitResult.COMMITTED, transaction.commit());
        org.mockito.InOrder order = inOrder(delegate);
        order.verify(delegate).set(4, 30, 7, state);
        order.verify(delegate).setTile(4, 30, 7, tile);
        order.verify(delegate).setData(4, 30, 7, "object@1");
    }

    @Test
    public void oneInvalidMetadataWriteRejectsTheWholePlacement() {
        Engine engine = mock(Engine.class);
        when(engine.getHeight()).thenReturn(128);
        when(engine.getHeight(eq(4), anyInt(), eq(true))).thenReturn(80);
        when(engine.getHeight(eq(5), anyInt(), eq(true))).thenReturn(35);
        IObjectPlacer delegate = mock(IObjectPlacer.class);
        when(delegate.getEngine()).thenReturn(engine);
        when(delegate.isCarved(anyInt(), anyInt(), anyInt())).thenReturn(true);
        CaveObjectPlacementTransaction transaction = new CaveObjectPlacementTransaction(delegate, 20, 10);

        transaction.set(4, 30, 7, mock(NativeBlockState.class));
        transaction.setData(5, 26, 7, "ghost");

        assertEquals(CaveObjectPlacementTransaction.CommitResult.REJECTED_BOUNDS, transaction.commit());
        verify(delegate, never()).set(anyInt(), anyInt(), anyInt(), any());
        verify(delegate, never()).setData(anyInt(), anyInt(), anyInt(), any());
    }

    @Test
    public void perColumnCaveCeilingRejectsTheWholePlacement() {
        Engine engine = mock(Engine.class);
        when(engine.getHeight()).thenReturn(128);
        when(engine.getHeight(anyInt(), anyInt(), eq(true))).thenReturn(80);
        IObjectPlacer delegate = mock(IObjectPlacer.class);
        when(delegate.getEngine()).thenReturn(engine);
        when(delegate.isCarved(eq(4), anyInt(), eq(7))).thenAnswer(invocation -> invocation.<Integer>getArgument(1) <= 60);
        when(delegate.isCarved(eq(5), anyInt(), eq(7))).thenAnswer(invocation -> invocation.<Integer>getArgument(1) <= 24);
        CaveObjectPlacementTransaction transaction = new CaveObjectPlacementTransaction(delegate, 20, 10);

        transaction.set(4, 30, 7, mock(NativeBlockState.class));
        transaction.setData(5, 25, 7, "outside-cave");

        assertEquals(CaveObjectPlacementTransaction.CommitResult.REJECTED_BOUNDS, transaction.commit());
        verify(delegate, never()).set(anyInt(), anyInt(), anyInt(), any());
        verify(delegate, never()).setData(anyInt(), anyInt(), anyInt(), any());
    }

    @Test
    public void exactBurialBoundaryIsAllowedAndShallowerAnchorsAreExcluded() {
        IObjectPlacer delegate = createPlacer(128, 80, 20, 90);
        CaveObjectPlacementTransaction transaction = new CaveObjectPlacementTransaction(delegate, 20, 10);
        transaction.set(4, 70, 7, mock(NativeBlockState.class));

        assertEquals(CaveObjectPlacementTransaction.CommitResult.COMMITTED, transaction.commit());
        assertEquals(71, MantleObjectComponent.caveAnchorScanUpperBound(128, 80, 10));
        assertEquals(61, MantleObjectComponent.caveAnchorScanUpperBound(128, 80, 20));
    }

    @Test
    public void ownedDryAndWetRoomDecorationCanCommitWithoutWeakeningSolidGuards() {
        for (SubterrainCell.Kind kind : new SubterrainCell.Kind[]{SubterrainCell.Kind.AIR, SubterrainCell.Kind.WATER}) {
            IObjectPlacer delegate = createPlacer(128, 80, 20, 60);
            SubterrainRoom room = new SubterrainRoom("cenote", "wet-cave", IrisSubterrainFamily.CENOTE,
                    4, 35, 7, 4, 21, 7, 19, 61, 8, 34, false, false, kind);
            when(delegate.getEngine().getSubterrainCell(4, 30, 7))
                    .thenReturn(new SubterrainCell(kind, kind == SubterrainCell.Kind.WATER ? "minecraft:water" : "minecraft:cave_air", room));
            NativeBlockState decoration = mock(NativeBlockState.class);
            CaveObjectPlacementTransaction transaction = new CaveObjectPlacementTransaction(delegate, 20, 10);
            transaction.set(4, 30, 7, decoration);
            assertEquals(CaveObjectPlacementTransaction.CommitResult.COMMITTED, transaction.commit());
            verify(delegate).set(4, 30, 7, decoration);
        }
    }

    @Test
    public void ownedWaterAndLavaUseTheirExclusiveAbsoluteFluidHeadAtTheOriginalAnchor() {
        for (int minimumY : new int[]{-64, 32}) {
            for (SubterrainCell.Kind kind : new SubterrainCell.Kind[]{SubterrainCell.Kind.WATER, SubterrainCell.Kind.LAVA}) {
                IObjectPlacer delegate = createPlacer(256, 220, 120, 160);
                Engine engine = delegate.getEngine();
                when(engine.getMinHeight()).thenReturn(minimumY);
                when(delegate.getFluidHeight(4, 7)).thenReturn(16);
                SubterrainRoom room = new SubterrainRoom("raised-room", "wet-cave", IrisSubterrainFamily.CENOTE,
                        4, minimumY + 130, 7, 4, minimumY + 121, 7, minimumY + 119,
                        minimumY + 161, 8, minimumY + 126, false, false, kind);
                when(engine.getSubterrainCell(4, 123, 7)).thenReturn(new SubterrainCell(kind,
                        kind == SubterrainCell.Kind.WATER ? "minecraft:water" : "minecraft:lava", room));
                CaveObjectPlacementTransaction transaction = new CaveObjectPlacementTransaction(delegate, 123, 10);
                assertEquals(127, transaction.getFluidHeight(4, 7));
                verify(engine).getSubterrainCell(4, 123, 7);
                verify(delegate, never()).getFluidHeight(4, 7);
            }
        }
    }

    @Test
    public void nonFluidAnchorsPreserveTheDelegateCoordinateFluidHeight() {
        for (SubterrainCell.Kind kind : new SubterrainCell.Kind[]{SubterrainCell.Kind.AIR, SubterrainCell.Kind.SOLID, SubterrainCell.Kind.OUTSIDE}) {
            IObjectPlacer delegate = createPlacer(128, 80, 20, 60);
            when(delegate.getFluidHeight(4, 7)).thenReturn(51);
            SubterrainRoom room = new SubterrainRoom("dry-room", "dry-cave", IrisSubterrainFamily.CENOTE,
                    4, 35, 7, 4, 21, 7, 19, 61, 8, 24, false, false, kind);
            SubterrainCell cell = kind == SubterrainCell.Kind.OUTSIDE ? SubterrainCell.OUTSIDE
                    : new SubterrainCell(kind, kind == SubterrainCell.Kind.AIR ? "minecraft:cave_air" : "minecraft:stone", room);
            when(delegate.getEngine().getSubterrainCell(4, 20, 7)).thenReturn(cell);
            CaveObjectPlacementTransaction transaction = new CaveObjectPlacementTransaction(delegate, 20, 10);
            assertEquals(51, transaction.getFluidHeight(4, 7));
            verify(delegate).getFluidHeight(4, 7);
        }
    }

    @Test
    public void featurePillarRejectsAllObjectMutations() {
        IObjectPlacer delegate = createPlacer(128, 80, 20, 60);
        SubterrainRoom room = new SubterrainRoom("cenote", "wet-cave", IrisSubterrainFamily.CENOTE,
                4, 35, 7, 4, 21, 7, 19, 61, 8, 24, false, true, SubterrainCell.Kind.SOLID);
        when(delegate.getEngine().getSubterrainCell(4, 30, 7))
                .thenReturn(new SubterrainCell(SubterrainCell.Kind.SOLID, "minecraft:stone", room));
        CaveObjectPlacementTransaction transaction = new CaveObjectPlacementTransaction(delegate, 20, 10);
        transaction.set(4, 30, 7, mock(NativeBlockState.class));
        transaction.setData(5, 30, 7, "decoration");
        assertEquals(CaveObjectPlacementTransaction.CommitResult.REJECTED_SUBTERRAIN, transaction.commit());
        verify(delegate, never()).set(anyInt(), anyInt(), anyInt(), any());
        verify(delegate, never()).setData(anyInt(), anyInt(), anyInt(), any());
    }

    @Test
    public void dryHydrologyHeadroomRejectsTheWholePlacement() {
        IObjectPlacer delegate = createPlacer(128, 80, 20, 90);
        when(delegate.getData(4, 30, 7, HydrologyCaveCell.class))
                .thenReturn(HydrologyCaveCell.of(HydrologyCaveAction.DRY_AIR));
        CaveObjectPlacementTransaction transaction = new CaveObjectPlacementTransaction(delegate, 20, 10);

        transaction.set(4, 30, 7, mock(NativeBlockState.class));
        transaction.setData(5, 30, 7, "object@1");

        assertEquals(CaveObjectPlacementTransaction.CommitResult.REJECTED_HYDROLOGY, transaction.commit());
        verify(delegate, never()).set(anyInt(), anyInt(), anyInt(), any());
        verify(delegate, never()).setData(anyInt(), anyInt(), anyInt(), any());
    }

    @Test
    public void transitionIntersectionRejectsTheWholePlacement() {
        Engine engine = mock(Engine.class);
        when(engine.getHeight()).thenReturn(128);
        IrisComplex complex = mock(IrisComplex.class);
        when(engine.getComplex()).thenReturn(complex);
        when(complex.allowsNewDiscreteContentAt(4, 7)).thenReturn(false);
        IObjectPlacer delegate = mock(IObjectPlacer.class);
        when(delegate.getEngine()).thenReturn(engine);
        CaveObjectPlacementTransaction transaction = new CaveObjectPlacementTransaction(delegate, 20, 10);

        transaction.set(4, 30, 7, mock(NativeBlockState.class));
        transaction.setData(4, 30, 7, "object@1");

        assertEquals(CaveObjectPlacementTransaction.CommitResult.REJECTED_TRANSITION, transaction.commit());
        verify(delegate, never()).set(anyInt(), anyInt(), anyInt(), any());
        verify(delegate, never()).setData(anyInt(), anyInt(), anyInt(), any());
    }

    private IObjectPlacer createPlacer(int worldHeight, int surfaceHeight, int caveFloor, int caveCeiling) {
        Engine engine = mock(Engine.class);
        when(engine.getHeight()).thenReturn(worldHeight);
        when(engine.getHeight(anyInt(), anyInt(), eq(true))).thenReturn(surfaceHeight);
        IObjectPlacer delegate = mock(IObjectPlacer.class);
        when(delegate.getEngine()).thenReturn(engine);
        when(delegate.isCarved(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
            int y = invocation.getArgument(1);
            return y >= caveFloor && y < caveCeiling;
        });
        return delegate;
    }
}
