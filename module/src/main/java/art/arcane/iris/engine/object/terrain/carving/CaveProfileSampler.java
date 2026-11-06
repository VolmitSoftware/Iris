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

package art.arcane.iris.engine.object.terrain.carving;

import art.arcane.iris.builtin.carve.CaveFieldModule;
import art.arcane.iris.modules.carve.CaveProfile;
import art.arcane.volmlib.util.math.M;
import com.volmit.config.Range;
import com.volmit.noise.NoiseFactory;
import com.volmit.noise.NoiseSampler;
import com.volmit.noise.NoiseStyle;
import com.volmit.noise.StyledRange;

import java.util.ArrayList;
import java.util.List;

public final class CaveProfileSampler {
    private static final double FLOATING_THRESHOLD_BIAS_SCALE = 0.2D;

    private final CaveProfile profile;
    private final NoiseSampler baseDensity;
    private final NoiseSampler detailDensity;
    private final NoiseSampler warpDensity;
    private final NoiseSampler thresholdNoise;
    private final ModuleState[] modules;
    private final double inverseNormalization;
    private final double baseWeight;
    private final double detailWeight;
    private final double warpStrength;
    private final boolean enabled;
    private final boolean hasWarp;
    private final boolean hasModules;

    public CaveProfileSampler(NoiseFactory noise, long carveSeed, CaveProfile profile) {
        this.profile = profile;
        List<ModuleState> moduleStates = new ArrayList<>();
        this.baseDensity = noise.sampler(profile.baseDensityStyle(), carveSeed + 934_447);
        this.detailDensity = noise.sampler(profile.detailDensityStyle(), carveSeed + 612_991);
        this.warpDensity = noise.sampler(profile.warpStyle(), carveSeed + 770_713);
        this.thresholdNoise = noise.sampler(profile.densityThreshold().style(), carveSeed + 489_112);
        this.baseWeight = profile.baseWeight();
        this.detailWeight = profile.detailWeight();
        this.warpStrength = profile.warpStrength();
        this.hasWarp = warpStrength > 0D;

        double weight = Math.abs(baseWeight) + Math.abs(detailWeight);
        int index = 0;
        for (CaveFieldModule module : profile.modules()) {
            NoiseSampler moduleDensity = noise.sampler(module.style(), carveSeed + 1_000_003L + (index * 65_537L));
            ModuleState state = new ModuleState(module, moduleDensity);
            moduleStates.add(state);
            weight += Math.abs(state.weight);
            index++;
        }

        this.modules = moduleStates.toArray(new ModuleState[0]);
        double normalization = weight <= 0 ? 1D : weight;
        this.inverseNormalization = 1D / normalization;
        this.hasModules = modules.length > 0;
        this.enabled = profile.enabled();
    }

    public boolean shouldCarve(int x, int y, int z, double floatingCarveThreshold) {
        if (!enabled) {
            return false;
        }

        double threshold = thresholdAt(x, z) - profile.thresholdBias();
        threshold += floatingThresholdBias(floatingCarveThreshold);
        return sampleDensity(x, y, z) <= threshold;
    }

    /** The profile's density threshold at the column, as a styled range picks its value. */
    private double thresholdAt(int x, int z) {
        StyledRange range = profile.densityThreshold();
        if (range.min() == range.max()) {
            return range.min();
        }
        if (NoiseStyle.FLAT.equals(range.style().style())) {
            return M.lerp(range.min(), range.max(), 0.5);
        }
        return range.min() + (thresholdNoise.sample(x, z) * (range.max() - range.min()));
    }

    double sampleDensity(int x, int y, int z) {
        if (hasWarp) {
            return sampleDensityWarped(x, y, z);
        }

        return sampleDensityUnwarped(x, y, z);
    }

    private double sampleDensityUnwarped(int x, int y, int z) {
        double density = baseDensity.signed(x, y, z) * baseWeight;
        density += detailDensity.signed(x, y, z) * detailWeight;
        if (hasModules) {
            for (ModuleState module : modules) {
                if (!module.isActive(y)) {
                    continue;
                }

                density += module.sample(x, y, z);
            }
        }

        return density * inverseNormalization;
    }

    private double sampleDensityWarped(int x, int y, int z) {
        double warpA = warpDensity.signed(x, y, z);
        double warpB = warpDensity.signed(x + 31.37D, y - 17.21D, z + 23.91D);
        double warpedX = x + (warpA * warpStrength);
        double warpedY = y + (warpB * warpStrength);
        double warpedZ = z + ((warpA - warpB) * 0.5D * warpStrength);
        double density = baseDensity.signed(warpedX, warpedY, warpedZ) * baseWeight;
        density += detailDensity.signed(warpedX, warpedY, warpedZ) * detailWeight;
        if (hasModules) {
            for (ModuleState module : modules) {
                if (!module.isActive(y)) {
                    continue;
                }

                density += module.sample(warpedX, warpedY, warpedZ);
            }
        }

        return density * inverseNormalization;
    }

    private double floatingThresholdBias(double floatingCarveThreshold) {
        double clamped = Math.max(0D, Math.min(1D, floatingCarveThreshold));
        return (1D - clamped) * FLOATING_THRESHOLD_BIAS_SCALE;
    }

    private static final class ModuleState {
        private final NoiseSampler density;
        private final int minY;
        private final int maxY;
        private final double weight;
        private final double threshold;
        private final boolean invert;

        private ModuleState(CaveFieldModule module, NoiseSampler density) {
            Range range = module.verticalRange();
            this.density = density;
            this.minY = (int) Math.floor(range.min());
            this.maxY = (int) Math.ceil(range.max());
            this.weight = module.weight();
            this.threshold = module.threshold();
            this.invert = module.invert();
        }

        private boolean isActive(int y) {
            return density != null && y >= minY && y <= maxY;
        }

        private double sample(double x, double y, double z) {
            double sampled = density.signed(x, y, z);
            return invert ? (threshold - sampled) * weight : (sampled - threshold) * weight;
        }
    }
}
