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

package art.arcane.iris.generation.decoration.formation;

import art.arcane.iris.generation.decoration.tree.TreeFunctions;
import art.arcane.volmlib.util.math.Vector3i;
import art.arcane.volmlib.util.math.RNG;

import java.util.HashMap;
import java.util.Map;

public final class FormationShapeBuilder {
    private FormationShapeBuilder() {
    }

    public static void spire(FormationCanvas canvas, IrisFormation f, int height, double baseRadius, RNG rng) {
        double topRadius = f.getTopWidth();
        column(canvas, f, height, baseRadius, topRadius, 0, 0, rng, true);
        double lean = Math.toRadians(f.getLean());
        double azimuth = Math.toRadians(f.getLeanAzimuth());
        int previousX = 0;
        int previousZ = 0;
        for (int y = 0; y < height; y++) {
            double shear = Math.tan(lean) * y;
            int centerX = (int) Math.round(Math.cos(azimuth) * shear);
            int centerZ = (int) Math.round(Math.sin(azimuth) * shear);
            boolean cap = y >= height - 2;
            for (int x = Math.min(previousX, centerX); x <= Math.max(previousX, centerX); x++) {
                setSpireCenter(canvas, x, y, previousZ, cap);
            }
            for (int z = Math.min(previousZ, centerZ); z <= Math.max(previousZ, centerZ); z++) {
                setSpireCenter(canvas, centerX, y, z, cap);
            }
            previousX = centerX;
            previousZ = centerZ;
        }
    }

    public static void seaStack(FormationCanvas canvas, IrisFormation f, int height, double baseRadius, RNG rng) {
        double topRadius = Math.max(1.0, baseRadius * 0.55);
        if (f.getTopWidth() > 0) {
            topRadius = f.getTopWidth();
        }
        column(canvas, f, height, baseRadius, topRadius, 0, 0, rng, true);
    }

    public static void hoodoo(FormationCanvas canvas, IrisFormation f, int height, double baseRadius, RNG rng) {
        double topRadius = f.getTopWidth() > 0 ? f.getTopWidth() : Math.max(1.0, baseRadius * 0.7);
        column(canvas, f, height, baseRadius, topRadius, 0, 0, rng, false);

        int capRadius = f.getHoodooCapRadius();
        if (capRadius <= 0) {
            return;
        }
        int capHeight = Math.max(1, f.getHoodooCapHeight());
        double lean = Math.toRadians(f.getLean());
        double azimuth = Math.toRadians(f.getLeanAzimuth());
        double shear = Math.tan(lean) * height;
        double topOffX = Math.cos(azimuth) * shear;
        double topOffZ = Math.sin(azimuth) * shear;
        double wideRadius = topRadius + capRadius;

        for (int cy = 0; cy < capHeight; cy++) {
            int y = height + cy;
            double shrink = capHeight <= 1 ? 0 : (cy / (double) (capHeight - 1)) * 0.5;
            double r = wideRadius * (1.0 - shrink);
            disc(canvas, f, (int) Math.round(topOffX), y, (int) Math.round(topOffZ), r, true, rng);
        }
    }

    public static void boulder(FormationCanvas canvas, IrisFormation f, int height, double baseRadius, RNG rng) {
        double rx = baseRadius;
        double ry = Math.max(2.0, height * 0.5);
        double rz = baseRadius * (0.8 + rng.d(0.0, 0.3));
        double roughness = Math.max(0.0, Math.min(1.0, f.getRoughness()));
        long noiseSeed = f.getSeed() + 4201L;

        int maxR = (int) Math.ceil(Math.max(rx, Math.max(ry, rz))) + 2;
        for (int x = -maxR; x <= maxR; x++) {
            for (int y = 0; y <= (int) Math.ceil(ry * 2) + 1; y++) {
                for (int z = -maxR; z <= maxR; z++) {
                    double nx = x / rx;
                    double ny = (y - ry) / ry;
                    double nz = z / rz;
                    double d = nx * nx + ny * ny + nz * nz;
                    double wobble = (TreeFunctions.valueNoise3D(x, y, z, noiseSeed) - 0.5) * roughness * 0.9;
                    if (d + wobble <= 1.0) {
                        canvas.setBody(x, y, z);
                    }
                }
            }
        }
    }

