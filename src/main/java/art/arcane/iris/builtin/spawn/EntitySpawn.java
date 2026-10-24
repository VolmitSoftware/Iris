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

package art.arcane.iris.builtin.spawn;

import com.volmit.config.Desc;
import com.volmit.config.MinNumber;
import com.volmit.config.Required;
import com.volmit.config.Ref;
import com.volmit.config.Snippet;

@Snippet("entity-spawn")
@Desc("Represents an entity spawn during initial chunk generation")
public final class EntitySpawn {
    @Ref(kind = EntityDefinition.class)
    @Required
    @Desc("The entity")
    private String entity = "";

    @MinNumber(1)
    @Desc("The 1 in RARITY chance for this entity to spawn")
    private int rarity = 1;

    @MinNumber(1)
    @Desc("The minumum of this entity to spawn")
    private int minSpawns = 1;

    @MinNumber(1)
    @Desc("The max of this entity to spawn")
    private int maxSpawns = 1;

    public EntitySpawn() {
    }

    /** The key of the entity of the pack. */
    public String entity() {
        return entity;
    }

    public int rarity() {
        return rarity;
    }

    public int minSpawns() {
        return minSpawns;
    }

    public int maxSpawns() {
        return maxSpawns;
    }
}
