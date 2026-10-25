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

import art.arcane.iris.studio.map.PickMeasuring.TypedSpread;
import art.arcane.iris.studio.map.components.PickDisplay;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.InferredType;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.volmlib.util.stream.ProceduralStream;

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
 * in view it shows how its noise picks them.
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
            List<JComponent> charts = region.spreads().stream()
                    .map(spread -> (JComponent) inspector.noiseChart(spread, "Biomes", region.key() + "/" + spread.title()))
                    .toList();
            sections.add(new PickInspectorPanel.Section(region.key(), region.name(), region.color(), region.share(),
                    biomeCountText(region.biomeCount()), charts));
        }
        inspector.showRegionSections("Each region splits its own biome noise by rarity", sections);
    }

    private static String biomeCountText(int count) {
        return count == 1 ? "1 biome" : count + " biomes";
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
        return new Measurement(regions);
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
        private int count;

        private RegionSamples(Engine engine, IrisComplex complex, IrisRegion region) {
            this.complex = complex;
            this.region = region;
            this.spreads = PickMeasuring.biomeSpreads(engine, complex, region);
        }

        private void add(double x, double z) {
            count++;
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
            String key = region.getLoadKey();
            String name = region.getName();
            int color = PickMeasuring.regionColor(engine, region);
            List<TypedSpread> inView = spreads.stream().filter(typed -> typed.spread().sampleCount() > 0).toList();
            int biomeCount = (int) inView.stream()
                    .flatMap(typed -> typed.spread().rows().stream())
                    .map(row -> row.option().key())
                    .distinct()
                    .count();
            return new RegionPicks(key, name, color, share, biomeCount, inView.stream().map(TypedSpread::spread).toList());
        }
    }

    private record RegionPicks(String key, String name, int color, double share, int biomeCount,
                               List<NoisePickSpread> spreads) {
    }

    private record Measurement(List<RegionPicks> regions) {
    }
}
