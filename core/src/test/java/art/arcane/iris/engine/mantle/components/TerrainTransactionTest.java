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

package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.modules.objects.TerrainTransaction;
import art.arcane.iris.modules.objects.TerrainTransaction.Commit;
import com.volmit.block.BlockState;
import com.volmit.object.BlockEntityData;
import com.volmit.placement.PlacementRecord;
import com.volmit.placement.PlacementTerrain;
import com.volmit.resource.ResourceId;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** A floating object that spills into a column its island does not support is written whole or not at all. */
public class TerrainTransactionTest {
    private static final BlockState FIRST = BlockState.parse("minecraft:oak_log");
    private static final BlockState SECOND = BlockState.parse("minecraft:chest");
    private static final BlockEntityData LOOT = new BlockEntityData(ResourceId.parse("test:chest"), 1, new byte[0]);
    private static final PlacementRecord TREE = PlacementRecord.of(ResourceId.parse("test:tree"), 7);

    @Test
    public void unsupportedFinalTerrainRejectsBlocksBlockEntitiesAndRecordsTogether() {
        PlacementTerrain terrain = mock(PlacementTerrain.class);
        TerrainTransaction transaction = islandOver(terrain, false);

        writeTreeAcrossTheChunkBorder(transaction);

        assertEquals(SECOND, transaction.get(16, 101, 8));
        assertEquals(TREE, transaction.placementAt(16, 101, 8).orElseThrow());
        verify(terrain, never()).set(anyInt(), anyInt(), anyInt(), any());

        assertEquals(Commit.REJECTED, transaction.commit());
        verify(terrain, never()).set(anyInt(), anyInt(), anyInt(), any());
        verify(terrain, never()).set(anyInt(), anyInt(), anyInt(), any(), any());
        verify(terrain, never()).recordPlacement(anyInt(), anyInt(), anyInt(), any());
    }

    @Test
    public void supportedNeighborChunkCommitsBlocksBlockEntitiesAndRecords() {
        PlacementTerrain terrain = mock(PlacementTerrain.class);
        TerrainTransaction transaction = islandOver(terrain, true);

        writeTreeAcrossTheChunkBorder(transaction);

        assertEquals(Commit.COMMITTED, transaction.commit());
        org.mockito.InOrder order = inOrder(terrain);
        order.verify(terrain).set(15, 101, 8, FIRST);
        order.verify(terrain).recordPlacement(15, 101, 8, TREE);
        order.verify(terrain).set(16, 101, 8, SECOND, LOOT);
        order.verify(terrain).recordPlacement(16, 101, 8, TREE);

        assertEquals(Commit.EMPTY, transaction.commit());
        verify(terrain).set(16, 101, 8, SECOND, LOOT);
    }

    private static void writeTreeAcrossTheChunkBorder(TerrainTransaction transaction) {
        transaction.set(15, 101, 8, FIRST);
        transaction.recordPlacement(15, 101, 8, TREE);
        transaction.set(16, 101, 8, SECOND, LOOT);
        transaction.recordPlacement(16, 101, 8, TREE);
    }

    private static TerrainTransaction islandOver(PlacementTerrain terrain, boolean supportsNeighborChunk) {
        return new TerrainTransaction(terrain) {
            @Override
            protected boolean accepts(int x, int y, int z) {
                return x == 15 || (supportsNeighborChunk && x == 16);
            }
        };
    }
}
