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

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.studio.map.components.MapLayer;

import java.awt.Desktop;
import java.io.File;
import java.util.Optional;

/**
 * Opens the pack file behind what a map layer shows at a spot in the editor.
 */
final class PackFileOpener {
    private PackFileOpener() {
    }

    /** The opened file's name, or empty when nothing could be opened. */
    static Optional<String> openAt(Engine engine, double worldX, double worldZ, MapLayer layer) {
        if (!Desktop.isDesktopSupported()) {
            return Optional.empty();
        }
        IrisComplex complex = engine.getComplex();
        File file = switch (layer.type()) {
            case BIOME, HEIGHT, RIVER -> complex.getTrueBiomeStream().get(worldX, worldZ).openInVSCode();
            case BIOME_LAND -> complex.getLandBiomeStream().get(worldX, worldZ).openInVSCode();
            case BIOME_SEA -> complex.getSeaBiomeStream().get(worldX, worldZ).openInVSCode();
            case REGION -> complex.getRegionStream().get(worldX, worldZ).openInVSCode();
            case REGION_PREVIEW -> openPreviewedBiome(engine, layer.previewRegionKey(), worldX, worldZ);
            case CAVE_LAND -> complex.getCaveBiomeStream().get(worldX, worldZ).openInVSCode();
        };
        return Optional.ofNullable(file).map(File::getName);
    }

    private static File openPreviewedBiome(Engine engine, String regionKey, double worldX, double worldZ) {
        IrisRegion region = engine.getData().getRegionLoader().load(regionKey);
        return region == null ? null : engine.getComplex().surfaceBiomeAsIfIn(region, worldX, worldZ).openInVSCode();
    }
}
