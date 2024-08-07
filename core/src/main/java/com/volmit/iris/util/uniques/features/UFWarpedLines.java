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

public class UFWarpedLines implements UFeature {
    @Override
    public void render(UImage image, RNG rng, double t, Consumer<Double> progressor, UFeatureMeta meta) {
        for (int g = 1; g < 5; g++) {
            CNG xShift = generator("x_warp_" + g, rng, 0.6, 1001 * g, meta);
            CNG yShift = generator("y_warp_" + g, rng, 0.6, 1002 * g, meta);
            CNG cX = generator("x_clip_" + g, rng, rng.d(0.035, 0.6), 77001 * g, meta);
            CNG cY = generator("y_clip" + g, rng, rng.d(0.035, 0.6), 77002, meta);
            CNG hue = generator("color_hue_" + g, rng, rng.d(0.25, 2.5), 1003 * g, meta);
            CNG sat = generator("color_sat_" + g, rng, rng.d(0.25, 2.5), 1004 * g, meta);
            CNG bri = generator("color_bri_" + g, rng, rng.d(0.25, 2.5), 1005 * g, meta);
            double tcf = rng.d(0.75, 11.25 + (g * 4));
            double rcf = rng.d(7.75, 16.25 + (g * 5));
            double xcf = rng.d(0.15, 0.55 + (g * 0.645));
            double w = rng.d(64, 186 + (g * 8));
            double ww = image.getWidth() / rng.d(3, 9);
            double hh = image.getHeight() / rng.d(3, 9);
            boolean wh = rng.nextBoolean();
            double sa = rng.d(0.35, 0.66);
            double sb = rng.d(0.35, 0.66);

            for (int i = 0; i < image.getWidth(); i += (wh ? image.getWidth() / w : 1)) {
                for (int j = 0; j < image.getHeight(); j += (!wh ? image.getHeight() / w : 1)) {
                    if (cX.fitDouble(0, 1, i, -j, t * xcf) > sa && cY.fitDouble(0, 1, -j, i, t * xcf) > sb) {
                        image.set(Math.round(i + xShift.fit(-ww, ww, i, j, (t * rcf))),
                                Math.round(j + yShift.fit(-hh, hh, -j, i, (t * rcf))),
                                color(hue, sat, bri, i, j, (t * tcf)));
                    }
                }
            }
        }
    }
}
