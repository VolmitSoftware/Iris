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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.volmit.iris.engine.object.IrisBiomeCustom;
import org.bukkit.NamespacedKey;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipFile;

// Works out biome tint colours the way Minecraft does, from the colour maps and vanilla biome files in the client jar.
final class MinecraftBiomeColors {
    private static final String COLOR_MAP_FOLDER = "assets/minecraft/textures/colormap/";
    private static final int COLOR_MAP_SIZE = 256;
    private static final int GRASS_FALLBACK = 0xFF00FF;
    private static final int FOLIAGE_FALLBACK = 0x48B518;
    private static final int DRY_FOLIAGE_FALLBACK = 0x5C3C32;
    private static final float UNKNOWN_BIOME_TEMPERATURE = 0.5F;
    private static final float UNKNOWN_BIOME_DOWNFALL = 0.5F;
    private static final int UNKNOWN_BIOME_WATER = 0x3F76E4;
    private static final int UNPARSEABLE_COLOR = 0x000000;

    private final ZipFile clientJar;
    private final ColorMap grassMap;
    private final ColorMap foliageMap;
    private final ColorMap dryFoliageMap;
    private final Map<NamespacedKey, BiomeTintColors> vanillaBiomes = new ConcurrentHashMap<>();

    private MinecraftBiomeColors(ZipFile clientJar, ColorMap grassMap, ColorMap foliageMap, ColorMap dryFoliageMap) {
        this.clientJar = clientJar;
        this.grassMap = grassMap;
        this.foliageMap = foliageMap;
        this.dryFoliageMap = dryFoliageMap;
    }

    static MinecraftBiomeColors load(ZipFile clientJar) throws IOException {
        return new MinecraftBiomeColors(
                clientJar,
                ColorMap.read(clientJar, "grass.png", GRASS_FALLBACK),
                ColorMap.read(clientJar, "foliage.png", FOLIAGE_FALLBACK),
                ColorMap.read(clientJar, "dry_foliage.png", DRY_FOLIAGE_FALLBACK)
        );
    }

    BiomeTintColors vanilla(NamespacedKey biome) {
        return vanillaBiomes.computeIfAbsent(biome, this::readVanillaBiome);
    }

    // Iris registers custom biomes as data pack biomes built from these fields, see IrisBiomeCustom#generateJson.
    BiomeTintColors custom(IrisBiomeCustom biome) {
        float temperature = (float) biome.getTemperature();
        float downfall = (float) biome.getHumidity();
        return new BiomeTintColors(
                biome.getGrassColor().isEmpty() ? grassMap.colorAt(temperature, downfall) : parseIrisColor(biome.getGrassColor()),
                BiomeTintColors.GrassColorModifier.NONE,
                biome.getFoliageColor().isEmpty() ? foliageMap.colorAt(temperature, downfall) : parseIrisColor(biome.getFoliageColor()),
                dryFoliageMap.colorAt(temperature, downfall),
                parseIrisColor(biome.getWaterColor())
        );
    }

    private BiomeTintColors readVanillaBiome(NamespacedKey biome) {
        JsonObject definition = readBiomeDefinition(biome);
        if (definition == null) {
            return fromClimate(UNKNOWN_BIOME_TEMPERATURE, UNKNOWN_BIOME_DOWNFALL, new JsonObject());
        }
        JsonObject effects = definition.has("effects") ? definition.getAsJsonObject("effects") : new JsonObject();
        return fromClimate(definition.get("temperature").getAsFloat(), definition.get("downfall").getAsFloat(), effects);
    }

    private BiomeTintColors fromClimate(float temperature, float downfall, JsonObject effects) {
        return new BiomeTintColors(
                effects.has("grass_color") ? parseVanillaColor(effects.get("grass_color")) : grassMap.colorAt(temperature, downfall),
                effects.has("grass_color_modifier")
                        ? BiomeTintColors.GrassColorModifier.named(effects.get("grass_color_modifier").getAsString())
                        : BiomeTintColors.GrassColorModifier.NONE,
                effects.has("foliage_color") ? parseVanillaColor(effects.get("foliage_color")) : foliageMap.colorAt(temperature, downfall),
                effects.has("dry_foliage_color") ? parseVanillaColor(effects.get("dry_foliage_color")) : dryFoliageMap.colorAt(temperature, downfall),
                effects.has("water_color") ? parseVanillaColor(effects.get("water_color")) : UNKNOWN_BIOME_WATER
        );
    }

    private JsonObject readBiomeDefinition(NamespacedKey biome) {
        try {
            return ClientJarFiles.readJson(clientJar, "data/" + biome.getNamespace() + "/worldgen/biome/" + biome.getKey() + ".json");
        } catch (IOException | RuntimeException error) {
            return null;
        }
    }

    // Older versions store colours as plain numbers, newer ones as "#rrggbb" strings.
    private static int parseVanillaColor(JsonElement color) {
        if (color.getAsJsonPrimitive().isNumber()) {
            return color.getAsInt() & 0xFFFFFF;
        }
        return Integer.parseInt(color.getAsString().substring(1), 16);
    }

    private static int parseIrisColor(String color) {
        try {
            return Color.decode((color.startsWith("#") ? color : "#" + color).trim()).getRGB() & 0xFFFFFF;
        } catch (RuntimeException error) {
            return UNPARSEABLE_COLOR;
        }
    }

    // Minecraft's ColorMapColorUtil: temperature picks the column and temperature times downfall picks the row.
    private record ColorMap(int[] pixels, int fallback) {
        private static ColorMap read(ZipFile clientJar, String name, int fallback) throws IOException {
            BufferedImage image = ClientJarFiles.readImage(clientJar, COLOR_MAP_FOLDER + name);
            if (image == null) {
                throw new IOException("Minecraft colour map " + name + " is missing");
            }
            int[] pixels = new int[COLOR_MAP_SIZE * COLOR_MAP_SIZE];
            for (int y = 0; y < COLOR_MAP_SIZE; y++) {
                for (int x = 0; x < COLOR_MAP_SIZE; x++) {
                    pixels[y * COLOR_MAP_SIZE + x] = image.getRGB(x, y) & 0xFFFFFF;
                }
            }
            return new ColorMap(pixels, fallback);
        }

        private int colorAt(float temperature, float downfall) {
            double clampedTemperature = Math.clamp(temperature, 0F, 1F);
            double clampedDownfall = Math.clamp(downfall, 0F, 1F) * clampedTemperature;
            int x = (int) ((1D - clampedTemperature) * 255D);
            int y = (int) ((1D - clampedDownfall) * 255D);
            int index = y << 8 | x;
            return index >= pixels.length ? fallback : pixels[index];
        }
    }
}
