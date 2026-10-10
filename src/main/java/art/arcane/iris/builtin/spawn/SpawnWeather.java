/*
 * Iris is a World Generator for Minecraft Servers
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

package art.arcane.iris.builtin.spawn;

import com.volmit.config.Desc;

@Desc("Represents a weather type")
public enum SpawnWeather {
    @Desc("Represents when weather is not causing downfall")
    NONE,

    @Desc("Represents rain or snow")
    DOWNFALL,

    @Desc("Represents rain or snow with thunder")
    DOWNFALL_WITH_THUNDER,

    @Desc("Any weather")
    ANY;

    /** Whether the weather of a world, which is {@code storming} and {@code thundering} or neither, is this one. */
    public boolean matches(boolean storming, boolean thundering) {
        return switch (this) {
            case NONE -> !storming && !thundering;
            case DOWNFALL -> storming;
            case DOWNFALL_WITH_THUNDER -> storming && thundering;
            case ANY -> true;
        };
    }
}
