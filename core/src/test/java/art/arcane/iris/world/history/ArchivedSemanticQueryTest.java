package art.arcane.iris.world.history;

import art.arcane.iris.generation.runtime.IrisEngine;
import art.arcane.iris.pack.AtomicDirectoryPublisher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class ArchivedSemanticQueryTest {
    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void objectsAndPoiRemainQueryableAfterTheOldPackAndRuntimeAreGone() throws Exception {
        Path world = temporary.newFolder("world").toPath();
        Path oldPack = pack("old");
        Path newPack = pack("new");
        GenerationEpoch.DimensionContract dimension = new GenerationEpoch.DimensionContract(
                "overworld", "iris:overworld", "NORMAL", "OVERWORLD", 63, -64, 384, 384, 1D,
                false, "none", 0, "0".repeat(64),
                GenerationEpochContractFactory.CURRENT_DIMENSION_TYPE_FINGERPRINT_SCHEMA, "c".repeat(64));
        GenerationHistory initial = GenerationHistory.create(world, oldPack, fingerprint(oldPack), 42L,
                dimension, GenerationRegistryContract.empty());
        String oldEpoch = initial.activeEpoch().epochId();
        GenerationSemanticIndex index = GenerationSemanticIndex.loadRequired(world);
        index.claimAndPersist(ChunkGenerationSemantics.builder(-2, 3, 1L)
                .addObject("iris:removed_object")
                .addPointOfInterest(new ChunkGenerationSemantics.PointOfInterest("buried_treasure",
                        new ChunkGenerationSemantics.BlockPosition(-30, 70, 51)))
                .seal().build());
        Path regions = Files.createDirectories(world.resolve("region"));
        SavedTerrainTestRegion.write(regions.resolve("r.-1.0.mca"), new int[][]{{-2, 3}});
        GenerationHistory updated = GenerationHistory.open(world);
        updated.stageUpdate(newPack, fingerprint(newPack), dimension, GenerationRegistryContract.empty(), 32);
        updated.prepareCurrentGenerator(32);
        GenerationHistory archived = GenerationHistory.open(world);
        assertTrue(Files.exists(archived.paths().packRoot(oldEpoch)));
        AtomicDirectoryPublisher.deleteTree(archived.paths().packRoot(oldEpoch));
        assertFalse(Files.exists(archived.paths().packRoot(oldEpoch)));
        IrisEngine engine = engine(archived);

        assertEquals(Set.of("iris:removed_object"), engine.getObjectsAt(-2, 3));
        assertEquals(Set.of(new ChunkGenerationSemantics.PointOfInterest("buried_treasure", new ChunkGenerationSemantics.BlockPosition(-30, 70, 51))), engine.getPOIsAt(-2, 3));
        verify(engine, never()).getMantle();
        verify(engine, never()).openGenerationHistoryCoordinateScope(anyInt(), anyInt());
    }

    @Test
    public void sealedEmptyRecordsDoNotFallBackToCurrentMantle() throws Exception {
        GenerationHistory history = mock(GenerationHistory.class);
        when(history.semantics(1, 2)).thenReturn(Optional.of(ChunkGenerationSemantics.builder(1, 2, 1L).seal().build()));
        IrisEngine engine = engine(history);

        assertTrue(engine.getObjectsAt(1, 2).isEmpty());
        assertTrue(engine.getPOIsAt(1, 2).isEmpty());
        verify(engine, never()).getMantle();
        verify(engine, never()).openGenerationHistoryCoordinateScope(anyInt(), anyInt());
    }

    private static IrisEngine engine(GenerationHistory history) {
        GenerationHistoryRuntimeRouter router = mock(GenerationHistoryRuntimeRouter.class);
        when(router.history()).thenReturn(history);
        IrisEngine engine = mock(IrisEngine.class);
        when(engine.getGenerationHistoryRuntimeRouter()).thenReturn(Optional.of(router));
        doCallRealMethod().when(engine).getObjectsAt(anyInt(), anyInt());
        doCallRealMethod().when(engine).getPOIsAt(anyInt(), anyInt());
        return engine;
    }

    private Path pack(String name) throws Exception {
        Path pack = temporary.newFolder(name).toPath();
        Files.createDirectories(pack.resolve("dimensions"));
        Files.writeString(pack.resolve("dimensions/overworld.json"), "{\"name\":\"" + name + "\"}");
        return pack;
    }

    private static String fingerprint(Path pack) throws Exception {
        return GenerationPackFingerprint.compute(pack, GenerationPackFingerprint.CURRENT_VERSION);
    }
}
