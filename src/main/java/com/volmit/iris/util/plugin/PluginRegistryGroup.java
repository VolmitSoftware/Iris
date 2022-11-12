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

public class PluginRegistryGroup<T> {
    private final KMap<String, PluginRegistry<T>> registries = new KMap<>();

    public T resolve(String namespace, String id) {
        if (registries.isEmpty()) {
            return null;
        }

        PluginRegistry<T> r = registries.get(namespace);
        if (r == null) {
            return null;
        }

        return r.resolve(id);
    }

    public void clearRegistries() {
        registries.clear();
    }

    public void removeRegistry(String namespace) {
        registries.remove(namespace);
    }

    public PluginRegistry<T> getRegistry(String namespace) {
        return registries.computeIfAbsent(namespace, PluginRegistry::new);
    }

    public KList<String> compile() {
        KList<String> l = new KList<>();
        registries.values().forEach((i)
                -> i.getRegistries().forEach((j)
                -> l.add(i.getNamespace() + ":" + j)));
        return l;
    }
}
