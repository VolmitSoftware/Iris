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

import com.volmit.iris.core.gui.PickMeasuring.TypedSpread;
import com.volmit.iris.core.gui.components.PickDisplay;
import com.volmit.iris.engine.IrisComplex;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.InferredType;
import com.volmit.iris.engine.object.IrisRegion;
import com.volmit.iris.util.stream.ProceduralStream;

import javax.swing.JComponent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The biome view's explorer: every region picks the biomes on its ground from its own biome lists, so for each region
 * in view it shows how its noise, or in hybrid terrain the height, picks them.
 */
final class BiomeExplorer {
    private final Supplier<Engine> engineSource;
    private final PickInspectorPanel inspector;
    private final LatestMeasurement<Measurement> measurer = new LatestMeasurement<>("Iris Biome Explorer");
    private MapCensus.ViewArea latestArea;
    private MapCensus.ViewArea measuredArea;

    /**
     * @param onDisplayChosen receives how the map should draw the picks
     */
    BiomeExplorer(Supplier<Engine> engineSource, Consumer<PickDisplay> onDisplayChosen) {
        this.engineSource = engineSource;
        this.inspector = new PickInspectorPanel("Biomes", onDisplayChosen);
    }

    PickInspectorPanel inspector() {
        return inspector;
    }

    /**
     * Measures the regions in this area and how they pick their biomes, unless that was already done.
     */
    void measure(MapCensus.ViewArea area) {
        latestArea = area;
        if (area == null || area.equals(measuredArea)) {
            return;
        }
        measuredArea = area;
        measurer.measure(() -> measurePicks(area), this::show);
    }

    /**
     * The pack changed, so the regions and their biomes are read again.
     */
    void refresh() {
        measuredArea = null;
        measure(latestArea);
    }

    void close() {
        measurer.close();
    }

    private void show(Measurement measurement) {
        List<PickInspectorPanel.Section> sections = new ArrayList<>();
        for (RegionPicks region : measurement.regions()) {
            List<JComponent> charts = region.coverage() != null
                    ? List.of(PickInspectorPanel.heightChart(region.coverage()))
                    : region.spreads().stream().map(spread -> (JComponent) new NoisePickChart(spread, "Biomes")).toList();
            sections.add(new PickInspectorPanel.Section(region.name(), region.color(), region.share(), charts, region.notes()));
        }
        String detail = measurement.hybrid()
                ? "Hybrid terrain · each region picks biomes by height"
                : "Each region splits its own biome noise by rarity";
        inspector.showRegionSections(detail, sections, measurement.notes());
    }

    private Measurement measurePicks(MapCensus.ViewArea area) {
        Engine engine = engineSource.get();
        IrisComplex complex = engine == null ? null : engine.getComplex();
        if (complex == null) {
            return null;
        }
        Map<IrisRegion, RegionSamples> samplesByRegion = new IdentityHashMap<>();
        int[] total = {0};
        PickMeasuring.forEachSample(area, (x, z) -> {
            IrisRegion region = complex.getRegionStream().get(x, z);
            if (region == null) {
                return;
            }
            total[0]++;
            samplesByRegion.computeIfAbsent(region, subject -> new RegionSamples(engine, complex, subject)).add(x, z);
        });
        List<RegionPicks> regions = samplesByRegion.values().stream()
                .sorted(Comparator.comparingInt(RegionSamples::count).reversed())
                .map(samples -> samples.picks(engine, total[0]))
                .toList();
        List<String> notes = new ArrayList<>();
        if (complex.isHybridTerrain()) {
            notes.add(PickMeasuring.hybridNoiseNote());
        } else {
            PickMeasuring.addRarityNotes(notes, complex, regions.stream().flatMap(region -> region.spreads().stream()).toList());
        }
        notes.add(PickMeasuring.measuredNote());
        return new Measurement(complex.isHybridTerrain(), regions, notes);
    }

    /**
     * The spots in view one region owns. The biome view shows each spot's base biome, which the region picks from the
     * biome list of the spot's continent type.
     */
    private static final class RegionSamples {
        private final IrisComplex complex;
        private final IrisRegion region;
        private final List<TypedSpread> spreads;
        private final Map<InferredType, ProceduralStream<Double>> noiseByType = new EnumMap<>(InferredType.class);
        private final List<Integer> pickingHeights = new ArrayList<>();
        private int count;

        private RegionSamples(Engine engine, IrisComplex complex, IrisRegion region) {
            this.complex = complex;
            this.region = region;
            this.spreads = complex.isHybridTerrain() ? List.of() : PickMeasuring.biomeSpreads(engine, complex, region);
        }

        private void add(double x, double z) {
            count++;
            if (complex.isHybridTerrain()) {
                pickingHeights.add((int) Math.round(complex.getPlacementHeightStream().get(x, z)));
                return;
            }
            InferredType type = complex.getBridgeStream().get(x, z);
            for (TypedSpread typed : spreads) {
                if (typed.type() == type) {
                    typed.spread().add(noiseByType.computeIfAbsent(type, shown -> complex.biomePickNoiseStream(region, shown)).get(x, z));
                }
            }
        }

        private int count() {
            return count;
        }

        private RegionPicks picks(Engine engine, int totalCount) {
            double share = totalCount == 0 ? 0D : count / (double) totalCount;
            String name = region.getName();
            int color = PickMeasuring.regionColor(engine, region);
            List<String> notes = new ArrayList<>();
            if (complex.isHybridTerrain()) {
                RegionHeightCoverage coverage = RegionHeightCoverage.measure(PickMeasuring.biomeRanges(engine, region),
                        pickingHeights.stream().mapToInt(Integer::intValue).toArray());
                notes.addAll(coverage.notes());
                return new RegionPicks(name, color, share, List.of(), coverage, notes);
            }
            List<TypedSpread> inView = spreads.stream().filter(typed -> typed.spread().sampleCount() > 0).toList();
            PickMeasuring.addBiomeSpreadNotes(notes, inView);
            PickMeasuring.addChildBiomeNote(notes, complex, region);
            return new RegionPicks(name, color, share, inView.stream().map(TypedSpread::spread).toList(), null, notes);
        }
    }

    /**
     * @param coverage how the region's biomes cover the heights in view in hybrid terrain, or null in biome terrain
     */
    private record RegionPicks(String name, int color, double share, List<NoisePickSpread> spreads,
                               RegionHeightCoverage coverage, List<String> notes) {
    }

    private record Measurement(boolean hybrid, List<RegionPicks> regions, List<String> notes) {
    }
}
