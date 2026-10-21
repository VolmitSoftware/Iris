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

import com.volmit.iris.engine.HybridBiomeSelector;
import com.volmit.iris.engine.IrisComplex;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.InferredType;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisDimension;
import com.volmit.iris.engine.object.IrisRegion;
import org.bukkit.block.Biome;

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
     * @param explainPicks     also look up the noise values that picked the region and biome, for the region view
     */
    static Details sample(Engine engine, double worldX, double worldZ, String previewRegionKey, boolean explainPicks) {
        IrisComplex complex = engine.getComplex();
        IrisRegion previewRegion = previewRegionKey == null ? null : engine.getData().getRegionLoader().load(previewRegionKey);
        IrisRegion region = previewRegion != null ? previewRegion : complex.getRegionStream().get(worldX, worldZ);
        IrisBiome surfaceBiome = previewRegion != null
                ? complex.surfaceBiomeAsIfIn(previewRegion, worldX, worldZ)
                : complex.getBaseBiomeStream().get(worldX, worldZ);
        IrisBiome caveBiome = complex.getCaveBiomeStream().get(worldX, worldZ);
        InferredType terrainKind = complex.getBridgeStream().get(worldX, worldZ);
        Biome vanillaBiome = complex.getTrueBiomeDerivativeStream().get(worldX, worldZ);
        double height = complex.getHeightStream().getDouble(worldX, worldZ);
        double slope = complex.getSlopeStream().getDouble(worldX, worldZ);
        IrisDimension dimension = engine.getDimension();
        return new Details(
                worldX,
                worldZ,
                surfaceBiome,
                caveBiome,
                region,
                terrainKindName(surfaceBiome, terrainKind),
                vanillaBiome == null ? null : vanillaBiome.getKey().getKey(),
                height,
                dimension == null ? 0D : dimension.getFluidHeight(),
                Math.toDegrees(Math.atan(slope / SLOPE_STREAM_RANGE_BLOCKS)),
                complex.explainHybridChoice(region, worldX, worldZ),
                explainPicks ? pickNoise(complex, region, previewRegion == null, worldX, worldZ) : null
        );
    }

    // HYBRID terrain picks biomes by height, which the placement line already tells, so only the region noise is left.
    private static PickNoise pickNoise(IrisComplex complex, IrisRegion region, boolean regionPicked, double x, double z) {
        Double regionNoise = regionPicked ? complex.getRegionStyleStream().get(x, z) : null;
        if (complex.isHybridTerrain() || region == null) {
            return new PickNoise(regionNoise, null, Double.NaN, null);
        }
        InferredType biomeType = complex.surfacePickAsIfIn(region, x, z).type();
        return new PickNoise(regionNoise, biomeType, complex.biomePickNoise(region, biomeType, x, z),
                complex.biomePickedByNoise(region, biomeType, x, z));
    }

    // A shore biome sits on land the continent stream calls land or sea, so the biome's own type says it best.
    private static String terrainKindName(IrisBiome surfaceBiome, InferredType continentKind) {
        InferredType kind = surfaceBiome != null && surfaceBiome.getInferredType() != null
                ? surfaceBiome.getInferredType()
                : continentKind;
        if (kind == null) {
            return null;
        }
        String name = kind.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    record Details(
            double worldX,
            double worldZ,
            IrisBiome surfaceBiome,
            IrisBiome caveBiome,
            IrisRegion region,
            String terrainKind,
            String vanillaBiome,
            double height,
            double fluidHeight,
            double slopeDegrees,
            HybridBiomeSelector.Choice placement,
            PickNoise pickNoise
    ) {
    }

    /**
     * @param regionNoise the region noise value, or null when the region preview chose the region
     * @param biomeType   the biome list the biome noise picked from, or null when no noise picked the biome
     * @param noisePick   the biome the noise picked, which a child biome can still replace
     */
    record PickNoise(Double regionNoise, InferredType biomeType, double biomeNoise, IrisBiome noisePick) {
    }
}
