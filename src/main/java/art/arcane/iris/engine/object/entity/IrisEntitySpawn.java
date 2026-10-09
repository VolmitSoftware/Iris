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

package art.arcane.iris.engine.object.entity;

import art.arcane.iris.engine.spawn.SpawnResources;
import art.arcane.iris.engine.object.entity.spawn.IrisSpawner;
import art.arcane.iris.engine.object.marker.IrisMarker;
import art.arcane.iris.engine.object.value.IRare;
import art.arcane.iris.engine.data.cache.AtomicCache;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.MinNumber;
import art.arcane.iris.engine.object.annotations.RegistryListResource;
import art.arcane.iris.engine.object.annotations.Required;
import art.arcane.iris.engine.object.annotations.Snippet;
import art.arcane.volmlib.util.math.RNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("entity-spawn")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents an entity spawn during initial chunk generation")
@Data
public class IrisEntitySpawn implements IRare {
    private final transient AtomicCache<RNG> rng = new AtomicCache<>();
    private final transient AtomicCache<IrisEntity> ent = new AtomicCache<>();
    @RegistryListResource(IrisEntity.class)
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
    private transient IrisSpawner referenceSpawner;
    private transient IrisMarker referenceMarker;

    public IrisEntity getRealEntity(Engine g) {
        return ent.aquire(() -> SpawnResources.entity(g.getData(), getEntity()));
    }
}
