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

import com.volmit.iris.Iris;
import com.volmit.iris.core.gui.NoisePickSpread.Option;
import com.volmit.iris.core.gui.RegionHeightCoverage.BiomeRange;
import com.volmit.iris.core.gui.RegionNavigator.RegionRow;
import com.volmit.iris.core.gui.components.PickDisplay;
import com.volmit.iris.core.gui.components.RenderType;
import com.volmit.iris.engine.IrisComplex;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.InferredType;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisRegion;

import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The region view's explorer: shows how the region noise picks the regions, and for a chosen region fills the map with
 * its biomes and shows how they are picked. Measuring what is in view runs off the Swing thread and only the newest
 * request counts.
 */
final class RegionExplorer {
    private static final int SAMPLES_PER_SIDE = 96;
    private static final List<InferredType> PLACED_TYPES = List.of(InferredType.LAND, InferredType.SHORE, InferredType.SEA);

    private final Supplier<Engine> engineSource;
    private final Consumer<String> onPreviewChanged;
    private final RegionNavigator navigator = new RegionNavigator(this::choose);
    private final RegionInspectorPanel inspector;
    private final ThreadPoolExecutor measurer = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1), RegionExplorer::daemonThread, new ThreadPoolExecutor.DiscardOldestPolicy());
    private final AtomicLong measurementGeneration = new AtomicLong();
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
        this.inspector = new RegionInspectorPanel(onDisplayChosen);
    }

    RegionNavigator navigator() {
        return navigator;
    }

    RegionInspectorPanel inspector() {
        return inspector;
    }

    void loadRegions() {
        Engine engine = engineSource.get();
        if (engine == null || engine.getComplex() == null) {
            return;
        }
        List<RegionRow> rows = new ArrayList<>();
        for (IrisRegion region : engine.getData().getRegionLoader().loadAll(engine.getDimension().getRegions())) {
            rows.add(new RegionRow(region.getLoadKey(), region.getName(), regionColor(engine, region)));
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
        long generation = measurementGeneration.incrementAndGet();
        measurer.execute(() -> {
            Measurement measurement = measureSafely(regionKey, area);
            EventQueue.invokeLater(() -> {
                if (generation == measurementGeneration.get() && measurement != null) {
                    show(measurement);
                }
            });
        });
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
        measurer.shutdownNow();
    }

    private void choose(String regionKey) {
        chosenRegionKey = regionKey;
        measuredArea = null;
        measurementGeneration.incrementAndGet();
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
            case RegionPicks picks -> inspector.showRegionPicks(picks.spread(), picks.notes());
            case BiomePicks picks -> inspector.showBiomePicks(picks.regionName(), picks.spreads(), picks.notes());
            case HeightPicks picks -> inspector.showCoverage(picks.regionName(), picks.coverage(), picks.notes());
        }
    }

    private Measurement measureSafely(String regionKey, MapCensus.ViewArea area) {
        try {
            return measurePicks(regionKey, area);
        } catch (Throwable error) {
            Iris.reportError(error);
            return null;
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
        return complex.isHybridTerrain()
                ? measureHeightPicks(engine, complex, region, area)
                : measureBiomePicks(engine, complex, region, area);
    }

    private static RegionPicks measureRegionPicks(Engine engine, IrisComplex complex, MapCensus.ViewArea area) {
        List<Option> options = complex.noisePickedRegions().stream()
                .map(region -> new Option(region.getLoadKey(), region.getName(), regionColor(engine, region), region.getRarity()))
                .toList();
        NoisePickSpread spread = NoisePickSpread.of("Region noise in view", options, complex.isLegacyRarity());
        forEachSample(area, (x, z) -> spread.add(complex.getRegionStyleStream().get(x, z)));
        List<String> notes = new ArrayList<>();
        addSpreadNotes(notes, spread, "region");
        addRarityNotes(notes, complex, List.of(spread));
        return new RegionPicks(spread, notes);
    }

    private static BiomePicks measureBiomePicks(Engine engine, IrisComplex complex, IrisRegion region, MapCensus.ViewArea area) {
        Map<InferredType, NoisePickSpread> spreads = new LinkedHashMap<>();
        for (InferredType type : PLACED_TYPES) {
            List<Option> options = complex.noisePickedBiomes(region, type).stream()
                    .map(biome -> biomeOption(engine, biome))
                    .toList();
            if (!options.isEmpty()) {
                spreads.put(type, NoisePickSpread.of(typeName(type) + " biome noise in view", options, complex.isLegacyRarity()));
            }
        }
        forEachSample(area, (x, z) -> {
            IrisComplex.BiomePick pick = complex.surfacePickAsIfIn(region, x, z);
            NoisePickSpread spread = spreads.get(pick.type());
            if (spread != null) {
                spread.add(complex.biomePickNoise(region, pick.type(), x, z));
            }
        });
        List<String> notes = new ArrayList<>();
        spreads.forEach((type, spread) -> addSpreadNotes(notes, spread, typeName(type).toLowerCase(Locale.ROOT) + " biome"));
        addChildBiomeNote(notes, complex, region);
        addRarityNotes(notes, complex, spreads.values());
        return new BiomePicks(region.getLoadKey(), region.getName(), List.copyOf(spreads.values()), notes);
    }

    private static HeightPicks measureHeightPicks(Engine engine, IrisComplex complex, IrisRegion region, MapCensus.ViewArea area) {
        RegionHeightCoverage coverage = RegionHeightCoverage.measure(biomeRanges(engine, region), pickingHeights(complex, area));
        List<String> notes = new ArrayList<>(coverage.notes());
        notes.add("Noise shows the height biomes are picked at: the overlay noise plus the dimension's biome placement "
                + "jitter. Where ranges share a height, each biome's competition noise, divided by its rarity, decides.");
        return new HeightPicks(region.getLoadKey(), region.getName(), coverage, notes);
    }

    private static void addSpreadNotes(List<String> notes, NoisePickSpread spread, String noun) {
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
            notes.add(String.join(", ", starved) + (starved.size() == 1 ? " gets" : " get")
                    + " under half " + (starved.size() == 1 ? "its" : "their") + " weight in view (▼): the "
                    + noun + " noise rarely reaches " + (starved.size() == 1 ? "its band." : "their bands."));
        }
    }

    private static void addChildBiomeNote(List<String> notes, IrisComplex complex, IrisRegion region) {
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

    private static void addRarityNotes(List<String> notes, IrisComplex complex, Collection<NoisePickSpread> spreads) {
        if (spreads.stream().allMatch(spread -> spread.bands().size() <= 1)) {
            return;
        }
        notes.add(complex.isLegacyRarity()
                ? "Legacy rarity: each weight is the total rarity minus the option's own."
                : "Each weight is one over the rarity, so rarity 2 gets half the noise of rarity 1.");
        notes.add("Measured on a " + SAMPLES_PER_SIDE + " × " + SAMPLES_PER_SIDE + " grid over the map in view.");
    }

    private static Option biomeOption(Engine engine, IrisBiome biome) {
        return new Option(biome.getLoadKey(), biome.getName(), biome.getColor(engine, RenderType.BIOME).getRGB() & 0xFFFFFF,
                biome.getRarity());
    }

    private static int regionColor(Engine engine, IrisRegion region) {
        return region.getColor(engine.getComplex(), RenderType.REGION).getRGB() & 0xFFFFFF;
    }

    private static String typeName(InferredType type) {
        String name = type.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static List<BiomeRange> biomeRanges(Engine engine, IrisRegion region) {
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

    // HYBRID terrain picks biomes by the overlay noise plus the placement jitter, in blocks above the fluid.
    private static int[] pickingHeights(IrisComplex complex, MapCensus.ViewArea area) {
        int[] heights = new int[SAMPLES_PER_SIDE * SAMPLES_PER_SIDE];
        int[] next = {0};
        forEachSample(area, (x, z) -> heights[next[0]++] = (int) Math.round(complex.getPlacementHeightStream().get(x, z)));
        return heights;
    }

    private static void forEachSample(MapCensus.ViewArea area, SampleVisitor visitor) {
        double stepX = (area.maximumX() - area.minimumX()) / SAMPLES_PER_SIDE;
        double stepZ = (area.maximumZ() - area.minimumZ()) / SAMPLES_PER_SIDE;
        for (int row = 0; row < SAMPLES_PER_SIDE; row++) {
            for (int column = 0; column < SAMPLES_PER_SIDE; column++) {
                visitor.visit(area.minimumX() + (column + 0.5D) * stepX, area.minimumZ() + (row + 0.5D) * stepZ);
            }
        }
    }

    private static Thread daemonThread(Runnable task) {
        Thread thread = new Thread(task, "Iris Region Explorer");
        thread.setDaemon(true);
        return thread;
    }

    @FunctionalInterface
    private interface SampleVisitor {
        void visit(double x, double z);
    }

    /**
     * What was measured for the region view; the region key is null when every region was measured.
     */
    private sealed interface Measurement permits RegionPicks, BiomePicks, HeightPicks {
        String regionKey();
    }

    private record RegionPicks(NoisePickSpread spread, List<String> notes) implements Measurement {
        @Override
        public String regionKey() {
            return null;
        }
    }

    private record BiomePicks(String regionKey, String regionName, List<NoisePickSpread> spreads, List<String> notes)
            implements Measurement {
    }

    private record HeightPicks(String regionKey, String regionName, RegionHeightCoverage coverage, List<String> notes)
            implements Measurement {
    }
}
