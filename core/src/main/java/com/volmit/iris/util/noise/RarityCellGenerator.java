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

package com.volmit.iris.util.noise;

import com.volmit.iris.engine.object.IRare;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.RNG;

public class RarityCellGenerator<T extends IRare> extends CellGenerator {
    public RarityCellGenerator(RNG rng) {
        super(rng);
    }

    public T get(double x, double z, KList<T> b) {
        if (b.size() == 0) {
            return null;
        }

        if (b.size() == 1) {
            return b.get(0);
        }

        KList<T> rarityMapped = new KList<>();
        boolean o = false;
        int max = 1;
        for (T i : b) {
            if (i.getRarity() > max) {
                max = i.getRarity();
            }
        }

        max++;

        for (T i : b) {
            for (int j = 0; j < max - i.getRarity(); j++) {
                //noinspection AssignmentUsedAsCondition
                if (o = !o) {
                    rarityMapped.add(i);
                } else {
                    rarityMapped.add(0, i);
                }
            }
        }

        if (rarityMapped.size() == 1) {
            return rarityMapped.get(0);
        }

        if (rarityMapped.isEmpty()) {
            throw new RuntimeException("BAD RARITY MAP! RELATED TO: " + b.toString(", or possibly "));
        }

        return rarityMapped.get(getIndex(x, z, rarityMapped.size()));
    }
}
