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
package art.arcane.iris.studio.map;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.structure.object.IrisObject;

import java.util.Arrays;
import java.util.List;

/**
 * The pack's object files, by their load keys.
 */
final class PackObjectSource implements ObjectSource {
    private static final int EMPTY_ICON_SIZE = 40;

    @Override
    public String noun() {
        return "object";
    }

    @Override
    public EmptyState emptyState() {
        return new EmptyState(MapViewIcon.large(MapView.OBJECTS, EMPTY_ICON_SIZE), "No objects yet",
                "Put the pack's .iob objects in", "objects/", "They appear here once the pack reloads.");
    }

    @Override
    public List<String> keys(IrisData data) {
        return Arrays.asList(data.getObjectLoader().getPossibleKeys());
    }

    @Override
    public List<IrisObject> variants(IrisData data, String key) {
        IrisObject object = data.getObjectLoader().load(key);
        return object == null ? List.of() : List.of(object);
    }
}
