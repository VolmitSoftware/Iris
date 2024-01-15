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

package com.volmit.iris.engine.object;

import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.object.annotations.*;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.interpolation.IrisInterpolation;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.noise.CNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("generator")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("A noise generator")
@Data
public class IrisNoiseGenerator {
    private final transient AtomicCache<CNG> generator = new AtomicCache<>();
    @MinNumber(0.0001)
    @Desc("The coordinate input zoom")
    private double zoom = 1;
    @Desc("Reverse the output. So that noise = -noise + opacity")
    private boolean negative = false;
    @MinNumber(0)
    @MaxNumber(1)
    @Desc("The output multiplier")
    private double opacity = 1;
    @Desc("Coordinate offset x")
    private double offsetX = 0;
    @Desc("Height output offset y. Avoid using with terrain generation.")
    private double offsetY = 0;
    @Desc("Coordinate offset z")
    private double offsetZ = 0;
    @Required
    @Desc("The seed")
    private long seed = 0;
    @Desc("Apply a parametric curve on the output")
    private boolean parametric = false;
    @Desc("Apply a bezier curve on the output")
    private boolean bezier = false;
    @Desc("Apply a sin-center curve on the output (0, and 1 = 0 and 0.5 = 1.0 using a sinoid shape.)")
    private boolean sinCentered = false;
    @Desc("The exponent noise^EXPONENT")
    private double exponent = 1;
    @Desc("Enable / disable. Outputs offsetY if disabled")
    private boolean enabled = true;
    @Required
    @Desc("The Noise Style")
    private IrisGeneratorStyle style = NoiseStyle.IRIS.style();
    @MinNumber(1)
    @Desc("Multiple octaves for multple generators of changing zooms added together")
    private int octaves = 1;
    @ArrayType(min = 1, type = IrisNoiseGenerator.class)
    @Desc("Apply a child noise generator to fracture the input coordinates of this generator")
    private KList<IrisNoiseGenerator> fracture = new KList<>();

    public IrisNoiseGenerator(boolean enabled) {
        this();
        this.enabled = enabled;
    }

    protected CNG getGenerator(long superSeed, IrisData data) {
        return generator.aquire(() -> style.create(new RNG(superSeed + 33955677 - seed), data).oct(octaves));
    }

    public double getMax() {
        return getOffsetY() + opacity;
    }

    public double getNoise(long superSeed, double xv, double zv, IrisData data) {
        if (!enabled) {
            return offsetY;
        }

        double x = xv;
        double z = zv;
        int g = 33;

        for (IrisNoiseGenerator i : fracture) {
            if (i.isEnabled()) {
                x += i.getNoise(superSeed + seed + g, xv, zv, data) - (opacity / 2D);
                z -= i.getNoise(superSeed + seed + g, zv, xv, data) - (opacity / 2D);
            }
            g += 819;
        }

        double n = getGenerator(superSeed, data).fitDouble(0, opacity, (x / zoom) + offsetX, (z / zoom) + offsetZ);
        n = negative ? (-n + opacity) : n;
        n = (exponent != 1 ? n < 0 ? -Math.pow(-n, exponent) : Math.pow(n, exponent) : n) + offsetY;
        n = parametric ? IrisInterpolation.parametric(n, 1) : n;
        n = bezier ? IrisInterpolation.bezier(n) : n;
        n = sinCentered ? IrisInterpolation.sinCenter(n) : n;

        return n;
    }

    public KList<IrisNoiseGenerator> getAllComposites() {
        KList<IrisNoiseGenerator> g = new KList<>();

        g.add(this);

        for (IrisNoiseGenerator i : getFracture()) {
            g.addAll(i.getAllComposites());
        }

        return g;
    }
}
