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

package com.volmit.iris.util.data;


import com.google.common.util.concurrent.AtomicDoubleArray;

import java.util.Arrays;

public class DoubleArrayUtils {
    public static void shiftRight(double[] values, double push) {
        if (values.length - 2 + 1 >= 0) System.arraycopy(values, 0, values, 1, values.length - 2 + 1);

        values[0] = push;
    }

    public static void wrapRight(double[] values) {
        double last = values[values.length - 1];
        shiftRight(values, last);
    }

    public static void fill(double[] values, double value) {
        Arrays.fill(values, value);
    }

    public static void fill(AtomicDoubleArray values, double value) {
        for (int i = 0; i < values.length(); i++) {
            values.set(i, value);
        }
    }

}
