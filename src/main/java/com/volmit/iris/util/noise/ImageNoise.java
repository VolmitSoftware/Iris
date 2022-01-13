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

package com.volmit.iris.util.noise;

import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.object.IrisImageMap;

public class ImageNoise implements NoiseGenerator {
    private final IrisImageMap expression;
    private final IrisData data;

    public ImageNoise(IrisData data, IrisImageMap expression) {
        this.data = data;
        this.expression = expression;
    }

    @Override
    public double noise(double x) {
        return noise(x, 0);
    }

    @Override
    public double noise(double x, double z) {
        return expression.getNoise(data, (int) x, (int) z);
    }

    @Override
    public double noise(double x, double y, double z) {
        return noise(x, z + y);
    }
}
