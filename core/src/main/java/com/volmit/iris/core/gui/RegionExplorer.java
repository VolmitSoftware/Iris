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
import com.volmit.iris.core.gui.RegionHeightCoverage.BiomeRange;
import com.volmit.iris.core.gui.RegionNavigator.RegionRow;
import com.volmit.iris.core.gui.components.RenderType;
import com.volmit.iris.engine.IrisComplex;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.InferredType;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisRegion;

import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The region view's explorer: choosing a region fills the map with its biomes and measures which of them covers which
 * ground height in view. Measuring runs off the Swing thread and only the newest request counts.
 */
final class RegionExplorer {
    private static final int GROUND_SAMPLES_PER_SIDE = 96;
    private static final List<InferredType> PLACED_TYPES = List.of(InferredType.LAND, InferredType.SHORE, InferredType.SEA);

    private final Supplier<Engine> engineSource;
    private final Consumer<String> onPreviewChanged;
    private final RegionNavigator navigator = new RegionNavigator(this::choose);
    private final RegionInspectorPanel inspector = new RegionInspectorPanel();
    private final ThreadPoolExecutor measurer = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1), RegionExplorer::daemonThread, new ThreadPoolExecutor.DiscardOldestPolicy());
    private final AtomicLong measurementGeneration = new AtomicLong();
    private String chosenRegionKey;
    private MapCensus.ViewArea latestArea;
    private MapCensus.ViewArea measuredArea;

    /**
     * @param onPreviewChanged receives the region to fill the map with, or null to show every region again
     */
    RegionExplorer(Supplier<Engine> engineSource, Consumer<String> onPreviewChanged) {
        this.engineSource = engineSource;
        this.onPreviewChanged = onPreviewChanged;
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
            rows.add(new RegionRow(region.getLoadKey(), region.getName(),
                    region.getColor(engine.getComplex(), RenderType.REGION).getRGB() & 0xFFFFFF));
        }
        navigator.showRegions(rows);
    }

    /**
     * Measures the chosen region against the ground in this area, unless that was already done.
     */
    void measure(MapCensus.ViewArea area) {
        latestArea = area;
        if (chosenRegionKey == null || area == null || area.equals(measuredArea)) {
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
        if (regionKey == null) {
            inspector.showNothingChosen();
            return;
        }
        inspector.showMeasuring(regionName(regionKey));
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
        if (measurement.coverage() == null) {
            inspector.showBiomeTerrain(measurement.regionName());
        } else {
            inspector.showCoverage(measurement.regionName(), measurement.coverage());
        }
    }

    private Measurement measureSafely(String regionKey, MapCensus.ViewArea area) {
        try {
            return measureRegion(regionKey, area);
        } catch (Throwable error) {
            Iris.reportError(error);
            return null;
        }
    }

    private Measurement measureRegion(String regionKey, MapCensus.ViewArea area) {
        Engine engine = engineSource.get();
        IrisComplex complex = engine == null ? null : engine.getComplex();
        IrisRegion region = complex == null ? null : engine.getData().getRegionLoader().load(regionKey);
        if (region == null) {
            return null;
        }
        if (!complex.isHybridTerrain()) {
            return new Measurement(regionKey, region.getName(), null);
        }
        RegionHeightCoverage coverage = RegionHeightCoverage.measure(biomeRanges(engine, region), groundHeights(complex, area));
        return new Measurement(regionKey, region.getName(), coverage);
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

    // In hybrid terrain the overlay noise is the ground's height above the fluid, before any biome adds its own terrain.
    private static int[] groundHeights(IrisComplex complex, MapCensus.ViewArea area) {
        int[] heights = new int[GROUND_SAMPLES_PER_SIDE * GROUND_SAMPLES_PER_SIDE];
        double stepX = (area.maximumX() - area.minimumX()) / GROUND_SAMPLES_PER_SIDE;
        double stepZ = (area.maximumZ() - area.minimumZ()) / GROUND_SAMPLES_PER_SIDE;
        for (int row = 0; row < GROUND_SAMPLES_PER_SIDE; row++) {
            for (int column = 0; column < GROUND_SAMPLES_PER_SIDE; column++) {
                double x = area.minimumX() + (column + 0.5D) * stepX;
                double z = area.minimumZ() + (row + 0.5D) * stepZ;
                heights[row * GROUND_SAMPLES_PER_SIDE + column] = (int) Math.round(complex.getOverlayStream().get(x, z));
            }
        }
        return heights;
    }

    private static Thread daemonThread(Runnable task) {
        Thread thread = new Thread(task, "Iris Region Explorer");
        thread.setDaemon(true);
        return thread;
    }

    /**
     * @param coverage how the region's biomes cover the ground in view, or null when the dimension uses biome terrain
     */
    private record Measurement(String regionKey, String regionName, RegionHeightCoverage coverage) {
    }
}
