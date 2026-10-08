/*
 * Iris is a World Generator for Minecraft Servers
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

package art.arcane.iris.core.modules;

import art.arcane.iris.builtin.blocks.ObjectMarkers;
import art.arcane.iris.engine.object.placement.IObjectPlacer;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.matter.MatterMarker;
import com.volmit.block.BlockState;
import com.volmit.mantle.BlockBox;
import com.volmit.mantle.Mantle;
import com.volmit.placement.PlacedBlock;
import com.volmit.placement.PlacedMarker;
import com.volmit.placement.PlacementEdit;
import com.volmit.world.BlockPosition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * One placement's writes, buffered over an Iris object placer until IrisApi commits them. Reads see the
 * buffered writes. Committing writes blocks in the order they were first set, then their tile data and
 * markers, then reports the object's own blocks to the caller's listener. The placer offers no rollback,
 * so a placer that fails part way through a commit keeps the writes before the failure.
 */
final class ObjectPlacerEdit implements PlacementEdit {
    private enum State { OPEN, COMMITTED, FAILED, CLOSED }

    private final IObjectPlacer placer;
    private final BlockBox bounds;
    private final PlatformBlockStates states;
    private final BiConsumer<art.arcane.volmlib.util.math.BlockPosition, PlatformBlockState> listener;
    private final Map<BlockPosition, PlacedBlock> blocks = new LinkedHashMap<>();
    private final List<PlacedMarker> markers = new ArrayList<>();
    private final List<BlockPosition> objectBlocks = new ArrayList<>();
    private State state = State.OPEN;

    /** {@code listener} hears each object block after the commit, as Iris placements report them; null for none. */
    ObjectPlacerEdit(IObjectPlacer placer, BlockBox bounds, PlatformBlockStates states,
                     BiConsumer<art.arcane.volmlib.util.math.BlockPosition, PlatformBlockState> listener) {
        this.placer = placer;
        this.bounds = bounds;
        this.states = states;
        this.listener = listener;
    }

    @Override
    public BlockBox bounds() {
        requireOpen();
        return bounds;
    }

    @Override
    public BlockState get(int x, int y, int z) {
        requireInside(x, y, z);
        PlacedBlock buffered = blocks.get(new BlockPosition(x, y, z));
        return buffered != null ? buffered.state() : states.toApi(placer.get(x, y, z));
    }

    @Override
    public void set(PlacedBlock block) {
        BlockPosition position = block.position();
        requireInside(position.x(), position.y(), position.z());
        blocks.put(position, block);
    }

    @Override
    public void marker(PlacedMarker marker) {
        BlockPosition position = marker.position();
        requireInside(position.x(), position.y(), position.z());
        ObjectMarkers.requireIrisMarker(marker);
        markers.add(marker);
    }

    @Override
    public Mantle mantle() {
        requireOpen();
        throw new UnsupportedOperationException("Mantle access from placement stages is not bridged to Iris yet");
    }

    /** Marks a buffered write as one of the object's own blocks, which the listener hears after the commit. */
    void recordObjectBlock(BlockPosition position) {
        requireOpen();
        objectBlocks.add(position);
    }

    @Override
    public void commit() {
        requireOpen();
        state = State.FAILED;
        writeBlocks();
        writeMarkers();
        reportObjectBlocks();
        state = State.COMMITTED;
    }

    @Override
    public void close() {
        blocks.clear();
        markers.clear();
        objectBlocks.clear();
        state = State.CLOSED;
    }

    private void writeBlocks() {
        for (PlacedBlock block : blocks.values()) {
            BlockPosition position = block.position();
            placer.set(position.x(), position.y(), position.z(), states.toPlatform(block.state()));
            block.blockEntity().ifPresent(entity -> placer.setTile(position.x(), position.y(), position.z(), BlockEntities.tileOf(entity)));
        }
    }

    private void writeMarkers() {
        for (PlacedMarker marker : markers) {
            BlockPosition position = marker.position();
            placer.setData(position.x(), position.y(), position.z(), new MatterMarker(ObjectMarkers.markerKey(marker)));
        }
    }

    private void reportObjectBlocks() {
        if (listener == null) {
            return;
        }
        for (BlockPosition position : objectBlocks) {
            PlacedBlock written = blocks.get(position);
            listener.accept(new art.arcane.volmlib.util.math.BlockPosition(position.x(), position.y(), position.z()),
                    states.toPlatform(written.state()));
        }
    }

    private void requireInside(int x, int y, int z) {
        if (!bounds().contains(x, y, z)) {
            throw new IndexOutOfBoundsException("Block " + x + ", " + y + ", " + z + " is outside the placement area " + bounds);
        }
    }

    private void requireOpen() {
        if (state != State.OPEN) {
            throw new IllegalStateException("The placement edit is " + state.name().toLowerCase(Locale.ROOT));
        }
    }
}
