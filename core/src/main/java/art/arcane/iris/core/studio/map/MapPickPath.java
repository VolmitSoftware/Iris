/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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
import art.arcane.iris.engine.framework.render.RenderType;
import art.arcane.iris.engine.object.value.IRare;
import art.arcane.iris.engine.object.world.biome.InferredType;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.region.IrisRegion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/**
 * How a spot got the region and biome a view shows, step by step, read from the same noise the generator picks with:
 * the region noise, the noise of the region's biome list, then each level of children.
 */
public final class MapPickPath {
    private MapPickPath() {
    }

    public static List<PickStep> explain(Engine engine, MapView view, double x, double z) {
        IrisComplex complex = engine.getComplex();
        IrisRegion region = complex.getRegionStream().get(x, z);
        List<PickStep> path = new ArrayList<>();
        if (region == null) {
            return path;
        }
        path.add(regionStep(engine, region, x, z));
        switch (view) {
            case LAND_BIOMES -> addListStep(path, engine, view, region, InferredType.LAND, x, z);
            case SEA_BIOMES -> addListStep(path, engine, view, region, InferredType.SEA, x, z);
            case CAVE_BIOMES -> addListStep(path, engine, view, region, InferredType.CAVE, x, z);
            case BIOMES, REGIONS, HEIGHT -> addBaseBiomeSteps(path, engine, view, region, x, z);
        }
        return path;
    }

    private static PickStep regionStep(Engine engine, IrisRegion region, double x, double z) {
        IrisComplex complex = engine.getComplex();
        if (complex.getFocusRegion() != null) {
            return PickStep.chosen(Kind.FOCUS, MapLabel.of(engine, region));
        }
        if (complex.getImageMapRuntime().sampleRegion(x, z) != null) {
            return PickStep.chosen(Kind.IMAGE_MAP, MapLabel.of(engine, region));
        }
        List<IrisRegion> regions = engine.getData().getRegionLoader().loadAll(engine.getDimension().getRegions());
        double noise = complex.getRegionStyleStream().get(x, z);
        return new PickStep(Kind.REGION, MapLabel.of(engine, region), null, null, noise,
                rarityBands(regions, candidate -> MapLabel.of(engine, candidate)));
    }

    // The base biome: the noise picks from the land or sea list the continent noise chose, then children replace it.
    private static void addBaseBiomeSteps(List<PickStep> path, Engine engine, MapView view, IrisRegion region, double x, double z) {
        IrisComplex complex = engine.getComplex();
        IrisBiome focus = complex.getFocusBiome();
        if (focus != null) {
            path.add(PickStep.chosen(Kind.FOCUS, biomeLabel(engine, view, focus)));
            addChildSteps(path, engine, view, focus, x, z);
            return;
        }
        IrisBiome mapped = complex.getImageMapRuntime().sampleBiome(x, z);
        if (mapped != null) {
            path.add(PickStep.chosen(Kind.IMAGE_MAP, biomeLabel(engine, view, mapped)));
            return;
        }
        IrisBiome picked = addListStep(path, engine, view, region, complex.getBridgeStream().get(x, z), x, z);
        if (picked != null) {
            addChildSteps(path, engine, view, picked, x, z);
        }
    }

    /** @return the biome the noise picked, or null when the region has no biomes of this type */
    private static IrisBiome addListStep(List<PickStep> path, Engine engine, MapView view, IrisRegion region,
                                         InferredType type, double x, double z) {
        IrisComplex complex = engine.getComplex();
        List<IrisBiome> options = complex.pickableBiomes(region, type);
        if (options.isEmpty()) {
            return null;
        }
        double noise = complex.biomePickNoise(region, type).get(x, z);
        IrisBiome picked = IRare.pick(options, noise);
        path.add(new PickStep(Kind.BIOME, biomeLabel(engine, view, picked), type.name().toLowerCase(Locale.ROOT), null,
                noise, rarityBands(options, option -> biomeLabel(engine, view, option))));
        return picked;
    }

