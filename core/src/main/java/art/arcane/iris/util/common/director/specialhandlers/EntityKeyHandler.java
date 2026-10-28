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

package art.arcane.iris.util.common.director.specialhandlers;

import com.volmit.resource.ResourceId;

/** The key of an entity of a pack: the open pack's, then those of the packs in the packs folder. */
public final class EntityKeyHandler extends PackResourceKeyHandler {
    private static final ResourceId ENTITIES = ResourceId.parse("iris:spawn/entity");

    public EntityKeyHandler() {
        super(ENTITIES, "Entity");
    }
}
