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
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.world.biome.InferredType;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import com.volmit.world.Weighted;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How the noise picks the regions or biomes across an area of the map, measured on a grid of spots: for each noise,
 * how its values in the area spread over the bands that pick each option, and how much ground each option gets
 * there against its share of the noise.
 */
public final class MapPickInspection {
    public static final int HISTOGRAM_BINS = 50;
    static final int SAMPLES_PER_SIDE = 96;
    private static final List<InferredType> PLACED_LISTS = List.of(InferredType.LAND, InferredType.SHORE, InferredType.SEA);

    private MapPickInspection() {
    }

    public static Inspection measure(Engine engine, MapLayer layer, Area area) {
        if (layer.previews()) {
            return measurePreview(engine, layer.previewRegion(), area);
        }
        return switch (layer.view()) {
            case REGIONS -> measureRegions(engine, area);
            case BIOMES -> measureBiomes(engine, area);
        };
    }

    private static Inspection measureRegions(Engine engine, Area area) {
        IrisComplex complex = engine.getComplex();
        if (complex.getFocusRegion() != null) {
            return new Inspection("One region is focused", List.of());
        }
        SpreadCounter regions = new SpreadCounter("Region noise", MapPickPath.regionBands(engine), Weighted.Edge.EARLIER);
        area.forEachSpot((x, z) -> {
            if (complex.getImageMapRuntime().sampleRegion(x, z) == null) {
                regions.add(complex.getRegionStyleStream().get(x, z));
            }
        });
        return new Inspection("Region noise by weight", List.of(new Section(null, 1D, 0, List.of(regions.spread()))));
    }

    // The previewed region owns every spot, so each spot's biome list is the one its surface needs.
    private static Inspection measurePreview(Engine engine, IrisRegion region, Area area) {
        IrisComplex complex = engine.getComplex();
        if (complex.getFocusBiome() != null) {
            return measureFocusBiome(engine, MapView.REGIONS, area);
        }
        RegionSamples samples = new RegionSamples(engine, MapView.REGIONS, region);
        area.forEachSpot((x, z) -> samples.add(complex.surfacePickAsIfIn(region, x, z).list(), x, z));
        return new Inspection("Biome noise by weight", List.of(samples.section(1)));
    }

    // Each region picks the base biomes on its ground from the list the continent chose there.
    private static Inspection measureBiomes(Engine engine, Area area) {
        IrisComplex complex = engine.getComplex();
        if (complex.getFocusBiome() != null) {
            return measureFocusBiome(engine, MapView.BIOMES, area);
        }
        Map<IrisRegion, RegionSamples> samplesByRegion = new IdentityHashMap<>();
        int[] picked = {0};
        area.forEachSpot((x, z) -> {
            IrisRegion region = complex.getRegionStream().get(x, z);
            if (region == null || complex.getImageMapRuntime().sampleBiome(x, z) != null) {
                return;
            }
            picked[0]++;
            samplesByRegion.computeIfAbsent(region, owner -> new RegionSamples(engine, MapView.BIOMES, owner))
                    .add(complex.getBridgeStream().get(x, z), x, z);
        });
        List<Section> sections = samplesByRegion.values().stream()
                .sorted(Comparator.comparingInt(RegionSamples::count).reversed())
                .map(samples -> samples.section(picked[0]))
                .toList();
        return new Inspection("Each region's biome noise", sections);
    }

    // A focused biome skips the regions and their lists: every spot starts at it.
    private static Inspection measureFocusBiome(Engine engine, MapView view, Area area) {
        IrisComplex complex = engine.getComplex();
        IrisBiome focus = complex.getFocusBiome();
        ChildCounters children = new ChildCounters(engine, view);
        area.forEachSpot((x, z) -> children.add(ChildPicks.from(complex, focus, x, z)));
        List<Spread> spreads = children.mostSampledFirst();
        Section section = new Section(MapPickPath.biomeLabel(engine, view, focus), 1D, distinctOptions(spreads), spreads);
        return new Inspection("Child noise by weight", List.of(section));
    }

    private static int distinctOptions(List<Spread> spreads) {
        return (int) spreads.stream()
                .flatMap(spread -> spread.rows().stream())
                .map(row -> row.option().key())
                .distinct()
                .count();
    }

    /** The spots of one region in the area, and the noises that picked their biomes. */
    private static final class RegionSamples {
        private final Engine engine;
        private final MapView view;
        private final IrisRegion region;
        private final Map<InferredType, SpreadCounter> lists = new EnumMap<>(InferredType.class);
        private final ChildCounters children;
        private int count;

        private RegionSamples(Engine engine, MapView view, IrisRegion region) {
            this.engine = engine;
            this.view = view;
            this.region = region;
            this.children = new ChildCounters(engine, view);
        }

        void add(InferredType list, double x, double z) {
            count++;
            IrisComplex complex = engine.getComplex();
            IrisBiome picked = complex.biomePicks(region, list).get(x, z);
            if (picked == null) {
                return;
            }
            lists.computeIfAbsent(list, this::listCounter).add(complex.biomePickNoise(region, list).get(x, z));
            children.add(ChildPicks.from(complex, picked, x, z));
        }

        int count() {
            return count;
        }

        Section section(int pickedSpots) {
            List<Spread> spreads = new ArrayList<>();
            for (InferredType list : PLACED_LISTS) {
                SpreadCounter counter = lists.get(list);
                if (counter != null) {
                    spreads.add(counter.spread());
                }
            }
            spreads.addAll(children.mostSampledFirst());
            double share = pickedSpots == 0 ? 0D : count / (double) pickedSpots;
            return new Section(MapLabel.of(engine, region), share, distinctOptions(spreads), spreads);
        }

