/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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
package art.arcane.iris.engine.procedural.part;

import com.volmit.block.PaletteSpec;
import com.volmit.config.Desc;
import com.volmit.config.MinNumber;
import com.volmit.config.Ref;
import com.volmit.config.Reference;

@Desc("Swaps a material's block where its conditions hold, such as moss on top faces or a cap on the highest blocks. Use minecraft:air to erode blocks away.")
public final class MaterialRule {
    @Ref(Reference.BLOCK)
    @Desc("The block to swap in. Ignored when palette is set.")
    private String block;

    @Desc("A noise palette to swap in.")
    private PaletteSpec palette;

    @MinNumber(0)
    @Desc("If above 0, the palette changes every this many blocks of height, for strata.")
    private int bands = 0;

    @Desc("The chance a block that meets the conditions is swapped. A curve runs from the bottom (0) to the top (1) of the object.")
    private ProceduralValue chance = ProceduralValue.constant(1);

    @Desc("How the chance is spread over the blocks.")
    private PartPattern pattern = PartPattern.RANDOM;

    @MinNumber(1)
    @Desc("The size of the patches in blocks for the PATCHES pattern.")
    private double scale = 3;

    @Desc("Only swaps blocks within this stretch of the object's height, from the bottom (0) to the top (1).")
    private ProceduralSpan height;

    @Desc("Only swaps blocks within this stretch of their own part, from its base (0) to its tip (1).")
    private ProceduralSpan along;

    @Desc("Only swaps blocks open to the air on this side.")
    private RuleFace face;

    public BlockChoice choice() {
        return new BlockChoice(block, palette, bands);
    }

    public ProceduralValue chance() {
        return chance == null ? ProceduralValue.constant(1) : chance;
    }

    public PartPattern pattern() {
        return pattern == null ? PartPattern.RANDOM : pattern;
    }

    public double scale() {
        return scale;
    }

    public ProceduralSpan height() {
        return height;
    }

    public ProceduralSpan along() {
        return along;
    }

    public RuleFace face() {
        return face;
    }
}
