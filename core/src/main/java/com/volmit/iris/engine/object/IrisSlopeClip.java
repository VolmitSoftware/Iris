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

import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.MaxNumber;
import com.volmit.iris.engine.object.annotations.MinNumber;
import com.volmit.iris.engine.object.annotations.Snippet;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("slope-clip")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Translate objects")
@Data
public class IrisSlopeClip {
    @MinNumber(0)
    @MaxNumber(1024)
    @Desc("The minimum slope for placement")
    private double minimumSlope = 0;

    @MinNumber(0)
    @MaxNumber(1024)
    @Desc("The maximum slope for placement")
    private double maximumSlope = 10;

    public boolean isDefault() {
        return minimumSlope <= 0 && maximumSlope >= 10;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isValid(double slope) {
        if (isDefault()) {
            return true;
        }

        return !(minimumSlope > slope) && !(maximumSlope < slope);
    }
}
