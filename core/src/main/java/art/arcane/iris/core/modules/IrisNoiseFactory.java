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

package art.arcane.iris.core.modules;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.object.noise.IrisGeneratorStyle;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.noise.NoiseFactory;
import com.volmit.noise.NoiseSampler;
import com.volmit.noise.NoiseStyle;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Builds the noise of IrisApi noise styles with Iris's own generators: each style becomes the
 * {@link IrisGeneratorStyle} that reads the same JSON ({@link IrisValues#generatorStyle}), once per pack snapshot, and
 * builds its noise from an {@link RNG} of the seed exactly as Iris's own features do, with Iris's per-style cache of
 * built generators.
 */
final class IrisNoiseFactory implements NoiseFactory {
    private static final double NO_EXTRA_SCALE = 1D;

    private final IrisData data;
    private final IrisPackResources resources;
    private final Map<NoiseStyle, IrisGeneratorStyle> styles = new ConcurrentHashMap<>();

    /** @param resources the snapshot whose keys the expressions and image maps of styles name */
    IrisNoiseFactory(IrisData data, IrisPackResources resources) {
        this.data = data;
        this.resources = resources;
    }

    @Override
    public NoiseSampler sampler(NoiseStyle style, long seed) {
        IrisGeneratorStyle iris = styles.computeIfAbsent(style, unbuilt -> IrisValues.generatorStyle(unbuilt, resources));
        try {
            return new CngSampler(iris.create(new RNG(seed), data), () -> iris.createNoCache(new RNG(seed), data), NO_EXTRA_SCALE);
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("Cannot build the noise of " + style + ": " + failure.getMessage(), failure);
        }
    }

    /**
     * Iris's noise as an IrisApi sampler; CNG is safe for the concurrent reads generation makes. {@code fresh} builds the same
     * generator again, unshared, for {@link #scaled}: scaling a generator changes it, and this one is shared.
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

        /** CNG's own signed read, which answers a repeated read of the same coordinates from a per-thread memo. */
        @Override
        public double signed(double worldX, double worldZ) {
            return noise.noiseFastSigned2D(worldX, worldZ);
        }

        @Override
        public double signed(double worldX, double y, double worldZ) {
            return noise.noiseFastSigned3D(worldX, y, worldZ);
        }

        /** The generator with {@code factor} baked into its scale, as {@code create(...).bake().scale(factor).bake()} always gave. */
        @Override
        public NoiseSampler scaled(double factor) {
            double total = extraScale * factor;
            return new CngSampler(fresh.get().bake().scale(total).bake(), fresh, total);
        }
    }
}
