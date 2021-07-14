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

package com.volmit.iris.object;

import com.volmit.iris.scaffold.cache.AtomicCache;
import com.volmit.iris.scaffold.data.DataProvider;
import com.volmit.iris.util.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("A biome mutation if a condition is met")
@Data
public class IrisBiomeMutation {

    @RegistryListBiome
    @Required
    @ArrayType(min = 1, type = String.class)
    @Desc("One of The following biomes or regions must show up")
    private KList<String> sideA = new KList<>();

    @RegistryListBiome
    @Required
    @ArrayType(min = 1, type = String.class)
    @Desc("One of The following biomes or regions must show up")
    private KList<String> sideB = new KList<>();

    @Required
    @MinNumber(1)
    @MaxNumber(1024)
    @Desc("The scan radius for placing this mutator")
    private int radius = 16;

    @Required
    @MinNumber(1)
    @MaxNumber(32)
    @Desc("How many tries per chunk to check for this mutation")
    private int checks = 2;

    @RegistryListObject
    @ArrayType(min = 1, type = IrisObjectPlacement.class)
    @Desc("Objects define what schematics (iob files) iris will place in this biome mutation")
    private KList<IrisObjectPlacement> objects = new KList<IrisObjectPlacement>();

    private final transient AtomicCache<KList<String>> sideACache = new AtomicCache<>();
    private final transient AtomicCache<KList<String>> sideBCache = new AtomicCache<>();

    public KList<String> getRealSideA(DataProvider xg) {
        return sideACache.aquire(() -> processList(xg, getSideA()));
    }

    public KList<String> getRealSideB(DataProvider xg) {
        return sideBCache.aquire(() -> processList(xg, getSideB()));
    }

    public KList<String> processList(DataProvider xg, KList<String> s) {
        KSet<String> r = new KSet<>();

        for (String i : s) {
            String q = i;

            if (q.startsWith("^")) {
                r.addAll(xg.getData().getRegionLoader().load(q.substring(1)).getLandBiomes());
                continue;
            } else if (q.startsWith("*")) {
                String name = q.substring(1);
                r.addAll(xg.getData().getBiomeLoader().load(name).getAllChildren(xg, 7));
            } else if (q.startsWith("!")) {
                r.remove(q.substring(1));
            } else if (q.startsWith("!*")) {
                String name = q.substring(2);
                r.removeAll(xg.getData().getBiomeLoader().load(name).getAllChildren(xg, 7));
            } else {
                r.add(q);
            }
        }

        return new KList<String>(r);
    }
}
