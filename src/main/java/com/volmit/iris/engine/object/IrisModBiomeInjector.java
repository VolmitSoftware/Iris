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

package com.volmit.iris.engine.object;

import com.volmit.iris.engine.object.annotations.*;
import com.volmit.iris.util.collection.KList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("biome-injector")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("A biome injector")
@Data
public class IrisModBiomeInjector {
    @Required
    @Desc("The region to find")
    @RegistryListResource(IrisRegion.class)
    private String region = "";

    @Required
    @Desc("A biome to inject into the region")
    @RegistryListResource(IrisBiome.class)
    @ArrayType(type = String.class, min = 1)
    private KList<String> inject = new KList<>();
}
