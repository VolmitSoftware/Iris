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

package com.volmit.iris.util.plugin;

import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PluginRegistry<T> {
    private final KMap<String, T> registry = new KMap<>();
    @Getter
    private final String namespace;

    public void unregisterAll() {
        registry.clear();
    }

    public KList<String> getRegistries() {
        return registry.k();
    }

    public T get(String s) {
        if (!registry.containsKey(s)) {
            return null;
        }

        return registry.get(s);
    }

    public void register(String s, T t) {
        registry.put(s, t);
    }

    public void unregister(String s) {
        registry.remove(s);
    }

    public T resolve(String id) {
        if (registry.isEmpty()) {
            return null;
        }

        return registry.get(id);
    }
}
