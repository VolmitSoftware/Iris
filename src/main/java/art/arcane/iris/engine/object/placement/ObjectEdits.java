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

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.object.material.BlockDataMergeSupport;
import art.arcane.iris.engine.object.prefab.IrisObjectShaping;
import art.arcane.iris.engine.object.prefab.TileData;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.math.RNG;

/**
 * A placement's block replacements ({@code edit}): each replacement, by its chance, swaps the blocks it
 * finds for its palette's block at the block's position, keeping the old block's properties when the
 * material stays the same.
 */
final class ObjectEdits {
    private ObjectEdits() {
    }

    /** {@code tile} may be null; the result's tile is the last replacement's tile, if it has one. */
    static Edited apply(IrisObjectPlacement config, PlatformBlockState data, TileData tile, RNG rng,
                        double x, double y, double z, IrisData rdata) {
        PlatformBlockState edited = data;
        TileData editedTile = tile;
        for (IrisObjectReplace replace : config.getEdit()) {
            if (!rng.chance(replace.getChance())) {
                continue;
            }
            for (PlatformBlockState find : replace.getFind(rdata)) {
                if (!finds(replace, find, edited)) {
                    continue;
                }
                edited = replaced(edited, replace.getReplace(rng, x, y, z, rdata));
                editedTile = replace.getReplace().getTile(rng, x, y, z, rdata).orElse(editedTile);
            }
        }
        return new Edited(edited, editedTile);
    }

    private static boolean finds(IrisObjectReplace replace, PlatformBlockState find, PlatformBlockState data) {
        return replace.isExact()
                ? find.matches(data)
                : IrisObjectShaping.materialKey(find).equals(IrisObjectShaping.materialKey(data));
    }

    private static PlatformBlockState replaced(PlatformBlockState data, PlatformBlockState replacement) {
        boolean sameMaterial = IrisObjectShaping.materialKey(replacement).equals(IrisObjectShaping.materialKey(data));
        if (sameMaterial && !replacement.isCustom() && !data.isCustom()) {
            return BlockDataMergeSupport.merge(data, replacement);
        }
        return replacement;
    }

    record Edited(PlatformBlockState data, TileData tile) {
    }
}
