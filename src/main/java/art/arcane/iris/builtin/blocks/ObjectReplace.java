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

package art.arcane.iris.builtin.blocks;

import com.volmit.block.BlockSpec;
import com.volmit.block.PaletteSpec;
import com.volmit.config.Desc;
import com.volmit.config.MaxNumber;
import com.volmit.config.MinNumber;
import com.volmit.config.Required;
import com.volmit.config.Snippet;

import java.util.Collections;
import java.util.List;

/** One find-and-replace of an object placement's blocks, an entry of the placement's {@code edit}. */
@Snippet("iris/blocks/object-block-replacer")
@Desc("Find and replace object materials")
public final class ObjectReplace {
    @Required
    @Desc("Find this block")
    private List<BlockSpec> find = List.of();
    @Required
    @Desc("Replace it with this block palette")
    private PaletteSpec replace = PaletteSpec.of(List.of(BlockSpec.of("stone")));
    @Desc("Exactly match the block data or not")
    private boolean exact = false;
    @MinNumber(0)
    @MaxNumber(1)
    @Desc("Modifies the chance the block is replaced")
    private float chance = 1;

    public ObjectReplace() {
    }

    public List<BlockSpec> find() {
        return find == null ? List.of() : Collections.unmodifiableList(find);
    }

    public PaletteSpec replace() {
        return replace == null ? PaletteSpec.of(List.of(BlockSpec.of("stone"))) : replace;
    }

    public boolean exact() {
        return exact;
    }

    public float chance() {
        return chance;
    }
}
