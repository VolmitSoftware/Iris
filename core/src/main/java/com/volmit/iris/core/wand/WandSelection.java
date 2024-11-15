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

package com.volmit.iris.core.wand;

import com.volmit.iris.util.data.Cuboid;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.misc.E;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.awt.*;

public class WandSelection {
    private static final Particle REDSTONE = E.getOrDefault(Particle.class,  "REDSTONE", "DUST");
    private static final double DISTANCE = 256;
    public static final double STEP = 0.25;
    private final Cuboid c;
    private final Player p;

    public WandSelection(Cuboid c, Player p) {
        this.c = c;
        this.p = p;
    }

    public void draw() {
        int lx = c.getLowerX();
        int ux = c.getUpperX();
        int ly = c.getLowerY();
        int uy = c.getUpperY();
        int lz = c.getLowerZ();
        int uz = c.getUpperZ();
        Location loc = p.getLocation();
        double px = loc.getX();
        double py = loc.getY();
        double pz = loc.getZ();

        {
            // edges sx -> ex
            double sx = lx - 1d;
            double ex = ux + 1d;
            double cx = clamp(px, sx, ex);
            for (int y : new int[]{ly, uy}) {
                for (int z : new int[]{lz, uz}) {
                    if (inDistance(px, py, pz, cx, y, z)) {
                        for (double x = sx; x < ex; x += STEP) {
                            renderParticleAt(x, y, z, x == lx || x == ux, true, true);
                        }
                    }
                }
            }
        }
        {
            // edges sy -> ey
            double sy = ly - 1d;
            double ey = uy + 1d;
            double cy = clamp(py, sy, ey);
            for (int x : new int[]{lx, ux}) {
                for (int z : new int[]{lz, uz}) {
                    if (inDistance(px, py, pz, x, cy, z)) {
                        for (double y = sy; y < ey; y += STEP) {
                            renderParticleAt(x, y, z, true, y == ly || y == uy, true);
                        }
                    }
                }
            }
        }
        {
            // edges sz -> ez
            double sz = lz - 1d;
            double ez = uz + 1d;
            double cz = clamp(pz, sz, ez);
            for (int x : new int[]{lx, ux}) {
                for (int y : new int[]{ly, uy}) {
                    if (inDistance(px, py, pz, x, y, cz)) {
                        for (double z = sz; z < ez; z += STEP) {
                            renderParticleAt(x, y, z, true, true, z == lz || z == uz);
                        }
                    }
                }
            }
        }
    }

    private void renderParticleAt(double x, double y, double z, boolean atX, boolean atY, boolean atZ) {
        double accuracy;
        double dist;
        Vector push = new Vector(x, y, z);

        if (x == c.getLowerX()) {
            push.add(new Vector(-0.55, 0, 0));
        }

        if (y == c.getLowerY()) {
            push.add(new Vector(0, -0.55, 0));
        }

        if (z == c.getLowerZ()) {
            push.add(new Vector(0, 0, -0.55));
        }

        if (x == c.getUpperX()) {
            push.add(new Vector(0.55, 0, 0));
        }

        if (y == c.getUpperY()) {
            push.add(new Vector(0, 0.55, 0));
        }

        if (z == c.getUpperZ()) {
            push.add(new Vector(0, 0, 0.55));
        }

        Location a = new Location(c.getWorld(), x, y, z).add(0.5, 0.5, 0.5).add(push);
        accuracy = M.lerpInverse(0, 64 * 64, p.getLocation().distanceSquared(a));
        dist = M.lerp(0.125, 3.5, accuracy);

        if (M.r(M.min(dist * 5, 0.9D) * 0.995)) {
            return;
        }

        if (atX && atY) {
            a.add(0, 0, RNG.r.d(-0.3, 0.3));
        }

        if (atZ && atY) {
            a.add(RNG.r.d(-0.3, 0.3), 0, 0);
        }

        if (atX && atZ) {
            a.add(0, RNG.r.d(-0.3, 0.3), 0);
        }

        if (p.getLocation().distanceSquared(a) < 256 * 256) {
            Color color = Color.getHSBColor((float) (0.5f + (Math.sin((x + y + z + (p.getTicksLived() / 2f)) / (20f)) / 2)), 1, 1);
            int r = color.getRed();
            int g = color.getGreen();
            int b = color.getBlue();

            p.spawnParticle(REDSTONE, a.getX(), a.getY(), a.getZ(),
                    1, 0, 0, 0, 0,
                    new Particle.DustOptions(org.bukkit.Color.fromRGB(r, g, b),
                            (float) dist * 3f));
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.min(Math.max(value, min), max);
    }

    private static boolean inDistance(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = ax - bx;
        double dy = ay - by;
        double dz = az - bz;
        return dx * dx + dy * dy + dz * dz < DISTANCE * DISTANCE;
    }
}
