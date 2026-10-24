package com.volmit.support;

import com.volmit.block.BlockState;
import com.volmit.mantle.BlockBox;
import com.volmit.mantle.Mantle;
import com.volmit.placement.PlacedBlock;
import com.volmit.placement.PlacedMarker;
import com.volmit.placement.PlacementEdit;
import com.volmit.world.BlockPosition;
import com.volmit.world.BlockView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** In-memory transactional test adapter over a base view; production storage is absent. */
public final class MemoryEdit implements PlacementEdit {
    public enum Status { OPEN, COMMITTED, CLOSED }

    private final BlockView base;
    private final Mantle mantle;
    private final Map<BlockPosition, BlockState> pending = new HashMap<>();
    private final Map<BlockPosition, BlockState> published = new HashMap<>();
    private final List<PlacedMarker> pendingMarkers = new ArrayList<>();
    private final List<PlacedMarker> publishedMarkers = new ArrayList<>();
    private Status status = Status.OPEN;
    private boolean committed;

    public MemoryEdit(BlockView base) {
        this(base, null);
    }

    /** An edit whose placement has {@code mantle}; null for none. */
    public MemoryEdit(BlockView base, Mantle mantle) {
        this.base = base;
        this.mantle = mantle;
    }

    public boolean committed() { return committed; }
    public Status status() { return status; }
    public Map<BlockPosition, BlockState> published() { return Map.copyOf(published); }
    public List<PlacedMarker> publishedMarkers() { return List.copyOf(publishedMarkers); }

    public BlockState publishedAt(int x, int y, int z) {
        return published.getOrDefault(new BlockPosition(x, y, z), base.get(x, y, z));
    }

    @Override
    public BlockBox bounds() {
        checkOpen();
        return base.bounds();
    }

    @Override
    public BlockState get(int x, int y, int z) {
        checkOpen();
        return pending.getOrDefault(new BlockPosition(x, y, z), base.get(x, y, z));
    }

    @Override
    public void set(int x, int y, int z, BlockState state) {
        checkOpen();
        if (!base.bounds().contains(x, y, z)) {
            throw new IndexOutOfBoundsException("Write outside prepared area: " + new BlockPosition(x, y, z));
        }
        pending.put(new BlockPosition(x, y, z), state);
    }

    @Override
    public void set(PlacedBlock block) {
        var position = block.position();
        set(position.x(), position.y(), position.z(), block.state());
    }

    @Override
    public void marker(PlacedMarker marker) {
        checkOpen();
        pendingMarkers.add(marker);
    }

    @Override
    public Mantle mantle() {
        checkOpen();
        if (mantle == null) throw new AssertionError("Unused mantle");
        return mantle;
    }

    @Override
    public void commit() {
        checkOpen();
        published.putAll(pending);
        publishedMarkers.addAll(pendingMarkers);
        committed = true;
        status = Status.COMMITTED;
    }

    @Override
    public void close() {
        pending.clear();
        pendingMarkers.clear();
        status = Status.CLOSED;
    }

    private void checkOpen() {
        if (status != Status.OPEN) throw new IllegalStateException("Edit is " + status);
    }
}
