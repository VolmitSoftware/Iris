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

package art.arcane.iris.core.structure.tooling;

import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;

/** The keys the jigsaw commands show and the studio files its sessions under. */
public final class StructureKeys {
    private StructureKeys() {
    }

    /** The key of a resource of the pack: its path when it is of the pack's namespace, else the id as written. */
    public static String keyOf(PackResources resources, ResourceId id) {
        return resources.idOf(id.path()).equals(id) ? id.path() : id.toString();
    }
}
