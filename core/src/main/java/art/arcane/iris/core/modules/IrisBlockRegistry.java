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

import art.arcane.iris.core.loader.IrisData;
import art.arcane.volmlib.util.cache.LazyBoundedCache;
import art.arcane.iris.engine.object.material.IrisBlockData;
import art.arcane.iris.engine.object.material.IrisMaterialPalette;
import art.arcane.iris.engine.object.prefab.TileData;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformFace;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.block.BlockFace;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockSpec;
import com.volmit.block.BlockState;
import com.volmit.block.BlockTrait;
import com.volmit.block.PaletteSampler;
import com.volmit.block.PaletteSpec;
import com.volmit.object.BlockEntityData;
import com.volmit.resource.ResourceId;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IrisApi's view of the bound platform's blocks. States convert through their keys, as {@link PlatformBlockStates}
 * does; the platform decides which blocks exist, so a key it does not know fails. Traits, tags and attachment to
 * a face are the platform's own answers: its block tags, its falling blocks and its face support.
 * <p>
 * {@link BlockSpec}s resolve as Iris resolves the block data packs write ({@link IrisValues#blockData}): a block of
 * the pack's {@code blocks} folder, the platform's block, the backup, then air. Each spec is converted once per
 * pack snapshot, so its resolution is too. {@link PaletteSpec}s pick as Iris's {@link IrisMaterialPalette} does, with
 * the seed the palette's old {@code RNG} had.
 */
final class IrisBlockRegistry implements BlockRegistry {
    /** Enough for the palettes of a pack with their world seeds; seeds that differ per call (a placement's) wear out the oldest. */
    private static final int PALETTE_CACHE_SIZE = 256;

    private final IrisData data;
    private final Map<BlockSpec, IrisBlockData> specs = new ConcurrentHashMap<>();
    /** Stages ask about every block of a chunk; the platform's state of each block is found once per snapshot. */
    private final Map<BlockState, PlatformBlockState> platformStates = new ConcurrentHashMap<>();
    private final LazyBoundedCache<SeededPalette, PaletteSampler> palettes = new LazyBoundedCache<>(PALETTE_CACHE_SIZE);

    /** @param data the pack whose {@code blocks} folder custom blocks come from */
    IrisBlockRegistry(IrisData data) {
        this.data = data;
    }

    @Override
    public BlockState resolve(String key) {
        return BlockState.parse(PlatformBlockStates.namespaced(platformState(key).key()));
    }

    @Override
    public BlockState resolve(BlockSpec spec) {
        return BlockState.parse(PlatformBlockStates.namespaced(blockData(spec).getBlockData(data).key()));
    }

    @Override
    public Optional<BlockEntityData> blockEntity(BlockSpec spec) {
        TileData tile = blockData(spec).tryGetTile(data);
        return tile == null ? Optional.empty() : Optional.of(BlockEntities.of(tile));
    }

    @Override
    public PaletteSampler palette(PaletteSpec spec, long seed) {
        return palettes.computeIfAbsent(new SeededPalette(spec, seed), this::pickerOf);
    }

    private PaletteSampler pickerOf(SeededPalette seeded) {
        if (seeded.spec().palette().stream().noneMatch(block -> block.weight() > 0)) {
            throw new IllegalArgumentException("Palette " + seeded.spec() + " has no block to pick: each block needs a weight of at least 1");
        }
        IrisMaterialPalette palette = IrisValues.materialPalette(seeded.spec());
        RNG rng = new RNG(seeded.seed());
        return (x, y, z) -> BlockState.parse(PlatformBlockStates.namespaced(palette.get(rng, x, y, z, data).key()));
    }

    private IrisBlockData blockData(BlockSpec spec) {
        return specs.computeIfAbsent(spec, IrisValues::blockData);
    }

    @Override
    public BlockState oreFor(BlockState rock, BlockState ore) {
        PlatformBlockState suited = IrisPlatforms.get().registries().deepSlateOre(platformState(rock), platformState(ore));
        return BlockState.parse(PlatformBlockStates.namespaced(suited.key()));
    }

    @Override
    public boolean hasTrait(BlockState state, BlockTrait trait) {
        return hasTrait(platformState(state), trait);
    }

    /** Whether the platform's {@code state} has {@code trait}. */
    static boolean hasTrait(PlatformBlockState platform, BlockTrait trait) {
        return switch (trait) {
            case AIR -> platform.isAir();
            case SOLID -> platform.isSolid();
            case OCCLUDING -> platform.isOccluding();
            case FLUID -> platform.isFluid();
            case WATER -> platform.isWater();
            case FOLIAGE -> platform.isFoliage();
            case DECORATION -> platform.isDecorant();
            case VINE -> platform.isVineBlock();
            case ORE -> platform.isOre();
            case INVENTORY -> platform.isStorage();
            case BLOCK_ENTITY -> platform.hasTileEntity();
            case CUSTOM -> platform.isCustom();
            case NEEDS_UPDATE -> platform.isUpdatable();
            case GRAVITY -> platform.hasGravity();
            case LOG -> platform.isLog();
            case LEAVES -> platform.isLeaves();
        };
    }

    @Override
    public boolean inTag(BlockState state, ResourceId tag) {
        return IrisPlatforms.get().registries().isInTag(platformState(state), tag.toString());
    }

    @Override
    public boolean canAttach(BlockState state, BlockState support, BlockFace face) {
        return platformState(state).canAttach(platformState(support), PlatformFace.valueOf(face.name()));
    }

    @Override
    public boolean canPlaceOn(BlockState state, BlockState below) {
        return platformState(state).canPlaceOnto(platformState(below));
    }

    private PlatformBlockState platformState(BlockState state) {
        return platformStates.computeIfAbsent(state, known -> platformState(known.key()));
    }

    private static PlatformBlockState platformState(String key) {
        PlatformBlockState state = IrisPlatforms.get().registries().blockOrNull(PlatformBlockStates.namespaced(key), false);
        if (state == null) {
            throw new IllegalArgumentException("Unknown block: " + key);
        }
        return state;
    }

    private record SeededPalette(PaletteSpec spec, long seed) {
    }
}
