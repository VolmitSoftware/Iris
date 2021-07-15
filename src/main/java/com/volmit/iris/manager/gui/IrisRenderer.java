/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.manager.gui;

import com.volmit.iris.scaffold.engine.Engine;
import com.volmit.iris.util.IrisInterpolation;
import org.bukkit.Material;

import java.awt.image.BufferedImage;

@SuppressWarnings("ClassCanBeRecord")
public class IrisRenderer {
    private final Renderer renderer;

    public IrisRenderer(Renderer renderer) {
        this.renderer = renderer;
    }

    public BufferedImage render(double sx, double sz, double size, int resolution) {
        BufferedImage image = new BufferedImage(resolution, resolution, BufferedImage.TYPE_INT_RGB);
        double x, z;
        int i, j;
        for (i = 0; i < resolution; i++) {
            x = IrisInterpolation.lerp(sx, sx + size, (double) i / (double) (resolution));

            for (j = 0; j < resolution; j++) {
                z = IrisInterpolation.lerp(sz, sz + size, (double) j / (double) (resolution));
                image.setRGB(i, j, renderer.draw(x, z).getRGB());
            }
        }

        return image;
    }

    public void set(double worldX, double worldZ) {
        ((Engine) renderer).getWorld().getBlockAt((int) worldX, 20, (int) worldZ).setType(Material.DIAMOND_BLOCK);
    }
}
