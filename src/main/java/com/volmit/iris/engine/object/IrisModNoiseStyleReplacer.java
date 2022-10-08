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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("noise-style-replacer")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("A noise style replacer")
@Data
public class IrisModNoiseStyleReplacer {
    @Required
    @Desc("A noise style to find")
    @ArrayType(type = String.class, min = 1)
    private NoiseStyle find = NoiseStyle.IRIS;

    @Required
    @Desc("If replaceTypeOnly is set to true, Iris will keep the existing generator style and only replace the type itself. Otherwise it will use the replace tag for every style using the find type.")
    private boolean replaceTypeOnly = false;

    @Required
    @Desc("A noise style to replace it with")
    @RegistryListResource(IrisBiome.class)
    private IrisGeneratorStyle replace = new IrisGeneratorStyle();
}
