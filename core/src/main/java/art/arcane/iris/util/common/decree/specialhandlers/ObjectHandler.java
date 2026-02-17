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

package art.arcane.iris.util.decree.specialhandlers;

import art.arcane.iris.Iris;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.iris.util.decree.DirectorParameterHandler;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;

import java.io.File;
import java.util.stream.Collectors;

public class ObjectHandler implements DirectorParameterHandler<String> {
    @Override
    public KList<String> getPossibilities() {
        KList<String> p = new KList<>();
        IrisData data = data();
        if (data != null) {
            return new KList<>(data.getObjectLoader().getPossibleKeys());
        }

        //noinspection ConstantConditions
        for (File i : Iris.instance.getDataFolder("packs").listFiles()) {
            if (i.isDirectory()) {
                data = IrisData.get(i);
                p.add(data.getObjectLoader().getPossibleKeys());
            }
        }

        return p;
    }

    @Override
    public String toString(String irisObject) {
        return irisObject;
    }

    @Override
    public String parse(String in, boolean force) throws DirectorParsingException {
        KList<String> options = getPossibilities(in);

        if (options.isEmpty()) {
            throw new DirectorParsingException("Unable to find Object \"" + in + "\"");
        }
        try {
            return options.stream().filter((i) -> toString(i).equalsIgnoreCase(in)).collect(Collectors.toList()).get(0);
        } catch (Throwable e) {
            throw new DirectorParsingException("Unable to filter which Object \"" + in + "\"");
        }
    }

    @Override
    public boolean supports(Class<?> type) {
        return type.equals(String.class);
    }

    @Override
    public String getRandomDefault() {
        String f = getPossibilities().getRandom();

        return f == null ? "object" : f;
    }
}
