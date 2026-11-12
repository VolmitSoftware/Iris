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

package art.arcane.iris.modules.carve;

import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.resource.ResourceKind;

/** The resource kind of module {@code iris:carve}: the caves of a pack's {@code caves/} folder. */
public final class CaveResources {
    /** Caves, in {@code caves/}. */
    public static final ResourceKind<Cave> CAVES = ResourceKind.of("iris:carve/cave", "caves", Cave.class);

    private CaveResources() {
    }

    /** The cave with this pack key; null for a blank key, a key the pack has no file for and a file that fails to load. */
    public static Cave cave(PackResources resources, String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return cave(resources, resources.idOf(key));
    }

    /** The cave with this id; null when the pack has no file for it or the file fails to load. */
    public static Cave cave(PackResources resources, ResourceId id) {
        try {
            return resources.find(CAVES, id).orElse(null);
        } catch (IllegalStateException failedToLoad) {
            return null;
        }
    }
}
