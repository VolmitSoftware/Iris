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
import com.volmit.iris.engine.IrisComplex;
import com.volmit.iris.engine.cache.AtomicCache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.EngineFramework;
import com.volmit.iris.engine.modifier.IrisCaveModifier;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.MinNumber;
import com.volmit.iris.engine.object.annotations.RegistryListResource;
import com.volmit.iris.engine.object.annotations.Required;
import com.volmit.iris.engine.object.common.CaveResult;
import com.volmit.iris.engine.object.common.IRare;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.math.RNG;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents an entity spawn during initial chunk generation")
@Data
public class IrisEntitySpawn implements IRare {
    @RegistryListResource(IrisEntity.class)
    @Required
    @Desc("The entity")
    private String entity = "";

    @Desc("The energy multiplier when calculating spawn energy usage")
    private double energyMultiplier = 1;

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
    private final transient AtomicCache<RNG> rng = new AtomicCache<>();
    private final transient AtomicCache<IrisEntity> ent = new AtomicCache<>();

    public int spawn(Engine gen, Chunk c, RNG rng) {
        int spawns = minSpawns == maxSpawns ? minSpawns : rng.i(Math.min(minSpawns, maxSpawns), Math.max(minSpawns, maxSpawns));
        int s = 0;

        if (spawns > 0) {
            for (int id = 0; id < spawns; id++) {
                int x = (c.getX() * 16) + rng.i(15);
                int z = (c.getZ() * 16) + rng.i(15);
                int h = gen.getHeight(x, z, true);
                int hf = gen.getHeight(x, z, false);
                Location l = switch (getReferenceSpawner().getGroup()) {
                    case NORMAL -> new Location(c.getWorld(), x, hf + 1, z);
                    case CAVE -> {
                        IrisComplex comp = gen.getFramework().getComplex();
                        EngineFramework frame = gen.getFramework();
                        IrisBiome cave = comp.getCaveBiomeStream().get(x, z);
                        KList<Location> r = new KList<>();
                        if (cave != null) {
                            for (CaveResult i : ((IrisCaveModifier) frame.getCaveModifier()).genCaves(x, z)) {
                                if (i.getCeiling() >= gen.getHeight() || i.getFloor() < 0 || i.getCeiling() - 2 <= i.getFloor()) {
                                    continue;
                                }

                                r.add(new Location(c.getWorld(), x, i.getFloor(), z));
                            }
                        }

                        yield r.getRandom(rng);
                    }

                    case UNDERWATER, BEACH -> new Location(c.getWorld(), x, rng.i(h + 1, hf), z);
                };

                if (l != null) {
                    if (spawn100(gen, l) != null)
                        s++;
                }
            }
        }

        return s;
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
            IrisEntity irisEntity = getRealEntity(g);

            if (!irisEntity.getSurface().matches(at.clone().subtract(0, 1, 0).getBlock().getState())) return null; //Make sure it can spawn on the block

            Entity e = irisEntity.spawn(g, at.add(0.5, 0, 0.5), rng.aquire(() -> new RNG(g.getTarget().getWorld().seed() + 4)));
            if (e != null) {
                Iris.debug("Spawned " + C.DARK_AQUA + "Entity<" + getEntity() + "> " + C.GREEN + e.getType() + C.LIGHT_PURPLE + " @ " + C.GRAY + e.getLocation().getX() + ", " + e.getLocation().getY() + ", " + e.getLocation().getZ());
            }

            return e;
        } catch (Throwable e) {
            Iris.reportError(e);
            e.printStackTrace();
            Iris.error("      Failed to retrieve real entity @ " + at);
            return null;
        }
    }
}