    // A parent that keeps the spot ends the walk: every level below would read the same noise and keep it again.
    private static void addChildSteps(List<PickStep> path, Engine engine, MapView view, IrisBiome biome, double x, double z) {
        IrisComplex complex = engine.getComplex();
        IrisBiome parent = biome;
        for (int level = 0; level < IrisComplex.CHILD_PICK_LEVELS && !parent.getChildren().isEmpty(); level++) {
            List<IrisBiome> slots = complex.childSlots(parent);
            if (slots.isEmpty()) {
                return;
            }
            double noise = complex.childNoise(parent).noiseFast2D(x, z);
            IrisBiome picked = slots.get(slotIndex(noise, slots.size())).withInferredType(parent.getInferredType());
            boolean kept = sameBiome(picked, parent);
            path.add(new PickStep(Kind.CHILD, biomeLabel(engine, view, picked), null, kept ? null : parent.getName(),
                    noise, slotBands(slots, option -> biomeLabel(engine, view, option))));
            if (kept) {
                return;
            }
            parent = picked;
        }
    }

    /** The slot the child noise picks, rounded the way {@code CNG.fit2D} rounds. */
    static int slotIndex(double noise, int slotCount) {
        if (slotCount == 1) {
            return 0;
        }
        long rounded = Math.round(noise * (slotCount - 1));
        return (int) Math.clamp(rounded, 0, slotCount - 1);
    }

    /** Each option's share of the noise, 1 / rarity wide, in the order {@link IRare#pick} walks them. */
    static <T> List<PickBand> rarityBands(List<T> options, Function<T, MapLabel> label) {
        double total = options.stream().mapToDouble(option -> 1D / IRare.get(option)).sum();
        List<PickBand> bands = new ArrayList<>();
        double from = 0D;
        for (int index = 0; index < options.size(); index++) {
            T option = options.get(index);
            double to = index == options.size() - 1 ? 1D : from + 1D / IRare.get(option) / total;
            bands.add(new PickBand(label.apply(option), from, to));
            from = to;
        }
        return bands;
    }

    /** Each slot's share of the child noise, neighbouring slots of one biome joined into one band. */
    static <T> List<PickBand> slotBands(List<T> slots, Function<T, MapLabel> label) {
        List<PickBand> bands = new ArrayList<>();
        int last = slots.size() - 1;
        for (int index = 0; index <= last; index++) {
            double from = index == 0 ? 0D : (index - 0.5D) / last;
            double to = index == last ? 1D : (index + 0.5D) / last;
            MapLabel option = label.apply(slots.get(index));
            PickBand previous = bands.isEmpty() ? null : bands.getLast();
            if (previous != null && previous.option().key().equals(option.key())) {
                bands.set(bands.size() - 1, new PickBand(option, previous.from(), to));
            } else {
                bands.add(new PickBand(option, from, to));
            }
        }
        return bands;
    }

    private static boolean sameBiome(IrisBiome first, IrisBiome second) {
        return Objects.equals(first.getLoadKey(), second.getLoadKey());
    }

    private static MapLabel biomeLabel(Engine engine, MapView view, IrisBiome biome) {
        return MapLabel.of(engine, biome, view == MapView.REGIONS ? RenderType.BIOME : view.colorType());
    }

    public enum Kind {
        /** The region noise picked the region. */
        REGION,
        /** The noise of one of the region's biome lists picked the biome. */
        BIOME,
        /** The biome's child noise kept it or replaced it with a child. */
        CHILD,
        /** The pack's image map set it. */
        IMAGE_MAP,
        /** The studio focuses on one region or biome, so nothing was picked. */
        FOCUS
    }

    /**
     * One step on the way to the spot's biome, starting with its region.
     *
     * @param list     the biome list a {@link Kind#BIOME} step picked from, such as {@code land}
     * @param replaced the parent a {@link Kind#CHILD} step replaced; null when the parent kept the spot
     * @param noise    the noise value that picked it, 0 to 1; null when nothing was picked
     * @param bands    every option's share of that noise, empty when nothing was picked
     */
    public record PickStep(Kind kind, MapLabel picked, String list, String replaced, Double noise, List<PickBand> bands) {
        static PickStep chosen(Kind kind, MapLabel picked) {
            return new PickStep(kind, picked, null, null, null, List.of());
        }
    }

    /** One option's share of a noise: the noise picks it from {@code from} up to {@code to}. */
    public record PickBand(MapLabel option, double from, double to) {
    }
}