    public static void arch(FormationCanvas canvas, IrisFormation f, int height, double baseRadius, RNG rng) {
        double span = Math.max(2.0, f.getArchSpan());
        double tubeRadius = Math.max(0.75, f.getArchThickness() * 0.5);
        double asymmetry = Math.max(0.0, Math.min(1.0, f.getArchAsymmetry()));
        double leftFootX = -span * 0.5 - tubeRadius - rng.d(0.0, span * 0.16) * asymmetry;
        double rightFootX = span * 0.5 + tubeRadius + rng.d(0.0, span * 0.16) * asymmetry;
        double leftFootZ = rng.d(-tubeRadius * 0.45, tubeRadius * 0.45) * asymmetry;
        double rightFootZ = rng.d(-tubeRadius * 0.45, tubeRadius * 0.45) * asymmetry;
        double crownZ = rng.d(-span * 0.2, span * 0.2) * asymmetry;
        double crownShiftX = rng.d(-span * 0.12, span * 0.12) * asymmetry;
        double depthTwist = rng.d(-span * 0.1, span * 0.1) * asymmetry;
        double crownPosition = 0.5 + rng.d(-0.12, 0.12) * asymmetry;
        double leftSteepness = 0.58 + rng.d(-0.16, 0.16) * asymmetry;
        double rightSteepness = 0.58 + rng.d(-0.16, 0.16) * asymmetry;
        double radiusWave = rng.d(-0.22, 0.22) * asymmetry;
        double radiusPhase = rng.d(0.0, Math.PI * 2.0);
        double crownCenterY = Math.max(tubeRadius + 2.0, height - 1.0 - tubeRadius);
        double footRadius = tubeRadius + Math.min(tubeRadius * 0.45, baseRadius * 0.18);
        long noiseSeed = rng.nextLong();
        int steps = Math.max(24, (int) Math.ceil((rightFootX - leftFootX + height) * 4.0));

        ball(canvas, f, (int) Math.round(leftFootX), (int) Math.round(footRadius),
                (int) Math.round(leftFootZ), footRadius, false, noiseSeed, rng);
        ball(canvas, f, (int) Math.round(rightFootX), (int) Math.round(footRadius),
                (int) Math.round(rightFootZ), footRadius, false, noiseSeed, rng);

        for (int step = 0; step <= steps; step++) {
            double t = step / (double) steps;
            double normalizedRise;
            double steepness;
            if (t <= crownPosition) {
                normalizedRise = Math.sin((t / crownPosition) * Math.PI * 0.5);
                steepness = leftSteepness;
            } else {
                normalizedRise = Math.sin(((1.0 - t) / (1.0 - crownPosition)) * Math.PI * 0.5);
                steepness = rightSteepness;
            }
            double rise = Math.pow(Math.max(0.0, normalizedRise), steepness);
            double x = leftFootX + (rightFootX - leftFootX) * t
                    + Math.sin(Math.PI * t) * crownShiftX;
            double inverse = 1.0 - t;
            double z = inverse * inverse * leftFootZ + 2.0 * inverse * t * crownZ + t * t * rightFootZ
                    + Math.sin(Math.PI * 2.0 * t) * depthTwist;
            double y = tubeRadius + (crownCenterY - tubeRadius) * rise;
            double radius = tubeRadius * (1.0 + radiusWave * Math.sin(Math.PI * t)
                    * Math.sin(Math.PI * 2.0 * t + radiusPhase));
            radius = Math.max(tubeRadius * 0.68, Math.min(tubeRadius * 1.32, radius));
            ball(canvas, f, (int) Math.round(x), (int) Math.round(y), (int) Math.round(z),
                    radius, false, noiseSeed, rng);
        }
        if (asymmetry == 0.0) {
            mirrorAcrossX(canvas);
        }
    }

