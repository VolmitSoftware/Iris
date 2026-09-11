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

package art.arcane.iris.util.common.director.specialhandlers;

import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.pack.PackDirectoryResolver;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.iris.util.common.director.DirectorParameterHandler;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class ObjectTargetHandler implements DirectorParameterHandler<String> {
    @Override
    public KList<String> getPossibilities() {
        KList<String> out = new KList<>();
        Set<String> prefixes = new HashSet<>();

        IrisData data = data();
        if (data != null) {
            collectObjectKeys(data, out, prefixes);
        } else {
            File packsFolder = IrisPlatforms.get().packsFolder();
            for (File pack : PackDirectoryResolver.listVisiblePackDirectories(packsFolder)) {
                collectObjectKeys(IrisData.get(pack), out, prefixes);
            }
        }

        for (String p : prefixes) {
            out.add(p);
        }
        return out;
    }

    private static void collectObjectKeys(IrisData data, KList<String> out, Set<String> prefixes) {
        for (String key : data.getObjectLoader().getPossibleKeys()) {
            out.add(key);
            collectPrefixes(key, prefixes);
        }
    }

    private static void collectPrefixes(String key, Set<String> prefixes) {
        int idx = 0;
        while ((idx = key.indexOf('/', idx)) >= 0) {
            prefixes.add(key.substring(0, idx + 1));
            idx++;
        }
    }

    @Override
    public String toString(String irisObject) {
        return irisObject;
    }

    @Override
    public String parse(String in, boolean force) throws DirectorParsingException {
        return in;
    }

    @Override
    public boolean supports(Class<?> type) {
        return type.equals(String.class);
    }

    @Override
    public String getRandomDefault() {
        String f = getPossibilities().getRandom();
        return f == null ? "trees/" : f;
    }
}
