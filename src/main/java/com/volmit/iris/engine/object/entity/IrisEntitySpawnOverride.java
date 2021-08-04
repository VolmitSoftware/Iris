/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.engine.object.entity;

import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.MinNumber;
import com.volmit.iris.engine.object.annotations.RegistryListResource;
import com.volmit.iris.engine.object.annotations.Required;
import com.volmit.iris.util.math.RNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.EntitySpawnEvent;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents an entity spawn")
@Data
public class IrisEntitySpawnOverride {
    @RegistryListResource(IrisEntity.class)
    @Required
    @Desc("The entity")
    private String entity = "";

    @Required
    @Desc("If the following entity type spawns, spawn this entity. Set to unknown for any entity spawn")
    private EntityType trigger = EntityType.UNKNOWN;

    @Desc("If the source is triggered, cancel spawning the original entity instead of ADDING a new entity.")
    private boolean cancelSourceSpawn = false;

    @MinNumber(1)
    @Desc("The 1 in RARITY chance for this entity to spawn")
    private int rarity = 1;

    private final transient AtomicCache<RNG> rng = new AtomicCache<>();
    private final transient AtomicCache<IrisEntity> ent = new AtomicCache<>();


    public Entity on(Engine g, Location at, EntityType t, EntitySpawnEvent ee) {
        if (!trigger.equals(EntityType.UNKNOWN)) {
            if (!trigger.equals(t)) {
                return null;
            }
        }

        Entity e = spawn(g, at);

        if (e != null && isCancelSourceSpawn()) {
            ee.setCancelled(true);
            ee.getEntity().remove();
        }

        return e;
    }

    public Entity spawn(Engine g, Location at) {
        if (getRealEntity(g) == null) {
            return null;
        }

        if (rng.aquire(() -> new RNG(g.getTarget().getWorld().seed() + 4)).i(1, getRarity()) == 1) {
            return getRealEntity(g).spawn(g, at, rng.aquire(() -> new RNG(g.getTarget().getWorld().seed() + 4)));
        }

        return null;
    }

    public IrisEntity getRealEntity(Engine g) {
        return ent.aquire(() -> g.getData().getEntityLoader().load(getEntity()));
    }
}
