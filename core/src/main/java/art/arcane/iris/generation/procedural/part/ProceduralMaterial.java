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
package art.arcane.iris.generation.procedural.part;

import art.arcane.iris.generation.terrain.IrisMaterialPalette;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.RegistryListBlockType;
import art.arcane.iris.pack.schema.annotation.Required;

import java.util.Collections;
import java.util.List;

@Description("The blocks of one role. Logs are turned along the part they belong to, and leaves get decay distances.")
public final class ProceduralMaterial {
    @Required
    @Description("The role the parts refer to, such as wood, leaves, rock or cap.")
    private String role = "main";

    @RegistryListBlockType
    @Description("The block. Ignored when palette is set.")
    private String block = "minecraft:stone";

    @Description("A noise palette mixing several blocks.")
    private IrisMaterialPalette palette;

    @MinNumber(0)
    @Description("If above 0, the palette changes every this many blocks of height, for strata.")
    private int bands = 0;

    @Description("Swaps for blocks that meet conditions; the first rule that applies wins.")
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
