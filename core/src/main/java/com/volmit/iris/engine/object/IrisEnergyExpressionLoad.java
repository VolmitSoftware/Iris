/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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

import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.Required;
import com.volmit.iris.engine.object.annotations.Snippet;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.stream.ProceduralStream;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("expression-load")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents a variable to use in your expression. Do not set the name to x, y, or z, also don't duplicate names.")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisEnergyExpressionLoad {
    @Required
    @Desc("The variable to assign this value to. Do not set the name to x, y, or z")
    private String name = "";

    @Desc("If the style value is not defined, this value will be used")
    private double staticValue = -1;

    @Desc("If defined, bukkit will stream some selected values.")
    private IrisBukkitStreamType bukkitStreamValue = null;


    // todo: Unknown: This may fuck up and provide fake values, unsure.
    private transient AtomicCache<Integer> streamCache = new AtomicCache<>();
    private transient AtomicCache<Double> valueCache = new AtomicCache<>();


    public double getValue(RNG rng, IrisData data, Double ce) {

        if (bukkitStreamValue != null) {
            return streamCache.aquire(() -> bukkitStreamValue.get(data.getEngine()));
        }

        return staticValue;
    }

}
