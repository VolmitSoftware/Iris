/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.engine.object.terrain.carving;

import art.arcane.iris.builtin.carve.CarvingEntry;
import art.arcane.iris.builtin.carve.CarvingSettings;
import art.arcane.iris.engine.framework.Engine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What the carve module keeps for one world: the cave biome bands its dimension declares ({@link CarvingSettings}) made ready
 * ({@link ResolvedCarvingEntry}), in the order the dimension lists them, and by id.
 */
public final class CarvingBands {
    private final List<ResolvedCarvingEntry> entries;
    private final Map<String, ResolvedCarvingEntry> index;

    CarvingBands(List<CarvingEntry> declared) {
        this.entries = declared.stream().filter(Objects::nonNull).map(ResolvedCarvingEntry::new).toList();
        this.index = indexById(entries);
    }

    CarvingBands(Engine engine) {
        this(engine.getDimension().getModuleSettings().find(CarvingSettings.class).map(CarvingSettings::entries).orElse(List.of()));
    }

    /** The bands of the world {@code engine} generates now. */
    public static CarvingBands of(Engine engine) {
        return engine.getGeneration().state(CarvingBands.class, CarvingBands::new);
    }

    /** Every declared entry, enabled or not. */
    public List<ResolvedCarvingEntry> entries() {
        return entries;
    }

    /** The entries by their trimmed ids; entries without an id are not in it, and the last of several with one id is. */
    public Map<String, ResolvedCarvingEntry> index() {
        return index;
    }

    private static Map<String, ResolvedCarvingEntry> indexById(List<ResolvedCarvingEntry> entries) {
        Map<String, ResolvedCarvingEntry> index = new HashMap<>();
        for (ResolvedCarvingEntry entry : entries) {
            if (!entry.id().isBlank()) {
                index.put(entry.id().trim(), entry);
            }
        }
        return Map.copyOf(index);
    }
}
