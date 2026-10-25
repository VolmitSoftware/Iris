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

package art.arcane.iris.studio.map.components;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisRegion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Numbers every biome and region the map has drawn, so a tile can remember what each of its pixels shows in two bytes.
 * One set of labels belongs to one generation of the pack: a hotload starts a new one.
 */
public final class MapLabels {
    public static final short NONE = -1;

    private final IdentityHashMap<Object, Short> idsBySubject = new IdentityHashMap<>();
    private final Map<String, Short> idsByKey = new HashMap<>();
    private final List<Label> labels = new ArrayList<>();

    synchronized short idOf(IrisBiome biome) {
        return idOf(biome, Kind.BIOME, biome.getLoadKey(), biome.getName());
    }

    synchronized short idOf(IrisRegion region) {
        return idOf(region, Kind.REGION, region.getLoadKey(), region.getName());
    }

    public synchronized Label label(short id) {
        return id < 0 || id >= labels.size() ? null : labels.get(id);
    }

    public synchronized short idOf(Kind kind, String key) {
        Short id = idsByKey.get(kind.name() + ':' + key);
        return id == null ? NONE : id;
    }

    private short idOf(Object subject, Kind kind, String key, String name) {
        Short existing = idsBySubject.get(subject);
        if (existing != null) {
            return existing;
        }
        if (labels.size() >= Short.MAX_VALUE) {
            return NONE;
        }
        short id = (short) labels.size();
        labels.add(new Label(id, kind, key, name, subject));
        idsBySubject.put(subject, id);
        idsByKey.putIfAbsent(kind.name() + ':' + key, id);
        return id;
    }

    public enum Kind {
        BIOME,
        REGION
    }

    public record Label(short id, Kind kind, String key, String name, Object subject) {
    }
}
