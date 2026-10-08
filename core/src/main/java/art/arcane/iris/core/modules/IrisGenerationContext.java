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

import art.arcane.iris.engine.framework.Engine;
import com.volmit.block.BlockState;
import com.volmit.config.FeatureSettings;
import com.volmit.generation.GenerationContext;
import com.volmit.host.MantleRegistration;
import com.volmit.mantle.BlockBox;
import com.volmit.mantle.Mantle;
import com.volmit.resource.PackResources;
import com.volmit.world.TerrainSampler;

import java.util.Collection;

/**
 * One chunk of an Iris engine as an IrisApi chunk invocation: the chunk's block hunk, Iris's terrain
 * streams, the pack's module settings and the chunk's mantle, which IrisApi gives chunk stages read-only.
 * Pack resources are the pack snapshot's, shared by every invocation of it.
 */
final class IrisGenerationContext implements GenerationContext {
    /** Chunk stages write only their own chunk. */
    private static final int CHUNK_STAGE_REACH = 0;
    private static final int CHUNK_WIDTH = 16;

    private final Engine engine;
    private final int originX;
    private final int originZ;
    private final BlockStateHunk blocks;
    private final IrisTerrainSampler terrain;
    private final ChunkModuleSettings settings;
    private final IrisModuleMantle mantle;

    IrisGenerationContext(Engine engine, int originX, int originZ, BlockStateHunk blocks, IrisModuleMantle.Chunks mantleChunks,
                          Collection<MantleRegistration<?>> mantleLayers) {
        PackResourceIds ids = new PackResourceIds(engine.getData().getDataFolder());
        this.engine = engine;
        this.originX = originX;
        this.originZ = originZ;
        this.blocks = blocks;
        this.terrain = new IrisTerrainSampler(engine, ids, originX, originZ, CHUNK_STAGE_REACH);
        this.settings = new ChunkModuleSettings(engine, ids);
        BlockBox chunk = new BlockBox(originX, engine.getMinHeight(), originZ,
                originX + CHUNK_WIDTH, engine.getMinHeight() + engine.getHeight(), originZ + CHUNK_WIDTH);
        this.mantle = new IrisModuleMantle(chunk, engine.getMinHeight(), mantleChunks, mantleLayers);
    }

    @Override
    public int originX() {
        return originX;
    }

    @Override
    public int originY() {
        return engine.getMinHeight();
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

    @Override
    @SuppressWarnings("unchecked") // The only hunk type is checked against the requested class.
    public <T> com.volmit.hunk.Hunk<T> hunk(Class<T> type) {
        if (type != BlockState.class) {
            throw new IllegalArgumentException("Iris provides chunk hunks of " + BlockState.class.getName() + " only, not " + type.getName());
        }
        return (com.volmit.hunk.Hunk<T>) blocks;
    }

    @Override
    public long worldSeed() {
        return engine.getSeedManager().getSeed();
    }

    @Override
    public PackResources resources() {
        return engine.getData().getPackResources();
    }
}
