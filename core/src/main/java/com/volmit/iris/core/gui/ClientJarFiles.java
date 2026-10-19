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


package com.volmit.iris.core.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class ClientJarFiles {
    private ClientJarFiles() {
    }

    static JsonObject readJson(ZipFile clientJar, String path) throws IOException {
        ZipEntry entry = clientJar.getEntry(path);
        if (entry == null) {
            return null;
        }
        try (InputStreamReader reader = new InputStreamReader(clientJar.getInputStream(entry), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    static BufferedImage readImage(ZipFile clientJar, String path) throws IOException {
        ZipEntry entry = clientJar.getEntry(path);
        if (entry == null) {
            return null;
        }
        try (InputStream input = clientJar.getInputStream(entry)) {
            BufferedImage image = ImageIO.read(input);
            return image != null && isGrey(image) ? withGreyKept(image) : image;
        }
    }

    private static boolean isGrey(BufferedImage image) {
        return image.getColorModel().getColorSpace().getType() == ColorSpace.TYPE_GRAY;
    }

    // Java treats grey PNGs, like stone's, as linear light and brightens them when converting to RGB; Minecraft reads
    // the stored values as they are.
    private static BufferedImage withGreyKept(BufferedImage image) {
        Raster raster = image.getRaster();
        boolean hasAlpha = raster.getNumBands() > 1;
        int greyMax = (1 << raster.getSampleModel().getSampleSize(0)) - 1;
        int alphaMax = hasAlpha ? (1 << raster.getSampleModel().getSampleSize(1)) - 1 : 1;
        BufferedImage converted = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int grey = raster.getSample(x, y, 0) * 255 / greyMax;
                int alpha = hasAlpha ? raster.getSample(x, y, 1) * 255 / alphaMax : 255;
                converted.setRGB(x, y, alpha << 24 | grey << 16 | grey << 8 | grey);
            }
        }
        return converted;
    }
}
