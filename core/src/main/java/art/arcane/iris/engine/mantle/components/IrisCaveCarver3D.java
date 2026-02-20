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

package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.mantle.MantleWriter;
import art.arcane.iris.engine.object.IrisCaveFieldModule;
import art.arcane.iris.engine.object.IrisCaveProfile;
import art.arcane.iris.engine.object.IrisRange;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.MatterCavern;

public class IrisCaveCarver3D {
    private static final byte LIQUID_AIR = 0;
    private static final byte LIQUID_WATER = 1;
    private static final byte LIQUID_LAVA = 2;
    private static final byte LIQUID_FORCED_AIR = 3;

    private final Engine engine;
    private final IrisData data;
    private final IrisCaveProfile profile;
    private final CNG baseDensity;
    private final CNG detailDensity;
    private final CNG warpDensity;
    private final CNG surfaceBreakDensity;
    private final RNG thresholdRng;
    private final KList<ModuleState> modules;
    private final double normalization;
    private final MatterCavern carveAir;
    private final MatterCavern carveWater;
    private final MatterCavern carveLava;
    private final MatterCavern carveForcedAir;

    public IrisCaveCarver3D(Engine engine, IrisCaveProfile profile) {
        this.engine = engine;
        this.data = engine.getData();
        this.profile = profile;
        this.carveAir = new MatterCavern(true, "", LIQUID_AIR);
        this.carveWater = new MatterCavern(true, "", LIQUID_WATER);
        this.carveLava = new MatterCavern(true, "", LIQUID_LAVA);
        this.carveForcedAir = new MatterCavern(true, "", LIQUID_FORCED_AIR);
        this.modules = new KList<>();

        RNG baseRng = new RNG(engine.getSeedManager().getCarve());
        this.baseDensity = profile.getBaseDensityStyle().create(baseRng.nextParallelRNG(934_447), data);
        this.detailDensity = profile.getDetailDensityStyle().create(baseRng.nextParallelRNG(612_991), data);
        this.warpDensity = profile.getWarpStyle().create(baseRng.nextParallelRNG(770_713), data);
        this.surfaceBreakDensity = profile.getSurfaceBreakStyle().create(baseRng.nextParallelRNG(341_219), data);
        this.thresholdRng = baseRng.nextParallelRNG(489_112);

        double weight = Math.abs(profile.getBaseWeight()) + Math.abs(profile.getDetailWeight());
        int index = 0;
        for (IrisCaveFieldModule module : profile.getModules()) {
            CNG moduleDensity = module.getStyle().create(baseRng.nextParallelRNG(1_000_003L + (index * 65_537L)), data);
            ModuleState state = new ModuleState(module, moduleDensity);
            modules.add(state);
            weight += Math.abs(state.weight);
            index++;
        }

        normalization = weight <= 0 ? 1 : weight;
    }

    public int carve(MantleWriter writer, int chunkX, int chunkZ) {
        int worldHeight = writer.getMantle().getWorldHeight();
        int minY = Math.max(0, (int) Math.floor(profile.getVerticalRange().getMin()));
        int maxY = Math.min(worldHeight - 1, (int) Math.ceil(profile.getVerticalRange().getMax()));
        int sampleStep = Math.max(1, profile.getSampleStep());
        int surfaceClearance = Math.max(0, profile.getSurfaceClearance());
        int surfaceBreakDepth = Math.max(0, profile.getSurfaceBreakDepth());
        double surfaceBreakNoiseThreshold = profile.getSurfaceBreakNoiseThreshold();
        double surfaceBreakThresholdBoost = Math.max(0, profile.getSurfaceBreakThresholdBoost());
        int waterMinDepthBelowSurface = Math.max(0, profile.getWaterMinDepthBelowSurface());
        boolean waterRequiresFloor = profile.isWaterRequiresFloor();
        boolean allowSurfaceBreak = profile.isAllowSurfaceBreak();
        if (maxY < minY) {
            return 0;
        }

        int x0 = chunkX << 4;
        int z0 = chunkZ << 4;
        int carved = 0;

        for (int x = x0; x < x0 + 16; x++) {
            for (int z = z0; z < z0 + 16; z++) {
                double threshold = profile.getDensityThreshold().get(thresholdRng, x, z, data) - profile.getThresholdBias();
                int columnSurface = engine.getHeight(x, z);
                int clearanceTopY = Math.min(maxY, Math.max(minY, columnSurface - surfaceClearance));
                boolean surfaceBreakColumn = allowSurfaceBreak
                        && signed(surfaceBreakDensity.noise(x, z)) >= surfaceBreakNoiseThreshold;
                int columnMaxY = surfaceBreakColumn
                        ? Math.min(maxY, Math.max(minY, columnSurface))
                        : clearanceTopY;
                int surfaceBreakFloorY = Math.max(minY, columnSurface - surfaceBreakDepth);
                if (columnMaxY < minY) {
                    continue;
                }

                for (int y = minY; y <= columnMaxY; y += sampleStep) {
                    double localThreshold = threshold;
                    if (surfaceBreakColumn && y >= surfaceBreakFloorY) {
                        localThreshold += surfaceBreakThresholdBoost;
                    }

                    if (sampleDensity(x, y, z) <= localThreshold) {
                        int carveMaxY = Math.min(columnMaxY, y + sampleStep - 1);
                        for (int yy = y; yy <= carveMaxY; yy++) {
                            writer.setData(x, yy, z, resolveMatter(x, yy, z, localThreshold, waterMinDepthBelowSurface, waterRequiresFloor));
                            carved++;
                        }
                    }
                }
            }
        }

        return carved;
    }

