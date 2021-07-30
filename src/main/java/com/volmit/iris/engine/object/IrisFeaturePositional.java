/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

import com.google.gson.Gson;
import com.volmit.iris.engine.cache.AtomicCache;
import com.volmit.iris.engine.interpolation.IrisInterpolation;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.Required;
import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.function.NoiseProvider;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.RNG;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@Data

@NoArgsConstructor
@Desc("Represents an Iris zone")
public class IrisFeaturePositional {
    public IrisFeaturePositional(int x, int z, IrisFeature feature) {
        this.x = x;
        this.z = z;
        this.feature = feature;
    }

    @Required
    @Desc("The x coordinate of this zone")
    private int x;

    @Required
    @Desc("The z coordinate of this zone")
    private int z;

    @Required
    @Desc("The Terrain Feature to apply")
    private IrisFeature feature;

    private transient AtomicCache<NoiseProvider> provider = new AtomicCache<>();
    private static double BLOCK = 1D / 256D; // TODO: WARNING HEIGHT

    public static IrisFeaturePositional read(DataInputStream s) throws IOException {
        return new Gson().fromJson(s.readUTF(), IrisFeaturePositional.class);
    }

    public void write(DataOutputStream s) throws IOException {
        s.writeUTF(new Gson().toJson(this));
    }

    @BlockCoordinates
    public boolean shouldFilter(double x, double z, RNG rng) {
        double actualRadius = getFeature().getActualRadius();
        double dist2 = distance2(x, z, rng);

        if (getFeature().isInvertZone()) {
            if (dist2 < Math.pow(getFeature().getBlockRadius() - actualRadius, 2)) {
                return false;
            }
        }

        return !(dist2 > Math.pow(getFeature().getBlockRadius() + actualRadius, 2));
    }

    public double getStrength(double x, double z, RNG rng) {
        double actualRadius = getFeature().getActualRadius();
        double dist2 = distance2(x, z, rng);

        if (getFeature().isInvertZone()) {
            if (dist2 < Math.pow(getFeature().getBlockRadius() - actualRadius, 2)) {
                return 0;
            }

            NoiseProvider d = provider.aquire(() -> getNoiseProvider(rng));
            double s = IrisInterpolation.getNoise(getFeature().getInterpolator(), (int) x, (int) z, getFeature().getInterpolationRadius(), d);

            if (s <= 0) {
                return 0;
            }

            return getFeature().getStrength() * s;
        } else {
            if (dist2 > Math.pow(getFeature().getBlockRadius() + actualRadius, 2)) {
                return 0;
            }

            NoiseProvider d = provider.aquire(() -> getNoiseProvider(rng));
            double s = IrisInterpolation.getNoise(getFeature().getInterpolator(), (int) x, (int) z, getFeature().getInterpolationRadius(), d);

            if (s <= 0) {
                return 0;
            }

            return getFeature().getStrength() * s;
        }
    }

    public double getObjectChanceModifier(double x, double z, RNG rng) {
        if (getFeature().getObjectChance() >= 1) {
            return getFeature().getObjectChance();
        }

        return M.lerp(1, getFeature().getObjectChance(), getStrength(x, z, rng));
    }

    public IrisBiome filter(double x, double z, IrisBiome biome, RNG rng) {
        if (getFeature().getCustomBiome() != null) {
            if (getStrength(x, z, rng) >= getFeature().getBiomeStrengthThreshold()) {
                IrisBiome b = biome.getLoader().getBiomeLoader().load(getFeature().getCustomBiome());
                b.setInferredType(biome.getInferredType());
                return b;
            }
        }

        return null;
    }

    public double filter(double x, double z, double noise, RNG rng) {
        double s = getStrength(x, z, rng);

        if (s <= 0) {
            return noise;
        }

        double fx = noise;

        if (getFeature().getConvergeToHeight() >= 0) {
            fx = getFeature().getConvergeToHeight();
        }

        fx *= getFeature().getMultiplyHeight();
        fx += getFeature().getShiftHeight();

        return M.lerp(noise, fx, s);
    }

    public double distance(double x, double z, RNG rng) {
        double mul = getFeature().getFractureRadius() != null ? getFeature().getFractureRadius().getMultiplier() / 2 : 1;
        double mod = getFeature().getFractureRadius() != null ? getFeature().getFractureRadius().create(rng).fitDouble(-mul, mul, x, z) : 0;
        return Math.sqrt(Math.pow(this.x - (x + mod), 2) + Math.pow(this.z - (z + mod), 2));
    }

    public double distance2(double x, double z, RNG rng) {
        double mul = getFeature().getFractureRadius() != null ? getFeature().getFractureRadius().getMultiplier() / 2 : 1;
        double mod = getFeature().getFractureRadius() != null ? getFeature().getFractureRadius().create(rng).fitDouble(-mul, mul, x, z) : 0;

        return Math.pow(this.x - (x + mod), 2) + Math.pow(this.z - (z + mod), 2);
    }

    private NoiseProvider getNoiseProvider(RNG rng) {
        if (getFeature().isInvertZone()) {
            return (x, z) -> distance(x, z, rng) > getFeature().getBlockRadius() ? 1D : 0D;
        } else {
            return (x, z) -> distance(x, z, rng) < getFeature().getBlockRadius() ? 1D : 0D;
        }
    }
}
