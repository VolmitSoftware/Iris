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
import art.arcane.iris.modules.placement.RunningPlacement;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.block.BlockState;
import com.volmit.block.BlockTrait;
import com.volmit.object.BlockEntityData;

import java.util.List;
import java.util.Optional;

/**
 * A placement's block replacements ({@code edit}): each replacement, by its chance, swaps the blocks it
 * finds for its palette's block at the block's position, keeping the old block's properties when the
 * material stays the same.
 */
final class ObjectEdits {
    private ObjectEdits() {
    }

    /** The result's block entity is the last replacement's, if it has one. */
    static Edited apply(List<ObjectReplace> edits, BlockState data, Optional<BlockEntityData> blockEntity, RNG rng,
                        double x, double y, double z, ResolvedReplaces replaces, RunningPlacement placement) {
        BlockState edited = data;
        Optional<BlockEntityData> editedEntity = blockEntity;
        for (ObjectReplace settings : edits) {
            ResolvedReplace replace = replaces.of(settings);
            if (!rng.chance(replace.chance())) {
                continue;
            }
            for (BlockState find : replace.find()) {
                if (!finds(placement, replace, find, edited)) {
                    continue;
                }
                edited = replaced(placement, edited, replace.replacement(rng, x, y, z));
                Optional<BlockEntityData> picked = replace.tile(rng);
                if (picked.isPresent()) {
                    editedEntity = picked;
                }
            }
        }
        return new Edited(edited, editedEntity);
    }

    private static boolean finds(RunningPlacement placement, ResolvedReplace replace, BlockState find, BlockState data) {
        return replace.exact()
                ? placement.matches(find, data)
                : find.material().equals(data.material());
    }

    private static BlockState replaced(RunningPlacement placement, BlockState data, BlockState replacement) {
        boolean sameMaterial = replacement.material().equals(data.material());
        if (sameMaterial && !placement.hasTrait(replacement, BlockTrait.CUSTOM) && !placement.hasTrait(data, BlockTrait.CUSTOM)) {
            return placement.merge(data, replacement);
        }
        return replacement;
    }

    record Edited(BlockState data, Optional<BlockEntityData> blockEntity) {
    }
}
