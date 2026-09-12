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

package art.arcane.iris.generation.decoration.crystal;

import art.arcane.volmlib.util.math.RNG;

public final class CrystalShardBuilder {
    private static final double GOLDEN_ANGLE = 137.50776405003785;

    private CrystalShardBuilder() {
    }

    public static void build(CrystalCanvas canvas, IrisCrystal crystal, RNG rng) {
        double[] normal = CrystalGeometry.surfaceNormal(crystal.getGrowthSurface());
        double[] u = CrystalGeometry.orthonormalBasisU(normal);
        double[] v = CrystalGeometry.orthonormalBasisV(normal, u);

        int countLo = Math.min(crystal.getShardCountMin(), crystal.getShardCountMax());
        int countHi = Math.max(crystal.getShardCountMin(), crystal.getShardCountMax());
        int shardCount = Math.max(1, rng.i(countLo, countHi + 1));

        int lengthLo = Math.min(crystal.getShardLengthMin(), crystal.getShardLengthMax());
        int lengthHi = Math.max(crystal.getShardLengthMin(), crystal.getShardLengthMax());

        double spreadRadians = Math.toRadians(Math.max(0.0, Math.min(90.0, crystal.getSpreadAngle())));
        double jitterAmount = Math.max(0.0, Math.min(1.0, crystal.getJitter()));
        double emitRadius = Math.max(0.0, crystal.getBaseRadius() * 0.6);

        for (int i = 0; i < shardCount; i++) {
            double azimuth = azimuth(crystal.getDistribution(), i, rng);
            azimuth += rng.d(-Math.PI, Math.PI) * jitterAmount * 0.5;

            double cone = spreadRadians * fanFraction(shardCount, i, rng);
            cone += rng.d(-spreadRadians, spreadRadians) * jitterAmount * 0.5;
            cone = Math.max(0.0, Math.min(Math.PI / 2.0, cone));

            double[] direction = CrystalGeometry.coneDirection(normal, u, v, cone, azimuth);

            double ox = normal[0] * emitRadius + u[0] * Math.cos(azimuth) * emitRadius + v[0] * Math.sin(azimuth) * emitRadius;
            double oy = normal[1] * emitRadius + u[1] * Math.cos(azimuth) * emitRadius + v[1] * Math.sin(azimuth) * emitRadius;
            double oz = normal[2] * emitRadius + u[2] * Math.cos(azimuth) * emitRadius + v[2] * Math.sin(azimuth) * emitRadius;

            int length = Math.max(1, rng.i(lengthLo, lengthHi + 1));
            rasterizeShard(canvas, crystal, ox, oy, oz, direction, length);
        }
    }

    private static void rasterizeShard(CrystalCanvas canvas, IrisCrystal crystal, double ox, double oy, double oz, double[] direction, int length) {
        double baseRadius = Math.max(0.5, crystal.getShardBaseRadius());
        double taper = Math.max(0.0, Math.min(1.0, crystal.getShardTaper()));

        int samples = Math.max(1, length * 3);

        for (int s = 0; s <= samples; s++) {
            double t = s / (double) samples;
            double cx = ox + direction[0] * length * t;
            double cy = oy + direction[1] * length * t;
            double cz = oz + direction[2] * length * t;

            double radius = baseRadius * (1.0 - taper * t);
            if (t >= 1.0) {
                radius = 0.0;
            }
            radius = Math.max(0.0, radius);

            int reach = (int) Math.floor(radius);
            int centerX = (int) Math.round(cx);
            int centerY = (int) Math.round(cy);
            int centerZ = (int) Math.round(cz);

            if (reach <= 0) {
                canvas.set(centerX, centerY, centerZ, CrystalRole.SHARD);
            } else {
                for (int dx = -reach; dx <= reach; dx++) {
                    for (int dy = -reach; dy <= reach; dy++) {
                        for (int dz = -reach; dz <= reach; dz++) {
                            double dist = Math.sqrt((double) dx * dx + (double) dy * dy + (double) dz * dz);
                            if (dist <= radius) {
                                canvas.set(centerX + dx, centerY + dy, centerZ + dz, CrystalRole.SHARD);
                            }
                        }
                    }
                }
            }
        }

        int tipX = (int) Math.round(ox + direction[0] * length);
        int tipY = (int) Math.round(oy + direction[1] * length);
        int tipZ = (int) Math.round(oz + direction[2] * length);
        canvas.set(tipX, tipY, tipZ, CrystalRole.TIP);
    }

    private static double azimuth(IrisCrystalDistribution distribution, int index, RNG rng) {
        if (distribution == IrisCrystalDistribution.GOLDEN_ANGLE) {
            return Math.toRadians(index * GOLDEN_ANGLE);
        }
        return rng.d(0.0, Math.PI * 2.0);
    }

    private static double fanFraction(int count, int index, RNG rng) {
        if (count <= 1) {
            return rng.d(0.0, 1.0);
        }
        double base = index / (double) (count - 1);
        return Math.max(0.0, Math.min(1.0, base * 0.7 + rng.d(0.0, 0.3)));
    }
}
