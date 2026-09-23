package art.arcane.iris.generation.mantle;

import art.arcane.iris.integration.Identifier;
import art.arcane.iris.generation.decoration.tree.TreeBlockMaterial;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.world.history.BoundaryColumnGeometry;
import art.arcane.iris.world.history.TerrainBoundarySignature;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.Terrain3DColumnFixtures;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.testsupport.BukkitTestServer;
import art.arcane.iris.world.storage.matter.IrisMatterSupport;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.IrisMatter;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ObjectDestinationTransactionTest {
    @Test
    public void commonAndGenericMetadataSurviveBlockReplacementAndSourceReplay() {
        MantleWriter writer = writer();
        NativeBlockState custom = mock(NativeBlockState.class);
        NativeBlockState base = mock(NativeBlockState.class);
        NativeBlockState replacement = mock(NativeBlockState.class);
        Identifier identifier = Identifier.fromString("iris:tree_block");
        when(custom.isCustom()).thenReturn(true);
        when(custom.deferredPlacementKey()).thenReturn(identifier.toString());
        when(custom.placementBaseState()).thenReturn(base);
        TreeBlockMaterial firstMaterial = new TreeBlockMaterial("minecraft:oak_log");
        TreeBlockMaterial secondMaterial = new TreeBlockMaterial("minecraft:birch_log");
        Marker generic = new Marker("generic");
        ObjectDestinationTransaction source = new ObjectDestinationTransaction(writer, 0, 0);
        int[][] positions = {{-17, 31}, {Integer.MIN_VALUE, Integer.MAX_VALUE}};
        for (int[] position : positions) {
            int x = position[0];
            int z = position[1];
            source.setData(x, 7, z, "first");
            source.setData(x, 7, z, firstMaterial);
            source.setData(x, 7, z, generic);
            source.set(x, 7, z, custom);
            source.setData(x, 7, z, "second");
            source.setData(x, 7, z, secondMaterial);
            source.setData(x, 7, z, Integer.valueOf(42));
            source.setData(x, 7, z, replacement);
            source.setData(x, 7, z, null);
            assertNull(source.getDataIfPresent(x, 7, z, Identifier.class));
        }
        ObjectSourcePlan plan = source.sourcePlanSince(0);
        assertEquals(16, source.mutationCheckpoint());
        assertEquals(35, plan.mutationWeight());
        for (int[] position : positions) {
            int x = position[0];
            int z = position[1];
            ObjectDestinationTransaction destination = new ObjectDestinationTransaction(writer, x >> 4, z >> 4);
            destination.apply(plan);
            List<ObjectDestinationTransaction.Mutation> sourceMutations = plan.mutationsFor(x >> 4, z >> 4);
            List<ObjectDestinationTransaction.Mutation> replayed = destination.sourcePlanSince(0).mutationsFor(x >> 4, z >> 4);
            assertEquals(sourceMutations.size(), replayed.size());
            for (int index = 0; index < sourceMutations.size(); index++) {
                assertSame(sourceMutations.get(index), replayed.get(index));
            }
            assertEquals(8, destination.mutationCheckpoint());
            assertSame(replacement, destination.get(x, 7, z));
            assertNull(destination.getDataIfPresent(x, 7, z, Identifier.class));
            assertEquals("second", destination.getDataIfPresent(x, 7, z, String.class));
            assertSame(secondMaterial, destination.getDataIfPresent(x, 7, z, TreeBlockMaterial.class));
            assertSame(generic, destination.getDataIfPresent(x, 7, z, Marker.class));
            assertEquals(Integer.valueOf(42), destination.getDataIfPresent(x, 7, z, Integer.class));
            destination.commit();
            InOrder order = inOrder(writer);
            order.verify(writer).setData(x, 7, z, "first");
            order.verify(writer).setData(x, 7, z, firstMaterial);
            order.verify(writer).setData(x, 7, z, generic);
            order.verify(writer).set(x, 7, z, custom);
            order.verify(writer).setData(x, 7, z, "second");
            order.verify(writer).setData(x, 7, z, secondMaterial);
            order.verify(writer).setData(x, 7, z, Integer.valueOf(42));
            order.verify(writer).setData(x, 7, z, replacement);
        }
    }

    @Test
    public void replayRechecksDestinationProtectionAndHeightBeforeRetainingMutations() {
        MantleWriter sourceWriter = writer();
        when(sourceWriter.getMantle().getWorldHeight()).thenReturn(128);
        NativeBlockState block = mock(NativeBlockState.class);
        NativeBlockState custom = mock(NativeBlockState.class);
        when(custom.isCustom()).thenReturn(true);
        when(custom.deferredPlacementKey()).thenReturn("iris:tree_block");
        when(custom.placementBaseState()).thenReturn(block);
        MatterCavern cavern = new MatterCavern(true, "", (byte) 0);
        TreeBlockMaterial material = new TreeBlockMaterial("minecraft:oak_log");
        ObjectDestinationTransaction source = new ObjectDestinationTransaction(sourceWriter, 0, 0);
        source.setData(0, 4, 0, block);
        source.setData(1, 4, 0, cavern);
        source.set(2, 4, 0, custom);
        source.setData(3, 0, 0, block);
        source.setData(4, 80, 0, block);
        source.setData(0, 4, 0, "tree");
        source.setData(0, 4, 0, material);
        ObjectSourcePlan plan = source.sourcePlanSince(0);
        assertEquals(7, plan.mutationsFor(0, 0).size());
        MantleWriter destinationWriter = writer();
        when(destinationWriter.getEngine().getDimension().isBedrock()).thenReturn(true);
        for (int x = 0; x < 3; x++) {
            when(destinationWriter.getPrerequisiteDataIfPresent(x, 4, 0, HydrologyCaveCell.class))
                    .thenReturn(HydrologyCaveCell.of(HydrologyCaveAction.SEAL_GUARD));
        }
        ObjectDestinationTransaction destination = new ObjectDestinationTransaction(destinationWriter, 0, 0);

        destination.apply(plan);

        List<ObjectDestinationTransaction.Mutation> replayed = destination.sourcePlanSince(0).mutationsFor(0, 0);
        assertEquals(2, replayed.size());
        assertSame(plan.mutationsFor(0, 0).get(5), replayed.get(0));
        assertSame(plan.mutationsFor(0, 0).get(6), replayed.get(1));
        assertNull(destination.getDataIfPresent(2, 4, 0, Identifier.class));
        assertNull(destination.getDataIfPresent(1, 4, 0, MatterCavern.class));
        assertEquals("tree", destination.getDataIfPresent(0, 4, 0, String.class));
        assertSame(material, destination.getDataIfPresent(0, 4, 0, TreeBlockMaterial.class));
        destination.commit();
        verify(destinationWriter, never()).setData(anyInt(), anyInt(), anyInt(), any(NativeBlockState.class));
        verify(destinationWriter, never()).set(anyInt(), anyInt(), anyInt(), any(NativeBlockState.class));
        verify(destinationWriter, never()).setData(anyInt(), anyInt(), anyInt(), any(MatterCavern.class));
    }

    @Test
    public void failedCommonMetadataPublicationRestoresOriginalValuesAndMissingTypes() {
        MantleWriter writer = writer();
        TreeBlockMaterial originalMaterial = new TreeBlockMaterial("minecraft:oak_log");
        TreeBlockMaterial replacementMaterial = new TreeBlockMaterial("minecraft:birch_log");
        Marker generic = new Marker("generic");
        Marker failure = new Marker("failure");
        when(writer.getPrerequisiteDataIfPresent(0, 4, 0, String.class)).thenReturn("original");
        when(writer.getPrerequisiteDataIfPresent(0, 4, 0, TreeBlockMaterial.class)).thenReturn(originalMaterial);
        doThrow(new IllegalStateException("publication failed"))
                .when(writer).setData(1, 4, 0, failure);
        ObjectDestinationTransaction transaction = new ObjectDestinationTransaction(writer, 0, 0);
        assertEquals("original", transaction.getDataIfPresent(0, 4, 0, String.class));
        assertSame(originalMaterial, transaction.getDataIfPresent(0, 4, 0, TreeBlockMaterial.class));
        transaction.setData(0, 4, 0, "replacement");
        transaction.setData(0, 4, 0, replacementMaterial);
        transaction.setData(0, 4, 0, generic);
        transaction.setData(1, 4, 0, failure);

        assertThrows(IllegalStateException.class, transaction::commit);

        verify(writer).clearData(0, 4, 0, String.class);
        verify(writer).setData(0, 4, 0, "original");
        verify(writer).clearData(0, 4, 0, TreeBlockMaterial.class);
        verify(writer).setData(0, 4, 0, originalMaterial);
        verify(writer).clearData(0, 4, 0, Marker.class);
        verify(writer).setData(0, 4, 0, generic);
    }

    @Test
    public void sparseOverlayMatchesScalarAndColumnReadsAcrossSignedCoordinates() {
        MantleWriter writer = writer();
        ObjectDestinationTransaction transaction = new ObjectDestinationTransaction(writer, 0, 0);
        int[][] columns = {{0, 0}, {-1, -1}, {1, -1}, {-1, 1},
                {Integer.MIN_VALUE, Integer.MAX_VALUE}, {Integer.MAX_VALUE, Integer.MIN_VALUE},
                {0, 1 << 26}, {1 << 26, 0}};
        NativeBlockState solid = mock(NativeBlockState.class);
        NativeBlockState air = mock(NativeBlockState.class);
        NativeBlockState fluid = mock(NativeBlockState.class);
        when(air.isAir()).thenReturn(true);
        when(fluid.isFluid()).thenReturn(true);
        MatterCavern cavern = new MatterCavern(true, "", (byte) 0);
        Object[] values = {solid, air, fluid, cavern,
                HydrologyCaveCell.of(HydrologyCaveAction.SEAL_GUARD),
                HydrologyCaveCell.of(HydrologyCaveAction.DRY_AIR)};
        Map<ObjectDestinationTransaction.DataKey, Object> expected = new HashMap<>();
        Random random = new Random(918241L);
        for (int pass = 0; pass < 12; pass++) {
            for (int write = 0; write < 80; write++) {
                int[] column = columns[random.nextInt(columns.length)];
                int y = random.nextInt(64);
                Object value = values[random.nextInt(values.length)];
                transaction.setData(column[0], y, column[1], value);
                HydrologyCaveCell hydrology = (HydrologyCaveCell) expected.get(
                        new ObjectDestinationTransaction.DataKey(column[0], y, column[1], HydrologyCaveCell.class));
                if (!(value instanceof HydrologyCaveCell)
                        && hydrology != null && hydrology.protectsPlacement()) {
                    continue;
                }
                Class<?> type = value instanceof NativeBlockState ? NativeBlockState.class : value.getClass();
                expected.put(new ObjectDestinationTransaction.DataKey(column[0], y, column[1], type), value);
            }
            for (int[] column : columns) {
                byte[] carved = new byte[64];
                for (int y = 0; y < 64; y++) {
                    NativeBlockState block = (NativeBlockState) expected.get(
                            new ObjectDestinationTransaction.DataKey(column[0], y, column[1], NativeBlockState.class));
                    HydrologyCaveCell hydrology = (HydrologyCaveCell) expected.get(
                            new ObjectDestinationTransaction.DataKey(column[0], y, column[1], HydrologyCaveCell.class));
                    MatterCavern expectedCavern = (MatterCavern) expected.get(
                            new ObjectDestinationTransaction.DataKey(column[0], y, column[1], MatterCavern.class));
                    boolean isCarved = block != solid && (hydrology == null
                            ? expectedCavern != null : hydrology.carves());
                    carved[y] = isCarved ? (byte) 1 : 0;
                    assertSame(block, transaction.get(column[0], y, column[1]));
                    assertSame(hydrology, transaction.getDataIfPresent(column[0], y, column[1], HydrologyCaveCell.class));
                    assertSame(expectedCavern, transaction.getDataIfPresent(column[0], y, column[1], MatterCavern.class));
                    assertEquals(isCarved, transaction.isCarved(column[0], y, column[1]));
                }
                assertArrayEquals(carved, transaction.getCarvedColumn(column[0], column[1], 64));
            }
        }
    }

    @Test
    public void overlayReadsLeavePrerequisiteUpdatesVisibleAndReturnedColumnsIndependent() {
        MantleWriter writer = writer();
        NativeBlockState first = mock(NativeBlockState.class);
        NativeBlockState second = mock(NativeBlockState.class);
        when(writer.getPrerequisiteBlock(-1, 5, -1)).thenReturn(first, second);
        byte[] prerequisite = new byte[64];
        prerequisite[5] = 1;
        when(writer.getPrerequisiteCarvedColumn(-1, -1, 64)).thenReturn(prerequisite);
        ObjectDestinationTransaction transaction = new ObjectDestinationTransaction(writer, -1, -1);

        assertSame(first, transaction.get(-1, 5, -1));
        assertSame(second, transaction.get(-1, 5, -1));
        byte[] column = transaction.getCarvedColumn(-1, -1, 64);
        column[5] = 0;
        assertEquals(1, transaction.getCarvedColumn(-1, -1, 64)[5]);
        transaction.setData(-1, 5, -1, second);
        assertEquals(0, transaction.getCarvedColumn(-1, -1, 64)[5]);
        assertEquals(1, prerequisite[5]);
    }

    @Test
    public void overlayReadsEarlierWritesAndCommitsOnlyDestinationChunk() {
        MantleWriter writer = writer();
        Marker prerequisite = new Marker("prerequisite");
        Marker outside = new Marker("outside");
        Marker first = new Marker("first");
        Marker second = new Marker("second");
        when(writer.getPrerequisiteDataIfPresent(0, 4, 0, Marker.class)).thenReturn(prerequisite);
        ObjectDestinationTransaction transaction = new ObjectDestinationTransaction(writer, 0, 0);

        assertSame(prerequisite, transaction.getDataIfPresent(0, 4, 0, Marker.class));
        transaction.setData(-1, 4, 0, outside);
        transaction.setData(0, 4, 0, first);
        transaction.setData(0, 4, 0, second);
        assertSame(outside, transaction.getDataIfPresent(-1, 4, 0, Marker.class));
        assertSame(second, transaction.getDataIfPresent(0, 4, 0, Marker.class));

        transaction.commit();

        verify(writer, never()).setData(-1, 4, 0, outside);
        InOrder order = inOrder(writer);
        order.verify(writer).setData(0, 4, 0, first);
        order.verify(writer).setData(0, 4, 0, second);
    }

    @Test
    public void sourcePlanCapturesOnlyTheSourceMutationTail() {
        MantleWriter writer = writer();
        Marker predecessor = new Marker("predecessor");
        Marker source = new Marker("source");
        ObjectDestinationTransaction scratch = new ObjectDestinationTransaction(writer, 0, 0);
        scratch.setData(0, 4, 0, predecessor);
        int checkpoint = scratch.mutationCheckpoint();
        scratch.setData(1, 4, 0, source);
        ObjectSourcePlan plan = scratch.sourcePlanSince(checkpoint);
        ObjectDestinationTransaction destination = new ObjectDestinationTransaction(writer, 0, 0);

        destination.apply(plan);

        assertNull(destination.getDataIfPresent(0, 4, 0, Marker.class));
        assertSame(source, destination.getDataIfPresent(1, 4, 0, Marker.class));
        destination.commit();
        verify(writer, never()).setData(0, 4, 0, predecessor);
        verify(writer).setData(1, 4, 0, source);
    }

    @Test
    public void applyingSourcePlanSkipsForeignChunksBeforeOverlayingAndPreservesLocalOrder() {
        MantleWriter writer = writer();
        Marker foreign = new Marker("foreign");
        Marker prerequisite = new Marker("prerequisite");
        Marker first = new Marker("first");
        Marker second = new Marker("second");
        when(writer.getPrerequisiteDataIfPresent(0, 4, 0, Marker.class)).thenReturn(prerequisite);
        ObjectDestinationTransaction source = new ObjectDestinationTransaction(writer, 0, 0);
        source.setData(-1, 4, 0, first);
        source.setData(0, 4, 0, foreign);
        source.setData(-1, 4, 0, second);
        source.setData(-1, 4, 16, foreign);
        ObjectDestinationTransaction destination = new ObjectDestinationTransaction(writer, -1, 0);

        destination.apply(source.sourcePlanSince(0));

        assertEquals(2, destination.mutationCheckpoint());
        assertSame(second, destination.getDataIfPresent(-1, 4, 0, Marker.class));
        assertSame(prerequisite, destination.getDataIfPresent(0, 4, 0, Marker.class));
        assertNull(destination.getDataIfPresent(-1, 4, 16, Marker.class));
        destination.commit();
        InOrder order = inOrder(writer);
        order.verify(writer).setData(-1, 4, 0, first);
        order.verify(writer).setData(-1, 4, 0, second);
        verify(writer, never()).setData(0, 4, 0, foreign);
        verify(writer, never()).setData(-1, 4, 16, foreign);
    }

    @Test
    public void indexedSourceReplayPreservesCustomIdentityAndWriteOrderAtNegativeChunks() {
        MantleWriter writer = writer();
        NativeBlockState custom = mock(NativeBlockState.class);
        NativeBlockState base = mock(NativeBlockState.class);
        NativeBlockState replacement = mock(NativeBlockState.class);
        Identifier identifier = Identifier.fromString("iris:custom_block");
        when(custom.isCustom()).thenReturn(true);
        when(custom.deferredPlacementKey()).thenReturn(identifier.toString());
        when(custom.placementBaseState()).thenReturn(base);
        Marker first = new Marker("first");
        Marker second = new Marker("second");
        Marker foreign = new Marker("foreign");
        ObjectDestinationTransaction source = new ObjectDestinationTransaction(writer, 0, 0);
        source.setData(-17, 4, 31, first);
        source.set(-17, 4, 31, custom);
        source.setData(-16, 4, 31, foreign);
        source.setData(-17, 4, 31, replacement);
        source.setData(-17, 4, 32, foreign);
        source.set(-17, 4, 31, custom);
        source.setData(-17, 4, 31, second);
        ObjectDestinationTransaction destination = new ObjectDestinationTransaction(writer, -2, 1);

        destination.apply(source.sourcePlanSince(0));

        assertEquals(5, destination.mutationCheckpoint());
        assertSame(base, destination.get(-17, 4, 31));
        assertEquals(identifier, destination.getDataIfPresent(-17, 4, 31, Identifier.class));
        assertSame(second, destination.getDataIfPresent(-17, 4, 31, Marker.class));
        destination.commit();
        InOrder order = inOrder(writer);
        order.verify(writer).setData(-17, 4, 31, first);
        order.verify(writer).set(-17, 4, 31, custom);
        order.verify(writer).setData(-17, 4, 31, replacement);
        order.verify(writer).set(-17, 4, 31, custom);
        order.verify(writer).setData(-17, 4, 31, second);
        verify(writer, never()).setData(-16, 4, 31, foreign);
        verify(writer, never()).setData(-17, 4, 32, foreign);
    }

    @Test
    public void sourcePlanRejectsAnInvalidCheckpoint() {
        ObjectDestinationTransaction transaction = new ObjectDestinationTransaction(writer(), 0, 0);

        assertThrows(IllegalArgumentException.class, () -> transaction.sourcePlanSince(1));
    }

    @Test
    public void carvedColumnComposesPrerequisiteHydrologyAndEarlierOriginWrites() {
        MantleWriter writer = writer();
        byte[] prerequisite = new byte[8];
        prerequisite[4] = 1;
        when(writer.getPrerequisiteCarvedColumn(0, 0, 8)).thenReturn(prerequisite);
        when(writer.isPrerequisiteCarved(0, 4, 0)).thenReturn(true);
        ObjectDestinationTransaction transaction = new ObjectDestinationTransaction(writer, 0, 0);
        HydrologyCaveCell sealed = HydrologyCaveCell.of(HydrologyCaveAction.SEAL_GUARD);
        MatterCavern cavern = new MatterCavern(true, "", (byte) 0);

        transaction.setData(0, 4, 0, sealed);
        transaction.setData(0, 5, 0, cavern);

        byte[] expected = new byte[8];
        expected[5] = 1;
        assertArrayEquals(expected, transaction.getCarvedColumn(0, 0, 8));
        assertFalse(transaction.isCarved(0, 4, 0));
        assertTrue(transaction.isCarved(0, 5, 0));
        assertArrayEquals(new byte[]{0, 0}, transaction.getCarvedColumn(0, 0, 2));
    }

    @Test
    public void densityOpeningsReachTransactionsBeforeMantleCarving() {
        try (TerrainFixture fixture = new TerrainFixture()) {
            ObjectDestinationTransaction transaction = new ObjectDestinationTransaction(fixture.writer, 0, 0);

            assertArrayEquals(new byte[]{0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0},
                    transaction.getCarvedColumn(0, 0, 16));
            assertFalse(transaction.isCarved(0, 4, 0));
            assertTrue(transaction.isCarved(0, 5, 0));
            assertTrue(transaction.isCarved(0, 9, 0));
            assertFalse(transaction.isCarved(0, 10, 0));
            assertArrayEquals(new byte[]{0, 0, 0, 0, 0, 1}, transaction.getCarvedColumn(0, 0, 6));
            assertArrayEquals(new byte[0], transaction.getCarvedColumn(0, 0, -1));
        }
    }

    @Test
    public void densityMaskPreservesPrerequisiteJournalAndHydrologyPrecedence() {
        try (TerrainFixture fixture = new TerrainFixture()) {
            MatterCavern cavern = new MatterCavern(true, "", (byte) 0);
            fixture.matter.<MatterCavern>slice(MatterCavern.class).set(0, 3, 0, cavern);
            fixture.writer.withComponentPriority(2, () -> {
                fixture.writer.clearData(0, 3, 0, MatterCavern.class);
                fixture.writer.setData(0, 4, 0, cavern);
            });
            fixture.matter.<HydrologyCaveCell>slice(HydrologyCaveCell.class)
                    .set(0, 6, 0, HydrologyCaveCell.of(HydrologyCaveAction.SEAL_GUARD));
            fixture.matter.<HydrologyCaveCell>slice(HydrologyCaveCell.class)
                    .set(0, 12, 0, HydrologyCaveCell.of(HydrologyCaveAction.DRY_AIR));
            ObjectDestinationTransaction transaction = new ObjectDestinationTransaction(fixture.writer, 0, 0);

            assertArrayEquals(new byte[]{0, 0, 0, 1, 0, 1, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0},
                    transaction.getCarvedColumn(0, 0, 16));
            assertTrue(transaction.isCarved(0, 3, 0));
            assertFalse(transaction.isCarved(0, 4, 0));
            assertFalse(transaction.isCarved(0, 6, 0));
            assertTrue(transaction.isCarved(0, 12, 0));

            transaction.setData(0, 5, 0, mock(NativeBlockState.class));

            assertFalse(transaction.isCarved(0, 5, 0));
            assertEquals(0, transaction.getCarvedColumn(0, 0, 16)[5]);
            assertTrue(fixture.writer.isPrerequisiteCarved(0, 5, 0));
        }
    }

    @Test
    public void additionalTerrainOwnershipExcludesRootDensityOpenings() {
        try (TerrainFixture fixture = new TerrainFixture()) {
            when(fixture.engine.isAdditionalTerrainOwned(0, 6, 0)).thenReturn(true);
            when(fixture.complex.isTerrain3DOpening(0, 6, 0)).thenReturn(false);
            ObjectDestinationTransaction transaction = new ObjectDestinationTransaction(fixture.writer, 0, 0);

            assertArrayEquals(new byte[]{0, 0, 0, 0, 0, 1, 0, 1, 1, 1, 0, 0, 0, 0, 0, 0},
                    transaction.getCarvedColumn(0, 0, 16));
            assertFalse(transaction.isCarved(0, 6, 0));
            assertTrue(transaction.isCarved(0, 7, 0));
        }
    }

    @Test
    public void historicalGeometryOwnsTransactionalCarvingOverCurrentDensity() {
        try (TerrainFixture fixture = new TerrainFixture()) {
            BoundaryColumnGeometry.Voxel solid = new BoundaryColumnGeometry.Voxel(
                    "minecraft:stone", BoundaryColumnGeometry.Phase.SOLID, "", false);
            BoundaryColumnGeometry.Voxel air = new BoundaryColumnGeometry.Voxel(
                    "minecraft:air", BoundaryColumnGeometry.Phase.AIR, "", false);
            List<BoundaryColumnGeometry.Voxel> voxels = new ArrayList<>(Collections.nCopies(16, solid));
            voxels.set(2, air);
            TerrainBoundarySignature historical = mock(TerrainBoundarySignature.class);
            when(historical.geometry()).thenReturn(BoundaryColumnGeometry.fromVoxels(-64, voxels));
            when(fixture.engine.getMinHeight()).thenReturn(-64);
            when(fixture.complex.resolvedTerrainColumn(0, 0)).thenReturn(Optional.of(historical));
            fixture.matter.<HydrologyCaveCell>slice(HydrologyCaveCell.class)
                    .set(0, 2, 0, HydrologyCaveCell.of(HydrologyCaveAction.SEAL_GUARD));
            ObjectDestinationTransaction transaction = new ObjectDestinationTransaction(fixture.writer, 0, 0);

            assertArrayEquals(new byte[]{0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
                    transaction.getCarvedColumn(0, 0, 16));
            assertTrue(transaction.isCarved(0, 2, 0));
            assertFalse(transaction.isCarved(0, 5, 0));
        }
    }

    @Test
    public void failedCommitRestoresEveryTouchedDestinationCell() {
        MantleWriter writer = writer();
        Marker firstOriginal = new Marker("first-original");
        Marker secondOriginal = new Marker("second-original");
        Marker first = new Marker("first");
        Marker second = new Marker("second");
        when(writer.getPrerequisiteDataIfPresent(0, 4, 0, Marker.class)).thenReturn(firstOriginal);
        when(writer.getPrerequisiteDataIfPresent(1, 4, 0, Marker.class)).thenReturn(secondOriginal);
        doThrow(new IllegalStateException("publication failed"))
                .when(writer).setData(1, 4, 0, second);
        ObjectDestinationTransaction transaction = new ObjectDestinationTransaction(writer, 0, 0);
        transaction.setData(0, 4, 0, first);
        transaction.setData(1, 4, 0, second);

        assertThrows(IllegalStateException.class, transaction::commit);

        verify(writer).clearData(0, 4, 0, Marker.class);
        verify(writer).clearData(1, 4, 0, Marker.class);
        verify(writer).setData(0, 4, 0, firstOriginal);
        verify(writer).setData(1, 4, 0, secondOriginal);
    }

    @Test
    public void rejectedWriterMutationsNeverEnterTheOverlay() {
        MantleWriter writer = writer();
        NativeBlockState prerequisite = mock(NativeBlockState.class);
        NativeBlockState rejected = mock(NativeBlockState.class);
        MatterCavern cavern = new MatterCavern(true, "", (byte) 0);
        when(writer.getPrerequisiteBlock(0, 0, 0)).thenReturn(prerequisite);
        when(writer.getEngine().getDimension().isBedrock()).thenReturn(true);
        when(writer.getPrerequisiteDataIfPresent(1, 4, 0, HydrologyCaveCell.class))
                .thenReturn(HydrologyCaveCell.of(HydrologyCaveAction.SEAL_GUARD));
        ObjectDestinationTransaction transaction = new ObjectDestinationTransaction(writer, 0, 0);

        transaction.setData(0, 0, 0, rejected);
        transaction.setData(1, 4, 0, cavern);

        assertSame(prerequisite, transaction.get(0, 0, 0));
        assertFalse(transaction.isCarved(1, 4, 0));
        transaction.commit();
        verify(writer, never()).setData(0, 0, 0, rejected);
        verify(writer, never()).setData(1, 4, 0, cavern);
    }

    @Test
    public void acceptedCustomPlacementPublishesBlockAndIdentifierAsOneMutation() {
        MantleWriter writer = writer();
        NativeBlockState custom = mock(NativeBlockState.class);
        NativeBlockState base = mock(NativeBlockState.class);
        Identifier identifier = Identifier.fromString("iris:custom_block");
        when(custom.isCustom()).thenReturn(true);
        when(custom.deferredPlacementKey()).thenReturn(identifier.toString());
        when(custom.placementBaseState()).thenReturn(base);
        ObjectDestinationTransaction transaction = new ObjectDestinationTransaction(writer, 0, 0);

        transaction.set(1, 4, 2, custom);

        assertSame(base, transaction.get(1, 4, 2));
        assertEquals(identifier, transaction.getDataIfPresent(1, 4, 2, Identifier.class));
        transaction.commit();
        verify(writer).set(1, 4, 2, custom);
        verify(writer, never()).setData(1, 4, 2, base);
        verify(writer, never()).setData(1, 4, 2, identifier);
    }

    @Test
    public void rejectedCustomPlacementStagesNeitherBlockNorIdentifier() {
        MantleWriter writer = writer();
        NativeBlockState custom = mock(NativeBlockState.class);
        NativeBlockState base = mock(NativeBlockState.class);
        when(custom.isCustom()).thenReturn(true);
        when(custom.deferredPlacementKey()).thenReturn("iris:custom_block");
        when(custom.placementBaseState()).thenReturn(base);
        when(writer.getEngine().getDimension().isBedrock()).thenReturn(true);
        when(writer.getPrerequisiteDataIfPresent(1, 4, 0, HydrologyCaveCell.class))
                .thenReturn(HydrologyCaveCell.of(HydrologyCaveAction.SEAL_GUARD));
        ObjectDestinationTransaction transaction = new ObjectDestinationTransaction(writer, 0, 0);

        transaction.set(0, 0, 0, custom);
        transaction.set(1, 4, 0, custom);
        transaction.set(2, 64, 0, custom);

        assertNull(transaction.getDataIfPresent(0, 0, 0, Identifier.class));
        assertNull(transaction.getDataIfPresent(1, 4, 0, Identifier.class));
        assertNull(transaction.getDataIfPresent(2, 64, 0, Identifier.class));
        transaction.commit();
        verify(writer, never()).set(anyInt(), anyInt(), anyInt(), any(NativeBlockState.class));
        verify(writer, never()).setData(anyInt(), anyInt(), anyInt(), any(Identifier.class));
    }

    @Test
    public void blockReplacementClearsOverlayIdentifierAndRestoresItAfterFailure() {
        MantleWriter writer = writer();
        NativeBlockState block = mock(NativeBlockState.class);
        Marker failure = new Marker("failure");
        Identifier originalIdentifier = Identifier.fromString("iris:deferred");
        when(writer.getPrerequisiteDataIfPresent(0, 4, 0, Identifier.class)).thenReturn(originalIdentifier);
        doThrow(new IllegalStateException("publication failed"))
                .when(writer).setData(1, 4, 0, failure);
        ObjectDestinationTransaction transaction = new ObjectDestinationTransaction(writer, 0, 0);

        transaction.setData(0, 4, 0, block);
        assertNull(transaction.getDataIfPresent(0, 4, 0, Identifier.class));
        transaction.setData(1, 4, 0, failure);
        ObjectSourcePlan plan = transaction.sourcePlanSince(0);
        ObjectDestinationTransaction destination = new ObjectDestinationTransaction(writer, 0, 0);
        destination.apply(plan);
        assertNull(destination.getDataIfPresent(0, 4, 0, Identifier.class));
        assertSame(plan.mutationsFor(0, 0).getFirst(), destination.sourcePlanSince(0).mutationsFor(0, 0).getFirst());

        assertThrows(IllegalStateException.class, destination::commit);
        verify(writer).clearData(0, 4, 0, Identifier.class);
        verify(writer).setData(0, 4, 0, originalIdentifier);
    }

    @SuppressWarnings("unchecked")
    private static MantleWriter writer() {
        MantleWriter writer = mock(MantleWriter.class);
        Mantle<Matter> mantle = mock(Mantle.class);
        Engine engine = mock(Engine.class);
        IrisDimension dimension = mock(IrisDimension.class);
        when(mantle.getWorldHeight()).thenReturn(64);
        when(writer.getMantle()).thenReturn(mantle);
        when(writer.getEngine()).thenReturn(engine);
        when(engine.getDimension()).thenReturn(dimension);
        when(writer.getPrerequisiteCarvedColumn(anyInt(), anyInt(), anyInt()))
                .thenAnswer(invocation -> new byte[invocation.getArgument(2)]);
        when(writer.getPrerequisiteDataIfPresent(anyInt(), anyInt(), anyInt(), any()))
                .thenReturn(null);
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(2);
            task.run();
            return null;
        }).when(writer).withChunkFence(anyInt(), anyInt(), any(Runnable.class));
        return writer;
    }

    private static final class TerrainFixture implements AutoCloseable {
        private final IrisPlatform previousPlatform;
        private final IrisComplex complex = mock(IrisComplex.class);
        private final Engine engine = mock(Engine.class);
        private final Matter matter;
        private final MantleWriter writer;

        @SuppressWarnings("unchecked")
        private TerrainFixture() {
            BukkitTestServer.install();
            previousPlatform = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
            IrisPlatforms.unbind();
            NativeBlockState air = mock(NativeBlockState.class);
            PlatformRegistries registries = mock(PlatformRegistries.class);
            IrisPlatform platform = mock(IrisPlatform.class);
            when(air.isAir()).thenReturn(true);
            when(registries.air()).thenReturn(air);
            when(registries.block(anyString())).thenReturn(air);
            when(platform.registries()).thenReturn(registries);
            IrisPlatforms.bind(platform);
            IrisMatterSupport.ensureRegistered();

            EngineMantle engineMantle = mock(EngineMantle.class);
            Mantle<Matter> mantle = mock(Mantle.class);
            MantleChunk<Matter> chunk = mock(MantleChunk.class);
            matter = new IrisMatter(16, 16, 16);
            when(engineMantle.getEngine()).thenReturn(engine);
            when(engineMantle.getComplex()).thenReturn(complex);
            when(engine.getComplex()).thenReturn(complex);
            when(engine.getDimension()).thenReturn(mock(IrisDimension.class));
            when(mantle.getWorldHeight()).thenReturn(16);
            when(mantle.getChunk(0, 0)).thenReturn(chunk);
            when(chunk.use()).thenReturn(chunk);
            when(chunk.exists(0)).thenReturn(true);
            when(chunk.getOrCreate(0)).thenReturn(matter);
            when(chunk.get(0)).thenReturn(matter);
            when(complex.terrainColumn(0, 0)).thenReturn(Terrain3DColumnFixtures.spans(8, 0, 4, 10, 11));
            for (int y = 5; y <= 9; y++) {
                when(complex.isTerrain3DOpening(0, y, 0)).thenReturn(true);
            }
            writer = new MantleWriter(engineMantle, mantle, 0, 0, 0, false);
        }

        @Override
        public void close() {
            writer.close();
            IrisPlatforms.unbind();
            if (previousPlatform != null) {
                IrisPlatforms.bind(previousPlatform);
            }
        }
    }

    private record Marker(String value) {
    }
}
