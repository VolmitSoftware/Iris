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

import com.volmit.iris.core.gui.components.MapLabels;
import com.volmit.iris.core.gui.components.RenderType;
import com.volmit.iris.core.loader.IrisRegistrant;
import com.volmit.iris.engine.HybridBiomeSelector;
import com.volmit.iris.engine.object.IrisBiome;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The text of the card that follows the cursor: what the drawn map shows there at once, and the rest once probed.
 */
final class MapHoverCard {
    private MapHoverCard() {
    }

    /**
     * @param subject the biome, or region in the region view, the drawn tile shows; null while that tile is drawing
     * @param details the probed facts about the spot, or null until they arrive
     * @param detailed whether Shift is held, which adds keys and files
     */
    static List<String> lines(MapLabels.Label subject, MapLabels.Label region, MapHoverProbe.Details details,
                              RenderType type, boolean detailed, double worldX, double worldZ) {
        List<String> lines = new ArrayList<>();
        lines.add(title(subject, details));
        if (type != RenderType.REGION) {
            String regionName = region != null ? region.name() : details != null ? details.region().getName() : null;
            if (regionName != null) {
                lines.add("Region " + regionName);
            }
        }
        if (details != null) {
            addTerrain(lines, details, type);
        }
        int blockX = MapCanvas.floorWorldCoordinate(worldX);
        int blockZ = MapCanvas.floorWorldCoordinate(worldZ);
        lines.add("Block " + blockX + ", " + blockZ + " · chunk " + (blockX >> 4) + ", " + (blockZ >> 4));
        if (!detailed) {
            lines.add("Shift for keys and files · H highlights");
            return lines;
        }
        addSource(lines, subject);
        if (subject != null && subject.kind() != MapLabels.Kind.REGION) {
            addSource(lines, region);
        }
        lines.add("Region file r." + (blockX >> 9) + "." + (blockZ >> 9) + ".mca");
        return lines;
    }

    private static String title(MapLabels.Label subject, MapHoverProbe.Details details) {
        if (subject != null) {
            return subject.name();
        }
        if (details != null && details.surfaceBiome() != null) {
            return details.surfaceBiome().getName();
        }
        return "Drawing…";
    }

    private static void addTerrain(List<String> lines, MapHoverProbe.Details details, RenderType type) {
        lines.add("Height " + Math.round(details.height()) + " · sea level " + Math.round(details.fluidHeight()));
        String slope = "slope " + Math.round(details.slopeDegrees()) + "°";
        lines.add(details.terrainKind() == null ? capitalize(slope) : details.terrainKind() + " · " + slope);
        IrisBiome surface = details.surfaceBiome();
        IrisBiome cave = details.caveBiome();
        if (type == RenderType.CAVE_LAND && surface != null) {
            lines.add("Surface biome " + surface.getName());
        } else if (type != RenderType.CAVE_LAND && cave != null && cave != surface) {
            lines.add("Cave biome " + cave.getName());
        }
        if (details.vanillaBiome() != null) {
            lines.add("Minecraft biome " + details.vanillaBiome().replace('_', ' '));
        }
        if (details.placement() != null) {
            lines.add(placementText(details.placement()));
        }
        if (details.pickNoise() != null) {
            addPickNoise(lines, details.pickNoise(), surface);
        }
    }

    private static void addPickNoise(List<String> lines, MapHoverProbe.PickNoise noise, IrisBiome surface) {
        if (noise.regionNoise() != null) {
            lines.add("Region noise " + noiseText(noise.regionNoise()));
        }
        if (noise.biomeType() == null) {
            return;
        }
        String biomeNoise = capitalize(noise.biomeType().name().toLowerCase(Locale.ROOT)) + " biome noise "
                + noiseText(noise.biomeNoise());
        IrisBiome picked = noise.noisePick();
        if (picked == null) {
            lines.add(biomeNoise);
        } else if (surface == null || picked.getLoadKey().equals(surface.getLoadKey())) {
            lines.add(biomeNoise + " picks " + picked.getName());
        } else {
            lines.add(biomeNoise + " picks " + picked.getName() + ", then " + surface.getName() + " takes over");
        }
    }

    private static String noiseText(double noise) {
        return String.format(Locale.ROOT, "%.3f", noise);
    }

    // How hybrid terrain chose the base biome; a child biome or a coast can still replace it afterwards.
    private static String placementText(HybridBiomeSelector.Choice choice) {
        String height = "Picked at " + heightAboveSea(choice.placementHeight()) + " · ";
        if (choice.fallback()) {
            return height + "no range covers it, closest range wins";
        }
        if (choice.rivalCount() == 0) {
            return height + "the only biome in range";
        }
        return height + "beat " + choice.rivalCount() + (choice.rivalCount() == 1 ? " other biome" : " other biomes") + " in range";
    }

    private static String heightAboveSea(double height) {
        long rounded = Math.round(height);
        return rounded < 0 ? -rounded + " below sea" : rounded + " above sea";
    }

    private static void addSource(List<String> lines, MapLabels.Label label) {
        if (label == null || !(label.subject() instanceof IrisRegistrant registrant)) {
            return;
        }
        String kind = label.kind() == MapLabels.Kind.REGION ? "Region" : "Biome";
        lines.add(kind + " key " + label.key());
        File file = registrant.getLoadFile();
        if (file != null) {
            File folder = file.getParentFile();
            lines.add(kind + " file " + (folder == null ? "" : folder.getName() + "/") + file.getName());
        }
    }

    private static String capitalize(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
