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
import com.volmit.placement.PlacementTerrain;
import com.volmit.block.BlockState;
import com.volmit.config.FeatureSettings;
import com.volmit.geometry.BlockBox;
import com.volmit.object.ObjectTransformer;
import com.volmit.placement.PlacementContext;
import com.volmit.world.BiomeKind;
import com.volmit.world.BiomeSample;
import com.volmit.world.BlockView;
import com.volmit.world.TerrainSample;
import com.volmit.world.PackWorld;
import com.volmit.world.TerrainSampler;
import com.volmit.world.UpperDimension;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;

/** The read-only IrisApi inputs of one Iris object placement. */
final class IrisPlacementInputs implements PlacementContext {
    private final ObjectTransformer objects = new IrisObjectTransformer();
    private final TerrainSampler terrain;
    private final FeatureSettings settings;
    private final BlockView blocks;
    private final Supplier<PackWorld> worldSource;
    private final PlacementTerrain placementTerrain;
    private final Object input;
    private PackWorld world;

    /**
     * @param worldSource the world the placement generates in, asked for the first time a stage needs it
     * @param input       what the placing module passed for its stages to read
     */
    IrisPlacementInputs(TerrainSampler terrain, FeatureSettings settings, BlockView blocks, Supplier<PackWorld> worldSource,
                        PlacementTerrain placementTerrain, Object input) {
        this.terrain = terrain;
        this.settings = settings;
        this.blocks = blocks;
        this.worldSource = worldSource;
        this.placementTerrain = placementTerrain;
        this.input = input;
    }

    @Override
    public <T> Optional<T> extension(Class<T> type) {
        return Optional.empty();
    }

    @Override
    public PlacementTerrain placementTerrain() {
        return placementTerrain;
    }

    @Override
    public <T> Optional<T> input(Class<T> type) {
        return type.isInstance(input) ? Optional.of(type.cast(input)) : Optional.empty();
    }

    /** The world of the engine's runtime, as it is when the placement first asks. */
    static Supplier<PackWorld> worldOf(Engine engine) {
        return () -> engine.getGeneration().world();
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

            @Override
            public BiomeKind surfaceKind(int worldX, int worldZ) {
                throw withoutEngine();
            }

            @Override
            public int minWorldY() {
                throw withoutEngine();
            }

            @Override
            public int fluidHeight() {
                throw withoutEngine();
            }

            @Override
            public OptionalInt upperDimensionY(int worldX, int worldZ) {
                throw withoutEngine();
            }

            @Override
            public Optional<BlockState> surfaceBlock(int worldX, int worldZ) {
                throw withoutEngine();
            }

            @Override
            public Optional<UpperDimension> upperDimension() {
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

    /** The world this placement first asked for; it keeps it if the world restarts meanwhile. */
    @Override
    public PackWorld world() {
        if (world == null) {
            world = worldSource.get();
        }
        return world;
    }
}
