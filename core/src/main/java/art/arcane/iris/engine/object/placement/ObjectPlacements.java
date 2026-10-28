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

package art.arcane.iris.engine.object.placement;

import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.ObjectSettings;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.volmlib.util.collection.KList;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The object placements a region or biome declares in the module settings of {@code iris:objects}, listed once and kept
 * for as long as the settings live. Only the pack exporter reads them.
 */
public final class ObjectPlacements {
    private static final Map<ObjectSettings, KList<ObjectPlacement>> PLACEMENTS = Collections.synchronizedMap(new WeakHashMap<>());

    private ObjectPlacements() {
    }

    /** Every placement {@code owner} declares. */
    public static KList<ObjectPlacement> all(IrisRegistrant owner) {
        return placementsOf(owner);
    }

    private static final ObjectSettings NO_OBJECTS = new ObjectSettings();

    private static KList<ObjectPlacement> placementsOf(IrisRegistrant owner) {
        ObjectSettings settings = owner.getModuleSettings().find(ObjectSettings.class).orElse(NO_OBJECTS);
        KList<ObjectPlacement> found = PLACEMENTS.get(settings);
        if (found != null) {
            return found;
        }
        return PLACEMENTS.computeIfAbsent(settings, key -> new KList<>(key.objects()));
    }
}
