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

package art.arcane.iris.generation.decoration.coral;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.generation.decoration.IrisProceduralBlocks;
import art.arcane.iris.generation.decoration.tree.TreeFunctions;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.math.Vector3i;
import art.arcane.volmlib.util.math.RNG;

import java.util.HashMap;
import java.util.Map;

public final class CoralGenerator {
    private static final double GOLDEN_ANGLE = 137.50776405003785;

    private CoralGenerator() {
    }

    public static IrisObject generate(IrisCoral coral, int variantIndex, RNG rng, IrisData data) {
        int lo = Math.min(coral.getHeightMin(), coral.getHeightMax());
        int hi = Math.max(coral.getHeightMin(), coral.getHeightMax());
        int height = Math.max(2, rng.i(lo, hi + 1));
        long shapeSeed = rng.getSeed() + variantIndex * 31L;

        CoralCanvas canvas = new CoralCanvas();
        switch (coral.getForm()) {
            case BRANCHING -> buildBranching(canvas, coral, height, shapeSeed, rng);
            case FAN -> buildFan(canvas, coral, height, shapeSeed);
            case BRAIN -> buildBrain(canvas, coral, height, shapeSeed);
            case PILLAR -> buildPillar(canvas, coral, height, shapeSeed);
            case TENDRIL -> buildTendril(canvas, coral, height, shapeSeed, rng);
        }

        Map<Vector3i, PlatformBlockState> resolved = resolve(canvas, coral, data);
        return IrisProceduralBlocks.assemble(resolved);
    }

    private static void buildBranching(CoralCanvas canvas, IrisCoral coral, int height, long seed, RNG rng) {
        int stalkTop = Math.max(1, (int) Math.round(height * 0.5));
        for (int y = 0; y < stalkTop; y++) {
            placeWavy(canvas, coral, 0, y, 0, y, seed, CoralCanvas.Role.STRUCTURE);
        }

        int count = Math.max(1, coral.getBranchCount());
        for (int b = 0; b < count; b++) {
            double az = branchAzimuth(coral, b, count, rng);
            int startY = stalkTop - 1 + (int) Math.round((b % 2) * 1.0);
            double[] tip = rasterizeArm(canvas, coral, 0, startY, 0, az,
                    coral.getBranchElevation(), coral.getBranchLength(), seed + b * 131L);

            placeTipCluster(canvas, coral, (int) Math.round(tip[0]), (int) Math.round(tip[1]), (int) Math.round(tip[2]),
                    coral.getTipClusterRadius(), seed + b * 977L);

            if (coral.isSubBranches()) {
                int subCount = Math.max(1, coral.getSubBranchCount());
                for (int s = 0; s < subCount; s++) {
                    double subAz = az + (s - (subCount - 1) / 2.0) * 35.0;
                    double subLen = coral.getBranchLength() * coral.getSubBranchScale();
                    double[] subTip = rasterizeArm(canvas, coral,
                            (int) Math.round(tip[0]), (int) Math.round(tip[1]), (int) Math.round(tip[2]),
                            subAz, coral.getBranchElevation() + 10.0, subLen, seed + b * 131L + s * 17L + 5000L);
                    placeTipCluster(canvas, coral, (int) Math.round(subTip[0]), (int) Math.round(subTip[1]), (int) Math.round(subTip[2]),
                            Math.max(0, coral.getTipClusterRadius() - 1), seed + b * 977L + s * 53L);
                }
            }
        }
    }

    private static void buildFan(CoralCanvas canvas, IrisCoral coral, int height, long seed) {
        int half = Math.max(1, coral.getFanWidth());
        int tippedWidth = -1;
        for (int y = height - 1; y >= 0; y--) {
            double t = y / (double) Math.max(1, height - 1);
            double profile = Math.sin(Math.PI * t);
            int w = (int) Math.round(half * profile);
            for (int dx = -w; dx <= w; dx++) {
                canvas.set(dx, y, 0, CoralCanvas.Role.STRUCTURE);
                if (Math.abs(dx) > tippedWidth) {
                    placeTip(canvas, coral, dx, y, 0, seed + dx);
                }
            }
            tippedWidth = Math.max(tippedWidth, w);
        }
    }

