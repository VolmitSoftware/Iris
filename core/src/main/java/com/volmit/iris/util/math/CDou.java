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

package com.volmit.iris.util.math;

@SuppressWarnings("ALL")
public class CDou {
    private final double max;
    private double number;

    public CDou(double max) {
        number = 0;
        this.max = max;
    }

    public CDou set(double n) {
        number = n;
        circ();
        return this;
    }

    public CDou add(double a) {
        number += a;
        circ();
        return this;
    }

    public CDou sub(double a) {
        number -= a;
        circ();
        return this;
    }

    public double get() {
        return number;
    }

    public void circ() {
        if (number < 0) {
            number = max - (Math.abs(number) > max ? max : Math.abs(number));
        }

        number = number % (max);
    }
}
