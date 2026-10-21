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

import com.volmit.iris.core.gui.NoisePickSpread.Option;
import com.volmit.iris.core.gui.RegionHeightCoverage.BiomeRange;
import com.volmit.iris.core.gui.components.RenderType;
import com.volmit.iris.engine.IrisComplex;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.InferredType;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisRegion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Samples the map in view to measure how regions and biomes are picked, and puts what stands out in words; shared by
 * the region and biome views.
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
        return new Option(region.getLoadKey(), region.getName(), regionColor(engine, region), region.getRarity());
    }

    static Option biomeOption(Engine engine, IrisBiome biome) {
        return new Option(biome.getLoadKey(), biome.getName(), biome.getColor(engine, RenderType.BIOME).getRGB() & 0xFFFFFF,
                biome.getRarity());
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
                        NoisePickSpread.of(typeName(type) + " biome noise in view", options, complex.isLegacyRarity())));
            }
        }
        return spreads;
    }

    static List<BiomeRange> biomeRanges(Engine engine, IrisRegion region) {
        List<BiomeRange> ranges = new ArrayList<>();
        for (InferredType type : PLACED_TYPES) {
            for (IrisBiome biome : region.getBiomes(engine.getComplex(), type)) {
                if (biome == null) {
                    continue;
                }
                ranges.add(new BiomeRange(biome.getLoadKey(), biome.getName(),
                        biome.getColor(engine, RenderType.BIOME).getRGB() & 0xFFFFFF, type,
                        biome.getPlacement().getMinHeight(), biome.getPlacement().getMaxHeight()));
            }
        }
        return ranges;
    }

    static void addSpreadNotes(List<String> notes, NoisePickSpread spread, String noun) {
        List<NoisePickSpread.Row> rows = spread.rows();
        if (spread.sampleCount() == 0) {
            notes.add("No " + noun + " is picked in view.");
            return;
        }
        if (rows.size() == 1) {
            notes.add(rows.getFirst().option().name() + " is the only " + noun + ", so the " + noun + " noise has nothing to pick.");
            return;
        }
        if (spread.belowZeroShare() > 0D) {
            notes.add(NoisePickChart.percent(spread.belowZeroShare()) + " of the " + noun + " noise in view is below 0, so "
                    + rows.getFirst().option().name() + " gets it.");
        }
        if (spread.aboveOneShare() > 0D) {
            notes.add(NoisePickChart.percent(spread.aboveOneShare()) + " of the " + noun + " noise in view is above 1, so "
                    + rows.getLast().option().name() + " gets it.");
        }
        List<String> starved = rows.stream().filter(NoisePickSpread.Row::isFarBelowWeight).map(row -> row.option().name()).toList();
        if (!starved.isEmpty()) {
            boolean single = starved.size() == 1;
            notes.add(String.join(", ", starved) + (single ? " gets" : " get") + " under half " + (single ? "its" : "their")
                    + " weight in view (▼): the " + noun + " noise rarely reaches " + (single ? "its band." : "their bands."));
        }
    }

    static void addBiomeSpreadNotes(List<String> notes, List<TypedSpread> spreads) {
        for (TypedSpread typed : spreads) {
            addSpreadNotes(notes, typed.spread(), typeName(typed.type()).toLowerCase(Locale.ROOT) + " biome");
        }
    }

    static void addChildBiomeNote(List<String> notes, IrisComplex complex, IrisRegion region) {
        String parents = PLACED_TYPES.stream()
                .flatMap(type -> complex.noisePickedBiomes(region, type).stream())
                .filter(biome -> !biome.getChildren().isEmpty())
                .map(IrisBiome::getName)
                .distinct()
                .collect(Collectors.joining(", "));
        if (!parents.isEmpty()) {
            notes.add(parents + " then hand some spots to child biomes by a noise of their own.");
        }
    }

    static void addRarityNotes(List<String> notes, IrisComplex complex, Collection<NoisePickSpread> spreads) {
        if (spreads.stream().allMatch(spread -> spread.bands().size() <= 1)) {
            return;
        }
        notes.add(complex.isLegacyRarity()
                ? "Legacy rarity: each weight is the total rarity minus the option's own."
                : "Each weight is one over the rarity, so rarity 2 gets half the noise of rarity 1.");
    }

    static String measuredNote() {
        return "Measured on a " + SAMPLES_PER_SIDE + " × " + SAMPLES_PER_SIDE + " grid over the map in view.";
    }

    static String hybridNoiseNote() {
        return "Noise shows the height biomes are picked at: the overlay noise plus the dimension's biome placement "
                + "jitter. Where ranges share a height, each biome's competition noise, divided by its rarity, decides.";
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
