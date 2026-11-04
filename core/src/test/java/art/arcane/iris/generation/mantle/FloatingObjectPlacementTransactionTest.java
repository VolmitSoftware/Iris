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

import art.arcane.iris.generation.hydrology.engine.HydrologyRuntime;
import art.arcane.iris.generation.hydrology.raster.ChunkWaterRaster;
import art.arcane.iris.generation.hydrology.raster.WaterRole;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.block.TileData;
import art.arcane.iris.structure.object.ObjectWaterRule;
import art.arcane.volmlib.util.stream.ProceduralStream;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.math.BlockPosition;
import org.junit.Test;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.IrisMatter;
import art.arcane.iris.structure.object.IObjectPlacer;
import art.arcane.iris.world.storage.matter.IrisMatterSupport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FloatingObjectPlacementTransactionTest {
    @Test
    public void canopyCommitRechecksSolidsAndSkipsAssociatedMetadata() {
        IslandObjectPlacer delegate = createPlacer(true);
        FloatingObjectPlacementTransaction transaction = new FloatingObjectPlacementTransaction(delegate,
                new ObjectContinuationBundle.PlacementKey(ObjectContinuationBundle.Kind.FLOATING, 0, 0, 0), ObjectWaterRule.KEEP_OUT);
        NativeBlockState canopy = mock(NativeBlockState.class);
        NativeBlockState trunk = mock(NativeBlockState.class);
        when(trunk.isSolid()).thenReturn(true);
        when(delegate.beginTreeCanopy(any())).thenCallRealMethod();
        when(delegate.canWriteObjectBlock(4, 101, 7)).thenReturn(true);
        assertTrue(transaction.beginTreeCanopy(new BlockPosition(4, 101, 7)));
        transaction.set(4, 101, 7, canopy);
        transaction.setData(4, 101, 7, "canopy");
        transaction.endTreeCanopy();
        when(delegate.get(4, 101, 7)).thenReturn(trunk);

        assertEquals(FloatingObjectPlacementTransaction.CommitResult.COMMITTED, transaction.commit());

        verify(delegate, never()).set(4, 101, 7, canopy);
        verify(delegate, never()).setData(4, 101, 7, "canopy");
    }

    @Test
    public void unsupportedFinalTerrainRejectsBlocksTilesAndMarkersTogether() {
        IslandObjectPlacer delegate = createPlacer(false);
        FloatingObjectPlacementTransaction transaction = new FloatingObjectPlacementTransaction(delegate,
                new ObjectContinuationBundle.PlacementKey(ObjectContinuationBundle.Kind.FLOATING, 0, 0, 0), ObjectWaterRule.KEEP_OUT);
        NativeBlockState first = mock(NativeBlockState.class);
        NativeBlockState second = mock(NativeBlockState.class);
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
        FloatingObjectPlacementTransaction transaction = new FloatingObjectPlacementTransaction(delegate,
                new ObjectContinuationBundle.PlacementKey(ObjectContinuationBundle.Kind.FLOATING, 0, 0, 0), ObjectWaterRule.KEEP_OUT);
        NativeBlockState first = mock(NativeBlockState.class);
        NativeBlockState second = mock(NativeBlockState.class);
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

    @Test
    @SuppressWarnings("unchecked")
    public void freelyFloatingObjectsPersistBothPendingFragments() {
        IrisMatterSupport.ensureRegistered();
        IObjectPlacer delegate = mock(IObjectPlacer.class);
        Engine engine = mock(Engine.class);
        EngineMantle engineMantle = mock(EngineMantle.class);
        Mantle<Matter> mantle = mock(Mantle.class);
        MantleChunk<Matter> left = mock(MantleChunk.class);
        MantleChunk<Matter> right = mock(MantleChunk.class);
        Matter leftSection = new IrisMatter(16, 16, 16);
        Matter rightSection = new IrisMatter(16, 16, 16);
        when(delegate.getEngine()).thenReturn(engine);
        when(engine.getHeight()).thenReturn(128);
        when(engine.getMantle()).thenReturn(engineMantle);
        when(engineMantle.getMantle()).thenReturn(mantle);
        when(mantle.useChunk(0, 0)).thenReturn(left);
        when(mantle.useChunk(1, 0)).thenReturn(right);
        when(left.getOrCreate(0)).thenReturn(leftSection);
        when(right.getOrCreate(0)).thenReturn(rightSection);
        NativeBlockState state = mock(NativeBlockState.class);
        when(state.key()).thenReturn("minecraft:oak_log");
        ObjectContinuationBundle.PlacementKey key = new ObjectContinuationBundle.PlacementKey(ObjectContinuationBundle.Kind.FLOATING, 0, 0, 7);
        FloatingObjectPlacementTransaction transaction = new FloatingObjectPlacementTransaction(delegate, key, ObjectWaterRule.KEEP_OUT);
        transaction.set(15, 101, 8, state);
        transaction.set(16, 101, 8, state);
        transaction.setData(16, 101, 8, "floating-tree@7");

        assertEquals(FloatingObjectPlacementTransaction.CommitResult.COMMITTED, transaction.commit());

        for (Matter section : new Matter[]{leftSection, rightSection}) {
            ObjectContinuationBundle bundle = section.<ObjectContinuationBundle>getSlice(ObjectContinuationBundle.class).get(0, 0, 0);
            assertNotNull(bundle);
            assertEquals(key, bundle.fragments().getFirst().key());
            assertEquals(2, bundle.fragments().getFirst().touchedChunks().size());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void floatingObjectsFollowTheirWaterRuleInRiverWater() {
        IObjectPlacer delegate = mock(IObjectPlacer.class);
        Engine engine = mock(Engine.class);
        IrisComplex complex = mock(IrisComplex.class);
        HydrologyRuntime runtime = mock(HydrologyRuntime.class);
        ChunkWaterRaster raster = mock(ChunkWaterRaster.class);
        ProceduralStream<WaterRole> roles = mock(ProceduralStream.class);
        ProceduralStream<Double> heads = mock(ProceduralStream.class);
        ProceduralStream<Integer> heights = mock(ProceduralStream.class);
        when(delegate.getEngine()).thenReturn(engine);
        when(engine.getComplex()).thenReturn(complex);
        when(complex.getHydrologyRuntime()).thenReturn(runtime);
        when(complex.allowsNewDiscreteContentAt(anyInt(), anyInt())).thenReturn(true);
        when(runtime.enabled()).thenReturn(true);
        when(runtime.raster(anyInt(), anyInt())).thenReturn(raster);
        when(raster.role(anyInt(), anyInt())).thenReturn(WaterRole.NONE);
        when(raster.waterHead(anyInt(), anyInt())).thenReturn(Integer.MIN_VALUE);
        when(raster.role(3, 3)).thenReturn(WaterRole.CHANNEL);
        when(raster.waterHead(3, 3)).thenReturn(9);
        when(raster.terrainHeight(3, 3)).thenReturn(5);
        when(roles.get(anyDouble(), anyDouble())).thenAnswer(call -> raster.role(local(call.getArgument(0)), local(call.getArgument(1))));
        when(heads.getDouble(anyDouble(), anyDouble())).thenAnswer(call -> (double) raster.waterHead(local(call.getArgument(0)), local(call.getArgument(1))));
        when(heights.get(anyDouble(), anyDouble())).thenAnswer(call -> raster.terrainHeight(local(call.getArgument(0)), local(call.getArgument(1))));
        when(complex.getWaterRoleStream()).thenReturn(roles);
        when(complex.getWaterHeadStream()).thenReturn(heads);
        when(complex.getRoundedHeighteightStream()).thenReturn(heights);
        NativeBlockState stone = mock(NativeBlockState.class);
        when(stone.isSolid()).thenReturn(true);
        ObjectContinuationBundle.PlacementKey key = new ObjectContinuationBundle.PlacementKey(ObjectContinuationBundle.Kind.FLOATING, 0, 0, 0);

        FloatingObjectPlacementTransaction land = new FloatingObjectPlacementTransaction(delegate, key, ObjectWaterRule.KEEP_OUT);
        land.set(3, 7, 3, stone);
        land.set(3, 10, 3, stone);
        assertEquals(FloatingObjectPlacementTransaction.CommitResult.COMMITTED, land.commit());
        verify(delegate, never()).set(3, 7, 3, stone);
        verify(delegate).set(3, 10, 3, stone);

        FloatingObjectPlacementTransaction water = new FloatingObjectPlacementTransaction(delegate, key, ObjectWaterRule.ENTERS_WATER);
        water.set(3, 7, 3, stone);
        assertEquals(FloatingObjectPlacementTransaction.CommitResult.COMMITTED, water.commit());
        verify(delegate).set(3, 7, 3, stone);

        NativeBlockState air = mock(NativeBlockState.class);
        FloatingObjectPlacementTransaction forced = new FloatingObjectPlacementTransaction(delegate, key, ObjectWaterRule.FORCED);
        forced.set(3, 8, 3, stone);
        forced.set(3, 6, 3, air);
        assertEquals(FloatingObjectPlacementTransaction.CommitResult.COMMITTED, forced.commit());
        verify(delegate).set(3, 8, 3, stone);
        verify(delegate).set(3, 6, 3, air);
    }

    private static int local(Object coordinate) {
        return (int) Math.floor(((Number) coordinate).doubleValue()) & 15;
    }

    @SuppressWarnings("unchecked")
    private IslandObjectPlacer createPlacer(boolean supportNeighborChunk) {
        IslandObjectPlacer placer = mock(IslandObjectPlacer.class);
        Engine engine = mock(Engine.class);
        when(engine.getHeight()).thenReturn(384);
        EngineMantle engineMantle = mock(EngineMantle.class);
        Mantle<Matter> mantle = mock(Mantle.class);
        MantleChunk<Matter> chunk = mock(MantleChunk.class);
        when(engine.getMantle()).thenReturn(engineMantle);
        when(engineMantle.getMantle()).thenReturn(mantle);
        when(mantle.useChunk(anyInt(), anyInt())).thenReturn(chunk);
        when(chunk.isFlagged(MantleFlag.REAL)).thenReturn(true);
        when(placer.getEngine()).thenReturn(engine);
        when(placer.canWriteObjectBlock(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
            int x = invocation.getArgument(0);
            return x == 15 || (supportNeighborChunk && x == 16);
        });
        return placer;
    }
}
