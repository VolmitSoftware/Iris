package art.arcane.iris.modded.service;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.util.project.matter.TileWrapper;
import art.arcane.volmlib.util.function.Consumer4;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.mantle.runtime.MantleDataAdapter;
import art.arcane.volmlib.util.mantle.runtime.MantleHooks;
import art.arcane.volmlib.util.matter.IrisMatter;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterSlice;
import art.arcane.volmlib.util.matter.MatterUpdate;
import art.arcane.volmlib.util.matter.slices.UpdateMatter;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

public class ModdedChunkUpdateServiceTest {
    @Test
    public void scansWhenPlayersArePresent() {
        assertTrue(ModdedChunkUpdateService.hasUpdateTargets(true, false));
    }

    @Test
    public void scansHeadlessForceLoadedChunks() {
        assertTrue(ModdedChunkUpdateService.hasUpdateTargets(false, true));
    }

    @Test
    public void skipsLevelsWithoutPlayersOrForcedChunks() {
        assertFalse(ModdedChunkUpdateService.hasUpdateTargets(false, false));
    }

    @Test
    public void deferredSliceIsDeletedAfterMaterialization() {
        RecordingMantleChunk chunk = new RecordingMantleChunk();

        ModdedChunkUpdateService.materializeDeferredSlice(
                chunk,
                TileWrapper.class,
                () -> chunk.record("materialize")
        );

        assertEquals(List.of("materialize", "delete:" + TileWrapper.class.getName()),
                chunk.operations());
    }

    @Test
    public void failedMaterializationRetainsDeferredSlice() {
        RecordingMantleChunk chunk = new RecordingMantleChunk();

        assertThrows(IllegalStateException.class, () ->
                ModdedChunkUpdateService.materializeDeferredSlice(
                        chunk,
                        TileWrapper.class,
                        () -> {
                            chunk.record("materialize");
                            throw new IllegalStateException("materialization failure");
                        }
                ));

        assertEquals(List.of("materialize"), chunk.operations());
    }

    @Test
    public void failedUpdateForgetsOnlyTheUpdatesAlreadyApplied() {
        ChestUpdateMantleChunk chunk = new ChestUpdateMantleChunk();

        assertThrows(IllegalStateException.class, () ->
                ModdedChunkUpdateService.applyQueuedUpdates(chunk, (Integer x, Integer yf, Integer z) -> {
                    if (x == 2) {
                        throw new IllegalStateException("update failure");
                    }
                }));

        assertFalse(chunk.deleted());
        assertNull(chunk.slice.get(1, 5, 1));
        assertNotNull(chunk.slice.get(2, 5, 2));
    }

    @Test
    public void failedChunkIsReportedAndTheNextChunkStillRuns() {
        AtomicInteger updatedChunks = new AtomicInteger();

        try (MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            logging.when(() -> IrisLogging.errorOnce(anyString(), anyString(), any(Object[].class))).thenReturn(true);

            ModdedChunkUpdateService.isolateChunkFailure(
                    () -> {
                        throw new IllegalStateException("chunk failure");
                    },
                    () -> false, "example:updates", 0, 0);
            ModdedChunkUpdateService.isolateChunkFailure(
                    updatedChunks::incrementAndGet, () -> false, "example:updates", 4, 0);

            assertEquals(1, updatedChunks.get());
            logging.verify(() -> IrisLogging.errorOnce(anyString(), anyString(), any(Object[].class)));
            logging.verify(() -> IrisLogging.reportError(any(Throwable.class)));
        }
    }

    @Test
    public void endedPassRethrowsTheChunkFailure() {
        IllegalStateException failure = new IllegalStateException("chunk failure");

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () ->
                ModdedChunkUpdateService.isolateChunkFailure(
                        () -> {
                            throw failure;
                        },
                        () -> true, "example:updates", 0, 0));

        assertSame(failure, thrown);
    }

    private static final class ChestUpdateMantleChunk extends MantleChunk<Matter> {
        private final Matter section = new IrisMatter(16, 16, 16);
        private final MatterSlice<MatterUpdate> slice = section.slice(MatterUpdate.class);
        private boolean deleted;

        private ChestUpdateMantleChunk() {
            super(1, 0, 0, emptyAdapter(), MantleHooks.NONE);
            slice.set(1, 5, 1, UpdateMatter.ON);
            slice.set(2, 5, 2, UpdateMatter.ON);
        }

        @Override
        public Matter get(int index) {
            return section;
        }

        @Override
        public <T> void iterate(Class<T> type, Consumer4<Integer, Integer, Integer, T> iterator) {
            if (type == MatterUpdate.class) {
                iterator.accept(1, 5, 1, type.cast(UpdateMatter.ON));
                iterator.accept(2, 5, 2, type.cast(UpdateMatter.ON));
            }
        }

        @Override
        public void deleteSlices(Class<?> type) {
            if (type == MatterUpdate.class) {
                deleted = true;
            }
        }

        private boolean deleted() {
            return deleted;
        }
    }

    private static final class RecordingMantleChunk extends MantleChunk<Matter> {
        private final ArrayList<String> operations = new ArrayList<>();

        private RecordingMantleChunk() {
            super(1, 0, 0, emptyAdapter(), MantleHooks.NONE);
        }

        @Override
        public void deleteSlices(Class<?> type) {
            operations.add("delete:" + type.getName());
        }

        private void record(String operation) {
            operations.add(operation);
        }

        private List<String> operations() {
            return List.copyOf(operations);
        }
    }

    @SuppressWarnings("unchecked")
    private static MantleDataAdapter<Matter> emptyAdapter() {
        return (MantleDataAdapter<Matter>) Proxy.newProxyInstance(
                MantleDataAdapter.class.getClassLoader(),
                new Class<?>[]{MantleDataAdapter.class},
                (proxy, method, arguments) -> {
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
