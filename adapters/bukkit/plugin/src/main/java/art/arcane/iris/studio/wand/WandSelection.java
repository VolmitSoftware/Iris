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

package art.arcane.iris.studio.wand;

import art.arcane.volmlib.util.data.Cuboid;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.awt.Color;

import static art.arcane.iris.platform.bukkit.registry.Particles.REDSTONE;

public class WandSelection {
    private static final double STEP = 0.10;
    private static final double MAX_DISTANCE = 256D;
    private static final double MAX_DISTANCE_SQUARED = MAX_DISTANCE * MAX_DISTANCE;

    private final Cuboid c;
    private final Player p;

    public WandSelection(Cuboid c, Player p) {
        this.c = c;
        this.p = p;
    }

    public void draw() {
        Location playerLoc = p.getLocation();
        if (c.getWorld() == null || !c.getWorld().equals(playerLoc.getWorld())) {
            return;
        }

        // cube!
        double minX = c.getLowerX();
        double minY = c.getLowerY();
        double minZ = c.getLowerZ();
        double maxX = c.getUpperX() + 1D;
        double maxY = c.getUpperY() + 1D;
        double maxZ = c.getUpperZ() + 1D;
        double playerX = playerLoc.getX();
        double playerY = playerLoc.getY();
        double playerZ = playerLoc.getZ();

        drawX(minX, maxX, minY, minZ, playerX, playerY, playerZ);
        drawX(minX, maxX, maxY, minZ, playerX, playerY, playerZ);
        drawX(minX, maxX, minY, maxZ, playerX, playerY, playerZ);
        drawX(minX, maxX, maxY, maxZ, playerX, playerY, playerZ);
        drawY(minY, maxY, minX, minZ, playerX, playerY, playerZ);
        drawY(minY, maxY, maxX, minZ, playerX, playerY, playerZ);
        drawY(minY, maxY, minX, maxZ, playerX, playerY, playerZ);
        drawY(minY, maxY, maxX, maxZ, playerX, playerY, playerZ);
        drawZ(minZ, maxZ, minX, minY, playerX, playerY, playerZ);
        drawZ(minZ, maxZ, maxX, minY, playerX, playerY, playerZ);
        drawZ(minZ, maxZ, minX, maxY, playerX, playerY, playerZ);
        drawZ(minZ, maxZ, maxX, maxY, playerX, playerY, playerZ);
    }

    private void drawX(double start, double end, double y, double z, double playerX, double playerY, double playerZ) {
        double fixedDistanceSquared = square(playerY - y) + square(playerZ - z);
        drawAxis(start, end, playerX, fixedDistanceSquared, (double coordinate, double distanceSquared) ->
                spawnParticle(coordinate, y, z, distanceSquared));
    }

    private void drawY(double start, double end, double x, double z, double playerX, double playerY, double playerZ) {
        double fixedDistanceSquared = square(playerX - x) + square(playerZ - z);
        drawAxis(start, end, playerY, fixedDistanceSquared, (double coordinate, double distanceSquared) ->
                spawnParticle(x, coordinate, z, distanceSquared));
    }

    private void drawZ(double start, double end, double x, double y, double playerX, double playerY, double playerZ) {
        double fixedDistanceSquared = square(playerX - x) + square(playerY - y);
        drawAxis(start, end, playerZ, fixedDistanceSquared, (double coordinate, double distanceSquared) ->
                spawnParticle(x, y, coordinate, distanceSquared));
    }

    private void drawAxis(double start, double end, double playerCoordinate, double fixedDistanceSquared, AxisParticle particle) {
        if (fixedDistanceSquared > MAX_DISTANCE_SQUARED) {
            return;
        }
        double visibleRadius = Math.sqrt(MAX_DISTANCE_SQUARED - fixedDistanceSquared);
        double visibleStart = Math.max(start, playerCoordinate - visibleRadius);
        double visibleEnd = Math.min(end, playerCoordinate + visibleRadius);
        if (visibleStart > visibleEnd) {
            return;
        }
        int firstSample = (int) Math.max(0D, Math.ceil((visibleStart - start) / STEP));
        int lastSample = (int) Math.floor((visibleEnd - start) / STEP);
        for (int index = firstSample; index <= lastSample; index++) {
            double coordinate = start + index * STEP;
            double distanceSquared = fixedDistanceSquared + square(playerCoordinate - coordinate);
            particle.spawn(coordinate, distanceSquared);
        }
    }

    private void spawnParticle(double x, double y, double z, double distanceSquared) {
        double accuracy = M.lerpInverse(0, 64 * 64, distanceSquared);
        double dist = M.lerp(0.125, 3.5, accuracy);

        if (M.r(Math.min(dist * 5, 0.9D) * 0.995)) {
            return;
        }

        float hue = (float) (0.5f + (Math.sin((x + y + z + (p.getTicksLived() / 2f)) / 20f) / 2));
        Color color = Color.getHSBColor(hue, 1, 1);

        p.spawnParticle(REDSTONE, x, y, z,
                0, 0, 0, 0, 1,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(color.getRed(), color.getGreen(), color.getBlue()),
                        Math.clamp((float) dist * 3f, 0.01f, 4f)));
    }

    private static double square(double value) {
        return value * value;
    }

    @FunctionalInterface
    private interface AxisParticle {
        void spawn(double coordinate, double distanceSquared);
    }
}
