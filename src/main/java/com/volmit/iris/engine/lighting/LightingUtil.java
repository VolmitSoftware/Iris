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

package com.volmit.iris.engine.lighting;

import com.bergerkiller.bukkit.common.utils.MathUtil;

/**
 * Just some utilities used by Light Cleaner
 */
public class LightingUtil {
    private static final TimeDurationFormat timeFormat_hh_mm = new TimeDurationFormat("HH 'hours' mm 'minutes'");
    private static final TimeDurationFormat timeFormat_mm_ss = new TimeDurationFormat("mm 'minutes' ss 'seconds'");

    private static final long SECOND_MILLIS = 1000L;
    private static final long MINUTE_MILLIS = 60L * SECOND_MILLIS;
    private static final long HOUR_MILLIS = 60L * MINUTE_MILLIS;
    private static final long DAY_MILLIS = 24L * HOUR_MILLIS;

    public static String formatDuration(long duration) {
        if (duration < MINUTE_MILLIS) {
            return MathUtil.round((double) duration / (double) SECOND_MILLIS, 2) + " seconds";
        } else if (duration < HOUR_MILLIS) {
            return timeFormat_mm_ss.format(duration);
        } else if (duration < (2 * DAY_MILLIS)) {
            return timeFormat_hh_mm.format(duration);
        } else {
            long num_days = duration / DAY_MILLIS;
            long num_hours = (duration % DAY_MILLIS) / HOUR_MILLIS;
            return num_days + " days " + num_hours + " hours";
        }
    }
}