        private SpreadCounter listCounter(InferredType list) {
            String name = MapPickPath.listName(list);
            String title = Character.toUpperCase(name.charAt(0)) + name.substring(1) + " biome noise";
            return new SpreadCounter(title, MapPickPath.biomeBands(engine, view, region, list), Weighted.Edge.EARLIER);
        }
    }

    /** The child noise of every parent the spots reach, each measured over the spots that reach it. */
    private static final class ChildCounters {
        private final Engine engine;
        private final MapView view;
        private final Map<String, SpreadCounter> byParent = new LinkedHashMap<>();

        private ChildCounters(Engine engine, MapView view) {
            this.engine = engine;
            this.view = view;
        }

        void add(List<ChildPicks.ChildPick> picks) {
            for (ChildPicks.ChildPick pick : picks) {
                byParent.computeIfAbsent(pick.parent().getLoadKey(), parent -> counterOf(pick)).add(pick.noise());
            }
        }

        List<Spread> mostSampledFirst() {
            return byParent.values().stream()
                    .sorted(Comparator.comparingLong(SpreadCounter::samples).reversed())
                    .map(SpreadCounter::spread)
                    .toList();
        }

        private SpreadCounter counterOf(ChildPicks.ChildPick pick) {
            return new SpreadCounter(pick.parent().getName() + " child noise",
                    MapPickPath.weightBands(pick.options(), option -> MapPickPath.biomeLabel(engine, view, option)),
                    IrisComplex.CHILD_EDGE);
        }
    }

    /** Counts one noise's values into its histogram and into the band each value picks. */
    private static final class SpreadCounter {
        private final String title;
        private final List<MapPickPath.PickBand> bands;
        private final Weighted.Edge edge;
        private final long[] histogram = new long[HISTOGRAM_BINS];
        private final long[] samplesByBand;
        private long samples;

        private SpreadCounter(String title, List<MapPickPath.PickBand> bands, Weighted.Edge edge) {
            this.title = title;
            this.bands = bands;
            this.edge = edge;
            this.samplesByBand = new long[bands.size()];
        }

        void add(double noise) {
            if (bands.isEmpty() || !Double.isFinite(noise)) {
                return;
            }
            samples++;
            histogram[Math.clamp((long) (noise * HISTOGRAM_BINS), 0, HISTOGRAM_BINS - 1)]++;
            samplesByBand[bandIndexOf(noise)]++;
        }

        long samples() {
            return samples;
        }

        // Every option once, with its bands' widths and spots added up, in the order the noise meets them.
        Spread spread() {
            Map<String, Row> rows = new LinkedHashMap<>();
            for (int index = 0; index < bands.size(); index++) {
                MapPickPath.PickBand band = bands.get(index);
                Row row = new Row(band.option(), band.to() - band.from(), share(samplesByBand[index]));
                rows.merge(band.option().key(), row, Row::plus);
            }
            return new Spread(title, bands, histogram.clone(), samples, List.copyOf(rows.values()));
        }

        private double share(long count) {
            return samples == 0 ? 0D : count / (double) samples;
        }

        // The generator gives values below 0 the first option and values above 1 the last, as these bands do; a value
        // on the edge between two bands goes where the list's edge rule sends it.
        private int bandIndexOf(double noise) {
            for (int index = 0; index < bands.size(); index++) {
                double to = bands.get(index).to();
                if (edge == Weighted.Edge.EARLIER ? noise <= to : noise < to) {
                    return index;
                }
            }
            return bands.size() - 1;
        }
    }

    /** A rectangle of the world, sampled at the centres of a {@value #SAMPLES_PER_SIDE}² grid. */
    public record Area(double minX, double minZ, double maxX, double maxZ) {
        public Area {
            if (!Double.isFinite(minX) || !Double.isFinite(minZ) || !Double.isFinite(maxX) || !Double.isFinite(maxZ)
                    || maxX <= minX || maxZ <= minZ) {
                throw new IllegalArgumentException("An area needs finite corners, the second beyond the first");
            }
        }

        void forEachSpot(SpotVisitor visitor) {
            double stepX = (maxX - minX) / SAMPLES_PER_SIDE;
            double stepZ = (maxZ - minZ) / SAMPLES_PER_SIDE;
            for (int row = 0; row < SAMPLES_PER_SIDE; row++) {
                for (int column = 0; column < SAMPLES_PER_SIDE; column++) {
                    visitor.visit(minX + (column + 0.5D) * stepX, minZ + (row + 0.5D) * stepZ);
                }
            }
        }
    }

    @FunctionalInterface
    interface SpotVisitor {
        void visit(double x, double z);
    }

    /** @param detail what the picks in the area are, in a few words */
    public record Inspection(String detail, List<Section> sections) {
    }

    /**
     * The picks on one region's ground, or on all of it in the region view.
     *
     * @param region     the region, or the focused biome; null in the region view, where the regions themselves are picked
     * @param share      how much of the area's picked ground the region holds
     * @param biomeCount how many biomes its noises can pick here
     */
    public record Section(MapLabel region, double share, int biomeCount, List<Spread> spreads) {
    }

    /**
     * One noise in the area.
     *
     * @param histogram how many spots had a value in each of {@value #HISTOGRAM_BINS} equal steps from 0 to 1
     * @param rows      every option once, in the order the noise meets it
     */
    public record Spread(String title, List<MapPickPath.PickBand> bands, long[] histogram, long samples, List<Row> rows) {
    }

    /**
     * @param weight how much of the noise from 0 to 1 picks the option
     * @param inView how much of the area's values pick it
     */
    public record Row(MapLabel option, double weight, double inView) {
        Row plus(Row other) {
            return new Row(option, weight + other.weight, inView + other.inView);
        }
    }
}
