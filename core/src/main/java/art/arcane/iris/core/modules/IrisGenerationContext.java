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
import com.volmit.mantle.Mantle;
import com.volmit.resource.PackResources;
import com.volmit.world.TerrainSampler;

/**
 * One chunk of an Iris engine as an IrisApi chunk invocation: the chunk's block hunk, Iris's terrain
 * streams and the pack's module settings. Mantle access and pack resources are not bridged yet; a stage
 * that asks for them fails the chunk with an error saying so, rather than seeing an empty mantle or
 * pack.
 */
final class IrisGenerationContext implements GenerationContext {
    private static final String NOT_BRIDGED = " is not bridged to Iris yet; modules cannot use it until a built-in port needs it";

    private final Engine engine;
    private final int originX;
    private final int originZ;
    private final BlockStateHunk blocks;
    private final IrisTerrainSampler terrain;
    private final ChunkModuleSettings settings;

    IrisGenerationContext(Engine engine, int originX, int originZ, BlockStateHunk blocks, int reachBlocks) {
        PackResourceIds ids = new PackResourceIds(engine.getData().getDataFolder());
        this.engine = engine;
        this.originX = originX;
        this.originZ = originZ;
        this.blocks = blocks;
        this.terrain = new IrisTerrainSampler(engine, ids, originX, originZ, reachBlocks);
        this.settings = new ChunkModuleSettings(engine, ids);
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
        throw new UnsupportedOperationException("Mantle access from module stages" + NOT_BRIDGED);
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
        throw new UnsupportedOperationException("Pack resources for module stages" + NOT_BRIDGED);
    }
}
