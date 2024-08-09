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

package com.volmit.iris.util.uniques;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;

public class UBufferedImage implements UImage {
    private final BufferedImage buf;

    public UBufferedImage(BufferedImage buf) {
        this.buf = buf;
    }

    @Override
    public int getWidth() {
        return buf.getWidth();
    }

    @Override
    public int getHeight() {
        return buf.getHeight();
    }

    @Override
    public UImage copy() {
        ColorModel cm = buf.getColorModel();
        boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
        WritableRaster raster = buf.copyData(null);
        return new UBufferedImage(new BufferedImage(cm, raster, isAlphaPremultiplied, null));
    }

    @Override
    public Color get(int x, int y) {
        return new Color(buf.getRGB(x, y));
    }

    @Override
    public void set(int x, int y, Color color) {
        try {
            buf.setRGB(x, y, color.getRGB());
        } catch (Throwable e) {

        }
    }
}
