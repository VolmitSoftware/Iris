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
import com.volmit.iris.engine.object.annotations.Snippet;
import lombok.Data;
import org.bukkit.World;

@Snippet("time-block")
@Data
@Desc("Represents a time of day (24h time, not 12h am/pm). Set both to the same number for any time. If they are both set to -1, it will always be not allowed.")
public class IrisTimeBlock {
    @Desc("The beginning hour. Set both to the same number for any time. If they are both set to -1, it will always be not allowed.")
    private double startHour = 0;

    @Desc("The ending hour. Set both to the same number for any time. If they are both set to -1, it will always be not allowed.")
    private double endHour = 0;

    public boolean isWithin(World world) {
        return isWithin(((world.getTime() / 1000D) + 6) % 24);
    }

    public boolean isWithin(double hour) {
        if (startHour == endHour) {
            return endHour != -1;
        }

        if (startHour > endHour) {
            return hour >= startHour || hour <= endHour;
        }

        return hour >= startHour && hour <= endHour;
    }
}
