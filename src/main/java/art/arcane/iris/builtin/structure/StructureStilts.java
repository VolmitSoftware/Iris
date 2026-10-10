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

package art.arcane.iris.builtin.structure;

import com.volmit.block.BlockSpec;
import com.volmit.block.PaletteSpec;
import com.volmit.config.Desc;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;

import java.util.List;
import java.util.Optional;

/**
 * Foundation columns built beneath a structure's pieces after they are placed, as packs write it:
 * {"maxDepth": 32, "palette": {"palette": [{"block": "stone_bricks"}]}, "spacing": 2}. Held by structure placements and
 * by the {@code importedStructures} adjustments; ownership records keep its JSON for the starts they track.
 */
@Desc("Defines foundation columns placed beneath a structure.")
public final class StructureStilts {
    private static final String DEFAULT_BLOCK = "minecraft:cobblestone";

    @MinNumber(1)
    @MaxNumber(4064)
    @Desc("Maximum number of blocks each foundation column may descend while searching for solid ground.")
    private int maxDepth = 64;

    @Desc("Block palette used for foundation columns.")
    private PaletteSpec palette = PaletteSpec.of(List.of(BlockSpec.of(DEFAULT_BLOCK)));

    @MinNumber(1)
    @MaxNumber(64)
    @Desc("Horizontal spacing between support columns. One fills every eligible foundation column; larger values create sparse stilts.")
    private int spacing = 1;

    @Desc("For Iris-authored structures, whether solid partial blocks such as slabs, stairs, and walls may seed foundation columns instead of requiring a fully occluding base block.")
    private boolean supportNonOccluding = false;

    public StructureStilts() {
    }

    public int maxDepth() {
        return maxDepth;
    }

    /** The blocks of the columns; empty when the pack wrote {@code "palette": null}. */
    public Optional<PaletteSpec> palette() {
        return Optional.ofNullable(palette);
    }

    public int spacing() {
        return spacing;
    }

    public boolean supportNonOccluding() {
        return supportNonOccluding;
    }
}
