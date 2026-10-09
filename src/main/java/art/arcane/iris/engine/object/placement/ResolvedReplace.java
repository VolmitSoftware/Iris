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

package art.arcane.iris.engine.object.placement;

import art.arcane.iris.builtin.blocks.ObjectReplace;
import art.arcane.iris.core.modules.BlockEntities;
import art.arcane.iris.engine.object.prefab.TileData;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockSpec;
import com.volmit.block.PaletteSpec;
import com.volmit.object.BlockEntityData;

import java.util.List;
import java.util.Optional;

/**
 * One {@link ObjectReplace} made ready for a pack: the blocks it finds resolved to the platform's, its palette
 * picked through the pack's block registry with the seed of the {@link RNG} the placement hands in, and the tile
 * data of the palette's entries resolved once. The old settings class cached the same things on itself.
 */
final class ResolvedReplace {
    private final List<PlatformBlockState> find;
    private final boolean exact;
    private final float chance;
    private final PaletteSpec replace;
    private final BlockRegistry blocks;
    private final List<Optional<BlockEntityData>> entryTiles;
    private final int weightedSize;

    ResolvedReplace(List<PlatformBlockState> find, boolean exact, float chance, PaletteSpec replace, BlockRegistry blocks) {
        this.find = List.copyOf(find);
        this.exact = exact;
        this.chance = chance;
        this.replace = replace;
        this.blocks = blocks;
        this.entryTiles = replace.palette().stream().map(blocks::blockEntity).toList();
        this.weightedSize = replace.palette().stream().mapToInt(spec -> Math.max(0, spec.weight())).sum();
    }

    static ResolvedReplace of(ObjectReplace settings, BlockRegistry blocks) {
        List<PlatformBlockState> find = settings.find().stream()
                .map(spec -> B.getState(blocks.resolve(spec).key()))
                .toList();
        return new ResolvedReplace(find, settings.exact(), settings.chance(), settings.replace(), blocks);
    }

    float chance() {
        return chance;
    }

    boolean exact() {
        return exact;
    }

    List<PlatformBlockState> find() {
        return find;
    }

    PlatformBlockState replacement(RNG rng, double x, double y, double z) {
        return B.getState(blocks.palette(replace, rng.getSeed()).at(x, y, z).key());
    }

    /** The tile data of the entry picked for {@code rng}: the first entry when the palette's weights add up to one, else a uniform pick. */
    Optional<TileData> tile(RNG rng) {
        if (weightedSize == 0) {
            return Optional.empty();
        }
        Optional<BlockEntityData> entity = entryTiles.get(weightedSize == 1 ? 0 : pickedEntry(rng));
        return entity.map(BlockEntities::tileOf);
    }

    private int pickedEntry(RNG rng) {
        return entryTiles.size() == 1 ? 0 : rng.nextInt(entryTiles.size());
    }
}
