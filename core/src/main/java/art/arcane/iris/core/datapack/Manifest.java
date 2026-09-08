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

package art.arcane.iris.core.datapack;

import art.arcane.iris.core.datapack.DatapackIngestService.Entry;

import java.util.ArrayList;
import java.util.List;

final class Manifest {
    List<Entry> entries = new ArrayList<>();

    Entry find(String url) {
        for (Entry entry : entries) {
            if (entry.url != null && entry.url.equals(url)) {
                return entry;
            }
        }
        return null;
    }

    Entry findById(String id) {
        for (Entry entry : entries) {
            if (entry.id != null && entry.id.equals(id)) {
                return entry;
            }
        }
        return null;
    }

    void put(Entry entry) {
        for (int i = 0; i < entries.size(); i++) {
            Entry current = entries.get(i);
            if (current.url != null && current.url.equals(entry.url)) {
                entries.set(i, entry);
                return;
            }
        }
        entries.add(entry);
    }

    boolean removeById(String id) {
        return entries.removeIf(entry -> id.equals(entry.id));
    }
}
