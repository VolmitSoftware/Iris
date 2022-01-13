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

import com.volmit.iris.engine.object.IrisExpression;
import com.volmit.iris.util.math.RNG;

public class ExpressionNoise implements NoiseGenerator {
    private final RNG rng;
    private final IrisExpression expression;

    public ExpressionNoise(RNG rng, IrisExpression expression) {
        this.rng = rng;
        this.expression = expression;
    }

    @Override
    public double noise(double x) {
        return expression.evaluate(rng, x, -1);
    }

    @Override
    public double noise(double x, double z) {
        return expression.evaluate(rng, x, z);
    }

    @Override
    public double noise(double x, double y, double z) {
        return expression.evaluate(rng, x, y, z);
    }
}
