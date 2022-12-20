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
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.awt.*;

public class WandSelection {
    private final Cuboid c;
    private final Player p;

    public WandSelection(Cuboid c, Player p) {
        this.c = c;
        this.p = p;
    }

    public void draw() {
        double accuracy;
        double dist;

        for (double i = c.getLowerX() - 1; i < c.getUpperX() + 1; i += 0.25) {
            for (double j = c.getLowerY() - 1; j < c.getUpperY() + 1; j += 0.25) {
                for (double k = c.getLowerZ() - 1; k < c.getUpperZ() + 1; k += 0.25) {
                    boolean ii = i == c.getLowerX() || i == c.getUpperX();
                    boolean jj = j == c.getLowerY() || j == c.getUpperY();
                    boolean kk = k == c.getLowerZ() || k == c.getUpperZ();

                    if ((ii && jj) || (ii && kk) || (kk && jj)) {
                        Vector push = new Vector(0, 0, 0);

                        if (i == c.getLowerX()) {
                            push.add(new Vector(-0.55, 0, 0));
                        }

                        if (j == c.getLowerY()) {
                            push.add(new Vector(0, -0.55, 0));
                        }

                        if (k == c.getLowerZ()) {
                            push.add(new Vector(0, 0, -0.55));
                        }

                        if (i == c.getUpperX()) {
                            push.add(new Vector(0.55, 0, 0));
                        }

                        if (j == c.getUpperY()) {
                            push.add(new Vector(0, 0.55, 0));
                        }

                        if (k == c.getUpperZ()) {
                            push.add(new Vector(0, 0, 0.55));
                        }

                        Location a = new Location(c.getWorld(), i, j, k).add(0.5, 0.5, 0.5).add(push);
                        accuracy = M.lerpInverse(0, 64 * 64, p.getLocation().distanceSquared(a));
                        dist = M.lerp(0.125, 3.5, accuracy);

                        if (M.r(M.min(dist * 5, 0.9D) * 0.995)) {
                            continue;
                        }

                        if (ii && jj) {
                            a.add(0, 0, RNG.r.d(-0.3, 0.3));
                        }

                        if (kk && jj) {
                            a.add(RNG.r.d(-0.3, 0.3), 0, 0);
                        }

                        if (ii && kk) {
                            a.add(0, RNG.r.d(-0.3, 0.3), 0);
                        }

                        if (p.getLocation().distanceSquared(a) < 256 * 256) {
                            Color color = Color.getHSBColor((float) (0.5f + (Math.sin((i + j + k + (p.getTicksLived() / 2f)) / (20f)) / 2)), 1, 1);
                            int r = color.getRed();
                            int g = color.getGreen();
                            int b = color.getBlue();

                            p.spawnParticle(Particle.REDSTONE, a.getX(), a.getY(), a.getZ(),
                                    1, 0, 0, 0, 0,
                                    new Particle.DustOptions(org.bukkit.Color.fromRGB(r, g, b),
                                            (float) dist * 3f));
                        }
                    }
                }
            }
        }
    }
}
