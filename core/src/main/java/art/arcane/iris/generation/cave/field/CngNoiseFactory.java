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

package art.arcane.iris.generation.cave.field;

import art.arcane.iris.generation.noise.IrisGeneratorStyle;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.noise.CNG;

import java.util.function.Supplier;

/**
 * Noise of cave fields from Iris's own generators: a style builds the same {@link CNG} the rest of Iris builds from it,
 * seeded from an {@link RNG} of the seed. The pack's data resolves expressions and image maps; without it only plain styles work.
 */
public final class CngNoiseFactory implements NoiseFactory {
    private static final double NO_EXTRA_SCALE = 1D;

    private final IrisData data;

    public CngNoiseFactory(IrisData data) {
        this.data = data;
    }

    /** Noise with no pack behind it: the plain styles, but no expressions and no image maps. */
    public static CngNoiseFactory standalone() {
        return new CngNoiseFactory(null);
    }

    @Override
    public NoiseSampler sampler(IrisGeneratorStyle style, long seed) {
        return new CngSampler(style.create(new RNG(seed), data), () -> style.createNoCache(new RNG(seed), data), NO_EXTRA_SCALE);
    }

    /**
     * {@code fresh} builds the same generator again, unshared, for {@link #scaled}: scaling a generator changes it, and
     * the one in use is shared.
     */
    private record CngSampler(CNG noise, Supplier<CNG> fresh, double extraScale) implements NoiseSampler {
        @Override
        public double sample(double worldX, double worldZ) {
            return noise.noise(worldX, worldZ);
        }

        @Override
        public double sample(double worldX, double y, double worldZ) {
            return noise.noise(worldX, y, worldZ);
        }

        @Override
        public double signed(double worldX, double worldZ) {
            return noise.noiseFastSigned2D(worldX, worldZ);
        }

        @Override
        public double signed(double worldX, double y, double worldZ) {
            return noise.noiseFastSigned3D(worldX, y, worldZ);
        }

        @Override
        public NoiseSampler scaled(double factor) {
            double total = extraScale * factor;
            return new CngSampler(fresh.get().bake().scale(total).bake(), fresh, total);
        }
    }
}
