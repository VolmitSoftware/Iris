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

import com.volmit.iris.core.gui.components.RenderType;

import java.awt.event.KeyEvent;

/**
 * Everything the map window can show, in the order and groups of its view list.
 */
enum MapView {
    BIOMES("Biomes", "The biome at each spot, in the biome's colour", Group.WORLD, RenderType.BIOME, KeyEvent.VK_1),
    REGIONS("Regions", "The region each spot belongs to", Group.WORLD, RenderType.REGION, KeyEvent.VK_2),
    HEIGHT("Height", "Terrain height, from deep water to snowy peaks", Group.WORLD, RenderType.HEIGHT, KeyEvent.VK_3),
    CONTINENTS("Continents", "Land, coast or sea, judged by each biome's first height generator",
            Group.WORLD, RenderType.CONTINENT, KeyEvent.VK_4),
    LAND_BIOMES("Land biomes", "The land biome each spot would get, also where the sea is",
            Group.BIOME_STREAMS, RenderType.BIOME_LAND, KeyEvent.VK_5),
    SEA_BIOMES("Sea biomes", "The sea biome each spot would get, also where the land is",
            Group.BIOME_STREAMS, RenderType.BIOME_SEA, KeyEvent.VK_6),
    CAVE_BIOMES("Cave biomes", "The cave biome under each spot", Group.BIOME_STREAMS, RenderType.CAVE_LAND, KeyEvent.VK_7),
    OBJECT_LOAD("Objects", "How densely each biome places objects, brighter is denser",
            Group.LOAD, RenderType.OBJECT_LOAD, KeyEvent.VK_8),
    DECORATOR_LOAD("Decorators", "How densely each biome places decorations, brighter is denser",
            Group.LOAD, RenderType.DECORATOR_LOAD, KeyEvent.VK_9),
    LAYER_LOAD("Layers", "How many block layers each biome stacks, brighter is more",
            Group.LOAD, RenderType.LAYER_LOAD, KeyEvent.VK_0),
    PALETTES("Palettes", "The blocks each biome's palette layers place, with their textures",
            Group.BLOCKS, null, KeyEvent.VK_P),
    NOISE("Noise", "The pack's noise generators and Iris' built-in noise styles",
            Group.GENERATORS, null, KeyEvent.VK_N);

    private final String label;
    private final String description;
    private final Group group;
    private final RenderType renderType;
    private final int shortcutKey;

    MapView(String label, String description, Group group, RenderType renderType, int shortcutKey) {
        this.label = label;
        this.description = description;
        this.group = group;
        this.renderType = renderType;
        this.shortcutKey = shortcutKey;
    }

    String label() {
        return label;
    }

    String description() {
        return description;
    }

    Group group() {
        return group;
    }

    /**
     * The tile renderer's mode for this view, or null for the palette and noise views, which draw themselves.
     */
    RenderType renderType() {
        return renderType;
    }

    int shortcutKey() {
        return shortcutKey;
    }

    String shortcutText() {
        return KeyEvent.getKeyText(shortcutKey);
    }

    boolean isTileMap() {
        return renderType != null;
    }

    static MapView forShortcut(int keyCode) {
        for (MapView view : values()) {
            if (view.shortcutKey == keyCode) {
                return view;
            }
        }
        return null;
    }

    enum Group {
        WORLD("World"),
        BIOME_STREAMS("Biome layers"),
        LOAD("Generation load"),
        BLOCKS("Blocks"),
        GENERATORS("Generators");

        private final String label;

        Group(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }
}
