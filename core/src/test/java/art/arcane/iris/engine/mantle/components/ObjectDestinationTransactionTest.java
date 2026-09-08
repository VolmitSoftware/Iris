package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.core.link.Identifier;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.history.BoundaryColumnGeometry;
import art.arcane.iris.engine.history.TerrainBoundarySignature;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.engine.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.engine.mantle.MantleWriter;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.terrain.Terrain3DColumnFixtures;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.testsupport.BukkitTestServer;
import art.arcane.iris.util.project.matter.IrisMatterSupport;
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

            transaction.setData(0, 5, 0, mock(PlatformBlockState.class));

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
        PlatformBlockState prerequisite = mock(PlatformBlockState.class);
        PlatformBlockState rejected = mock(PlatformBlockState.class);
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
        PlatformBlockState custom = mock(PlatformBlockState.class);
        PlatformBlockState base = mock(PlatformBlockState.class);
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
        PlatformBlockState custom = mock(PlatformBlockState.class);
        PlatformBlockState base = mock(PlatformBlockState.class);
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
        verify(writer, never()).set(anyInt(), anyInt(), anyInt(), any(PlatformBlockState.class));
        verify(writer, never()).setData(anyInt(), anyInt(), anyInt(), any(Identifier.class));
    }

    @Test
    public void blockReplacementClearsOverlayIdentifierAndRestoresItAfterFailure() {
        MantleWriter writer = writer();
        PlatformBlockState block = mock(PlatformBlockState.class);
        Marker failure = new Marker("failure");
        Identifier originalIdentifier = Identifier.fromString("iris:deferred");
        when(writer.getPrerequisiteDataIfPresent(0, 4, 0, Identifier.class)).thenReturn(originalIdentifier);
        doThrow(new IllegalStateException("publication failed"))
                .when(writer).setData(1, 4, 0, failure);
        ObjectDestinationTransaction transaction = new ObjectDestinationTransaction(writer, 0, 0);

        transaction.setData(0, 4, 0, block);
        assertNull(transaction.getDataIfPresent(0, 4, 0, Identifier.class));
        transaction.setData(1, 4, 0, failure);

        assertThrows(IllegalStateException.class, transaction::commit);
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
            PlatformBlockState air = mock(PlatformBlockState.class);
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
