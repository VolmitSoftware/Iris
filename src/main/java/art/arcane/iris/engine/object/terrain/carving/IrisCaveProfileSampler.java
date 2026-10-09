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

import art.arcane.iris.builtin.carve.CaveProfile;
import art.arcane.iris.engine.object.value.IrisRange;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.volmlib.util.math.RNG;

import java.util.ArrayList;
import java.util.List;

public final class IrisCaveProfileSampler {
    private static final double FLOATING_THRESHOLD_BIAS_SCALE = 0.2D;

    private final IrisData data;
    private final CaveProfile profile;
    private final ResolvedCaveProfile resolved;
    private final CNG baseDensity;
    private final CNG detailDensity;
    private final CNG warpDensity;
    private final RNG thresholdRng;
    private final ModuleState[] modules;
    private final double inverseNormalization;
    private final double baseWeight;
    private final double detailWeight;
    private final double warpStrength;
    private final boolean enabled;
    private final boolean hasWarp;
    private final boolean hasModules;

    public IrisCaveProfileSampler(Engine engine, CaveProfile profile) {
        this.data = engine.getData();
        this.profile = profile;
        this.resolved = ResolvedCaveProfile.of(profile);
        List<ModuleState> moduleStates = new ArrayList<>();
        RNG baseRng = new RNG(engine.getSeedManager().getCarve());
        this.baseDensity = resolved.baseDensityStyle().create(baseRng.nextParallelRNG(934_447), data);
        this.detailDensity = resolved.detailDensityStyle().create(baseRng.nextParallelRNG(612_991), data);
        this.warpDensity = resolved.warpStyle().create(baseRng.nextParallelRNG(770_713), data);
        this.thresholdRng = baseRng.nextParallelRNG(489_112);
        this.baseWeight = profile.baseWeight();
        this.detailWeight = profile.detailWeight();
        this.warpStrength = profile.warpStrength();
        this.hasWarp = warpStrength > 0D;

        double weight = Math.abs(baseWeight) + Math.abs(detailWeight);
        int index = 0;
        for (ResolvedCaveProfile.Module module : resolved.modules()) {
            CNG moduleDensity = module.style().create(baseRng.nextParallelRNG(1_000_003L + (index * 65_537L)), data);
            ModuleState state = new ModuleState(module, moduleDensity);
            moduleStates.add(state);
            weight += Math.abs(state.weight);
            index++;
        }

        this.modules = moduleStates.toArray(new ModuleState[0]);
        double normalization = weight <= 0 ? 1D : weight;
        this.inverseNormalization = 1D / normalization;
        this.hasModules = modules.length > 0;
        this.enabled = profile.enabled() && baseDensity != null && detailDensity != null && (!hasWarp || warpDensity != null);
    }

    public boolean shouldCarve(int x, int y, int z, double floatingCarveThreshold) {
        if (!enabled) {
            return false;
        }

        double threshold = resolved.densityThreshold().get(thresholdRng, x, z, data) - profile.thresholdBias();
        threshold += floatingThresholdBias(floatingCarveThreshold);
        return sampleDensity(x, y, z) <= threshold;
    }

    double sampleDensity(int x, int y, int z) {
        if (hasWarp) {
            return sampleDensityWarped(x, y, z);
        }

        return sampleDensityUnwarped(x, y, z);
    }

    private double sampleDensityUnwarped(int x, int y, int z) {
        double density = baseDensity.noiseFastSigned3D(x, y, z) * baseWeight;
        density += detailDensity.noiseFastSigned3D(x, y, z) * detailWeight;
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
        double warpA = warpDensity.noiseFastSigned3D(x, y, z);
        double warpB = warpDensity.noiseFastSigned3D(x + 31.37D, y - 17.21D, z + 23.91D);
        double warpedX = x + (warpA * warpStrength);
        double warpedY = y + (warpB * warpStrength);
        double warpedZ = z + ((warpA - warpB) * 0.5D * warpStrength);
        double density = baseDensity.noiseFastSigned3D(warpedX, warpedY, warpedZ) * baseWeight;
        density += detailDensity.noiseFastSigned3D(warpedX, warpedY, warpedZ) * detailWeight;
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
        private final CNG density;
        private final int minY;
        private final int maxY;
        private final double weight;
        private final double threshold;
        private final boolean invert;

        private ModuleState(ResolvedCaveProfile.Module module, CNG density) {
            IrisRange range = module.verticalRange();
            this.density = density;
            this.minY = (int) Math.floor(range.getMin());
            this.maxY = (int) Math.ceil(range.getMax());
            this.weight = module.weight();
            this.threshold = module.threshold();
            this.invert = module.invert();
        }

        private boolean isActive(int y) {
            return density != null && y >= minY && y <= maxY;
        }

        private double sample(double x, double y, double z) {
            double sampled = density.noiseFastSigned3D(x, y, z);
            return invert ? (threshold - sampled) * weight : (sampled - threshold) * weight;
        }
    }
}
