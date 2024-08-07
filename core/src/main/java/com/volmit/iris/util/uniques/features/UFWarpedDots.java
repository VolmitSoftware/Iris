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

import java.awt.*;
import java.util.function.Consumer;

public class UFWarpedDots implements UFeature {
    @Override
    public void render(UImage image, RNG rng, double t, Consumer<Double> progressor, UFeatureMeta meta) {
        CNG genX = generator("x_pos", rng, 4, 2000, meta);
        CNG genY = generator("y_pos", rng, 4, 2001, meta);

        double tcf = rng.d(0.75, 11.25);

        for (int i = 1; i <= 8; i++) {
            CNG xShift = generator("x_warp_" + i, rng, 2, 2006 + i, meta);
            CNG yShift = generator("y_warp_" + i, rng, 2, 2007 + i, meta);
            CNG hue = generator("color_hue_" + i, rng, rng.d(0.55, 3.5), 2003 + i, meta);
            CNG sat = generator("color_sat_" + i, rng, rng.d(0.55, 3.5), 2004 + i, meta);
            CNG bri = generator("color_bri_" + i, rng, rng.d(0.55, 3.5), 2005 + i, meta);
            int x = genX.fit(0, image.getWidth(), i * 128, i * 5855);
            int y = genY.fit(0, image.getHeight(), i * 128, i * 5855);
            Color color = color(hue, sat, bri, x, y, t);
            double r = Math.max(genX.fit(image.getWidth() / 10, image.getWidth() / 6, x, y), genY.fit(image.getHeight() / 10, image.getHeight() / 6, x, y));

            for (int j = (int) (x - r); j < x + r; j++) {
                for (int k = (int) (y - r); k < y + r; k++) {
                    if (image.isInBounds(j, k) && Math.pow(x - j, 2) + Math.pow(y - k, 2) <= r * r) {
                        image.set(Math.round(j + xShift.fit(-r / 2, r / 2, j + t, -k + t)),
                                Math.round(k + yShift.fit(-r / 2, r / 2, k + t, -j + t)),
                                color(hue, sat, bri, j, k, tcf * t));
                    }
                }
            }

            progressor.accept(i / 32D);
        }
    }
}
