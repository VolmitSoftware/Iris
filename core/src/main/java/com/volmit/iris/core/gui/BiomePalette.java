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

import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisBiomePaletteLayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One of the block palettes a biome places: a surface, sea or cave ceiling layer, or its wall or slab blocks.
 */
record BiomePalette(String biomeKey, Kind kind, int index) {
    private static final String ID_PREFIX = "palette:";
    private static final char ID_SEPARATOR = '#';

    enum Kind {
        LAYER("Layer", true),
        SEA_LAYER("Sea layer", true),
        CAVE_CEILING_LAYER("Cave ceiling layer", true),
        WALL("Wall", false),
        SLAB("Slab", false);

        private final String label;
        private final boolean stacked;

        Kind(String label, boolean stacked) {
            this.label = label;
            this.stacked = stacked;
        }

        /**
         * Whether the palette is a stack of blocks with a thickness, rather than single blocks on walls or slopes.
         */
        boolean isStacked() {
            return stacked;
        }

        // A biome without wall or slab blocks still has those palettes, just empty.
        List<IrisBiomePaletteLayer> palettesOf(IrisBiome biome) {
            return switch (this) {
                case LAYER -> biome.getLayers();
                case SEA_LAYER -> biome.getSeaLayers();
                case CAVE_CEILING_LAYER -> biome.getCaveCeilingLayers();
                case WALL -> biome.getWall().getPalette().isEmpty() ? List.of() : List.of(biome.getWall());
                case SLAB -> biome.getSlab().getPalette().isEmpty() ? List.of() : List.of(biome.getSlab());
            };
        }
    }

    static List<BiomePalette> allOf(String biomeKey, IrisBiome biome) {
        List<BiomePalette> palettes = new ArrayList<>();
        for (Kind kind : Kind.values()) {
            List<IrisBiomePaletteLayer> layers = kind.palettesOf(biome);
            for (int index = 0; index < layers.size(); index++) {
                if (!layers.get(index).getPalette().isEmpty()) {
                    palettes.add(new BiomePalette(biomeKey, kind, index));
                }
            }
        }
        return List.copyOf(palettes);
    }

    static BiomePalette fromId(String id) {
        if (id == null || !id.startsWith(ID_PREFIX)) {
            return null;
        }
        int indexSeparator = id.lastIndexOf(ID_SEPARATOR);
        int kindSeparator = id.lastIndexOf(ID_SEPARATOR, indexSeparator - 1);
        if (kindSeparator < ID_PREFIX.length()) {
            return null;
        }
        return new BiomePalette(
                id.substring(ID_PREFIX.length(), kindSeparator),
                Kind.valueOf(id.substring(kindSeparator + 1, indexSeparator)),
                Integer.parseInt(id.substring(indexSeparator + 1))
        );
    }

    static boolean belongsTo(String id, String biomeKey) {
        return id != null && id.startsWith(ID_PREFIX + biomeKey + ID_SEPARATOR);
    }

    String id() {
        return ID_PREFIX + biomeKey + ID_SEPARATOR + kind.name() + ID_SEPARATOR + index;
    }

    String label() {
        return kind.isStacked() ? kind.label + " " + index : kind.label;
    }

    String lowercaseLabel() {
        return label().toLowerCase(Locale.ROOT);
    }

    /**
     * The palette in the given copy of the biome, or null when a hotload removed it.
     */
    IrisBiomePaletteLayer resolve(IrisBiome biome) {
        List<IrisBiomePaletteLayer> layers = kind.palettesOf(biome);
        return index < layers.size() ? layers.get(index) : null;
    }
}
