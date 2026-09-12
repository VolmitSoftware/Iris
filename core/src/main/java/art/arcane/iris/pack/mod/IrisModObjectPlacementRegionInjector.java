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

import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.structure.object.IrisObjectPlacement;

import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.RegistryListResource;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.volmlib.util.collection.KList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("object-placement-region-injector")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("An object placement injector")
@Data
public class IrisModObjectPlacementRegionInjector {
    @Required
    @Description("The region to find")
    @RegistryListResource(IrisRegion.class)
    private String biome = "";

    @Required
    @Description("Object placements to inject into the region")
    @ArrayType(type = IrisObjectPlacement.class, min = 1)
    private KList<IrisObjectPlacement> place = new KList<>();
}
