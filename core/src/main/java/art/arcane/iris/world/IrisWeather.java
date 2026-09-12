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

package art.arcane.iris.world;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.spi.PlatformWorld;

@Description("Represents a weather type")
public enum IrisWeather {
    @Description("Represents when weather is not causing downfall")
    NONE,

    @Description("Represents rain or snow")
    DOWNFALL,

    @Description("Represents rain or snow with thunder")
    DOWNFALL_WITH_THUNDER,

    @Description("Any weather")
    ANY;

    public boolean is(PlatformWorld world) {
        return switch (this) {
            case NONE -> !world.isStorming() && !world.isThundering();
            case DOWNFALL -> world.isStorming();
            case DOWNFALL_WITH_THUNDER -> world.isStorming() && world.isThundering();
            case ANY -> true;
        };
    }
}
