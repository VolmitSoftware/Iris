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

import art.arcane.iris.studio.map.NoisePickSpread.Option;
import art.arcane.volmlib.util.math.Rarity;
import art.arcane.iris.studio.render.RenderType;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.InferredType;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisRegion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Samples the map in view to measure how regions and biomes are picked; shared by the region and biome views.
 */
final class PickMeasuring {
    static final List<InferredType> PLACED_TYPES = List.of(InferredType.LAND, InferredType.SHORE, InferredType.SEA);
    private static final int SAMPLES_PER_SIDE = 96;

    private PickMeasuring() {
    }

    static void forEachSample(MapCensus.ViewArea area, SampleVisitor visitor) {
        double stepX = (area.maximumX() - area.minimumX()) / SAMPLES_PER_SIDE;
        double stepZ = (area.maximumZ() - area.minimumZ()) / SAMPLES_PER_SIDE;
        for (int row = 0; row < SAMPLES_PER_SIDE; row++) {
            for (int column = 0; column < SAMPLES_PER_SIDE; column++) {
                visitor.visit(area.minimumX() + (column + 0.5D) * stepX, area.minimumZ() + (row + 0.5D) * stepZ);
            }
        }
    }

    static Option regionOption(Engine engine, IrisRegion region) {
        return new Option(region.getLoadKey(), region.getName(), regionColor(engine, region), Rarity.get(region));
    }

    static Option biomeOption(Engine engine, IrisBiome biome) {
        return new Option(biome.getLoadKey(), biome.getName(), biome.getColor(engine, RenderType.BIOME).getRGB() & 0xFFFFFF,
                Rarity.get(biome));
    }

    static int regionColor(Engine engine, IrisRegion region) {
        return region.getColor(engine.getComplex(), RenderType.REGION).getRGB() & 0xFFFFFF;
    }

    /**
     * One spread per biome list of the region that has biomes, in land, shore, sea order.
     */
    static List<TypedSpread> biomeSpreads(Engine engine, IrisComplex complex, IrisRegion region) {
        List<TypedSpread> spreads = new ArrayList<>();
        for (InferredType type : PLACED_TYPES) {
            List<Option> options = complex.noisePickedBiomes(region, type).stream()
                    .map(biome -> biomeOption(engine, biome))
                    .toList();
            if (!options.isEmpty()) {
                spreads.add(new TypedSpread(type,
                        NoisePickSpread.of(typeName(type) + " biome noise in view", options)));
            }
        }
        return spreads;
    }

    static String typeName(InferredType type) {
        String name = type.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    @FunctionalInterface
    interface SampleVisitor {
        void visit(double x, double z);
    }

    /**
     * The spread of the noise that picks from one of a region's biome lists.
     */
    record TypedSpread(InferredType type, NoisePickSpread spread) {
    }
}
