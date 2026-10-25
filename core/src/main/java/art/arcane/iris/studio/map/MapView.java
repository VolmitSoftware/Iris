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

import art.arcane.iris.studio.map.components.TileType;

import java.awt.event.KeyEvent;

/**
 * Everything the map window can show, in the order and groups of its view list.
 */
enum MapView {
    BIOMES("Biomes", "The biome at each spot, in the biome's colour", Group.WORLD, TileType.BIOME, KeyEvent.VK_1),
    REGIONS("Regions", "The region each spot belongs to; pick one to see the biomes it places",
            Group.WORLD, TileType.REGION, KeyEvent.VK_2),
    HEIGHT("Height", "Terrain height, from deep water to snowy peaks", Group.WORLD, TileType.HEIGHT, KeyEvent.VK_3),
    RIVERS("Rivers", "Rivers, falls, grottos and the rest of the hydrology the engine plans",
            Group.WORLD, TileType.RIVER, KeyEvent.VK_4),
    WORLD_3D("3D world", "The terrain in 3D, coloured by biome or region", Group.WORLD, null, KeyEvent.VK_5),
    LAND_BIOMES("Land biomes", "The land biome each spot would get, also where the sea is",
            Group.BIOME_STREAMS, TileType.BIOME_LAND, KeyEvent.VK_6),
    SEA_BIOMES("Sea biomes", "The sea biome each spot would get, also where the land is",
            Group.BIOME_STREAMS, TileType.BIOME_SEA, KeyEvent.VK_7),
    CAVE_BIOMES("Cave biomes", "The cave biome under each spot", Group.BIOME_STREAMS, TileType.CAVE_LAND, KeyEvent.VK_8),
    PALETTES("Palettes", "The blocks each biome's palette layers place, with their textures",
            Group.BLOCKS, null, KeyEvent.VK_P),
    OBJECTS("Objects", "Every object in the pack in 3D, with the blocks it is made of",
            Group.BLOCKS, null, KeyEvent.VK_O),
    PROCEDURAL("Procedural", "Trees, rock formations and more that Iris generates from settings, every variant in 3D",
            Group.BLOCKS, null, KeyEvent.VK_T),
    NOISE("Noise", "The pack's noise generators and Iris' built-in noise styles",
            Group.GENERATORS, null, KeyEvent.VK_N),
    IMAGE_MAPS("Image maps", "Terrain height, biomes or masks painted from a PNG, previewed and exported into the pack",
            Group.GENERATORS, null, KeyEvent.VK_I);

    private final String label;
    private final String description;
    private final Group group;
    private final TileType renderType;
    private final int shortcutKey;

    MapView(String label, String description, Group group, TileType renderType, int shortcutKey) {
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
     * The tile renderer's mode for this view, or null for the 3D world, palette, object, procedural, noise and image map
     * views, which draw themselves.
     */
    TileType renderType() {
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
