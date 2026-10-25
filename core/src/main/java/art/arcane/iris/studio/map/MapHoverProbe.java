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

import art.arcane.iris.studio.map.NoisePickSpread.Band;
import art.arcane.iris.studio.map.NoisePickSpread.Option;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.InferredType;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.terrain.IrisRegion;

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
        IrisComplex.BiomePick surfacePick = previewRegion != null
                ? complex.surfacePickAsIfIn(region, worldX, worldZ)
                : complex.basePickIn(region, worldX, worldZ);
        String vanillaBiomeKey = complex.getTrueBiomeStream().get(worldX, worldZ).getDerivativeKey();
        double slope = complex.getSlopeStream().getDouble(worldX, worldZ);
        IrisDimension dimension = engine.getDimension();
        return new Details(
                worldX,
                worldZ,
                surfacePick.biome(),
                complex.getCaveBiomeStream().get(worldX, worldZ),
                region,
                vanillaBiomeKey == null ? null : vanillaBiomeKey.substring(vanillaBiomeKey.indexOf(':') + 1),
                complex.getHeightStream().getDouble(worldX, worldZ),
                dimension == null ? 0D : dimension.getFluidHeight(),
                Math.toDegrees(Math.atan(slope / SLOPE_STREAM_RANGE_BLOCKS)),
                pickPath(engine, region, previewRegion != null, explainBiomePick ? surfacePick : null, worldX, worldZ)
        );
    }

    // The region preview chose the region itself; the real map picks it by the region noise. Each explains the biome it
    // draws: the preview its surface biome, the real map its base biome, before the height corrects it near coasts.
    private static List<PickStep> pickPath(Engine engine, IrisRegion region, boolean previewed,
                                           IrisComplex.BiomePick surfacePick, double x, double z) {
        List<PickStep> path = new ArrayList<>();
        path.add(previewed ? previewedRegionStep(engine, region) : regionStep(engine, region, x, z));
        if (surfacePick == null || surfacePick.type() == null || engine.getComplex().getFocusBiome() != null) {
            return path;
        }
        PickStep biomeStep = noiseStep(engine, region, surfacePick.type(), x, z);
        path.add(biomeStep);
        if (!biomeStep.picked().key().equals(surfacePick.biome().getLoadKey())) {
            path.add(new PickStep(StepKind.CHILD, PickMeasuring.biomeOption(engine, surfacePick.biome()), null, null, List.of(),
                    Double.NaN));
        }
        return path;
    }

    private static PickStep regionStep(Engine engine, IrisRegion region, double x, double z) {
        IrisComplex complex = engine.getComplex();
        double noise = complex.getRegionStyleStream().get(x, z);
        List<Option> regions = complex.noisePickedRegions().stream()
                .map(candidate -> PickMeasuring.regionOption(engine, candidate))
                .toList();
        return new PickStep(StepKind.REGION_NOISE, PickMeasuring.regionOption(engine, region), null, noiseText(noise),
                NoisePickSpread.splitNoise(regions), noise);
    }

    private static PickStep previewedRegionStep(Engine engine, IrisRegion region) {
        return new PickStep(StepKind.PREVIEWED_REGION, PickMeasuring.regionOption(engine, region), null, null, List.of(),
                Double.NaN);
    }

    private static PickStep noiseStep(Engine engine, IrisRegion region, InferredType type, double x, double z) {
        IrisComplex complex = engine.getComplex();
        double noise = complex.biomePickNoise(region, type, x, z);
        List<Option> biomes = complex.noisePickedBiomes(region, type).stream()
                .map(biome -> PickMeasuring.biomeOption(engine, biome))
                .toList();
        IrisBiome picked = complex.biomePickedByNoise(region, type, x, z);
        return new PickStep(StepKind.BIOME_NOISE, PickMeasuring.biomeOption(engine, picked), typeName(type), noiseText(noise),
                NoisePickSpread.splitNoise(biomes), noise);
    }

    private static String typeName(InferredType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }

    private static String noiseText(double noise) {
        return String.format(Locale.ROOT, "%.3f", noise);
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
     * How a step on the way to the spot's biome was picked.
     */
    enum StepKind {
        REGION_NOISE,
        PREVIEWED_REGION,
        BIOME_NOISE,
        CHILD
    }

    /**
     * One step on the way to the spot's biome, starting with its region.
     *
     * @param detail the biome list the step picked from; null for the rest
     * @param value  the noise value that picked it, or null
     * @param bands  the noise bands of all the options the noise picked from, empty when no noise picked it
     */
    record PickStep(StepKind kind, Option picked, String detail, String value, List<Band> bands, double noise) {
    }
}
