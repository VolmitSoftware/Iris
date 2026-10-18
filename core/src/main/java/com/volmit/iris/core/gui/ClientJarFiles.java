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
import java.awt.image.BufferedImage;
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
            return ImageIO.read(input);
        }
    }
}
