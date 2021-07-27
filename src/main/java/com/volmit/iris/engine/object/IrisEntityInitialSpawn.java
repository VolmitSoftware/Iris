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

package com.volmit.iris.engine.object;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.cache.AtomicCache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.MinNumber;
import com.volmit.iris.engine.object.annotations.RegistryListEntity;
import com.volmit.iris.engine.object.annotations.Required;
import com.volmit.iris.util.math.RNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents an entity spawn during initial chunk generation")
@Data
public class IrisEntityInitialSpawn {
    @RegistryListEntity
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

    private final transient AtomicCache<RNG> rng = new AtomicCache<>();
    private final transient AtomicCache<IrisEntity> ent = new AtomicCache<>();

    public void spawn(Engine gen, Chunk c, RNG rng) {
        int spawns = rng.i(1, rarity) == 1 ? rng.i(minSpawns, maxSpawns) : 0;

        if (spawns > 0) {
            for (int i = 0; i < spawns; i++) {
                int x = (c.getX() * 16) + rng.i(15);
                int z = (c.getZ() * 16) + rng.i(15);
                int h = gen.getHeight(x, z) + gen.getMinHeight();
                spawn100(gen, new Location(c.getWorld(), x, h, z));
            }
        }
    }

    public IrisEntity getRealEntity(Engine g) {
        return ent.aquire(() -> g.getData().getEntityLoader().load(getEntity()));
    }

    public Entity spawn(Engine g, Location at) {
        if (getRealEntity(g) == null) {
            return null;
        }

        if (rng.aquire(() -> new RNG(g.getTarget().getWorld().seed() + 4)).i(1, getRarity()) == 1) {
            return spawn100(g, at);
        }

        return null;
    }

    private Entity spawn100(Engine g, Location at) {
        try {
            return getRealEntity(g).spawn(g, at.clone().add(0.5, 1, 0.5), rng.aquire(() -> new RNG(g.getTarget().getWorld().seed() + 4)));
        } catch (Throwable e) {
            Iris.reportError(e);
            Iris.debug("Failed to retrieve real entity @ " + at);
            return null;
        }
    }
}
