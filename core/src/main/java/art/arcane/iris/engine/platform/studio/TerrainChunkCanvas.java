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

package art.arcane.iris.engine.platform.studio;

import art.arcane.iris.core.modules.PlatformBlockStates;
import art.arcane.iris.core.runtime.StudioPainterSlots.PaintedBlockEntity;
import art.arcane.iris.engine.data.chunk.TerrainChunk;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import com.volmit.block.BlockState;
import com.volmit.object.BlockEntityData;
import com.volmit.tooling.world.ChunkCanvas;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * The canvas of one paint of a tool world: block states go into a {@link TerrainChunk}, and the block entities the
 * painter asks for are kept to be applied once the chunk is live. One canvas serves one paint on one thread.
 */
public final class TerrainChunkCanvas implements ChunkCanvas {
    private static final int CHUNK_SIZE = 16;

    private final TerrainChunk chunk;
    private final PlatformBlockStates states;
    private final List<PaintedBlockEntity> blockEntities = new ArrayList<>();

    /** {@code platformStates} turns a block state key into the platform's state; it fails for a key the platform lacks. */
    public TerrainChunkCanvas(TerrainChunk chunk, Function<String, PlatformBlockState> platformStates) {
        this.chunk = Objects.requireNonNull(chunk, "chunk");
        this.states = new PlatformBlockStates(Objects.requireNonNull(platformStates, "platformStates"));
    }

    /** A canvas over {@code chunk} that resolves block states through the platform Iris is bound to. */
    public static TerrainChunkCanvas onPlatform(TerrainChunk chunk) {
        return new TerrainChunkCanvas(chunk, TerrainChunkCanvas::platformState);
    }

    private static PlatformBlockState platformState(String key) {
        PlatformBlockState state = B.getStateOrNull(key, false);
        if (state == null) {
            throw new IllegalArgumentException("The platform has no block state " + key);
        }
        return state;
    }

    @Override
    public int minY() {
        return chunk.getMinHeight();
    }

    @Override
    public int maxY() {
        return chunk.getMaxHeight();
    }

    @Override
    public void set(int x, int y, int z, BlockState state) {
        requireInside(x, y, z);
        chunk.setBlock(x, y, z, states.toPlatform(Objects.requireNonNull(state, "state")));
        blockEntities.removeIf(painted -> painted.x() == x && painted.y() == y && painted.z() == z);
    }

    @Override
    public void set(int x, int y, int z, BlockState state, BlockEntityData data) {
        Objects.requireNonNull(data, "data");
        set(x, y, z, state);
        blockEntities.add(new PaintedBlockEntity(x, y, z, data));
    }

    /** The block entities painted so far, one for each position that still holds the block it was painted with. */
    public List<PaintedBlockEntity> blockEntities() {
        return List.copyOf(blockEntities);
    }

    private void requireInside(int x, int y, int z) {
        if (x < 0 || x >= CHUNK_SIZE || z < 0 || z >= CHUNK_SIZE) {
            throw new IllegalArgumentException("Chunk-local x and z are 0 to 15: " + x + ", " + z);
        }
        if (y < minY() || y >= maxY()) {
            throw new IllegalArgumentException("Y " + y + " is outside the world, " + minY() + " up to " + maxY());
        }
    }
}
