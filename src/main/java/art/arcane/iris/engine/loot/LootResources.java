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

package art.arcane.iris.engine.loot;

import art.arcane.iris.builtin.loot.LootTable;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.IrisPackResources;
import com.volmit.resource.ResourceId;
import com.volmit.resource.ResourceKind;

import java.util.List;

/**
 * The resource kind of module {@code iris:loot}, which keeps the folder and files Iris packs have always used, and
 * lookups by pack key for code that still names loot tables by key. Every lookup goes through the pack's
 * {@link com.volmit.resource.PackResources}, so it sees the pack's current snapshot.
 * <p>
 * Lookups answer null where Iris's loader did: for a blank key, a key the pack has no file for, and a file that fails
 * to load, which was reported when it was read.
 */
public final class LootResources {
    /** Loot tables, in {@code loot/}. */
    public static final ResourceKind<LootTable> TABLES = ResourceKind.of("iris:loot/table", "loot", LootTable.class);

    private LootResources() {
    }

    /** Every resource kind of the loot module. */
    public static List<ResourceKind<?>> kinds() {
        return List.of(TABLES);
    }

    public static LootTable table(IrisData data, String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        IrisPackResources resources = data.getPackResources();
        try {
            return resources.find(TABLES.key(resources.idOf(key))).orElse(null);
        } catch (IllegalStateException failedToLoad) {
            return null;
        }
    }

    /** The key of every loot table of the pack, in key order. */
    public static List<String> tableKeys(IrisData data) {
        return data.getPackResources().ids(TABLES.type()).stream().map(ResourceId::path).toList();
    }
}
