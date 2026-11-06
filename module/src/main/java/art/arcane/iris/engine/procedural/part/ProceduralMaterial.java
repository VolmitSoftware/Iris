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
import com.volmit.config.Required;

import java.util.Collections;
import java.util.List;

@Desc("The blocks of one role. Logs are turned along the part they belong to, and leaves get decay distances.")
public final class ProceduralMaterial {
    @Required
    @Desc("The role the parts refer to, such as wood, leaves, rock or cap.")
    private String role = "main";

    @Ref(Reference.BLOCK)
    @Desc("The block. Ignored when palette is set.")
    private String block = "minecraft:stone";

    @Desc("A noise palette mixing several blocks.")
    private PaletteSpec palette;

    @MinNumber(0)
    @Desc("If above 0, the palette changes every this many blocks of height, for strata.")
    private int bands = 0;

    @Desc("Swaps for blocks that meet conditions; the first rule that applies wins.")
    private List<MaterialRule> rules = List.of();

    public String role() {
        return role == null ? "main" : role;
    }

    public BlockChoice choice() {
        return new BlockChoice(block, palette, bands);
    }

    public List<MaterialRule> rules() {
        return rules == null ? List.of() : Collections.unmodifiableList(rules);
    }
}
