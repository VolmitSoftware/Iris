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

import art.arcane.iris.modules.objects.CaveTransaction;
import art.arcane.iris.modules.objects.TerrainTransaction.Commit;
import com.volmit.block.BlockState;
import com.volmit.object.BlockEntityData;
import com.volmit.placement.PlacementTerrain;
import com.volmit.resource.ResourceId;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A cave object is written whole or not at all: every block must stay inside the cave and below the buried depth. */
public class CaveTransactionTest {
    private static final int WORLD_HEIGHT = 128;
    private static final int ANCHOR_Y = 20;
    private static final int MIN_DEPTH = 10;
    private static final BlockState STONE = BlockState.parse("minecraft:stone");
    private static final BlockState CHEST = BlockState.parse("minecraft:chest");

    @Test
    public void validPlacementCommitsBlocksBlockEntitiesAndRecordsTogether() {
        PlacementTerrain terrain = cave(80, 20, 60);
        BlockEntityData loot = new BlockEntityData(ResourceId.parse("test:chest"), 1, new byte[0]);
        CaveTransaction transaction = new CaveTransaction(terrain, WORLD_HEIGHT, ANCHOR_Y, MIN_DEPTH);

        transaction.set(4, 30, 7, STONE);
        transaction.set(4, 31, 7, CHEST, loot);
        transaction.recordPlacement(4, 30, 7, "object@1");

        assertEquals(STONE, transaction.get(4, 30, 7));
        assertEquals("object@1", transaction.placementAt(4, 30, 7).orElseThrow());
        verify(terrain, never()).set(anyInt(), anyInt(), anyInt(), any());
        verify(terrain, never()).recordPlacement(anyInt(), anyInt(), anyInt(), anyString());

        assertEquals(Commit.COMMITTED, transaction.commit());
        org.mockito.InOrder order = inOrder(terrain);
        order.verify(terrain).set(4, 30, 7, STONE);
        order.verify(terrain).set(4, 31, 7, CHEST, loot);
        order.verify(terrain).recordPlacement(4, 30, 7, "object@1");
    }

    @Test
    public void oneRecordAboveTheBuriedDepthRejectsTheWholePlacement() {
        PlacementTerrain terrain = mock(PlacementTerrain.class);
        when(terrain.highest(eq(4), anyInt(), eq(true))).thenReturn(80);
        when(terrain.highest(eq(5), anyInt(), eq(true))).thenReturn(35);
        when(terrain.isCarved(anyInt(), anyInt(), anyInt())).thenReturn(true);
        CaveTransaction transaction = new CaveTransaction(terrain, WORLD_HEIGHT, ANCHOR_Y, MIN_DEPTH);

        transaction.set(4, 30, 7, STONE);
        transaction.recordPlacement(5, 26, 7, "ghost");

        assertEquals(Commit.REJECTED, transaction.commit());
        verify(terrain, never()).set(anyInt(), anyInt(), anyInt(), any());
        verify(terrain, never()).recordPlacement(anyInt(), anyInt(), anyInt(), anyString());
    }

    @Test
    public void perColumnCaveCeilingRejectsTheWholePlacement() {
        PlacementTerrain terrain = mock(PlacementTerrain.class);
        when(terrain.highest(anyInt(), anyInt(), anyBoolean())).thenReturn(80);
        when(terrain.isCarved(eq(4), anyInt(), eq(7))).thenAnswer(call -> call.<Integer>getArgument(1) <= 60);
        when(terrain.isCarved(eq(5), anyInt(), eq(7))).thenAnswer(call -> call.<Integer>getArgument(1) <= 24);
        CaveTransaction transaction = new CaveTransaction(terrain, WORLD_HEIGHT, ANCHOR_Y, MIN_DEPTH);

        transaction.set(4, 30, 7, STONE);
        transaction.recordPlacement(5, 25, 7, "outside-cave");

        assertEquals(Commit.REJECTED, transaction.commit());
        verify(terrain, never()).set(anyInt(), anyInt(), anyInt(), any());
        verify(terrain, never()).recordPlacement(anyInt(), anyInt(), anyInt(), anyString());
    }

    @Test
    public void exactBurialBoundaryIsAllowedAndShallowerAnchorsAreExcluded() {
        CaveTransaction transaction = new CaveTransaction(cave(80, 20, 90), WORLD_HEIGHT, ANCHOR_Y, MIN_DEPTH);
        transaction.set(4, 70, 7, STONE);

        assertEquals(Commit.COMMITTED, transaction.commit());
        assertEquals(71, CaveAnchors.scanUpperBound(WORLD_HEIGHT, 80, 10));
        assertEquals(61, CaveAnchors.scanUpperBound(WORLD_HEIGHT, 80, 20));
    }

    private static PlacementTerrain cave(int surfaceHeight, int caveFloor, int caveCeiling) {
        PlacementTerrain terrain = mock(PlacementTerrain.class);
        when(terrain.highest(anyInt(), anyInt(), eq(true))).thenReturn(surfaceHeight);
        when(terrain.isCarved(anyInt(), anyInt(), anyInt())).thenAnswer(call -> {
            int y = call.getArgument(1);
            return y >= caveFloor && y < caveCeiling;
        });
        return terrain;
    }
}
