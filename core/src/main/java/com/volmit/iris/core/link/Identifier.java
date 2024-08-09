/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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

package com.volmit.iris.core.link;

import org.bukkit.NamespacedKey;

public record Identifier(String namespace, String key) {

    private static final String DEFAULT_NAMESPACE = "minecraft";

    public static Identifier fromNamespacedKey(NamespacedKey key) {
        return new Identifier(key.getNamespace(), key.getKey());
    }

    public static Identifier fromString(String id) {
        String[] strings = id.split(":", 2);
        if (strings.length == 1) {
            return new Identifier(DEFAULT_NAMESPACE, strings[0]);
        } else {
            return new Identifier(strings[0], strings[1]);
        }
    }

    @Override
    public String toString() {
        return namespace + ":" + key;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Identifier i) {
            return i.namespace().equals(this.namespace) && i.key().equals(this.key);
        } else if (obj instanceof NamespacedKey i) {
            return i.getNamespace().equals(this.namespace) && i.getKey().equals(this.key);
        } else {
            return false;
        }
    }
}
