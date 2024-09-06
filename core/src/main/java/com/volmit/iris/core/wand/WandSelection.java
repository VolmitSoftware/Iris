/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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
    private static final Particle REDSTONE = E.getOrDefault(Particle.class, "REDSTONE", "DUST");
    private final Cuboid c;
    private final Player p;
    private static final double STEP = 0.10;

    public WandSelection(Cuboid c, Player p) {
        this.c = c;
        this.p = p;
    }

    public void draw() {
        Location playerLoc = p.getLocation();
        double maxDistanceSquared = 256 * 256;
        int particleCount = 0;

        // cube!
        Location[][] edges = {
                {c.getLowerNE(), new Location(c.getWorld(), c.getUpperX() + 1, c.getLowerY(), c.getLowerZ())},
                {c.getLowerNE(), new Location(c.getWorld(), c.getLowerX(), c.getUpperY() + 1, c.getLowerZ())},
                {c.getLowerNE(), new Location(c.getWorld(), c.getLowerX(), c.getLowerY(), c.getUpperZ() + 1)},
                {new Location(c.getWorld(), c.getUpperX() + 1, c.getLowerY(), c.getLowerZ()), new Location(c.getWorld(), c.getUpperX() + 1, c.getUpperY() + 1, c.getLowerZ())},
                {new Location(c.getWorld(), c.getUpperX() + 1, c.getLowerY(), c.getLowerZ()), new Location(c.getWorld(), c.getUpperX() + 1, c.getLowerY(), c.getUpperZ() + 1)},
                {new Location(c.getWorld(), c.getLowerX(), c.getUpperY() + 1, c.getLowerZ()), new Location(c.getWorld(), c.getUpperX() + 1, c.getUpperY() + 1, c.getLowerZ())},
                {new Location(c.getWorld(), c.getLowerX(), c.getUpperY() + 1, c.getLowerZ()), new Location(c.getWorld(), c.getLowerX(), c.getUpperY() + 1, c.getUpperZ() + 1)},
                {new Location(c.getWorld(), c.getLowerX(), c.getLowerY(), c.getUpperZ() + 1), new Location(c.getWorld(), c.getUpperX() + 1, c.getLowerY(), c.getUpperZ() + 1)},
                {new Location(c.getWorld(), c.getLowerX(), c.getLowerY(), c.getUpperZ() + 1), new Location(c.getWorld(), c.getLowerX(), c.getUpperY() + 1, c.getUpperZ() + 1)},
                {new Location(c.getWorld(), c.getUpperX() + 1, c.getUpperY() + 1, c.getLowerZ()), new Location(c.getWorld(), c.getUpperX() + 1, c.getUpperY() + 1, c.getUpperZ() + 1)},
                {new Location(c.getWorld(), c.getLowerX(), c.getUpperY() + 1, c.getUpperZ() + 1), new Location(c.getWorld(), c.getUpperX() + 1, c.getUpperY() + 1, c.getUpperZ() + 1)},
                {new Location(c.getWorld(), c.getUpperX() + 1, c.getLowerY(), c.getUpperZ() + 1), new Location(c.getWorld(), c.getUpperX() + 1, c.getUpperY() + 1, c.getUpperZ() + 1)}
        };

        for (Location[] edge : edges) {
            Vector direction = edge[1].toVector().subtract(edge[0].toVector());
            double length = direction.length();
            direction.normalize();

            for (double d = 0; d <= length; d += STEP) {
                Location particleLoc = edge[0].clone().add(direction.clone().multiply(d));

                if (playerLoc.distanceSquared(particleLoc) > maxDistanceSquared) {
                    continue;
                }

                spawnParticle(particleLoc, playerLoc);
                particleCount++;
            }
        }
    }

    private void spawnParticle(Location particleLoc, Location playerLoc) {
        double accuracy = M.lerpInverse(0, 64 * 64, playerLoc.distanceSquared(particleLoc));
        double dist = M.lerp(0.125, 3.5, accuracy);

        if (M.r(Math.min(dist * 5, 0.9D) * 0.995)) {
            return;
        }

        float hue = (float) (0.5f + (Math.sin((particleLoc.getX() + particleLoc.getY() + particleLoc.getZ() + (p.getTicksLived() / 2f)) / 20f) / 2));
        Color color = Color.getHSBColor(hue, 1, 1);

        p.spawnParticle(REDSTONE, particleLoc,
                0, 0, 0, 0, 1,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(color.getRed(), color.getGreen(), color.getBlue()),
                        (float) dist * 3f));
    }
}
