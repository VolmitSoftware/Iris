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
import com.volmit.config.Snippet;

import java.util.concurrent.TimeUnit;

@Snippet("iris/spawn/duration")
@Desc("Represents a combined duration. Fill each property to add time into a single duration")
public final class SpawnDuration {
    private static final long MILLISECONDS_PER_MINECRAFT_TICK = 50L;
    private static final long MILLISECONDS_PER_MINECRAFT_HOUR = 50_000L;
    private static final long MILLISECONDS_PER_MINECRAFT_DAY = 1_200_000L;
    private static final long MILLISECONDS_PER_MINECRAFT_WEEK = 8_400_000L;
    private static final long MILLISECONDS_PER_MINECRAFT_LUNAR_CYCLE = 9_600_000L;

    @Desc("Milliseconds (1000ms = 1 second)")
    private int milliseconds = 0;

    @Desc("Minecraft Ticks (20 minecraft ticks = 1 second)")
    private int minecraftTicks = 0;

    @Desc("Seconds (60 seconds = 1 minute)")
    private int seconds = 0;

    @Desc("Minutes (60 minutes = 1 hour)")
    private int minutes = 0;

    @Desc("Minecraft Hours (about 50 real seconds)")
    private int minecraftHours = 0;

    @Desc("Hours (24 hours = 1 day)")
    private int hours = 0;

    @Desc("Minecraft Days (1 minecraft day = 20 real minutes)")
    private int minecraftDays = 0;

    @Desc("Minecraft Weeks (7 minecraft days = 2 real hours and 20 real minutes)")
    private int minecraftWeeks = 0;

    @Desc("Minecraft Lunar Cycles (8 minecraft days = 2 real hours and 40 real minutes)")
    private int minecraftLunarCycles = 0;

    @Desc("REAL (not minecraft) Days")
    private int days = 0;

    public SpawnDuration() {
    }

    public long toMilliseconds() {
        return milliseconds
                + TimeUnit.SECONDS.toMillis(seconds)
                + TimeUnit.MINUTES.toMillis(minutes)
                + TimeUnit.HOURS.toMillis(hours)
                + TimeUnit.DAYS.toMillis(days)
                + (minecraftTicks * MILLISECONDS_PER_MINECRAFT_TICK)
                + (minecraftHours * MILLISECONDS_PER_MINECRAFT_HOUR)
                + (minecraftDays * MILLISECONDS_PER_MINECRAFT_DAY)
                + (minecraftWeeks * MILLISECONDS_PER_MINECRAFT_WEEK)
                + (minecraftLunarCycles * MILLISECONDS_PER_MINECRAFT_LUNAR_CYCLE);
    }
}