    public static void basaltColumns(FormationCanvas canvas, IrisFormation f, int height, double baseRadius, RNG rng) {
        int count = Math.max(2, f.getBasaltColumns());
        int colRadius = Math.max(1, f.getBasaltColumnRadius());
        double variance = Math.max(0.0, Math.min(1.0, f.getBasaltHeightVariance()));
        int spread = (int) Math.ceil(baseRadius);

        for (int c = 0; c < count; c++) {
            double angle = rng.d(0.0, Math.PI * 2.0);
            double dist = rng.d(0.0, spread);
            int ox = (int) Math.round(Math.cos(angle) * dist);
            int oz = (int) Math.round(Math.sin(angle) * dist);
            double hVar = 1.0 - variance + rng.d(0.0, variance * 2.0);
            int colHeight = Math.max(3, (int) Math.round(height * hVar));

            for (int y = 0; y < colHeight; y++) {
                for (int x = -colRadius; x <= colRadius; x++) {
                    for (int z = -colRadius; z <= colRadius; z++) {
                        if (Math.abs(x) + Math.abs(z) > colRadius) {
                            continue;
                        }
                        canvas.setBody(ox + x, y, oz + z);
                    }
                }
            }
            if (colRadius > 0) {
                canvas.setCap(ox, colHeight - 1, oz);
            }
        }
    }

    public static void iceberg(FormationCanvas canvas, IrisFormation f, int height, double baseRadius, RNG rng) {
        long noiseSeed = rng.nextLong();
        double bodyHeight = Math.max(2.0, height * 0.42);
        double radiusX = baseRadius * rng.d(1.15, 1.5);
        double radiusZ = baseRadius * rng.d(1.0, 1.35);
        ellipsoid(canvas, radiusX, bodyHeight, radiusZ, bodyHeight * 0.36, noiseSeed, f.getRoughness());

        int peaks = Math.max(1, f.getIcebergPeaks());
        double phase = rng.d(0.0, Math.PI * 2.0);
        for (int i = 0; i < peaks; i++) {
            double angle = phase + (Math.PI * 2.0 * i / peaks) + rng.d(-0.35, 0.35);
            double distance = rng.d(0.0, baseRadius * 0.62);
            int offsetX = (int) Math.round(Math.cos(angle) * distance);
            int offsetZ = (int) Math.round(Math.sin(angle) * distance);
            int peakHeight = Math.max(3, (int) Math.round(height * rng.d(0.58, 1.0)));
            double peakRadius = Math.max(1.0, baseRadius * rng.d(0.28, 0.58));
            double leanDistance = rng.d(0.0, Math.max(1.0, baseRadius * 0.45));
            taperedPeak(canvas, f, peakHeight, peakRadius, offsetX, offsetZ, angle, leanDistance, rng);
        }
    }

    public static void fissure(FormationCanvas canvas, IrisFormation f, int height, double baseRadius, RNG rng) {
        int count = Math.max(2, f.getFractureCount());
        double separation = Math.max(1.0, f.getFractureSeparation());
        double shardRadius = Math.max(1.0, baseRadius / Math.max(1.6, count * 0.72));
        double phase = rng.d(0.0, Math.PI * 2.0);

        for (int i = 0; i < count; i++) {
            double lane = i - ((count - 1) / 2.0);
            double offset = lane * (shardRadius * 2.0 + separation);
            int offsetX = (int) Math.round(Math.cos(phase) * offset);
            int offsetZ = (int) Math.round(Math.sin(phase) * offset);
            double outwardAngle = lane < 0 ? phase + Math.PI : phase;
            int shardHeight = Math.max(3, (int) Math.round(height * rng.d(0.62, 1.0)));
            double leanDistance = rng.d(separation * 0.4, separation + baseRadius * 0.8);
            taperedPeak(canvas, f, shardHeight, shardRadius, offsetX, offsetZ, outwardAngle, leanDistance, rng);
        }
    }

    public static void spiral(FormationCanvas canvas, IrisFormation f, int height, double baseRadius, RNG rng) {
        int steps = Math.max(12, height * 4);
        double turns = Math.max(0.25, f.getSpiralTurns());
        double startRadius = Math.max(1.0, f.getSpiralRadius());
        double startThickness = Math.max(1.0, f.getSpiralThickness());
        double phase = rng.d(0.0, Math.PI * 2.0);
        long noiseSeed = rng.nextLong();

        for (int step = 0; step <= steps; step++) {
            double t = step / (double) steps;
            double angle = phase + Math.PI * 2.0 * turns * t;
            double radius = startRadius * (1.0 - 0.72 * t);
            int x = (int) Math.round(Math.cos(angle) * radius);
            int y = (int) Math.round(t * (height - 1));
            int z = (int) Math.round(Math.sin(angle) * radius);
            double thickness = Math.max(0.75, startThickness * (1.0 - 0.48 * t));
            ball(canvas, f, x, y, z, thickness, step == steps, noiseSeed, rng);
        }

        double footRadius = Math.max(1.0, Math.min(baseRadius, startThickness + 1.0));
        disc(canvas, f, (int) Math.round(Math.cos(phase) * startRadius), 0,
                (int) Math.round(Math.sin(phase) * startRadius), footRadius, false, rng);
    }