    private static void buildBrain(CoralCanvas canvas, IrisCoral coral, int height, long seed) {
        int r = Math.max(1, coral.getBrainRadius());
        int ry = Math.max(1, Math.min(r, (int) Math.round(height * 0.6)));
        double rough = coral.getBrainRoughness();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = 0; dy <= ry * 2; dy++) {
                    double nx = dx / (double) r;
                    double ny = (dy - ry) / (double) ry;
                    double nz = dz / (double) r;
                    double d = nx * nx + ny * ny + nz * nz;
                    double wobble = (TreeFunctions.valueNoise3D(dx, dy, dz, seed) - 0.5) * 2.0 * rough;
                    if (d <= 1.0 + wobble) {
                        canvas.set(dx, dy, dz, CoralCanvas.Role.STRUCTURE);
                    }
                }
            }
        }
    }

    private static void buildPillar(CoralCanvas canvas, IrisCoral coral, int height, long seed) {
        int r = Math.max(1, coral.getPillarRadius());
        for (int y = 0; y < height; y++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz <= r * r) {
                        canvas.set(dx, y, dz, CoralCanvas.Role.STRUCTURE);
                    }
                }
            }
        }
        placeTipCluster(canvas, coral, 0, height - 1, 0, coral.getTipClusterRadius(), seed);
    }

    private static void buildTendril(CoralCanvas canvas, IrisCoral coral, int height, long seed, RNG rng) {
        int count = Math.max(1, coral.getTendrilCount());
        double radius = Math.max(0.0, coral.getSpread());
        for (int c = 0; c < count; c++) {
            double az = Math.toRadians(c * (360.0 / count));
            double bx = Math.cos(az) * radius * 0.5;
            double bz = Math.sin(az) * radius * 0.5;
            int tx = (int) Math.round(bx);
            int tz = (int) Math.round(bz);
            int top = 0;
            for (int y = 0; y < height; y++) {
                placeWavy(canvas, coral, tx, y, tz, y * (c + 1), seed + c * 311L, CoralCanvas.Role.STRUCTURE);
                top = y;
            }
            placeTip(canvas, coral, tx, top, tz, seed + c * 311L);
        }
    }

    private static double[] rasterizeArm(CoralCanvas canvas, IrisCoral coral, int ox, int oy, int oz,
                                         double azimuthDeg, double elevationDeg, double length, long seed) {
        double azRad = Math.toRadians(azimuthDeg);
        double elRad = Math.toRadians(elevationDeg);
        double dx = length * Math.cos(elRad) * Math.sin(azRad);
        double dy = length * Math.sin(elRad);
        double dz = length * Math.cos(elRad) * Math.cos(azRad);
        int steps = Math.max(1, (int) Math.round(Math.max(Math.abs(dy), Math.max(Math.abs(dx), Math.abs(dz)))));

        double[] tip = new double[]{ox, oy, oz};
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double sway = swayOffset(coral, t * length, seed);
            int x = (int) Math.round(ox + dx * t + sway);
            int y = (int) Math.round(oy + dy * t);
            int z = (int) Math.round(oz + dz * t - sway);
            canvas.set(x, y, z, CoralCanvas.Role.STRUCTURE);
            tip[0] = x;
            tip[1] = y;
            tip[2] = z;
        }
        return tip;
    }

    private static void placeWavy(CoralCanvas canvas, IrisCoral coral, int x, int y, int z, double phase, long seed, CoralCanvas.Role role) {
        double sway = swayOffset(coral, phase, seed);
        int sx = (int) Math.round(sway);
        int sz = (int) Math.round(swayOffset(coral, phase + 53.0, seed + 7L));
        canvas.set(x + sx, y, z + sz, role);
    }

    private static double swayOffset(IrisCoral coral, double phase, long seed) {
        double amount = Math.max(0.0, Math.min(1.0, coral.getSway()));
        if (amount <= 0.0) {
            return 0.0;
        }
        double n = TreeFunctions.valueNoise1D(phase * 0.35, seed) - 0.5;
        return n * 2.0 * amount * (1.0 + coral.getSpread() * 0.25);
    }

    private static void placeTipCluster(CoralCanvas canvas, IrisCoral coral, int cx, int cy, int cz, int radius, long seed) {
        if (radius <= 0) {
            placeTip(canvas, coral, cx, cy, cz, seed);
            return;
        }
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz <= radius * radius) {
                        canvas.set(cx + dx, cy + dy, cz + dz, CoralCanvas.Role.STRUCTURE);
                    }
                }
            }
        }
        placeTip(canvas, coral, cx, cy + radius + 1, cz, seed);
    }

    private static void placeTip(CoralCanvas canvas, IrisCoral coral, int x, int y, int z, long seed) {
        if (!hasTip(coral)) {
            return;
        }
        if (TreeFunctions.valueNoise1D(x * 31.0 + y * 17.0 + z * 13.0, seed) <= coral.getTipChance()) {
            canvas.set(x, y, z, CoralCanvas.Role.TIP);
        }
    }

    private static boolean hasTip(IrisCoral coral) {
        return IrisProceduralBlocks.paletteSet(coral.getTipPalette())
                || (coral.getTipBlock() != null && !coral.getTipBlock().isEmpty());
    }

    private static double branchAzimuth(IrisCoral coral, int index, int count, RNG rng) {
        return switch (coral.getBranchAzimuth()) {
            case GOLDEN_ANGLE -> index * GOLDEN_ANGLE;
            case EVEN -> index * (360.0 / count);
            case RANDOM -> rng.d(0.0, 360.0);
        };
    }

    private static Map<Vector3i, PlatformBlockState> resolve(CoralCanvas canvas, IrisCoral coral, IrisData data) {
        Map<Vector3i, PlatformBlockState> out = new HashMap<>();
        RNG paletteRng = new RNG(coral.getSeed());
        for (Map.Entry<Long, CoralCanvas.Role> entry : canvas.getCells().entrySet()) {
            int[] xyz = CoralCanvas.decode(entry.getKey());
            int x = xyz[0];
            int y = xyz[1];
            int z = xyz[2];
            PlatformBlockState state;
            if (entry.getValue() == CoralCanvas.Role.TIP) {
                state = IrisProceduralBlocks.resolve(coral.getTipBlock(), coral.getTipPalette(), data, x, y, z, paletteRng);
                if (state == null) {
                    state = IrisProceduralBlocks.resolve(coral.getBlock(), coral.getBlockPalette(), data, x, y, z, paletteRng);
                }
            } else {
                state = IrisProceduralBlocks.resolve(coral.getBlock(), coral.getBlockPalette(), data, x, y, z, paletteRng);
            }
            if (state == null) {
                continue;
            }
            if (coral.isWaterlogged() && IrisProceduralBlocks.hasProperty(state, "waterlogged")) {
                try {
                    state = state.withProperty("waterlogged", "true");
                } catch (IllegalArgumentException ignored) {
                }
            }
            out.put(new Vector3i(x, y, z), state);
        }
        return out;
    }
}
