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

package com.volmit.iris.util.decree.handlers;

import com.volmit.iris.Iris;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.decree.DecreeParameterHandler;
import com.volmit.iris.util.decree.exceptions.DecreeParsingException;

import java.io.File;
import java.util.stream.Collectors;

public class BiomeHandler implements DecreeParameterHandler<IrisBiome> {
    @Override
    public KList<IrisBiome> getPossibilities() {
        KMap<String, IrisBiome> p = new KMap<>();

        //noinspection ConstantConditions
        for (File i : Iris.instance.getDataFolder("packs").listFiles()) {
            if (i.isDirectory()) {
                IrisData data = IrisData.get(i);
                for (IrisBiome j : data.getBiomeLoader().loadAll(data.getBiomeLoader().getPossibleKeys())) {
                    p.putIfAbsent(j.getLoadKey(), j);
                }

                data.close();
            }
        }

        return p.v();
    }

    @Override
    public String toString(IrisBiome dim) {
        return dim.getLoadKey();
    }

    @Override
    public IrisBiome parse(String in, boolean force) throws DecreeParsingException {
        if (in.equals("null")) {
            return null;
        }
        KList<IrisBiome> options = getPossibilities(in);

        if (options.isEmpty()) {
            throw new DecreeParsingException("Unable to find Biome \"" + in + "\"");
        }

        try {
            return options.stream().filter((i) -> toString(i).equalsIgnoreCase(in)).collect(Collectors.toList()).get(0);
        } catch (Throwable e) {
            throw new DecreeParsingException("Unable to filter which Biome \"" + in + "\"");
        }
    }

    @Override
    public boolean supports(Class<?> type) {
        return type.equals(IrisBiome.class);
    }

    @Override
    public String getRandomDefault() {
        return "biome";
    }
}
