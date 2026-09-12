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

package art.arcane.iris.pack.mod;

import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.generation.noise.NoiseStyle;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.iris.pack.schema.annotation.Snippet;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("noise-style-replacer")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("A noise style replacer")
@Data
public class IrisModNoiseStyleReplacer {
    @Required
    @Description("A noise style to find")
    private NoiseStyle find = NoiseStyle.IRIS;

    @Required
    @Description("If replaceTypeOnly is set to true, Iris will keep the existing generator style and only replace the type itself. Otherwise it will use the replace tag for every style using the find type.")
    private boolean replaceTypeOnly = false;

    @Required
    @Description("A noise style to replace it with")
    private IrisGeneratorStyle replace = new IrisGeneratorStyle();
}
