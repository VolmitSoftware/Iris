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

package art.arcane.iris.core.pack;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;

/**
 * The dimension, region and biome files of a pack whose JSON Iris cannot load, one blocking error each, such as module
 * settings with an option outside its declared bounds. A file that is not JSON at all is left to
 * {@link PackObjectSurfaceValidator#validateHostFilesParse}, so it is reported once.
 */
final class PackLoadFailures {
    private PackLoadFailures() {
    }

    static List<String> of(IrisData data) {
        List<String> blockingErrors = new ArrayList<>();
        addFailures(blockingErrors, "Dimension", data.getDimensionLoader());
        addFailures(blockingErrors, "Region", data.getRegionLoader());
        addFailures(blockingErrors, "Biome", data.getBiomeLoader());
        return blockingErrors;
    }

    private static void addFailures(List<String> blockingErrors, String resourceType, ResourceLoader<?> loader) {
        Arrays.stream(loader.getPossibleKeys()).sorted().forEach(key -> loader.load(key, false));
        new TreeMap<>(loader.loadFailures()).forEach((key, failure) -> {
            if (!failure.invalidJson()) {
                blockingErrors.add(resourceType + " '" + key + "' cannot be loaded: " + failure.message());
            }
        });
    }
}