    public static void overhang(FormationCanvas canvas, IrisFormation f, int height, double baseRadius, RNG rng) {
        int steps = Math.max(12, height * 4);
        double reach = Math.max(1.0, f.getOverhangReach());
        double drop = Math.max(0.0, f.getOverhangDrop());
        double phase = Math.toRadians(f.getLeanAzimuth()) + rng.d(-0.18, 0.18);
        double trunkHeight = Math.max(3.0, height * 0.62);
        long noiseSeed = rng.nextLong();

        for (int step = 0; step <= steps; step++) {
            double t = step / (double) steps;
            double extension;
            double y;
            if (t <= 0.58) {
                double rise = t / 0.58;
                extension = reach * 0.16 * rise * rise;
                y = trunkHeight * rise;
            } else {
                double hook = (t - 0.58) / 0.42;
                extension = reach * (0.16 + 0.84 * Math.sin(hook * Math.PI * 0.5));
                y = trunkHeight + Math.sin(hook * Math.PI) * height * 0.18 - drop * hook * hook;
            }
            int x = (int) Math.round(Math.cos(phase) * extension);
            int z = (int) Math.round(Math.sin(phase) * extension);
            double thickness = Math.max(0.9, baseRadius * (1.0 - 0.64 * t));
            ball(canvas, f, x, Math.max(0, (int) Math.round(y)), z, thickness, step == steps, noiseSeed, rng);
        }
    }

    private static void column(FormationCanvas canvas, IrisFormation f, int height, double baseRadius, double topRadius, int extraBaseX, int extraBaseZ, RNG rng, boolean capTop) {
        double lean = Math.toRadians(f.getLean());
        double azimuth = Math.toRadians(f.getLeanAzimuth());
        double roughness = Math.max(0.0, Math.min(1.0, f.getRoughness()));
        double jitter = Math.max(0.0, Math.min(1.0, f.getJitter()));
        long noiseSeed = f.getSeed() + 9001L;

        for (int y = 0; y < height; y++) {
            double t = height <= 1 ? 1.0 : (y / (double) (height - 1));
            double radius = FormationProfiles.radiusAt(f, baseRadius, topRadius, t);
            double shear = Math.tan(lean) * y;
            int cx = extraBaseX + (int) Math.round(Math.cos(azimuth) * shear);
            int cz = extraBaseZ + (int) Math.round(Math.sin(azimuth) * shear);
            boolean cap = capTop && y >= height - 2;
            ringDisc(canvas, f, cx, y, cz, radius, roughness, jitter, noiseSeed, cap, rng);
        }
    }

    private static void setSpireCenter(FormationCanvas canvas, int x, int y, int z, boolean cap) {
        if (cap) {
            canvas.setCap(x, y, z);
        } else {
            canvas.setBody(x, y, z);
        }
    }

