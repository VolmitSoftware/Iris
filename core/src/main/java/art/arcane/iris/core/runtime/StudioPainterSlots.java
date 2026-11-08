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

package art.arcane.iris.core.runtime;

import com.volmit.object.BlockEntityData;
import com.volmit.tooling.world.ChunkPainter;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Which studio worlds are painted by a tool instead of generated from their pack, keyed by the logical name of the
 * world. A tool world installs its slot before the world is created, so the first chunk the world generates is already
 * the tool's, and removes it when the world closes. The world's generator reads the slot on every chunk, so a slot that
 * is installed or removed takes effect at the next chunk.
 */
public final class StudioPainterSlots {
    private static final Map<String, Slot> SLOTS = new ConcurrentHashMap<>();

    private StudioPainterSlots() {
    }

    /**
     * A tool's claim on one world: the painter, and where the block entities it painted go. They go to
     * {@code blockEntities} because they can only be applied once the chunk is live, which is later than the paint.
     */
    public record Slot(ChunkPainter painter, Consumer<PaintedChunk> blockEntities) {
        public Slot {
            Objects.requireNonNull(painter, "painter");
            Objects.requireNonNull(blockEntities, "blockEntities");
        }
    }

    /** The block entities one paint of chunk {@code chunkX}, {@code chunkZ} asked for; never empty. */
    public record PaintedChunk(int chunkX, int chunkZ, List<PaintedBlockEntity> blockEntities) {
        public PaintedChunk {
            blockEntities = List.copyOf(blockEntities);
        }
    }

    /** A block entity at chunk-local {@code x} and {@code z} and world {@code y}. */
    public record PaintedBlockEntity(int x, int y, int z, BlockEntityData data) {
        public PaintedBlockEntity {
            Objects.requireNonNull(data, "data");
        }
    }

    /**
     * Makes the world called {@code worldName} a painted one.
     *
     * @throws IllegalStateException when a tool painted that world already
     */
    public static void install(String worldName, Slot slot) {
        Objects.requireNonNull(worldName, "worldName");
        Objects.requireNonNull(slot, "slot");
        if (SLOTS.putIfAbsent(worldName, slot) != null) {
            throw new IllegalStateException("The world " + worldName + " is painted by a tool already");
        }
    }

    /** The slot of the world called {@code worldName}; empty for a world generated from its pack. */
    public static Optional<Slot> slotOf(String worldName) {
        return worldName == null ? Optional.empty() : Optional.ofNullable(SLOTS.get(worldName));
    }

    public static boolean isInstalled(String worldName) {
        return slotOf(worldName).isPresent();
    }

    /** Gives the world back to its pack. Does nothing for a world nobody painted. */
    public static void remove(String worldName) {
        if (worldName != null) {
            SLOTS.remove(worldName);
        }
    }
}
