/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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

import com.volmit.iris.Iris;
import com.volmit.iris.engine.object.annotations.*;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.documentation.ChunkCoordinates;
import com.volmit.iris.util.math.RNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("jigsaw-structure-placement")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents a jigsaw structure placer")
@Data
@EqualsAndHashCode(callSuper = false)
public class IrisJigsawStructurePlacement implements IRare {
    @RegistryListResource(IrisJigsawStructure.class)
    @Required
    @Desc("The structure to place")
    private String structure;

    @Required
    @Desc("The 1 in X chance rarity applies when generating multiple structures at once")
    private int rarity = 100;

    @Required
    @Desc("The salt to use when generating the structure (to differentiate structures)")
    @MinNumber(Long.MIN_VALUE)
    @MaxNumber(Long.MAX_VALUE)
    private long salt = 0;

    @Required
    @MinNumber(0)
    @Desc("Average distance in chunks between two neighboring generation attempts")
    private int spacing = -1;

    @Required
    @MinNumber(0)
    @Desc("Minimum distance in chunks between two neighboring generation attempts\nThe maximum distance of two neighboring generation attempts is 2*spacing - separation")
    private int separation = -1;

    @Desc("The method used to spread the structure")
    private SpreadType spreadType = SpreadType.LINEAR;

    @ArrayType(type = IrisJigsawMinDistance.class)
    @Desc("List of minimum distances to check for")
    private KList<IrisJigsawMinDistance> minDistances = new KList<>();

    public KMap<String, Integer> collectMinDistances() {
        KMap<String, Integer> map = new KMap<>();
        for (IrisJigsawMinDistance d : minDistances) {
            map.compute(d.getStructure(), (k, v) -> v != null ? Math.min(toChunks(d.getDistance()), v) : toChunks(d.getDistance()));
        }
        return map;
    }

    private int toChunks(int blocks) {
        return (int) Math.ceil(blocks / 16d);
    }

    private void calculateMissing(double divisor, long seed) {
        seed = seed + hashCode();
        if (salt == 0) {
            salt = new RNG(seed).nextLong(Integer.MIN_VALUE, Integer.MAX_VALUE);
        }

        if (separation == -1 || spacing == -1) {
            separation = (int) Math.round(rarity / divisor);
            spacing = new RNG(seed).nextInt(separation, separation * 2);
        }
    }

    @ChunkCoordinates
    public boolean shouldPlace(double divisor, long seed, int x, int z) {
        calculateMissing(divisor, seed);
        if (separation > spacing) {
            separation = spacing;
            Iris.warn("JigsawStructurePlacement: separation must be less than or equal to spacing");
        }

        int i = Math.floorDiv(x, spacing);
        int j = Math.floorDiv(z, spacing);
        RNG rng = new RNG(i * 341873128712L + j * 132897987541L + seed + salt);

        int k = spacing - separation;
        int l = spreadType.apply(rng, k);
        int m = spreadType.apply(rng, k);
        return i * spacing + l == x && j * spacing + m == z;
    }

    @Desc("Spread type")
    public enum SpreadType {
        @Desc("Linear spread")
        LINEAR(RNG::i),
        @Desc("Triangular spread")
        TRIANGULAR((rng, bound) -> (rng.i(bound) + rng.i(bound)) / 2);
        private final SpreadMethod method;

        SpreadType(SpreadMethod method) {
            this.method = method;
        }

        public int apply(RNG rng, int bound) {
            return method.apply(rng, bound);
        }
    }

    private interface SpreadMethod {
        int apply(RNG rng, int bound);
    }
}
