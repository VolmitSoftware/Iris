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

import com.volmit.iris.util.data.DoubleArrayUtils;

/**
 * Provides an incredibly fast averaging object. It swaps values from a sum
 * using an array. Averages do not use any form of looping. An average of 10,000
 * entries is the same speed as an average with 5 entries.
 *
 * @author cyberpwn
 */
public class Average {
    protected final double[] values;
    protected int cursor;
    private double average;
    private double lastSum;
    private boolean dirty;
    private boolean brandNew;

    /**
     * Create an average holder
     *
     * @param size the size of entries to keep
     */
    public Average(int size) {
        values = new double[size];
        DoubleArrayUtils.fill(values, 0);
        brandNew = true;
        average = 0;
        cursor = 0;
        lastSum = 0;
        dirty = false;
    }

    /**
     * Put a value into the average (rolls over if full)
     *
     * @param i the value
     */
    public void put(double i) {

        dirty = true;

        if (brandNew) {
            DoubleArrayUtils.fill(values, i);
            lastSum = size() * i;
            brandNew = false;
            return;
        }

        double current = values[cursor];
        lastSum = (lastSum - current) + i;
        values[cursor] = i;
        cursor = cursor + 1 < size() ? cursor + 1 : 0;
    }

    /**
     * Get the current average
     *
     * @return the average
     */
    public double getAverage() {
        if (dirty) {
            calculateAverage();
            return getAverage();
        }

        return average;
    }

    private void calculateAverage() {
        average = lastSum / (double) size();
        dirty = false;
    }

    public int size() {
        return values.length;
    }

    public boolean isDirty() {
        return dirty;
    }
}
