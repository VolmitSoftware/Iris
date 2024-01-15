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

package com.volmit.iris.util.data;

import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KeyPair;

import java.util.Random;

public class WeightedRandom<T> {

    private final KList<KeyPair<T, Integer>> weightedObjects = new KList<>();
    private final Random random;
    private int totalWeight = 0;

    public WeightedRandom(Random random) {
        this.random = random;
    }

    public WeightedRandom() {
        this.random = new Random();
    }

    public void put(T object, int weight) {
        weightedObjects.add(new KeyPair<>(object, weight));
        totalWeight += weight;
    }

    public T pullRandom() {
        int pull = random.nextInt(totalWeight);
        int index = 0;
        while (pull > 0) {
            pull -= weightedObjects.get(index).getV();
            index++;
        }
        return weightedObjects.get(index).getK();
    }

    public int getSize() {
        return weightedObjects.size();
    }

    public void shuffle() {
        weightedObjects.shuffle(random);
    }
}
