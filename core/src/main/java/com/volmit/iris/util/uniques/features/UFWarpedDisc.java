/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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

package com.volmit.iris.util.uniques.features;

import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.noise.CNG;
import com.volmit.iris.util.uniques.UFeature;
import com.volmit.iris.util.uniques.UFeatureMeta;
import com.volmit.iris.util.uniques.UImage;

import java.util.function.Consumer;

public class UFWarpedDisc implements UFeature {
    @Override
    public void render(UImage image, RNG rng, double t, Consumer<Double> progressor, UFeatureMeta meta) {
        double r = Math.min(image.getWidth(), image.getHeight()) / 3D;
        CNG xShift = generator("x_warp", rng, 0.6, 1001, meta);
        CNG yShift = generator("y_warp", rng, 0.6, 1002, meta);
        CNG hue = generator("color_hue", rng, rng.d(0.25, 2.5), 1003, meta);
        CNG sat = generator("color_sat", rng, rng.d(0.25, 2.5), 1004, meta);
        CNG bri = generator("color_bri", rng, rng.d(0.25, 2.5), 1005, meta);
        double tcf = rng.d(0.75, 11.25);
        int x = image.getWidth() / 2;
        int y = image.getHeight() / 2;

        for (int i = (int) (x - r); i < x + r; i++) {
            for (int j = (int) (y - r); j < y + r; j++) {
                if (image.isInBounds(i, j) && Math.pow(x - i, 2) + Math.pow(y - j, 2) <= r * r) {
                    image.set(Math.round(i + xShift.fit(-r / 2, r / 2, i + t, -j + t)),
                            Math.round(j + yShift.fit(-r / 2, r / 2, j + t, -i + t)),
                            color(hue, sat, bri, i, j, tcf * t));
                }
            }

            progressor.accept(i / (x + r));
        }
    }
}
