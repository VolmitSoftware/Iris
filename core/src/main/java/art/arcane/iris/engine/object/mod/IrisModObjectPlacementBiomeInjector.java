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

package art.arcane.iris.engine.object.mod;

import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.annotations.ArrayType;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.RegistryListResource;
import art.arcane.iris.engine.object.annotations.Required;
import art.arcane.iris.engine.object.annotations.Snippet;
import art.arcane.volmlib.util.collection.KList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("object-placement-biome-injector")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("An object placement injector")
@Data
public class IrisModObjectPlacementBiomeInjector {
    @Required
    @Desc("The biome to find")
    @RegistryListResource(IrisBiome.class)
    private String biome = "";

    @Required
    @Desc("Object placements to inject into the biome")
    @ArrayType(type = ObjectPlacement.class, min = 1)
    private KList<ObjectPlacement> place = new KList<>();
}
