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

package art.arcane.iris.builtin.objects.tree;

import com.volmit.block.PaletteSpec;
import com.volmit.config.Desc;
import com.volmit.config.Snippet;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;
import com.volmit.config.Ref;
import com.volmit.config.Reference;
import com.volmit.config.Required;

import java.util.Optional;

@Snippet("tree-decorator")
@Desc("An accent block applied to a generated tree such as fruit on branch tips, vines on the trunk, snow on the crown, or a glowing underside.")
public final class TreeDecorator {
    @Desc("Where this decorator is placed on the tree.")
    private TreeDecoratorTarget target = TreeDecoratorTarget.BRANCH_TIP;

    @Required
    @Desc("The block id to place, e.g. minecraft:magma_block. Ignored when palette is set.")
    @Ref(Reference.BLOCK)
    private String block = "";

    @Desc("A noise-driven palette for this decorator. When set this overrides the single block.")
    private PaletteSpec palette;

    @MinNumber(0)
    @MaxNumber(1)
    @Desc("The chance per candidate position for this decorator to place.")
    private double chance = 0.5;

    @MinNumber(1)
    @Desc("Maximum strand length downward for the CANOPY_HANG target (random 1..length per column).")
    private int length = 1;

    @Desc("If true the block's facing is oriented away from the trunk (for trunk-mounted fences/gates/banners).")
    private boolean axisAware = false;

    public TreeDecorator() {
    }

    public TreeDecoratorTarget target() {
        return target == null ? TreeDecoratorTarget.BRANCH_TIP : target;
    }

    public String block() {
        return block;
    }

    /** Empty where the pack sets none. */
    public Optional<PaletteSpec> palette() {
        return Optional.ofNullable(palette);
    }

    public double chance() {
        return chance;
    }

    public int length() {
        return length;
    }

    public boolean axisAware() {
        return axisAware;
    }
}
