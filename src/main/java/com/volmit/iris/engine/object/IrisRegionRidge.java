/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.engine.object;

import com.volmit.iris.engine.cache.AtomicCache;
import com.volmit.iris.engine.noise.CellGenerator;
import com.volmit.iris.engine.object.annotations.*;
import com.volmit.iris.util.math.RNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("A ridge config")
@Data
public class IrisRegionRidge {
    @RegistryListResource(IrisBiome.class)
    @Required
    @Desc("The biome name")
    private String biome = "";

    @Required
    @Desc("The type this biome should override (land sea or shore)")
    private InferredType type = InferredType.LAND;

    @Desc("What type this spot is (i.e. target SEA but as LAND) like an island. Default matches the target type")
    private InferredType as = InferredType.DEFER;

    @Desc("Use the distance from cell value to add or remove noise value. (Forces depth or height)")
    private double noiseMultiplier = 0;

    @Required
    @MinNumber(0)
    @MaxNumber(1)
    @Desc("The chance this biome will be placed in a given spot")
    private double chance = 0.75;

    @MinNumber(0)
    @Desc("The scale of the biome ridge. Higher values = wider veins & bigger connected cells")
    private double scale = 5;

    @Desc("The chance scale (cell chances)")
    private double chanceScale = 4;

    @MinNumber(0)
    @Desc("The shuffle, how 'natural' this looks. Compared to pure polygons")
    private double shuffle = 16;

    @MinNumber(0)
    @Desc("The chance shuffle (polygon cell chances)")
    private double chanceShuffle = 128;

    @MinNumber(0)
    @Desc("The thickness of the vein")
    private double thickness = 0.125;

    @Desc("If the noise multiplier is below zero, what should the air be filled with?")
    private IrisBiomePaletteLayer air = new IrisBiomePaletteLayer().zero();
    private final transient AtomicCache<CellGenerator> spot = new AtomicCache<>();
    private final transient AtomicCache<CellGenerator> ridge = new AtomicCache<>();

    public CellGenerator getSpotGenerator(RNG rng) {
        return spot.aquire(() ->
        {
            CellGenerator spot = new CellGenerator(rng.nextParallelRNG((int) (198523 * getChance())));
            spot.setCellScale(chanceScale);
            spot.setShuffle(shuffle);
            return spot;
        });
    }

    public CellGenerator getRidgeGenerator(RNG rng) {
        return spot.aquire(() ->
        {
            CellGenerator ridge = new CellGenerator(rng.nextParallelRNG((int) (465583 * getChance())));
            ridge.setCellScale(scale);
            ridge.setShuffle(shuffle);
            return ridge;
        });
    }

    public double getRidgeHeight(RNG rng, double x, double z) {
        if (getNoiseMultiplier() == 0) {
            return 0;
        }

        return getSpotGenerator(rng).getDistance(x, z) * getRidgeGenerator(rng).getDistance(x, z) * getNoiseMultiplier();
    }

    public boolean isRidge(RNG rng, double x, double z) {
        if (chance < 1) {
            if (getSpotGenerator(rng).getIndex(x, z, 1000) > chance * 1000) {
                return false;
            }
        }

        return getRidgeGenerator(rng).getDistance(x, z) <= thickness;
    }
}
