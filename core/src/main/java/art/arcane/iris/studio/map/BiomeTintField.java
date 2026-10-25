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

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.biome.IrisBiomeCustom;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// The tint Minecraft draws on each column of one Iris biome. Iris picks the Minecraft biome once per chunk
// (see IrisBiomeActuator), and the client averages the colours of the columns around each block.
final class BiomeTintField {
    private static final int MINECRAFT_DEFAULT_BLEND_RADIUS = 2;
    private static final int BLENDED_COLUMN_COUNT = (MINECRAFT_DEFAULT_BLEND_RADIUS * 2 + 1) * (MINECRAFT_DEFAULT_BLEND_RADIUS * 2 + 1);
    private static final int BLENDED_TINT_CACHE_SIZE = 1 << 16;
    private static final int CHUNK_COLORS_CACHE_SIZE = 1 << 10;

    private final IrisBiome biome;
    private final RNG biomeRandom;
    private final MinecraftBiomeColors minecraftColors;
    private final BiomeTintColors sameColorsEverywhere;
    private final Map<IrisBiomeCustom, BiomeTintColors> colorsByCustomBiome = new ConcurrentHashMap<>();
    private final BlendedTint[] blendedTintCache = new BlendedTint[BLENDED_TINT_CACHE_SIZE];
    private final ChunkColors[] chunkColorsCache = new ChunkColors[CHUNK_COLORS_CACHE_SIZE];

    BiomeTintField(IrisBiome biome, RNG biomeRandom, MinecraftBiomeColors minecraftColors) {
        this.biome = biome;
        this.biomeRandom = biomeRandom;
        this.minecraftColors = minecraftColors;
        BiomeTintColors onlyColors = onlyPossibleColors();
        this.sameColorsEverywhere = onlyColors == null || onlyColors.variesByPosition() ? null : onlyColors;
    }

    int colorAt(BiomeTint tint, int blockX, int blockZ) {
        if (sameColorsEverywhere != null) {
            return sameColorsEverywhere.colorAt(tint, blockX, blockZ);
        }
        int cacheSlot = cacheSlot(blockX, blockZ, tint.ordinal(), BLENDED_TINT_CACHE_SIZE);
        BlendedTint cached = blendedTintCache[cacheSlot];
        if (cached != null && cached.x() == blockX && cached.z() == blockZ && cached.tint() == tint) {
            return cached.color();
        }
        int color = blendedColor(tint, blockX, blockZ);
        blendedTintCache[cacheSlot] = new BlendedTint(blockX, blockZ, tint, color);
        return color;
    }

    // Minecraft's ClientLevel#calculateBlockTint at the default biome blend radius.
    private int blendedColor(BiomeTint tint, int blockX, int blockZ) {
        int red = 0;
        int green = 0;
        int blue = 0;
        for (int x = blockX - MINECRAFT_DEFAULT_BLEND_RADIUS; x <= blockX + MINECRAFT_DEFAULT_BLEND_RADIUS; x++) {
            for (int z = blockZ - MINECRAFT_DEFAULT_BLEND_RADIUS; z <= blockZ + MINECRAFT_DEFAULT_BLEND_RADIUS; z++) {
                int color = chunkColors(x >> 4, z >> 4).colorAt(tint, x, z);
                red += (color >>> 16) & 0xFF;
                green += (color >>> 8) & 0xFF;
                blue += color & 0xFF;
            }
        }
        return (red / BLENDED_COLUMN_COUNT) << 16 | (green / BLENDED_COLUMN_COUNT) << 8 | (blue / BLENDED_COLUMN_COUNT);
    }

    private BiomeTintColors chunkColors(int chunkX, int chunkZ) {
        int cacheSlot = cacheSlot(chunkX, chunkZ, 0, CHUNK_COLORS_CACHE_SIZE);
        ChunkColors cached = chunkColorsCache[cacheSlot];
        if (cached != null && cached.chunkX() == chunkX && cached.chunkZ() == chunkZ) {
            return cached.colors();
        }
        BiomeTintColors colors = colorsForChunkAt(chunkX << 4, chunkZ << 4);
        chunkColorsCache[cacheSlot] = new ChunkColors(chunkX, chunkZ, colors);
        return colors;
    }

    private BiomeTintColors colorsForChunkAt(int originX, int originZ) {
        if (biome.isCustom()) {
            return customColors(biome.getCustomBiome(biomeRandom, originX, 0, originZ));
        }
        return vanillaColors(biome.getSkyBiomeKey(biomeRandom, originX, 0, originZ));
    }

    // Follows the same choices as IrisBiome#getSkyBiomeKey and #getCustomBiome, returning null when chunks can differ.
    private BiomeTintColors onlyPossibleColors() {
        if (biome.isCustom()) {
            KList<IrisBiomeCustom> variants = biome.getCustomDerivitives();
            return variants.size() == 1 ? customColors(variants.get(0)) : null;
        }
        KList<String> skyBiomes = biome.getBiomeSkyScatter();
        if (!skyBiomes.isEmpty()) {
            return skyBiomes.size() == 1 ? vanillaColors(skyBiomes.get(0)) : null;
        }
        KList<String> groundBiomes = biome.getBiomeScatter();
        if (groundBiomes.isEmpty()) {
            return vanillaColors(biome.getDerivativeKey());
        }
        return groundBiomes.size() == 1 ? vanillaColors(groundBiomes.get(0)) : null;
    }

    private BiomeTintColors customColors(IrisBiomeCustom customBiome) {
        return colorsByCustomBiome.computeIfAbsent(customBiome, minecraftColors::custom);
    }

    private BiomeTintColors vanillaColors(String vanillaBiomeKey) {
        return minecraftColors.vanilla(vanillaBiomeKey.trim().toLowerCase(Locale.ROOT));
    }

    private static int cacheSlot(int x, int z, int salt, int cacheSize) {
        long hash = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
        return (int) ((hash ^ (hash >>> 29)) & (cacheSize - 1));
    }

    private record BlendedTint(int x, int z, BiomeTint tint, int color) {
    }

    private record ChunkColors(int chunkX, int chunkZ, BiomeTintColors colors) {
    }
}
