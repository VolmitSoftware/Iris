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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Formatter for a duration String.
 * Can represent a duration in milliseconds as a String.
 * Taken from Traincarts (permission granted by same author)<br>
 * <br>
 * https://github.com/bergerhealer/TrainCarts/blob/master/src/main/java/com/bergerkiller/bukkit/tc/utils/TimeDurationFormat.java
 */
public class TimeDurationFormat {
    private final TimeZone timeZone;
    private final SimpleDateFormat sdf;

    /**
     * Creates a new time duration format. The format accepts the same formatting
     * tokens as the Date formatter does.
     *
     * @throws IllegalArgumentException if the input format is invalid
     */
    public TimeDurationFormat(String format) {
        if (format == null) {
            throw new IllegalArgumentException("Input format should not be null");
        }
        this.timeZone = TimeZone.getTimeZone("GMT+0");
        this.sdf = new SimpleDateFormat(format, Locale.getDefault());
        this.sdf.setTimeZone(this.timeZone);
    }

    /**
     * Formats the duration
     *
     * @return formatted string
     */
    public String format(long durationMillis) {
        return this.sdf.format(new Date(durationMillis - this.timeZone.getRawOffset()));
    }
}
