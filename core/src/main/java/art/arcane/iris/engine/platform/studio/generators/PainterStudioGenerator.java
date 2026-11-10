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

package art.arcane.iris.engine.platform.studio.generators;

import art.arcane.iris.core.runtime.StudioPainterSlots;
import art.arcane.iris.engine.data.chunk.TerrainChunk;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.GenerationSessionLease;
import art.arcane.iris.engine.framework.WrongEngineBroException;
import art.arcane.iris.engine.platform.studio.StudioGenerator;
import art.arcane.iris.engine.platform.studio.TerrainChunkCanvas;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.util.project.context.IrisContext;

import java.util.Objects;
import java.util.function.Function;

/** Generates the chunks of a tool world: whatever the world's {@link StudioPainterSlots.Slot} paints, and nothing else. */
public final class PainterStudioGenerator implements StudioGenerator {
    private static final String LEASE_OPERATION = "bukkit_tool_world_paint";

    private final StudioPainterSlots.Slot slot;
    private final Function<TerrainChunk, TerrainChunkCanvas> canvases;

    public PainterStudioGenerator(StudioPainterSlots.Slot slot) {
        this(slot, TerrainChunkCanvas::onPlatform);
    }

    PainterStudioGenerator(StudioPainterSlots.Slot slot, Function<TerrainChunk, TerrainChunkCanvas> canvases) {
        this.slot = Objects.requireNonNull(slot, "slot");
        this.canvases = Objects.requireNonNull(canvases, "canvases");
    }

    /** The slot this generator paints for; a world whose slot changed needs a new generator. */
    public StudioPainterSlots.Slot slot() {
        return slot;
    }

    @Override
    public void generateChunk(Engine engine, TerrainChunk terrainChunk, int chunkX, int chunkZ)
            throws WrongEngineBroException {
        try (GenerationSessionLease lease = engine.acquireGenerationLease(LEASE_OPERATION);
             IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            paint(terrainChunk, chunkX, chunkZ);
        }
    }

    /** Paints the chunk into {@code terrainChunk}; a painter that throws leaves the chunk as far as it got. */
    void paint(TerrainChunk terrainChunk, int chunkX, int chunkZ) {
        TerrainChunkCanvas canvas = canvases.apply(terrainChunk);
        try {
            slot.painter().paint(chunkX, chunkZ, canvas);
        } catch (RuntimeException failure) {
            IrisLogging.reportError("A tool world painter failed at chunk " + chunkX + "," + chunkZ + ".", failure);
        }
        if (!canvas.blockEntities().isEmpty()) {
            slot.blockEntities().accept(new StudioPainterSlots.PaintedChunk(chunkX, chunkZ, canvas.blockEntities()));
        }
    }
}
