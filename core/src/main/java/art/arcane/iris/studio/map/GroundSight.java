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

package art.arcane.iris.studio.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.DoubleBinaryOperator;

/**
 * How the 3D terrain view looks at the ground for one camera: which ground lies under a pixel, and which ground is on
 * screen. The ground's height is asked for anew, so it holds wherever tiles are still coarse or missing.
 *
 * @param left   where the part of the view no panel floats over starts
 * @param floorY the lowest the ground goes, as far as the view knows
 * @param topY   the highest the ground goes, as far as the view knows
 */
record GroundSight(OrbitProjection projection, int left, int width, int height, int floorY, int topY) {
    // Across the ground per step along a line of sight, so no column is stepped over.
    private static final double MARCH_STEP_BLOCKS = 1D;
    private static final int REFINE_STEPS = 10;

    /**
     * The ground drawn at a pixel: the first point down the line of sight that lies below the surface.
     *
     * @param surfaceY the ground's height at a spot, or the water's where that is higher
     */
    Optional<Vec3> groundAt(double screenX, double screenY, DoubleBinaryOperator surfaceY) {
        double sinPitch = projection.towardCamera().y();
        double stepY = MARCH_STEP_BLOCKS * sinPitch / Math.sqrt(Math.max(1E-6, 1D - sinPitch * sinPitch));
        double aboveY = topY;
        for (double y = topY; y >= floorY; y -= stepY) {
            Vec3 point = projection.pointAt(screenX, screenY, y);
            if (surfaceY.applyAsDouble(point.x(), point.z()) >= y) {
                return Optional.of(refine(screenX, screenY, aboveY, y, surfaceY));
            }
            aboveY = y;
        }
        return Optional.empty();
    }

    // Halves the stretch of the line of sight where it passes into the ground until the point sits on the surface.
    private Vec3 refine(double screenX, double screenY, double aboveY, double belowY, DoubleBinaryOperator surfaceY) {
        double above = aboveY;
        double below = belowY;
        for (int step = 0; step < REFINE_STEPS; step++) {
            double middle = (above + below) / 2D;
            Vec3 point = projection.pointAt(screenX, screenY, middle);
            if (surfaceY.applyAsDouble(point.x(), point.z()) >= middle) {
                below = middle;
            } else {
                above = middle;
            }
        }
        Vec3 point = projection.pointAt(screenX, screenY, below);
        return new Vec3(point.x(), surfaceY.applyAsDouble(point.x(), point.z()), point.z());
    }

    /**
     * Spots spread evenly over the ground on screen, about {@code most} of them over the stretch the view can reach. The
     * view looks along parallel lines, so even on the ground is even on screen, but for slopes and hidden valleys. The
     * spots keep to a grid fixed in the world, so turning the view a little doesn't change which spots are counted.
     */
    List<Vec3> groundInView(int most, DoubleBinaryOperator surfaceY) {
        Reach reach = reach();
        double spacing = gridSpacing(reach, most);
        List<Vec3> spots = new ArrayList<>();
        for (double z = Math.floor(reach.minZ() / spacing) * spacing; z <= reach.maxZ(); z += spacing) {
            for (double x = Math.floor(reach.minX() / spacing) * spacing; x <= reach.maxX(); x += spacing) {
                double y = surfaceY.applyAsDouble(x, z);
                if (isOnScreen(x, y, z)) {
                    spots.add(new Vec3(x, y, z));
                }
            }
        }
        return spots;
    }

    private boolean isOnScreen(double x, double y, double z) {
        double screenX = projection.screenX(x, y, z);
        double screenY = projection.screenY(x, y, z);
        return screenX >= left && screenX < width && screenY >= 0D && screenY < height;
    }

    // The ground the view's corners look at, from the floor to the top.
    private Reach reach() {
        double minX = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (double screenX : new double[]{left, width}) {
            for (double screenY : new double[]{0D, height}) {
                for (double groundY : new double[]{floorY, topY}) {
                    Vec3 point = projection.pointAt(screenX, screenY, groundY);
                    minX = Math.min(minX, point.x());
                    minZ = Math.min(minZ, point.z());
                    maxX = Math.max(maxX, point.x());
                    maxZ = Math.max(maxZ, point.z());
                }
            }
        }
        return new Reach(minX, minZ, maxX, maxZ);
    }

    // A whole power of two, so zooming only changes the grid when the spacing has doubled or halved.
    private static double gridSpacing(Reach reach, int most) {
        double area = Math.max(1D, (reach.maxX() - reach.minX()) * (reach.maxZ() - reach.minZ()));
        double spacing = Math.sqrt(area / Math.max(1, most));
        return Math.pow(2D, Math.ceil(Math.log(Math.max(1D, spacing)) / Math.log(2D)));
    }

    private record Reach(double minX, double minZ, double maxX, double maxZ) {
    }
}
