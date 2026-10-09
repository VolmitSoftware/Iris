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

/**
 * Builds the noise of IrisApi noise styles with Iris's own generators: each style becomes the
 * {@link IrisGeneratorStyle} that reads the same JSON ({@link IrisValues#generatorStyle}), once per pack snapshot, and
 * builds its noise from an {@link RNG} of the seed exactly as Iris's own features do, with Iris's per-style cache of
 * built generators.
 */
final class IrisNoiseFactory implements NoiseFactory {
    private final IrisData data;
    private final Map<NoiseStyle, IrisGeneratorStyle> styles = new ConcurrentHashMap<>();

    IrisNoiseFactory(IrisData data) {
        this.data = data;
    }

    @Override
    public NoiseSampler sampler(NoiseStyle style, long seed) {
        IrisGeneratorStyle iris = styles.computeIfAbsent(style, IrisValues::generatorStyle);
        try {
            return new CngSampler(iris.create(new RNG(seed), data));
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("Cannot build the noise of " + style + ": " + failure.getMessage(), failure);
        }
    }

    /** Iris's noise as an IrisApi sampler; CNG is safe for the concurrent reads generation makes. */
    private record CngSampler(CNG noise) implements NoiseSampler {
        @Override
        public double sample(double worldX, double worldZ) {
            return noise.noise(worldX, worldZ);
        }

        @Override
        public double sample(double worldX, double y, double worldZ) {
            return noise.noise(worldX, y, worldZ);
        }
    }
}
