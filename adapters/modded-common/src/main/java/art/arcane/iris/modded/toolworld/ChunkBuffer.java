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

package art.arcane.iris.modded.toolworld;

import com.volmit.block.BlockState;
import com.volmit.object.BlockEntityData;
import com.volmit.tooling.world.ChunkCanvas;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** What one paint of a tool world chunk asked for, kept until it is written to a chunk. One buffer serves one paint on one thread. */
final class ChunkBuffer implements ChunkCanvas {
    private static final int CHUNK_SIZE = 16;

    /** A block entity asked for at chunk-local {@code x} and {@code z} and world {@code y}. */
    record Painted(int x, int y, int z, BlockEntityData data) {
    }

    private final int minY;
    private final int height;
    private final net.minecraft.world.level.block.state.BlockState[] states;
    private final List<Painted> blockEntities = new ArrayList<>();

    ChunkBuffer(int minY, int height) {
        this.minY = minY;
        this.height = height;
        this.states = new net.minecraft.world.level.block.state.BlockState[CHUNK_SIZE * CHUNK_SIZE * height];
    }

    @Override
    public int minY() {
        return minY;
    }

    @Override
    public int maxY() {
        return minY + height;
    }

    @Override
    public void set(int x, int y, int z, BlockState state) {
        requireInside(x, y, z);
        states[indexOf(x, y, z)] = ToolBlocks.minecraftStateOf(Objects.requireNonNull(state, "state"));
        blockEntities.removeIf(painted -> painted.x() == x && painted.y() == y && painted.z() == z);
    }

    @Override
    public void set(int x, int y, int z, BlockState state, BlockEntityData data) {
        Objects.requireNonNull(data, "data");
        set(x, y, z, state);
        blockEntities.add(new Painted(x, y, z, data));
    }

    /** The state painted at chunk-local {@code x} and {@code z} and world {@code y}; null where nothing was painted. */
    net.minecraft.world.level.block.state.BlockState stateAt(int x, int y, int z) {
        return states[indexOf(x, y, z)];
    }

    List<Painted> blockEntities() {
        return List.copyOf(blockEntities);
    }

    private int indexOf(int x, int y, int z) {
        return ((y - minY) * CHUNK_SIZE + z) * CHUNK_SIZE + x;
    }

    private void requireInside(int x, int y, int z) {
        if (x < 0 || x >= CHUNK_SIZE || z < 0 || z >= CHUNK_SIZE) {
            throw new IllegalArgumentException("Chunk-local x and z are 0 to 15: " + x + ", " + z);
        }
        if (y < minY || y >= maxY()) {
            throw new IllegalArgumentException("Y " + y + " is outside the world, " + minY + " up to " + maxY());
        }
    }
}