    private static void ringDisc(FormationCanvas canvas, IrisFormation f, int cx, int y, int cz, double radius, double roughness, double jitter, long noiseSeed, boolean cap, RNG rng) {
        int r = (int) Math.ceil(radius) + 1;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                double dist = Math.sqrt(x * x + z * z);
                double perturb = (TreeFunctions.valueNoise3D(cx + x, y, cz + z, noiseSeed) - 0.5) * roughness * (radius + 1.0);
                double effective = radius + perturb;
                if (dist <= effective) {
                    if (jitter > 0 && dist > effective - 1.0 && rng.chance(jitter * 0.5)) {
                        continue;
                    }
                    if (cap) {
                        canvas.setCap(cx + x, y, cz + z);
                    } else {
                        canvas.setBody(cx + x, y, cz + z);
                    }
                }
            }
        }
    }

    private static void disc(FormationCanvas canvas, IrisFormation f, int cx, int y, int cz, double radius, boolean cap, RNG rng) {
        int r = (int) Math.ceil(radius) + 1;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (Math.sqrt(x * x + z * z) <= radius) {
                    if (cap) {
                        canvas.setCap(cx + x, y, cz + z);
                    } else {
                        canvas.setBody(cx + x, y, cz + z);
                    }
                }
            }
        }
    }

    private static void taperedPeak(FormationCanvas canvas, IrisFormation f, int height, double radius,
                                    int offsetX, int offsetZ, double leanAngle, double leanDistance, RNG rng) {
        long noiseSeed = rng.nextLong();
        double roughness = Math.max(0.0, Math.min(1.0, f.getRoughness()));
        double jitter = Math.max(0.0, Math.min(1.0, f.getJitter()));
        for (int y = 0; y < height; y++) {
            double t = height <= 1 ? 1.0 : y / (double) (height - 1);
            int x = offsetX + (int) Math.round(Math.cos(leanAngle) * leanDistance * t * t);
            int z = offsetZ + (int) Math.round(Math.sin(leanAngle) * leanDistance * t * t);
            double layerRadius = Math.max(0.45, radius * Math.pow(1.0 - t, 0.72));
            ringDisc(canvas, f, x, y, z, layerRadius, roughness, jitter, noiseSeed, y >= height - 2, rng);
        }
    }

    private static void ellipsoid(FormationCanvas canvas, double radiusX, double radiusY, double radiusZ,
                                  double centerY, long noiseSeed, double configuredRoughness) {
        double roughness = Math.max(0.0, Math.min(1.0, configuredRoughness));
        int maxX = (int) Math.ceil(radiusX) + 1;
        int maxY = (int) Math.ceil(radiusY) + 1;
        int maxZ = (int) Math.ceil(radiusZ) + 1;
        for (int x = -maxX; x <= maxX; x++) {
            for (int y = 0; y <= maxY; y++) {
                for (int z = -maxZ; z <= maxZ; z++) {
                    double nx = x / radiusX;
                    double ny = (y - centerY) / radiusY;
                    double nz = z / radiusZ;
                    double distance = nx * nx + ny * ny + nz * nz;
                    double wobble = (TreeFunctions.valueNoise3D(x, y, z, noiseSeed) - 0.5) * roughness * 0.55;
                    if (distance + wobble <= 1.0) {
                        canvas.setBody(x, y, z);
                    }
                }
            }
        }
    }

    private static void ball(FormationCanvas canvas, IrisFormation f, int centerX, int centerY, int centerZ,
                             double radius, boolean cap, long noiseSeed, RNG rng) {
        int extent = (int) Math.ceil(radius);
        double roughness = Math.max(0.0, Math.min(1.0, f.getRoughness()));
        double jitter = Math.max(0.0, Math.min(1.0, f.getJitter()));
        for (int x = -extent; x <= extent; x++) {
            for (int y = -extent; y <= extent; y++) {
                for (int z = -extent; z <= extent; z++) {
                    double distance = Math.sqrt(x * x + y * y + z * z);
                    double perturb = (TreeFunctions.valueNoise3D(centerX + x, centerY + y, centerZ + z, noiseSeed) - 0.5)
                            * roughness * Math.max(1.0, radius * 0.6);
                    double effectiveRadius = radius + perturb;
                    if (distance > effectiveRadius + 0.2) {
                        continue;
                    }
                    if (jitter > 0.0 && distance > effectiveRadius - 0.75 && rng.chance(jitter * 0.35)) {
                        continue;
                    }
                    int targetY = centerY + y;
                    if (targetY < 0) {
                        continue;
                    }
                    if (cap) {
                        canvas.setCap(centerX + x, targetY, centerZ + z);
                    } else {
                        canvas.setBody(centerX + x, targetY, centerZ + z);
                    }
                }
            }
        }
    }

    private static void mirrorAcrossX(FormationCanvas canvas) {
        Map<Vector3i, FormationCanvas.Role> cells = new HashMap<>(canvas.getCells());
        for (Map.Entry<Vector3i, FormationCanvas.Role> entry : cells.entrySet()) {
            Vector3i position = entry.getKey();
            if (entry.getValue() == FormationCanvas.Role.CAP) {
                canvas.setCap(-position.getBlockX(), position.getBlockY(), position.getBlockZ());
            } else {
                canvas.setBody(-position.getBlockX(), position.getBlockY(), position.getBlockZ());
            }
        }
    }
}
