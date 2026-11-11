/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.core.studio.map;

import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.render.RenderType;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.util.project.stream.ProceduralStream;

import java.util.Arrays;
import java.util.Optional;

/** The studio map's views of a world drawn from its engine streams, one tile at a time. */
public enum MapView {
    BIOMES("biomes", "Biomes", "The biome at each spot, in the biome's colour", Group.WORLD, RenderType.BIOME),
    REGIONS("regions", "Regions", "The region each spot belongs to", Group.WORLD, RenderType.REGION),
    LAND_BIOMES("landBiomes", "Land biomes", "The land biome each spot would get, also where the sea is",
            Group.BIOME_LAYERS, RenderType.BIOME_LAND),
    SEA_BIOMES("seaBiomes", "Sea biomes", "The sea biome each spot would get, also where the land is",
            Group.BIOME_LAYERS, RenderType.BIOME_SEA),
    CAVE_BIOMES("caveBiomes", "Cave biomes", "The cave biome under each spot", Group.BIOME_LAYERS, RenderType.CAVE_LAND);

    private final String id;
    private final String label;
    private final String description;
    private final Group group;
    private final RenderType colorType;

    MapView(String id, String label, String description, Group group, RenderType colorType) {
        this.id = id;
        this.label = label;
        this.description = description;
        this.group = group;
        this.colorType = colorType;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public Group group() {
        return group;
    }

    public static Optional<MapView> byId(String id) {
        return Arrays.stream(values()).filter(view -> view.id.equals(id)).findFirst();
    }

    /** Whether each spot is labelled with its region rather than a biome. */
    boolean labelsRegions() {
        return this == REGIONS;
    }

    /** The colouring biomes and regions get in this view. */
    RenderType colorType() {
        return colorType;
    }

    /** The biome each spot is labelled with: the base biome, before the height corrects it near coasts. */
    ProceduralStream<IrisBiome> biomeStream(IrisComplex complex) {
        return switch (this) {
            case LAND_BIOMES -> complex.getLandBiomeStream();
            case SEA_BIOMES -> complex.getSeaBiomeStream();
            case CAVE_BIOMES -> complex.getCaveBiomeStream();
            case BIOMES, REGIONS -> complex.getBaseBiomeStream();
        };
    }

    public enum Group {
        WORLD("World"),
        BIOME_LAYERS("Biome layers");

        private final String label;

        Group(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }
}
