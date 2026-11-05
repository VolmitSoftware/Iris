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
package art.arcane.iris.engine.procedural.part;

import art.arcane.volmlib.util.math.RNG;

/**
 * A blob: a volume around a center whose horizontal cross-section is scaled by a profile from its bottom to its top,
 * an ellipsoid unless the pack shapes it, optionally hollowed into a shell.
 */
final class BlobShape implements PartShape {
    private static final double LARGEST_RADIUS_BLOCKS = 96;
    private static final double MINIMUM_RADIUS_BLOCKS = 0.05;
    private static final int[][] SHELL_DIRECTIONS = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}, {0, -1, 0}};
    private static final int[][] OPEN_SHELL_DIRECTIONS = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}};

    private final double[] center;
    private final double radiusX;
    private final double radiusZ;
    private final double up;
    private final double down;
    private final ProceduralValue profile;
    private final PartSection section;
    private final double shell;
    private final boolean open;
    private final double outward;

    private BlobShape(double[] center, double radiusX, double radiusZ, double up, double down, ProceduralValue profile,
                      PartSection section, double shell, boolean open, double outward) {
        this.center = center;
        this.radiusX = radiusX;
        this.radiusZ = radiusZ;
        this.up = up;
        this.down = down;
        this.profile = profile;
        this.section = section;
        this.shell = shell;
        this.open = open;
        this.outward = outward;
    }

    static BlobShape trace(PartBlob blob, PartSpot spot, RNG rng) {
        double t = spot.parentT();
        double radius = clampRadius(blob.radius().sample(rng, t));
        double up = blob.height() == null ? radius : clampRadius(blob.height().sample(rng, t));
        double down = blob.depth() == null ? up : Math.max(0, Math.min(LARGEST_RADIUS_BLOCKS, blob.depth().sample(rng, t)));
        double shell = Math.max(0, blob.shell().sample(rng, t));
        double outward = spot.hasOutward() ? spot.outward() : rng.d(0, 360);
        return new BlobShape(spot.origin(), Math.max(MINIMUM_RADIUS_BLOCKS, radius * blob.stretchX()),
                Math.max(MINIMUM_RADIUS_BLOCKS, radius * blob.stretchZ()), up, down,
                blob.profile() == null ? null : blob.profile().fix(rng), blob.section(), shell, blob.open(), outward);
    }

    void draw(PartFill fill, double roughness, double jitter) {
        int reachX = (int) Math.ceil(radiusX * (1 + roughness) + 1);
        int reachZ = (int) Math.ceil(radiusZ * (1 + roughness) + 1);
        int cx = (int) Math.round(center[0]);
        int cy = (int) Math.round(center[1]);
        int cz = (int) Math.round(center[2]);
        for (int y = cy - (int) Math.ceil(down) - 1; y <= cy + (int) Math.ceil(up) + 1; y++) {
            for (int x = cx - reachX; x <= cx + reachX; x++) {
                for (int z = cz - reachZ; z <= cz + reachZ; z++) {
                    drawBlock(fill, x, y, z, roughness, jitter);
                }
            }
        }
    }

    private void drawBlock(PartFill fill, int x, int y, int z, double roughness, double jitter) {
        if (!inside(x, y, z, roughness, fill.seed())) {
            return;
        }
        if (shell > 0 && !nearSurface(x, y, z, roughness, fill.seed())) {
            return;
        }
        double dx = x - center[0];
        double dy = y - center[1];
        double dz = z - center[2];
        double edge = edge(dx, dy, dz);
        if (jitter > 0 && edge > 1 - 1 / (Math.max(radiusX, radiusZ) + 1) && PartNoise.hash(x, y, z, fill.seed() + 77L) < jitter * 0.35) {
            return;
        }
        double height = (dy + down + 0.5) / (up + down + 1);
        double falloffEdge = Math.sqrt(sq(dx / (radiusX + 1)) + sq(dy / ((dy >= 0 ? up : down) + 1)) + sq(dz / (radiusZ + 1)));
        fill.place(x, y, z, falloffEdge, height, PartCanvas.Axis.Y);
    }

    private boolean inside(int x, int y, int z, double roughness, long seed) {
        double wobble = roughness <= 0 ? 0 : (PartNoise.hash(x, y, z, seed + 4201L) - 0.5) * roughness * 1.2;
        return edge(x - center[0], y - center[1], z - center[2]) <= 1 + wobble;
    }

    /** Whether the outside lies within the shell's thickness, looking up and sideways, and down unless the shell is open. */
    private boolean nearSurface(int x, int y, int z, double roughness, long seed) {
        int thickness = Math.max(1, (int) Math.round(shell));
        int[][] directions = open ? OPEN_SHELL_DIRECTIONS : SHELL_DIRECTIONS;
        for (int step = 1; step <= thickness; step++) {
            for (int[] direction : directions) {
                if (!inside(x + direction[0] * step, y + direction[1] * step, z + direction[2] * step, roughness, seed)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** How far out a point is, where 1 is the surface; above 1 is outside. */
    private double edge(double dx, double dy, double dz) {
        double vertical = dy >= 0 ? dy / (up + 0.5) : dy / (down + 0.5);
        if (Math.abs(vertical) > 1) {
            return Double.MAX_VALUE;
        }
        double width = profile == null
                ? Math.sqrt(1 - vertical * vertical)
                : Math.max(0, profile.at((dy + down + 0.5) / (up + down + 1)));
        if (width <= 0) {
            return Double.MAX_VALUE;
        }
        return Sections.norm(section, dx / radiusX, dz / radiusZ) / width;
    }

    @Override
    public PartSpot spotAt(double t) {
        double y = center[1] - down + Math.max(0, Math.min(1, t)) * (up + down);
        return new PartSpot(new double[]{center[0], y, center[2]}, Vectors.UP.clone(), t, up + down + 1, outward);
    }

    @Override
    public double length() {
        return up + down + 1;
    }

    @Override
    public double defaultAt() {
        return 1;
    }

    private static double clampRadius(double radius) {
        return Math.max(0, Math.min(LARGEST_RADIUS_BLOCKS, radius));
    }

    private static double sq(double value) {
        return value * value;
    }
}
