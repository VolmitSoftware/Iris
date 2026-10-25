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

import art.arcane.iris.studio.map.components.MapLabels;
import art.arcane.iris.studio.render.RenderType;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisRegion;

/**
 * What the 3D world view colours its ground by.
 */
enum WorldColoring {
    BIOMES("Biomes", "Colour the ground by its biome") {
        @Override
        WorldSubject subjectAt(Engine engine, double x, double z) {
            IrisBiome biome = engine.getComplex().getTrueBiomeStream().get(x, z);
            return new WorldSubject(MapLabels.Kind.BIOME, biome.getLoadKey(), biome.getName(),
                    biome.getColor(engine, RenderType.BIOME).getRGB() & 0xFFFFFF);
        }
    },
    REGIONS("Regions", "Colour the ground by its region") {
        @Override
        WorldSubject subjectAt(Engine engine, double x, double z) {
            IrisComplex complex = engine.getComplex();
            IrisRegion region = complex.getRegionStream().get(x, z);
            return new WorldSubject(MapLabels.Kind.REGION, region.getLoadKey(), region.getName(),
                    region.getColor(complex, RenderType.REGION).getRGB() & 0xFFFFFF);
        }
    };

    private final String label;
    private final String description;

    WorldColoring(String label, String description) {
        this.label = label;
        this.description = description;
    }

    /**
     * @return what colours the ground here, or null when this colouring doesn't apply to the world
     */
    abstract WorldSubject subjectAt(Engine engine, double x, double z);

    String label() {
        return label;
    }

    String description() {
        return description;
    }

    /**
     * A biome or region as the ground shows it.
     *
     * @param key told apart by, and shown when names clash
     */
    record WorldSubject(MapLabels.Kind kind, String key, String name, int color) {
    }
}
