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
import art.arcane.iris.generation.biome.IrisBiomePaletteLayer;
import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.generation.decoration.IrisDecorator;
import art.arcane.volmlib.util.collection.KList;

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
    private static final String SNIPPET_PREFIX = "snippet/";

    enum Kind {
        LAYER("Layer", "layers", SnippetFolder.BLOCK_PALETTES, true, true),
        SEA_LAYER("Sea layer", "seaLayers", SnippetFolder.BLOCK_PALETTES, true, true),
        CAVE_CEILING_LAYER("Cave ceiling layer", "caveCeilingLayers", SnippetFolder.BLOCK_PALETTES, true, true),
        WALL("Wall", "wall", SnippetFolder.BLOCK_PALETTES, false, false),
        SLAB("Slab", "slab", SnippetFolder.BLOCK_PALETTES, false, false),
        DECORATOR("Decorator", "decorators", SnippetFolder.DECORATORS, false, true);

        private final String label;
        private final String jsonKey;
        private final SnippetFolder snippetFolder;
        private final boolean stacked;
        private final boolean numbered;

        Kind(String label, String jsonKey, SnippetFolder snippetFolder, boolean stacked, boolean numbered) {
            this.label = label;
            this.jsonKey = jsonKey;
            this.snippetFolder = snippetFolder;
            this.stacked = stacked;
            this.numbered = numbered;
        }

        SnippetFolder snippetFolder() {
            return snippetFolder;
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

    /**
     * The folders under snippet/ that palettes can come from, named after the Iris type they hold.
     */
    enum SnippetFolder {
        BLOCK_PALETTES("biome-palette", "Block palettes"),
        DECORATORS("decorator", "Decorators");

        private final String folder;
        private final String label;

        SnippetFolder(String folder, String label) {
            this.folder = folder;
            this.label = label;
        }

        String folder() {
            return folder;
        }

        String label() {
            return label;
        }

        /**
         * The snippet's path inside this folder. Packs may leave the folder out, the way the snippet loader allows.
         */
        String pathOf(String reference) {
            String path = reference.startsWith(SNIPPET_PREFIX) ? reference.substring(SNIPPET_PREFIX.length()) : reference;
            return path.startsWith(folder + "/") ? path.substring(folder.length() + 1) : path;
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
