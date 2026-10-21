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
package com.volmit.iris.core.gui;

import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.object.IrisObject;

import java.util.List;
import java.util.OptionalLong;

/**
 * Where an object explorer gets what it shows: the pack's object files, or the objects Iris generates from settings.
 * Both are called on the explorer's loader thread only.
 */
interface ObjectSource {
    /** What one entry is called, such as "object", for the browser's texts. */
    String noun();

    /** The key of every entry, where a slash separates folders. */
    List<String> keys(IrisData data);

    /** The objects of the entry, one for an object file and one per variant for a procedural object; empty when none load. */
    List<IrisObject> variants(IrisData data, String key);

    /** The seed the pack bakes the entry's variants from; empty for anything not generated from a seed. */
    default OptionalLong seed(String key) {
        return OptionalLong.empty();
    }

    /** The entry's variants as {@code seed} would bake them; only asked of entries that have a seed. */
    default List<IrisObject> variants(IrisData data, String key, long seed) {
        throw new UnsupportedOperationException(key + " is not generated from a seed");
    }
}
