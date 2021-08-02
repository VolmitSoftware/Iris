/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

import com.volmit.iris.core.project.loader.IrisData;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.Required;
import com.volmit.iris.util.math.RNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@SuppressWarnings("DefaultAnnotationParam")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents Block Data")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisExpressionLoad {
    @Required
    @Desc("The variable to assign this value to")
    private String name = "";

    @Desc("If the style value is not defined, this value will be used")
    private double staticValue = -1;

    @Desc("If defined, this variable will use a generator style as it's result")
    private IrisGeneratorStyle styleValue = null;

    public double getValue(RNG rng, IrisData data, double x, double z) {
        if (styleValue != null) {
            return styleValue.create(rng, data).noise(x, z);
        }

        return staticValue;
    }

    public double getValue(RNG rng, IrisData data, double x, double y, double z) {
        if (styleValue != null) {
            return styleValue.create(rng, data).noise(x, y, z);
        }

        return staticValue;
    }
}
