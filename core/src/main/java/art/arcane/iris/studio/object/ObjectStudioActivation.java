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

package art.arcane.iris.studio.object;

import art.arcane.iris.pack.loading.IrisData;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ObjectStudioActivation {
    private static final Set<String> ACTIVE = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Map<String, Map<String, IrisData>> SOURCES = new ConcurrentHashMap<>();

    private ObjectStudioActivation() {
    }

    public static void activate(String packKey) {
        if (packKey == null) return;
        ACTIVE.add(normalize(packKey));
    }

    public static void deactivate(String packKey) {
        if (packKey == null) return;
        String norm = normalize(packKey);
        ACTIVE.remove(norm);
        SOURCES.remove(norm);
    }

    public static boolean isActive(String packKey) {
        if (packKey == null) return false;
        return ACTIVE.contains(normalize(packKey));
    }

    public static void setSources(String packKey, Map<String, IrisData> sources) {
        if (packKey == null || sources == null || sources.isEmpty()) return;
        SOURCES.put(normalize(packKey), new LinkedHashMap<>(sources));
    }

    public static Map<String, IrisData> getSources(String packKey) {
        if (packKey == null) return null;
        return SOURCES.get(normalize(packKey));
    }

    private static String normalize(String key) {
        return key.toLowerCase(Locale.ROOT);
    }
}
