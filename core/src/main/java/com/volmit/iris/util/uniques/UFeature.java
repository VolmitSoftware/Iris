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

package com.volmit.iris.util.uniques;

import com.volmit.iris.util.function.NoiseInjector;
import com.volmit.iris.util.interpolation.InterpolationMethod;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.noise.CNG;

import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

public interface UFeature {
    List<NoiseInjector> injectors = List.of(
            CNG.ADD,
            CNG.DST_MOD,
            CNG.DST_POW,
            CNG.DST_SUBTRACT,
            CNG.MAX,
            CNG.MIN,
            CNG.SRC_MOD,
            CNG.SRC_POW,
            CNG.SRC_SUBTRACT,
            CNG.MULTIPLY
    );

    void render(UImage image, RNG rng, double time, Consumer<Double> progressor, UFeatureMeta meta);

    default Color color(CNG hue, CNG saturation, CNG brightness, double x, double y, double t) {
        return Color.getHSBColor((float) hue.fitDouble(0, 1, x + t, y + t),
                (float) saturation.fitDouble(0, 1, x + t, y + t),
                (float) brightness.fitDouble(0, 1, x + t, y + t));
    }

    default InterpolationMethod interpolator(RNG rng) {
        return rng.pick(
                UniqueRenderer.renderer.getInterpolators()
        );
    }

    default CNG generator(String key, RNG rng, double scaleMod, long salt, UFeatureMeta meta) {
        return generator(key, rng, scaleMod, rng.i(1, 3), rng.i(1, 5), salt, meta);
    }

    default CNG generator(String key, RNG rng, double scaleMod, int fractures, int composites, long salt, UFeatureMeta meta) {
        RNG rngg = rng.nextParallelRNG(salt);
        CNG cng = rng.pick(UniqueRenderer.renderer.getStyles()).create(rngg).oct(rng.i(1, 5));
        RNG rngf = rngg.nextParallelRNG(-salt);
        cng.scale(rngf.d(0.33 * scaleMod, 1.66 * scaleMod));

        if (fractures > 0) {
            cng.fractureWith(generator(null, rngf.nextParallelRNG(salt + fractures), scaleMod / rng.d(4, 17), fractures - 1, composites, salt + fractures + 55, null), scaleMod * rngf.nextDouble(16, 256));
        }

        for (int i = 0; i < composites; i++) {
            CNG sub = generator(null, rngf.nextParallelRNG(salt + fractures), scaleMod * rngf.d(0.4, 3.3), fractures / 3, 0, salt + fractures + composites + 78, null);
            sub.setInjector(rng.pick(injectors));
            cng.child(sub);
        }

        if (key != null && meta != null) {
            meta.registerGenerator(key, cng);
        }
        return cng;
    }
}
