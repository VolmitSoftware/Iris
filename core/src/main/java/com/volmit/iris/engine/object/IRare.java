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

package com.volmit.iris.engine.object;

import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.stream.ProceduralStream;
import com.volmit.iris.util.stream.interpolation.Interpolated;

import java.util.List;

public interface IRare {
    static <T extends IRare> ProceduralStream<T> stream(ProceduralStream<Double> noise, List<T> possibilities) {
        return ProceduralStream.of((x, z) -> pick(possibilities, noise.get(x, z)),
                (x, y, z) -> pick(possibilities, noise.get(x, y, z)),
                new Interpolated<T>() {
                    @Override
                    public double toDouble(T t) {
                        return 0;
                    }

                    @Override
                    public T fromDouble(double d) {
                        return null;
                    }
                });
    }


    static <T extends IRare> T pickSlowly(List<T> possibilities, double noiseValue) {
        if (possibilities.isEmpty()) {
            return null;
        }

        if (possibilities.size() == 1) {
            return possibilities.get(0);
        }

        KList<T> rarityTypes = new KList<>();
        int totalRarity = 0;
        for (T i : possibilities) {
            totalRarity += IRare.get(i);
        }

        for (T i : possibilities) {
            rarityTypes.addMultiple(i, totalRarity / IRare.get(i));
        }

        return rarityTypes.get((int) (noiseValue * rarityTypes.last()));
    }

    static <T extends IRare> T pick(List<T> possibilities, double noiseValue) {
        if (possibilities.isEmpty()) {
            return null;
        }

        if (possibilities.size() == 1) {
            return possibilities.get(0);
        }
        int totalWeight = 0; // This is he baseline
        int buffer = 0;
        for (T i : possibilities) { // Im adding all of the rarity together
            totalWeight += i.getRarity();
        }
        double threshold = totalWeight * (possibilities.size() - 1) * noiseValue;
        for (T i : possibilities) {
            buffer += totalWeight - i.getRarity();

            if (buffer >= threshold) {
                return i;
            }
        }
        return possibilities.get(possibilities.size() - 1);
    }


    static <T extends IRare> T pickOld(List<T> possibilities, double noiseValue) {
        if (possibilities.isEmpty()) {
            return null;
        }

        if (possibilities.size() == 1) {
            return possibilities.get(0);
        }

        double completeWeight = 0.0;
        double highestWeight = 0.0;

        for (T item : possibilities) {
            double weight = Math.max(item.getRarity(), 1);
            highestWeight = Math.max(highestWeight, weight);
            completeWeight += weight;
        }

        double r = noiseValue * completeWeight;
        double countWeight = 0.0;

        for (T item : possibilities) {
            double weight = Math.max(highestWeight - Math.max(item.getRarity(), 1), 1);
            countWeight += weight;
            if (countWeight >= r) {
                return item;
            }
        }

        return possibilities.get(possibilities.size() - 1);
    }

    static int get(Object v) {
        return v instanceof IRare ? Math.max(1, ((IRare) v).getRarity()) : 1;
    }

    int getRarity();
}
