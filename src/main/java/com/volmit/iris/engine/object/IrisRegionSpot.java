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
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.MinNumber;
import com.volmit.iris.engine.object.annotations.RegistryListResource;
import com.volmit.iris.engine.object.annotations.Required;
import com.volmit.iris.util.math.RNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("A spot config")
@Data
public class IrisRegionSpot {
    @RegistryListResource(IrisBiome.class)
    @Required
    @Desc("The biome to be placed")
    private String biome = "";

    @Required
    @Desc("Where this spot overrides. Land sea or shore")
    private InferredType type = InferredType.LAND;

    @Desc("What type this spot is (i.e. target SEA but as LAND) like an island. Default matches the target type")
    private InferredType as = InferredType.DEFER;

    @Desc("Use the distance from cell value to add or remove noise value. (Forces depth or height)")
    private double noiseMultiplier = 0;

    @MinNumber(0)
    @Desc("The scale of splotches")
    private double scale = 1;

    @Required
    @MinNumber(1)
    @Desc("Rarity is how often this splotch appears. higher = less often")
    private double rarity = 1;

    @MinNumber(0)
    @Desc("The shuffle or how natural the splotch looks like (anti-polygon)")
    private double shuffle = 128;

    @Desc("If the noise multiplier is below zero, what should the air be filled with?")
    private IrisBiomePaletteLayer air = new IrisBiomePaletteLayer().zero();

    private final transient AtomicCache<CellGenerator> spot = new AtomicCache<>();

    public CellGenerator getSpotGenerator(RNG rng) {
        return spot.aquire(() ->
        {
            CellGenerator spot = new CellGenerator(rng.nextParallelRNG((int) (168583 * (shuffle + 102) + rarity + (scale * 10465) + biome.length() + type.ordinal() + as.ordinal())));
            spot.setCellScale(scale);
            spot.setShuffle(shuffle);
            return spot;
        });
    }

    public double getSpotHeight(RNG rng, double x, double z) {
        if (getNoiseMultiplier() == 0) {
            return 0;
        }

        return getSpotGenerator(rng).getDistance(x, z) * getNoiseMultiplier();
    }

    public boolean isSpot(RNG rng, double x, double z) {
        return getSpotGenerator(rng).getIndex(x, z, (int) (Math.round(rarity) + 8)) == (int) ((Math.round(rarity) + 8) / 2);
    }
}