    private double sampleDensity(int x, int y, int z) {
        double warpedX = x;
        double warpedY = y;
        double warpedZ = z;
        double warpStrength = profile.getWarpStrength();

        if (warpStrength > 0) {
            double offsetX = signed(warpDensity.noise(x, y, z)) * warpStrength;
            double offsetY = signed(warpDensity.noise(y, z, x)) * warpStrength;
            double offsetZ = signed(warpDensity.noise(z, x, y)) * warpStrength;
            warpedX += offsetX;
            warpedY += offsetY;
            warpedZ += offsetZ;
        }

        double density = signed(baseDensity.noise(warpedX, warpedY, warpedZ)) * profile.getBaseWeight();
        density += signed(detailDensity.noise(warpedX, warpedY, warpedZ)) * profile.getDetailWeight();

        for (ModuleState module : modules) {
            if (y < module.minY || y > module.maxY) {
                continue;
            }

            double moduleDensity = signed(module.density.noise(warpedX, warpedY, warpedZ)) - module.threshold;
            if (module.invert) {
                moduleDensity = -moduleDensity;
            }

            density += moduleDensity * module.weight;
        }

        return density / normalization;
    }

    private MatterCavern resolveMatter(int x, int y, int z, double localThreshold, int waterMinDepthBelowSurface, boolean waterRequiresFloor) {
        int lavaHeight = engine.getDimension().getCaveLavaHeight();
        int fluidHeight = engine.getDimension().getFluidHeight();

        if (profile.isAllowLava() && y <= lavaHeight) {
            return carveLava;
        }

        if (profile.isAllowWater() && y <= fluidHeight) {
            int surfaceY = engine.getHeight(x, z);
            if (surfaceY - y < waterMinDepthBelowSurface) {
                return carveAir;
            }

            double depthFactor = Math.max(0, Math.min(1.5, (fluidHeight - y) / 48D));
            double cutoff = 0.35 + (depthFactor * 0.2);
            double aquifer = signed(detailDensity.noise(x, y * 0.5D, z));
            if (aquifer <= cutoff) {
                return carveAir;
            }

            if (waterRequiresFloor && !hasAquiferCupSupport(x, y, z, localThreshold)) {
                return carveAir;
            }

            return carveWater;
        }

        if (!profile.isAllowLava() && y <= lavaHeight) {
            return carveForcedAir;
        }

        return carveAir;
    }

    private boolean hasAquiferCupSupport(int x, int y, int z, double threshold) {
        int floorY = Math.max(0, y - 1);
        int deepFloorY = Math.max(0, y - 2);
        int aboveY = Math.min(engine.getHeight() - 1, y + 1);
        if (!isDensitySolid(x, floorY, z, threshold)) {
            return false;
        }

        if (!isDensitySolid(x, deepFloorY, z, threshold - 0.05D)) {
            return false;
        }

        int support = 0;
        if (isDensitySolid(x + 1, y, z, threshold)) {
            support++;
        }
        if (isDensitySolid(x - 1, y, z, threshold)) {
            support++;
        }
        if (isDensitySolid(x, y, z + 1, threshold)) {
            support++;
        }
        if (isDensitySolid(x, y, z - 1, threshold)) {
            support++;
        }
        if (isDensitySolid(x, aboveY, z, threshold)) {
            support++;
        }

        return support >= 4;
    }

    private boolean isDensitySolid(int x, int y, int z, double threshold) {
        return sampleDensity(x, y, z) > threshold;
    }

    private double signed(double value) {
        return (value * 2D) - 1D;
    }

    private static final class ModuleState {
        private final CNG density;
        private final int minY;
        private final int maxY;
        private final double weight;
        private final double threshold;
        private final boolean invert;

        private ModuleState(IrisCaveFieldModule module, CNG density) {
            IrisRange range = module.getVerticalRange();
            this.density = density;
            this.minY = (int) Math.floor(range.getMin());
            this.maxY = (int) Math.ceil(range.getMax());
            this.weight = module.getWeight();
            this.threshold = module.getThreshold();
            this.invert = module.isInvert();
        }
    }
}
