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

package art.arcane.iris.engine.spawn;

import art.arcane.iris.builtin.spawn.SpawnTimeBlock;
import art.arcane.iris.builtin.spawn.SpawnWeather;
import com.volmit.world.RunningWorld.Sky;

/** The time of day and the weather a command or spawner of the pack asks for, checked against a world's sky. */
final class SpawnConditions {
    private static final double TICKS_PER_HOUR = 1000D;
    private static final double HOURS_AT_TIME_ZERO = 6;
    private static final double HOURS_PER_DAY = 24;

    private SpawnConditions() {
    }

    static boolean allow(SpawnTimeBlock time, SpawnWeather weather, Sky sky) {
        return time.contains(hourOf(sky)) && weather.matches(sky.storming(), sky.thundering());
    }

    /** The world's time of day as a clock hour: tick 0 is 6 in the morning. */
    static double hourOf(Sky sky) {
        return ((sky.time() / TICKS_PER_HOUR) + HOURS_AT_TIME_ZERO) % HOURS_PER_DAY;
    }
}
