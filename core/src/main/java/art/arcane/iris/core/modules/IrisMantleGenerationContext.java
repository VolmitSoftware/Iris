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

import art.arcane.iris.builtin.MatterMantle;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.util.common.data.B;
import com.volmit.config.FeatureSettings;
import com.volmit.generation.MantleGenerationContext;
import com.volmit.host.MantleRegistration;
import com.volmit.geometry.BlockBox;
import com.volmit.mantle.Mantle;
import com.volmit.placement.PlacementTerrain;
import com.volmit.world.PackWorld;
import com.volmit.world.TerrainSampler;

import java.util.Collection;
import java.util.Optional;

/**
 * One chunk of an Iris engine as an IrisApi mantle invocation: the mantle around the chunk as far as the
 * pipeline reaches, Iris's terrain streams over the same area, the pack's module settings and the pack
 * snapshot's resources.
 */
final class IrisMantleGenerationContext implements MantleGenerationContext {
    private static final int CHUNK_SHIFT = 4;
    private static final int CHUNK_WIDTH = 16;
    /** Mantle stages see the terrain at least this far around their chunk, as chunk stages do (IrisApi's promise). */
    private static final int TERRAIN_MARGIN_BLOCKS = 16;

    private final Engine engine;
    private final int originX;
    private final int originZ;
    private final IrisTerrainSampler terrain;
    private final ChunkModuleSettings settings;
    private final IrisModuleMantle mantle;
    private final IrisMantleChunk irisChunk;
    private PackWorld world;
    private PlacementTerrain placementTerrain;

    /** {@code irisChunk} is what Iris's built-in mantle stages see of the chunk as an extension. */
    IrisMantleGenerationContext(Engine engine, int chunkX, int chunkZ, int reachBlocks, IrisModuleMantle.Chunks chunks,
                                Collection<MantleRegistration<?>> layers, IrisMantleChunk irisChunk) {
        PackResourceIds ids = new PackResourceIds(engine.getData().getDataFolder());
        this.engine = engine;
        this.irisChunk = irisChunk;
        this.originX = chunkX << CHUNK_SHIFT;
        this.originZ = chunkZ << CHUNK_SHIFT;
        this.terrain = new IrisTerrainSampler(engine, ids, originX, originZ, Math.max(reachBlocks, TERRAIN_MARGIN_BLOCKS));
        this.settings = new ChunkModuleSettings(engine, ids);
        BlockBox reach = ApiHeights.columns(engine, originX - reachBlocks, originZ - reachBlocks,
                originX + CHUNK_WIDTH + reachBlocks, originZ + CHUNK_WIDTH + reachBlocks);
        PlannedBlocks plannedBlocks = PlannedBlocks.of(engine, reach, chunks, new PlatformBlockStates(B::getState));
        this.mantle = new IrisModuleMantle(reach, chunks, layers, plannedBlocks);
    }

    @Override
    public int originX() {
        return originX;
    }

    @Override
    public int originZ() {
        return originZ;
    }

    @Override
    public TerrainSampler terrain() {
        return terrain;
    }

    @Override
    public FeatureSettings settings() {
        return settings;
    }

    @Override
    public Mantle mantle() {
        return mantle;
    }

    /** Iris's own view of the chunk, and the matter of the mantle as far as the stage reaches. */
    @Override
    public <T> Optional<T> extension(Class<T> type) {
        if (type == IrisMantleChunk.class) {
            return HostExtensions.as(type, irisChunk);
        }
        if (type == MatterMantle.class) {
            return HostExtensions.as(type, mantle);
        }
        return Optional.empty();
    }


    /** The chunk's mantle as Iris's object placer writes it, within the chunk's reach. */
    @Override
    public PlacementTerrain placementTerrain() {
        if (placementTerrain == null) {
            placementTerrain = PlacerTerrain.of(irisChunk.writer(), engine.getData());
        }
        return placementTerrain;
    }

    /** The world this invocation first asked for; it keeps it if the world restarts meanwhile. */
    @Override
    public PackWorld world() {
        if (world == null) {
            world = engine.getGeneration().world();
        }
        return world;
    }
}
