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

import com.volmit.iris.core.gui.NoisePickSpread.Band;
import com.volmit.iris.core.gui.NoisePickSpread.Option;
import com.volmit.iris.engine.HybridBiomeSelector;
import com.volmit.iris.engine.IrisComplex;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.InferredType;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisDimension;
import com.volmit.iris.engine.object.IrisRegion;
import org.bukkit.block.Biome;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Looks up everything the hover card can tell about one spot of the world; runs off the Swing thread.
 */
final class MapHoverProbe {
    // IrisComplex measures slope as the height change over this many blocks.
    private static final double SLOPE_STREAM_RANGE_BLOCKS = 3D;

    private MapHoverProbe() {
    }

    /**
     * @param previewRegionKey the region the region preview treats every spot as part of, or null for the real world
     * @param explainBiomePick whether the card shows the surface biome, so the whole way to it is worth looking up;
     *                         otherwise only how the region was picked
     */
    static Details sample(Engine engine, double worldX, double worldZ, String previewRegionKey, boolean explainBiomePick) {
        IrisComplex complex = engine.getComplex();
        IrisRegion previewRegion = previewRegionKey == null ? null : engine.getData().getRegionLoader().load(previewRegionKey);
        IrisRegion region = previewRegion != null ? previewRegion : complex.getRegionStream().get(worldX, worldZ);
        IrisComplex.BiomePick surfacePick = complex.surfacePickAsIfIn(region, worldX, worldZ);
        Biome vanillaBiome = complex.getTrueBiomeDerivativeStream().get(worldX, worldZ);
        double slope = complex.getSlopeStream().getDouble(worldX, worldZ);
        IrisDimension dimension = engine.getDimension();
        return new Details(
                worldX,
                worldZ,
                surfacePick.biome(),
                complex.getCaveBiomeStream().get(worldX, worldZ),
                region,
                vanillaBiome == null ? null : vanillaBiome.getKey().getKey(),
                complex.getHeightStream().getDouble(worldX, worldZ),
                dimension == null ? 0D : dimension.getFluidHeight(),
                Math.toDegrees(Math.atan(slope / SLOPE_STREAM_RANGE_BLOCKS)),
                pickPath(engine, region, previewRegion != null, explainBiomePick ? surfacePick : null, worldX, worldZ)
        );
    }

    // The region preview chose the region itself; the real map picks it by the region noise.
    private static List<PickStep> pickPath(Engine engine, IrisRegion region, boolean previewed,
                                           IrisComplex.BiomePick surfacePick, double x, double z) {
        List<PickStep> path = new ArrayList<>();
        path.add(previewed ? previewedRegionStep(engine, region) : regionStep(engine, region, x, z));
        if (surfacePick == null || surfacePick.type() == null || engine.getComplex().getFocusBiome() != null) {
            return path;
        }
        PickStep biomeStep = engine.getComplex().isHybridTerrain()
                ? heightStep(engine, region, surfacePick.type(), x, z)
                : noiseStep(engine, region, surfacePick.type(), x, z);
        if (biomeStep == null) {
            return path;
        }
        path.add(biomeStep);
        if (!biomeStep.picked().key().equals(surfacePick.biome().getLoadKey())) {
            path.add(new PickStep(PickMeasuring.biomeOption(engine, surfacePick.biome()), "child", null, List.of(), Double.NaN));
        }
        return path;
    }

    private static PickStep regionStep(Engine engine, IrisRegion region, double x, double z) {
        IrisComplex complex = engine.getComplex();
        double noise = complex.getRegionStyleStream().get(x, z);
        List<Option> regions = complex.noisePickedRegions().stream()
                .map(candidate -> PickMeasuring.regionOption(engine, candidate))
                .toList();
        return new PickStep(PickMeasuring.regionOption(engine, region), "region", noiseText(noise),
                NoisePickSpread.splitNoise(regions, complex.isLegacyRarity()), noise);
    }

    private static PickStep previewedRegionStep(Engine engine, IrisRegion region) {
        return new PickStep(PickMeasuring.regionOption(engine, region), "previewed", null, List.of(), Double.NaN);
    }

    private static PickStep noiseStep(Engine engine, IrisRegion region, InferredType type, double x, double z) {
        IrisComplex complex = engine.getComplex();
        double noise = complex.biomePickNoise(region, type, x, z);
        List<Option> biomes = complex.noisePickedBiomes(region, type).stream()
                .map(biome -> PickMeasuring.biomeOption(engine, biome))
                .toList();
        IrisBiome picked = complex.biomePickedByNoise(region, type, x, z);
        return new PickStep(PickMeasuring.biomeOption(engine, picked), typeName(type), noiseText(noise),
                NoisePickSpread.splitNoise(biomes, complex.isLegacyRarity()), noise);
    }

    // HYBRID terrain picks by the height a spot is placed at, and only competes where biome ranges share that height.
    private static PickStep heightStep(Engine engine, IrisRegion region, InferredType type, double x, double z) {
        HybridBiomeSelector.Choice choice = engine.getComplex().explainHybridChoice(region, type, x, z);
        if (choice == null) {
            return null;
        }
        return new PickStep(PickMeasuring.biomeOption(engine, choice.biome()), typeName(type) + ", " + competitionText(choice),
                signedHeightText(choice.placementHeight()), List.of(), Double.NaN);
    }

    private static String competitionText(HybridBiomeSelector.Choice choice) {
        if (choice.fallback()) {
            return "nearest range";
        }
        if (choice.rivalCount() == 0) {
            return "only fit";
        }
        return "beat " + choice.rivalCount();
    }

    private static String typeName(InferredType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }

    private static String noiseText(double noise) {
        return String.format(Locale.ROOT, "%.3f", noise);
    }

    private static String signedHeightText(double heightAboveSea) {
        long rounded = Math.round(heightAboveSea);
        return rounded < 0 ? "−" + -rounded : "+" + rounded;
    }

    record Details(
            double worldX,
            double worldZ,
            IrisBiome surfaceBiome,
            IrisBiome caveBiome,
            IrisRegion region,
            String vanillaBiome,
            double height,
            double fluidHeight,
            double slopeDegrees,
            List<PickStep> pickPath
    ) {
    }

    /**
     * One step on the way to the spot's biome, starting with its region.
     *
     * @param reason what picked it: its kind, the biome list the noise picked from, or "child"
     * @param value  the noise value or placement height that picked it, or null
     * @param bands  the noise bands of all the options the noise picked from, empty when no noise picked it
     */
    record PickStep(Option picked, String reason, String value, List<Band> bands, double noise) {
        boolean showsBands() {
            return !bands.isEmpty();
        }
    }
}
