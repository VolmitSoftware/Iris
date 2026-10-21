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

package com.volmit.iris.engine;

import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.noise.CNG;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chooses biomes for HYBRID terrain. Biomes whose placement allows the base terrain height
 * compete through their own noise, so a biome dropping out never reshuffles the others.
 */
public class HybridBiomeSelector {
    private final RNG rng;
    private final IrisData data;
    private final Map<String, CNG> competitionNoiseByBiome = new ConcurrentHashMap<>();

    public HybridBiomeSelector(RNG rng, IrisData data) {
        this.rng = rng;
        this.data = data;
    }

    public Choice choose(List<IrisBiome> candidates, double placementHeight, double x, double z) {
        IrisBiome strongest = null;
        double strongestScore = Double.NEGATIVE_INFINITY;
        int allowedCount = 0;

        for (IrisBiome biome : candidates) {
            if (!biome.getPlacement().allowsHeight(placementHeight)) {
                continue;
            }

            allowedCount++;
            double score = competitionScore(biome, x, z);

            if (score > strongestScore) {
                strongest = biome;
                strongestScore = score;
            }
        }

        if (strongest == null) {
            return new Choice(closestBiomeByHeight(candidates, placementHeight), placementHeight, 0, true);
        }

        return new Choice(strongest, placementHeight, allowedCount - 1, false);
    }

    private double competitionScore(IrisBiome biome, double x, double z) {
        return competitionNoise(biome).noise(x, z) / biome.getRarity();
    }

    private CNG competitionNoise(IrisBiome biome) {
        return competitionNoiseByBiome.computeIfAbsent(biome.getLoadKey(), key -> biome.getPlacement()
                .getCompetitionStyle()
                .createNoCache(rng.nextParallelRNG(key.hashCode()), data));
    }

    private IrisBiome closestBiomeByHeight(List<IrisBiome> candidates, double placementHeight) {
        IrisBiome closest = null;
        double closestDistance = Double.POSITIVE_INFINITY;

        for (IrisBiome biome : candidates) {
            double distance = biome.getPlacement().distanceToHeightRange(placementHeight);

            if (distance < closestDistance) {
                closest = biome;
                closestDistance = distance;
            }
        }

        return closest;
    }

    /**
     * @param rivalCount how many other biomes allowed this height and lost the competition
     * @param fallback   whether no biome allowed this height, so the one with the closest range was taken
     */
    public record Choice(IrisBiome biome, double placementHeight, int rivalCount, boolean fallback) {
    }
}
