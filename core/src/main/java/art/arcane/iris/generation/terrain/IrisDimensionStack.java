/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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

package art.arcane.iris.generation.terrain;

import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MaxNumber;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.RegistryListResource;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.volmlib.util.collection.KList;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Stacks dimension terrain upright in declared top-to-bottom order")
@Data
public class IrisDimensionStack {
    @Required
    @ArrayType(min = 2, type = String.class)
    @RegistryListResource(IrisDimension.class)
    @Description("Dimension load keys ordered from the highest layer to the lowest layer. The final key must be this dimension.")
    private KList<String> dimensions = new KList<>();

    @MinNumber(0)
    @MaxNumber(256)
    @Description("Nominal air gap in blocks between adjacent dimension layers")
    private int spacer = 32;

    @Description("Optional noise that blends adjacent layer boundaries. Omit this object for a constant configured gap.")
    private IrisDimensionStackBlend blend = null;
}
