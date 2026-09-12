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

package art.arcane.iris.pack.value;

import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.volmlib.util.format.Form;
import lombok.Data;

import java.util.concurrent.TimeUnit;

@Snippet("duration")
@Data
@Description("Represents a combined duration. Fill each property to add time into a single duration")
public class IrisDuration {
    @Description("Milliseconds (1000ms = 1 second)")
    private int milliseconds = 0;

    @Description("Minecraft Ticks (20 minecraft ticks = 1 second)")
    private int minecraftTicks = 0;

    @Description("Seconds (60 seconds = 1 minute)")
    private int seconds = 0;

    @Description("Minutes (60 minutes = 1 hour)")
    private int minutes = 0;

    @Description("Minecraft Hours (about 50 real seconds)")
    private int minecraftHours = 0;

    @Description("Hours (24 hours = 1 day)")
    private int hours = 0;

    @Description("Minecraft Days (1 minecraft day = 20 real minutes)")
    private int minecraftDays = 0;

    @Description("Minecraft Weeks (7 minecraft days = 2 real hours and 20 real minutes)")
    private int minecraftWeeks = 0;

    @Description("Minecraft Lunar Cycles (8 minecraft days = 2 real hours and 40 real minutes)")
    private int minecraftLunarCycles = 0;

    @Description("REAL (not minecraft) Days")
    private int days = 0;

    public String toString() {
        return Form.duration((double) toMilliseconds(), 2);
    }

    public long toMilliseconds() {
        return getMilliseconds()
                + TimeUnit.SECONDS.toMillis(getSeconds())
                + TimeUnit.MINUTES.toMillis(getMinutes())
                + TimeUnit.HOURS.toMillis(getHours())
                + TimeUnit.DAYS.toMillis(getDays())
                + (getMinecraftTicks() * 50L)
                + (getMinecraftHours() * 50000L)
                + (getMinecraftDays() * 1_200_000L)
                + (getMinecraftWeeks() * 8_400_000L)
                + (getMinecraftLunarCycles() * 9_600_000L);
    }
}
