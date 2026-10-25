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
import art.arcane.iris.studio.map.RegionNavigator.RegionRow;
import art.arcane.iris.studio.map.components.PickDisplay;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.InferredType;
import art.arcane.iris.generation.terrain.IrisRegion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The region view's explorer: shows how the region noise picks the regions, and for a chosen region fills the map with
 * its biomes and shows how they are picked, measured against what is in view.
 */
final class RegionExplorer {
    private final Supplier<Engine> engineSource;
    private final Consumer<String> onPreviewChanged;
    private final RegionNavigator navigator = new RegionNavigator(this::choose);
    private final PickInspectorPanel inspector;
    private final LatestMeasurement<Measurement> measurer = new LatestMeasurement<>("Iris Region Explorer");
    private String chosenRegionKey;
    private MapCensus.ViewArea latestArea;
    private MapCensus.ViewArea measuredArea;

    /**
     * @param onPreviewChanged receives the region to fill the map with, or null to show every region again
     * @param onDisplayChosen  receives how the map should draw the picks
     */
    RegionExplorer(Supplier<Engine> engineSource, Consumer<String> onPreviewChanged, Consumer<PickDisplay> onDisplayChosen) {
        this.engineSource = engineSource;
        this.onPreviewChanged = onPreviewChanged;
        this.inspector = new PickInspectorPanel("Regions", onDisplayChosen);
    }

    RegionNavigator navigator() {
        return navigator;
    }

    PickInspectorPanel inspector() {
        return inspector;
    }

    void loadRegions() {
        Engine engine = engineSource.get();
        if (engine == null || engine.getComplex() == null) {
            return;
        }
        List<RegionRow> rows = new ArrayList<>();
        for (IrisRegion region : engine.getComplex().noisePickedRegions()) {
            rows.add(new RegionRow(region.getLoadKey(), region.getName(), PickMeasuring.regionColor(engine, region)));
        }
        navigator.showRegions(rows);
    }

    /**
     * Measures the regions, or the chosen region's biomes, against this area, unless that was already done.
     */
    void measure(MapCensus.ViewArea area) {
        latestArea = area;
        if (area == null || area.equals(measuredArea)) {
            return;
        }
        measuredArea = area;
        String regionKey = chosenRegionKey;
        measurer.measure(() -> measurePicks(regionKey, area), this::show);
    }

    /**
     * The pack changed, so the regions and their biomes are read again.
     */
    void refresh() {
        loadRegions();
        measuredArea = null;
        measure(latestArea);
    }

    void close() {
        measurer.close();
    }

    private void choose(String regionKey) {
        chosenRegionKey = regionKey;
        measuredArea = null;
        measurer.discardPending();
        onPreviewChanged.accept(regionKey);
        inspector.showMeasuring(regionKey == null ? "Regions" : regionName(regionKey));
        measure(latestArea);
    }

    private String regionName(String regionKey) {
        Engine engine = engineSource.get();
        IrisRegion region = engine == null ? null : engine.getData().getRegionLoader().load(regionKey);
        return region == null ? regionKey : region.getName();
    }

    private void show(Measurement measurement) {
        if (!Objects.equals(measurement.regionKey(), chosenRegionKey)) {
            return;
        }
        switch (measurement) {
            case RegionPicks picks -> inspector.showRegionPicks(picks.spread());
            case BiomePicks picks -> inspector.showBiomePicks(picks.regionName(), picks.spreads());
        }
    }

    private Measurement measurePicks(String regionKey, MapCensus.ViewArea area) {
        Engine engine = engineSource.get();
        IrisComplex complex = engine == null ? null : engine.getComplex();
        if (complex == null) {
            return null;
        }
        if (regionKey == null) {
            return measureRegionPicks(engine, complex, area);
        }
        IrisRegion region = engine.getData().getRegionLoader().load(regionKey);
        if (region == null) {
            return null;
        }
        return measureBiomePicks(engine, complex, region, area);
    }

    private static RegionPicks measureRegionPicks(Engine engine, IrisComplex complex, MapCensus.ViewArea area) {
        List<NoisePickSpread.Option> options = complex.noisePickedRegions().stream()
                .map(region -> PickMeasuring.regionOption(engine, region))
                .toList();
        NoisePickSpread spread = NoisePickSpread.of("Region noise in view", options);
        PickMeasuring.forEachSample(area, (x, z) -> spread.add(complex.getRegionStyleStream().get(x, z)));
        return new RegionPicks(spread);
    }

    // The preview treats every spot as part of the region, so each spot's biome list is the one its surface needs.
    private static BiomePicks measureBiomePicks(Engine engine, IrisComplex complex, IrisRegion region, MapCensus.ViewArea area) {
        List<TypedSpread> spreads = PickMeasuring.biomeSpreads(engine, complex, region);
        PickMeasuring.forEachSample(area, (x, z) -> {
            InferredType type = complex.surfacePickAsIfIn(region, x, z).type();
            spreads.stream()
                    .filter(typed -> typed.type() == type)
                    .findFirst()
                    .ifPresent(typed -> typed.spread().add(complex.biomePickNoise(region, type, x, z)));
        });
        List<NoisePickSpread> plainSpreads = spreads.stream().map(TypedSpread::spread).toList();
        return new BiomePicks(region.getLoadKey(), region.getName(), plainSpreads);
    }

    /**
     * What was measured for the region view; the region key is null when every region was measured.
     */
    private sealed interface Measurement permits RegionPicks, BiomePicks {
        String regionKey();
    }

    private record RegionPicks(NoisePickSpread spread) implements Measurement {
        @Override
        public String regionKey() {
            return null;
        }
    }

    private record BiomePicks(String regionKey, String regionName, List<NoisePickSpread> spreads) implements Measurement {
    }
}
