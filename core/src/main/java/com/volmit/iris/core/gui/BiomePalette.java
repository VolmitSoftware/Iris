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
import com.volmit.iris.engine.object.IrisBlockData;
import com.volmit.iris.engine.object.IrisDecorator;
import com.volmit.iris.util.collection.KList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One of the block palettes a biome places: a surface, sea or cave ceiling layer, its wall or slab blocks, or a
 * decorator.
 */
record BiomePalette(String biomeKey, Kind kind, int index) {
    private static final String ID_PREFIX = "palette:";
    private static final char ID_SEPARATOR = '#';

    enum Kind {
        LAYER("Layer", "layers", true, true),
        SEA_LAYER("Sea layer", "seaLayers", true, true),
        CAVE_CEILING_LAYER("Cave ceiling layer", "caveCeilingLayers", true, true),
        WALL("Wall", "wall", false, false),
        SLAB("Slab", "slab", false, false),
        DECORATOR("Decorator", "decorators", false, true);

        private final String label;
        private final String jsonKey;
        private final boolean stacked;
        private final boolean numbered;

        Kind(String label, String jsonKey, boolean stacked, boolean numbered) {
            this.label = label;
            this.jsonKey = jsonKey;
            this.stacked = stacked;
            this.numbered = numbered;
        }

        /**
         * Whether the palette is a stack of blocks with a thickness, rather than single blocks on walls or slopes.
         */
        boolean isStacked() {
            return stacked;
        }

        /**
         * The biome file's key holding these palettes: a list of them, or a single one for walls and slabs.
         */
        String jsonKey() {
            return jsonKey;
        }

        // A biome without wall or slab blocks still has those palettes, just empty.
        List<IrisBiomePaletteLayer> layersOf(IrisBiome biome) {
            return switch (this) {
                case LAYER -> biome.getLayers();
                case SEA_LAYER -> biome.getSeaLayers();
                case CAVE_CEILING_LAYER -> biome.getCaveCeilingLayers();
                case WALL -> biome.getWall().getPalette().isEmpty() ? List.of() : List.of(biome.getWall());
                case SLAB -> biome.getSlab().getPalette().isEmpty() ? List.of() : List.of(biome.getSlab());
                case DECORATOR -> List.of();
            };
        }

        // A decorator snippet that couldn't be found loads as an empty entry, which places nothing.
        List<KList<IrisBlockData>> blockListsOf(IrisBiome biome) {
            if (this != DECORATOR) {
                return layersOf(biome).stream().map(IrisBiomePaletteLayer::getPalette).toList();
            }
            return biome.getDecorators().stream()
                    .map(decorator -> decorator == null ? new KList<IrisBlockData>() : decorator.getPalette())
                    .toList();
        }
    }

    static List<BiomePalette> allOf(String biomeKey, IrisBiome biome) {
        List<BiomePalette> palettes = new ArrayList<>();
        for (Kind kind : Kind.values()) {
            List<KList<IrisBlockData>> blockLists = kind.blockListsOf(biome);
            for (int index = 0; index < blockLists.size(); index++) {
                if (!blockLists.get(index).isEmpty()) {
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
        return kind.numbered ? kind.label + " " + index : kind.label;
    }

    String lowercaseLabel() {
        return label().toLowerCase(Locale.ROOT);
    }

    /**
     * The palette's blocks in the given copy of the biome, or null when a hotload removed it.
     */
    KList<IrisBlockData> blocksIn(IrisBiome biome) {
        List<KList<IrisBlockData>> blockLists = kind.blockListsOf(biome);
        return index < blockLists.size() ? blockLists.get(index) : null;
    }

    IrisBiomePaletteLayer layerIn(IrisBiome biome) {
        return kind.layersOf(biome).get(index);
    }

    IrisDecorator decoratorIn(IrisBiome biome) {
        return biome.getDecorators().get(index);
    }
}
