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

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.spi.PlatformBlockState;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FloatingObjectPlacementTransactionTest {
    @Test
    public void unsupportedFinalTerrainRejectsBlocksTilesAndMarkersTogether() {
        IslandObjectPlacer delegate = createPlacer(false);
        FloatingObjectPlacementTransaction transaction = new FloatingObjectPlacementTransaction(delegate);
        PlatformBlockState first = mock(PlatformBlockState.class);
        PlatformBlockState second = mock(PlatformBlockState.class);
        TileData tile = mock(TileData.class);

        transaction.set(15, 101, 8, first);
        transaction.setData(15, 101, 8, "tree@7");
        transaction.set(16, 101, 8, second);
        transaction.setTile(16, 101, 8, tile);
        transaction.setData(16, 101, 8, "tree@7");

        assertSame(second, transaction.get(16, 101, 8));
        assertEquals("tree@7", transaction.getData(16, 101, 8, String.class));
        verify(delegate, never()).set(anyInt(), anyInt(), anyInt(), any());
        verify(delegate, never()).setTile(anyInt(), anyInt(), anyInt(), any());
        verify(delegate, never()).setData(anyInt(), anyInt(), anyInt(), any());

        assertEquals(FloatingObjectPlacementTransaction.CommitResult.REJECTED_SUPPORT, transaction.commit());
        verify(delegate, never()).set(anyInt(), anyInt(), anyInt(), any());
        verify(delegate, never()).setTile(anyInt(), anyInt(), anyInt(), any());
        verify(delegate, never()).setData(anyInt(), anyInt(), anyInt(), any());
    }

    @Test
    public void supportedNeighborChunkCommitsBlocksTilesAndMarkers() {
        IslandObjectPlacer delegate = createPlacer(true);
        FloatingObjectPlacementTransaction transaction = new FloatingObjectPlacementTransaction(delegate);
        PlatformBlockState first = mock(PlatformBlockState.class);
        PlatformBlockState second = mock(PlatformBlockState.class);
        TileData tile = mock(TileData.class);

        transaction.set(15, 101, 8, first);
        transaction.setData(15, 101, 8, "tree@7");
        transaction.set(16, 101, 8, second);
        transaction.setTile(16, 101, 8, tile);
        transaction.setData(16, 101, 8, "tree@7");

        assertEquals(FloatingObjectPlacementTransaction.CommitResult.COMMITTED, transaction.commit());
        org.mockito.InOrder order = inOrder(delegate);
        order.verify(delegate).set(15, 101, 8, first);
        order.verify(delegate).setData(15, 101, 8, "tree@7");
        order.verify(delegate).set(16, 101, 8, second);
        order.verify(delegate).setTile(16, 101, 8, tile);
        order.verify(delegate).setData(16, 101, 8, "tree@7");

        assertEquals(FloatingObjectPlacementTransaction.CommitResult.EMPTY, transaction.commit());
        verify(delegate).set(16, 101, 8, second);
    }

    private IslandObjectPlacer createPlacer(boolean supportNeighborChunk) {
        IslandObjectPlacer placer = mock(IslandObjectPlacer.class);
        Engine engine = mock(Engine.class);
        when(engine.getHeight()).thenReturn(384);
        when(placer.getEngine()).thenReturn(engine);
        when(placer.canWriteObjectBlock(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
            int x = invocation.getArgument(0);
            return x == 15 || (supportNeighborChunk && x == 16);
        });
        return placer;
    }
}
