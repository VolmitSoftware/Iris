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

package com.volmit.iris.engine;

import com.volmit.iris.Iris;
import com.volmit.iris.util.collection.KMap;

public class EnginePanic {
    private static final KMap<String, String> stuff = new KMap<>();
    private static KMap<String, String> last = new KMap<>();

    public static void add(String key, String value) {
        stuff.put(key, value);
    }

    public static void saveLast() {
        last = stuff.copy();
    }

    public static void lastPanic() {
        for (String i : last.keySet()) {
            Iris.error("Last Panic " + i + ": " + stuff.get(i));
        }
    }

    public static void panic() {
        lastPanic();
        for (String i : stuff.keySet()) {
            Iris.error("Engine Panic " + i + ": " + stuff.get(i));
        }
    }
}
