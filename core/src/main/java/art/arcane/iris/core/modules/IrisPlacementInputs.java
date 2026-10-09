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
import com.volmit.config.FeatureSettings;
import com.volmit.mantle.BlockBox;
import com.volmit.object.ObjectTransformer;
import com.volmit.placement.PlacementContext;
import com.volmit.resource.PackResources;
import com.volmit.world.BiomeSample;
import com.volmit.world.BlockView;
import com.volmit.world.TerrainSample;
import com.volmit.world.PackWorld;
import com.volmit.world.TerrainSampler;

/** The read-only IrisApi inputs of one Iris object placement. */
final class IrisPlacementInputs implements PlacementContext {
    private final ObjectTransformer objects = new IrisObjectTransformer();
    private final long worldSeed;
    private final TerrainSampler terrain;
    private final FeatureSettings settings;
    private final BlockView blocks;
    private final PackResources resources;
    private final Engine engine;
    private PackWorld world;

    /** @param engine the engine whose world the placement generates in, or null for a placement without one */
    IrisPlacementInputs(long worldSeed, TerrainSampler terrain, FeatureSettings settings, BlockView blocks, PackResources resources,
                        Engine engine) {
        this.resources = resources;
        this.engine = engine;
        this.worldSeed = worldSeed;
        this.terrain = terrain;
        this.settings = settings;
        this.blocks = blocks;
    }

    /** For placements without an engine, such as pasting an object into a world Iris does not generate. */
    static TerrainSampler noTerrain(BlockBox bounds) {
        return new TerrainSampler() {
            @Override
            public BlockBox bounds() {
                return bounds;
            }

            @Override
            public TerrainSample column(int worldX, int worldZ) {
                throw withoutEngine();
            }

            @Override
            public BiomeSample biome(int worldX, int y, int worldZ) {
                throw withoutEngine();
            }
        };
    }

    private static UnsupportedOperationException withoutEngine() {
        return new UnsupportedOperationException("This placement runs without an Iris engine, so it has no terrain to sample");
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
    public ObjectTransformer objects() {
        return objects;
    }

    @Override
    public BlockView blocks() {
        return blocks;
    }

    @Override
    public long worldSeed() {
        return worldSeed;
    }

    @Override
    public PackResources resources() {
        return resources;
    }

    /** The world this placement first asked for; it keeps it if the world restarts meanwhile. */
    @Override
    public PackWorld world() {
        if (engine == null) {
            throw new IllegalStateException("This placement runs without an Iris engine, so it is not part of a world");
        }
        if (world == null) {
            world = engine.getGeneration().world();
        }
        return world;
    }
}
