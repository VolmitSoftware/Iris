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
package art.arcane.iris.engine.decorator;

import art.arcane.iris.builtin.IrisSeed;
import art.arcane.iris.builtin.IrisSeeds;
import com.volmit.ModuleLog;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockState;
import com.volmit.block.BlockTrait;
import com.volmit.hunk.Hunk;
import com.volmit.mantle.Mantle;
import com.volmit.mantle.MantleLayer;
import com.volmit.stage.ChunkStageContext;
import com.volmit.world.TerrainSampler;

import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Everything the decorators of one chunk look at: its blocks, the blocks the mantle planned around it, the terrain, the pack's
 * block rules and the biomes' decorators. A decorator places into {@link #blocks()} only. What it asks of a block is asked
 * once for each distinct block instance, since a chunk holds few.
 */
public final class DecorationScene {
    private final Hunk<BlockState> blocks;
    private final BlockRegistry registry;
    private final TerrainSampler terrain;
    private final MantleLayer<BlockState> planned;
    private final BiomeDecorators decorators;
    private final long seed;
    private final ModuleLog log;
    private final Map<BlockTrait, Map<BlockState, Boolean>> traits = new EnumMap<>(BlockTrait.class);
    private final Map<BlockState, Map<BlockState, Boolean>> supports = new IdentityHashMap<>();

    /** The chunk of {@code context}, decorated with the world's decorators. */
    public static DecorationScene of(ChunkStageContext context) {
        Mantle mantle = context.mantle();
        return new DecorationScene(context.hunk(BlockState.class), context.world().resources().blocks(), context.terrain(),
                mantle == null ? null : mantle.blocks(), BiomeDecorators.of(context.world()), context.world().extension(IrisSeeds.class).orElseThrow().seed(IrisSeed.COMPONENT),
                context.log());
    }

    public DecorationScene(Hunk<BlockState> blocks, BlockRegistry registry, TerrainSampler terrain, MantleLayer<BlockState> planned,
                           BiomeDecorators decorators, long seed, ModuleLog log) {
        this.blocks = blocks;
        this.registry = registry;
        this.terrain = terrain;
        this.planned = planned;
        this.decorators = decorators;
        this.seed = seed;
        this.log = log;
    }

    public Hunk<BlockState> blocks() {
        return blocks;
    }

    public BlockRegistry registry() {
        return registry;
    }

    public TerrainSampler terrain() {
        return terrain;
    }

    public BiomeDecorators decorators() {
        return decorators;
    }

    /** The seed the decorators of every place draw from, each shifted by its own part. */
    long seed() {
        return seed;
    }

    ModuleLog log() {
        return log;
    }

    /** The height of the chunk's volume. */
    public int worldHeight() {
        return blocks.height();
    }

    public int fluidHeight() {
        return terrain.fluidHeight();
    }

    /** The slope of the terrain at the column. */
    double slopeAt(int worldX, int worldZ) {
        return terrain.column(worldX, worldZ).slope();
    }

    /** The height of the terrain at the column, rounded. */
    long roundedHeightAt(int worldX, int worldZ) {
        return terrain.surfaceHeight(worldX, worldZ);
    }

    /** The block a placement planned at the position, or air; positions the mantle does not cover read as air. */
    BlockState plannedAt(int worldX, int y, int worldZ) {
        if (planned == null || y < 0 || y >= worldHeight()) {
            return null;
        }
        return planned.get(worldX, y, worldZ);
    }

    boolean has(BlockTrait trait, BlockState block) {
        return traits.computeIfAbsent(trait, ignored -> new IdentityHashMap<>())
                .computeIfAbsent(block, known -> registry.hasTrait(known, trait));
    }

    public boolean isAir(BlockState block) {
        return has(BlockTrait.AIR, block);
    }

    public boolean isFluid(BlockState block) {
        return has(BlockTrait.FLUID, block);
    }

    public boolean isSolid(BlockState block) {
        return has(BlockTrait.SOLID, block);
    }

    public boolean isWater(BlockState block) {
        return has(BlockTrait.WATER, block);
    }

    /** Whether {@code decoration} may stand on {@code surface}: its kind of block on that kind of block, and the surface carries a block on its top face. */
    boolean canGoOn(BlockState decoration, BlockState surface) {
        return supports.computeIfAbsent(decoration, ignored -> new IdentityHashMap<>())
                .computeIfAbsent(surface, known -> registry.canPlaceOn(decoration, known)
                        && registry.canAttach(decoration, known, com.volmit.block.BlockFace.UP));
    }
}
