package com.volmit.host;

import com.volmit.block.BlockState;
import com.volmit.generation.Dependencies;
import com.volmit.mantle.*;
import com.volmit.object.*;
import com.volmit.placement.*;
import com.volmit.resource.ResourceId;
import com.volmit.support.TestContexts;
import com.volmit.world.BlockPosition;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class PlacementPipelineTest {
    private static final BlockState STONE = new BlockState("minecraft:stone");
    private static final BlockState AIR = new BlockState("minecraft:air");
    private static ResourceId id(String name) { return ResourceId.parse("test:" + name); }

    private static PlacementRequest request() {
        var object = new GeneratedObject(new BlockBox(0, 0, 0, 2, 1, 1), new BlockPosition(1, 0, 0),
                List.of(new ObjectBlock(new BlockPosition(1, 0, 0), STONE), new ObjectBlock(new BlockPosition(0, 0, 0), STONE)),
                List.of(new ObjectMarker(new BlockPosition(1, 0, 0), id("owner"), Map.of("name", "column"))));
        return new PlacementRequest(id("attempt"), object, new BlockPosition(-2, -60, -3));
    }

    private static ConfiguredPlacementModifier configured(PlacementModifier modifier) {
        return new ConfiguredPlacementModifier(ResourceId.parse(modifier.id() + "/0"), modifier);
    }

    @Test
    void validatesCompletePlanBeforeWritesAndAfterHooksSeeOverlayAndActualBounds() {
        var events = new ArrayList<String>();
        var modifier = new PlacementModifier() {
            public String id() { return "test:foundation"; }
            public Optional<PlacementRequest> prepare(PlacementContext context, PlacementRequest request) {
                events.add("prepare"); return Optional.of(request);
            }
            public Optional<PlacedBlock> mapBlock(PlacementContext context, PlacedBlock block) {
                events.add("map");
                return block.position().x() == -3 ? Optional.empty() : Optional.of(block);
            }
            public boolean validate(PlacementContext context, PlacementPlan plan) {
                events.add("validate");
                assertEquals(1, plan.blocks().size());
                return true;
            }
            public void beforePlace(PlacementContext context, PlacementAccess access, PlacementPlan plan) {
                events.add("before");
                assertEquals(AIR, access.get(-2, -60, -3));
            }
            public void afterPlace(PlacementContext context, PlacementAccess access, PlacementPlan written) {
                events.add("after");
                assertEquals(STONE, access.get(-2, -60, -3));
                assertEquals(new BlockBox(-2, -60, -3, -1, -59, -2), written.writtenBounds().orElseThrow());
                assertFalse(access instanceof PlacementEdit);
                access.set(new PlacedBlock(new BlockPosition(-2, -61, -3), STONE, Optional.empty()));
            }
        };
        var pipeline = PlacementPipeline.resolve(List.of(configured(modifier)));
        var edit = new RecordingEdit(events);
        var result = pipeline.place(request(), edit, ignored -> TestContexts.placement(), plan -> {
            events.add("host-validate"); assertEquals(0, edit.mutations); return true;
        });
        assertTrue(result.isPresent());
        assertEquals(List.of("prepare", "map", "map", "host-validate", "validate", "before", "write", "marker", "after", "write", "commit", "close"), events);
        assertEquals(2, edit.published.size());
        assertEquals(1, edit.publishedMarkers.size());
        assertThrows(IllegalStateException.class, () -> edit.get(-2, -60, -3));
    }

    @Test
    void validationRejectionPublishesNothingAndDoesNotStartMutating() {
        var modifier = new PlacementModifier() {
            public String id() { return "test:reject"; }
            public boolean validate(PlacementContext context, PlacementPlan plan) { return false; }
        };
        var edit = new RecordingEdit(new ArrayList<>());
        assertTrue(PlacementPipeline.resolve(List.of(configured(modifier)))
                .place(request(), edit, ignored -> TestContexts.placement(), ignored -> true).isEmpty());
        assertEquals(0, edit.mutations);
        assertTrue(edit.published.isEmpty());
        assertTrue(edit.closed);
    }

    @Test
    void failureAfterBufferedObjectWritesDiscardsEntireAttempt() {
        var modifier = new PlacementModifier() {
            public String id() { return "test:failure"; }
            public void afterPlace(PlacementContext context, PlacementAccess access, PlacementPlan plan) {
                access.set(new PlacedBlock(new BlockPosition(-2, -61, -3), STONE, Optional.empty()));
                throw new IllegalStateException("Foundation failed");
            }
        };
        var edit = new RecordingEdit(new ArrayList<>());
        assertThrows(IllegalStateException.class, () -> PlacementPipeline.resolve(List.of(configured(modifier)))
                .place(request(), edit, ignored -> TestContexts.placement(), ignored -> true));
        assertTrue(edit.mutations > 0);
        assertTrue(edit.published.isEmpty());
        assertTrue(edit.publishedMarkers.isEmpty());
        assertTrue(edit.closed);
    }

    @Test
    void prepareRejectionAndFailedCommitCloseWithoutPublication() {
        var rejection = new PlacementModifier() {
            public String id() { return "test:prepare-reject"; }
            public Optional<PlacementRequest> prepare(PlacementContext context, PlacementRequest request) { return Optional.empty(); }
        };
        var rejected = new RecordingEdit(new ArrayList<>());
        assertTrue(PlacementPipeline.resolve(List.of(configured(rejection)))
                .place(request(), rejected, ignored -> TestContexts.placement(), ignored -> true).isEmpty());
        assertEquals(0, rejected.mutations);
        var failed = new RecordingEdit(new ArrayList<>());
        failed.failCommit = true;
        assertThrows(IllegalStateException.class, () -> PlacementPipeline.resolve(List.of())
                .place(request(), failed, ignored -> TestContexts.placement(), ignored -> true));
        assertTrue(failed.closed);
        assertTrue(failed.published.isEmpty());
    }

    @Test
    void rejectsMappedBoundsDuplicateWritesAndInsufficientPreparedReachBeforeMutation() {
        var escape = new PlacementModifier() {
            public String id() { return "test:escape"; }
            public Optional<PlacedBlock> mapBlock(PlacementContext context, PlacedBlock block) {
                return Optional.of(new PlacedBlock(new BlockPosition(5, -60, -3), block.state(), block.blockEntity()));
            }
        };
        var escaped = new RecordingEdit(new ArrayList<>());
        assertThrows(IllegalArgumentException.class, () -> PlacementPipeline.resolve(List.of(configured(escape)))
                .place(request(), escaped, ignored -> TestContexts.placement(), ignored -> true));
        assertEquals(0, escaped.mutations);
        var radius = new PlacementModifier() {
            public String id() { return "test:radius"; }
            public int writeRadius() { return 32; }
        };
        var unprepared = new RecordingEdit(new ArrayList<>());
        assertThrows(IndexOutOfBoundsException.class, () -> PlacementPipeline.resolve(List.of(configured(radius)))
                .place(request(), unprepared, ignored -> TestContexts.placement(), ignored -> true));
        assertEquals(0, unprepared.mutations);
    }

    @Test
    void hooksCannotWriteBeyondReachOrRetainUsableViews() {
        var retained = new AtomicReference<PlacementAccess>();
        var retainedLayer = new AtomicReference<MantleLayer<String>>();
        var modifier = new PlacementModifier() {
            public String id() { return "test:bounded"; }
            public void afterPlace(PlacementContext context, PlacementAccess access, PlacementPlan plan) {
                retained.set(access);
                retainedLayer.set(access.mantle().layer(new MantleKey<>("test:owner", String.class)));
                assertThrows(IndexOutOfBoundsException.class, () -> access.set(new PlacedBlock(new BlockPosition(5, -60, -3), STONE, Optional.empty())));
                assertThrows(UnsupportedOperationException.class, () -> access.mantle().edit());
                assertThrows(IndexOutOfBoundsException.class, () -> access.mantle().layer(new MantleKey<>("test:owner", String.class)).get(5, -60, -3));
            }
        };
        var edit = new RecordingEdit(new ArrayList<>());
        PlacementPipeline.resolve(List.of(configured(modifier))).place(request(), edit, ignored -> TestContexts.placement(), ignored -> true);
        assertThrows(IllegalStateException.class, () -> retained.get().get(-2, -60, -3));
        assertThrows(IllegalStateException.class, () -> retainedLayer.get().get(-2, -60, -3));
    }

    @Test
    void placementDependenciesStayInTheirOwnGraphAndRejectConflicts() {
        var first = new PlacementModifier() { public String id() { return "test:first"; } };
        var second = new PlacementModifier() {
            public String id() { return "test:second"; }
            public Dependencies depends() { return Dependencies.requires("test:first"); }
        };
        assertEquals(List.of(ResourceId.parse("test:first"), ResourceId.parse("test:second")),
                PlacementPipeline.resolve(List.of(configured(second), configured(first))).ordered().stream().map(ConfiguredPlacementModifier::id).toList());
        assertThrows(IllegalArgumentException.class, () -> PlacementPipeline.resolve(List.of(configured(second))));
        assertThrows(IllegalArgumentException.class, () -> PlacementPipeline.resolve(List.of(configured(first), configured(first))));
    }

    @Test
    void mappedWritesOutsideReachFailBeforeMutationAndEachHookKeepsItsOwnReach() {
        var moved = new PlacementModifier() {
            public String id() { return "test:moved"; }
            public Optional<PlacedBlock> mapBlock(PlacementContext context, PlacedBlock block) {
                return Optional.of(new PlacedBlock(block.position().add(new BlockPosition(8, 0, 0)), block.state(), block.blockEntity()));
            }
        };
        var edit = new RecordingEdit(new ArrayList<>());
        assertThrows(IndexOutOfBoundsException.class, () -> PlacementPipeline.resolve(List.of(configured(moved)))
                .place(request(), edit, ignored -> TestContexts.placement(), ignored -> true));
        assertEquals(0, edit.mutations);
        var large = new PlacementModifier() {
            public String id() { return "test:large"; }
            public int writeRadius() { return 2; }
            public void afterPlace(PlacementContext context, PlacementAccess access, PlacementPlan plan) {
                assertEquals(AIR, access.get(0, -60, -3));
                assertEquals(new BlockBox(-5, -64, -5, 1, 320, 0), access.mantle().bounds());
                assertNull(access.mantle().layer(new MantleKey<>("test:owner", String.class))
                        .get(0, -60, -3));
            }
        };
        var small = new PlacementModifier() {
            public String id() { return "test:small"; }
            public void afterPlace(PlacementContext context, PlacementAccess access, PlacementPlan plan) {
                assertThrows(IndexOutOfBoundsException.class, () -> access.get(0, -60, -3));
                assertEquals(new BlockBox(-3, -64, -3, -1, 320, -2), access.mantle().bounds());
                assertThrows(IndexOutOfBoundsException.class, () -> access.mantle()
                        .layer(new MantleKey<>("test:owner", String.class)).get(0, -60, -3));
            }
        };
        assertTrue(PlacementPipeline.resolve(List.of(configured(small), configured(large)))
                .place(request(), new RecordingEdit(new ArrayList<>()), ignored -> TestContexts.placement(), ignored -> true).isPresent());
    }

    /** In-memory transactional test adapter; production storage/persistence is deliberately absent. */
    private static class RecordingEdit implements PlacementEdit {
        final List<String> events;
        final Map<BlockPosition, BlockState> pending = new HashMap<>();
        final Map<BlockPosition, BlockState> published = new HashMap<>();
        final List<PlacedMarker> pendingMarkers = new ArrayList<>();
        final List<PlacedMarker> publishedMarkers = new ArrayList<>();
        boolean closed;
        boolean failCommit;
        int mutations;
        RecordingEdit(List<String> events) { this.events = events; }
        private void checkOpen() { if (closed) throw new IllegalStateException("Closed edit"); }
        public BlockBox bounds() { checkOpen(); return new BlockBox(-16, -64, -16, 16, 320, 16); }
        public BlockState get(int x, int y, int z) { checkOpen(); return pending.getOrDefault(new BlockPosition(x, y, z), AIR); }
        public void set(PlacedBlock block) { checkOpen(); mutations++; events.add("write"); pending.put(block.position(), block.state()); }
        public void marker(PlacedMarker marker) { checkOpen(); mutations++; events.add("marker"); pendingMarkers.add(marker); }
        public Mantle mantle() {
            checkOpen();
            return new Mantle() {
                public BlockBox bounds() { return RecordingEdit.this.bounds(); }
                public MantleEdit edit() { throw new UnsupportedOperationException(); }
                public <T> MantleLayer<T> layer(MantleKey<T> key) {
                    return new MantleLayer<>() {
                        public T get(int x, int y, int z) { checkOpen(); return null; }
                        public void set(int x, int y, int z, T value) { throw new AssertionError("Unused mantle mutation"); }
                        public void remove(int x, int y, int z) { throw new AssertionError("Unused mantle mutation"); }
                        public void clear(BlockBox area) { throw new AssertionError("Unused mantle mutation"); }
                        public void visit(BlockBox area, MantleVisitor<T> visitor) { checkOpen(); }
                    };
                }
            };
        }
        public void commit() {
            checkOpen();
            if (failCommit) throw new IllegalStateException("Commit failed");
            events.add("commit"); published.putAll(pending); publishedMarkers.addAll(pendingMarkers); closed = true;
        }
        public void close() {
            if (!events.contains("close")) events.add("close");
            closed = true; pending.clear(); pendingMarkers.clear();
        }
    }
}
