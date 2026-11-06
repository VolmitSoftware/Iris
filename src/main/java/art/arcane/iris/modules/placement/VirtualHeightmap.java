/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.modules.placement;

import art.arcane.iris.builtin.objects.HeightmapNoise;
import com.volmit.noise.NoiseFactory;
import com.volmit.noise.NoiseSampler;

import java.util.ArrayList;
import java.util.List;

/**
 * The heights of an object placement's virtual heightmap: the noise of a {@link HeightmapNoise} and of the
 * generators that fracture its coordinates, built once from API noise and seeded from one number. The setting
 * {@code octaves} never changed Iris's values, so it is not read.
 */
public final class VirtualHeightmap {
    private static final long GENERATOR_SEED_SALT = 33_955_677L;
    private static final long FIRST_FRACTURE_SEED_OFFSET = 33L;
    private static final long FRACTURE_SEED_STEP = 819L;

    private final HeightmapNoise settings;
    private final NoiseSampler sampler;
    private final List<VirtualHeightmap> fractures;

    private VirtualHeightmap(HeightmapNoise settings, NoiseFactory noise, long seed) {
        this.settings = settings;
        this.sampler = settings.enabled() ? noise.sampler(settings.style(), seed + GENERATOR_SEED_SALT - settings.seed()) : null;
        this.fractures = fracturesOf(settings, noise, seed);
    }

    /** The heightmap {@code settings} describe, its noise seeded with {@code seed}. */
    public static VirtualHeightmap of(HeightmapNoise settings, NoiseFactory noise, long seed) {
        return new VirtualHeightmap(settings, noise, seed);
    }

    /** The height at the column, rounded to a whole block. */
    public int surface(int x, int z) {
        return (int) Math.round(valueAt(x, z));
    }

    private static List<VirtualHeightmap> fracturesOf(HeightmapNoise settings, NoiseFactory noise, long seed) {
        List<VirtualHeightmap> built = new ArrayList<>();
        long fractureOffset = FIRST_FRACTURE_SEED_OFFSET;
        for (HeightmapNoise fracture : settings.fracture()) {
            if (fracture.enabled()) {
                built.add(new VirtualHeightmap(fracture, noise, seed + settings.seed() + fractureOffset));
            }
            fractureOffset += FRACTURE_SEED_STEP;
        }
        return List.copyOf(built);
    }

    private double valueAt(double columnX, double columnZ) {
        if (!settings.enabled()) {
            return settings.offsetY();
        }
        double x = columnX;
        double z = columnZ;
        for (VirtualHeightmap fracture : fractures) {
            double centre = fracture.settings.opacity() / 2D;
            x += fracture.valueAt(columnX, columnZ) - centre;
            z += fracture.valueAt(columnZ, columnX) - centre;
        }
        double sampleX = (x / settings.zoom()) + settings.offsetX();
        double sampleZ = (z / settings.zoom()) + settings.offsetZ();
        return shaped(sampler.sample(sampleX, sampleZ) * settings.opacity());
    }

    private double shaped(double sampled) {
        double value = settings.negative() ? (-sampled + settings.opacity()) : sampled;
        value = raised(value) + settings.offsetY();
        if (settings.parametric()) {
            value = parametric(value);
        }
        if (settings.bezier()) {
            value = value * value * (3D - 2D * value);
        }
        return settings.sinCentered() ? Math.sin(value * Math.PI) : value;
    }

    private double raised(double value) {
        double exponent = settings.exponent();
        if (exponent == 1D) {
            return value;
        }
        return value < 0 ? -Math.pow(-value, exponent) : Math.pow(value, exponent);
    }

    /** The parametric curve of alpha 1, which is what the setting has always applied. */
    private static double parametric(double value) {
        double raised = Math.pow(value, 1D);
        return raised / ((raised - Math.pow(value, 0D)) + 1D);
    }
}
