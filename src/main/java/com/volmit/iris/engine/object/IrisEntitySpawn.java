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

package com.volmit.iris.engine.object;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.annotations.*;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.matter.MatterMarker;
import com.volmit.iris.util.matter.slices.MarkerMatter;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

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
    private transient IrisMarker referenceMarker;

    public int spawn(Engine gen, Chunk c, RNG rng) {
        int spawns = minSpawns == maxSpawns ? minSpawns : rng.i(Math.min(minSpawns, maxSpawns), Math.max(minSpawns, maxSpawns));
        int s = 0;

        if (spawns > 0) {
            for (int id = 0; id < spawns; id++) {
                int x = (c.getX() * 16) + rng.i(15);
                int z = (c.getZ() * 16) + rng.i(15);
                int h = gen.getHeight(x, z, true) + (gen.getWorld().tryGetRealWorld() ? gen.getWorld().realWorld().getMinHeight() : -64);
                int hf = gen.getHeight(x, z, false) + (gen.getWorld().tryGetRealWorld() ? gen.getWorld().realWorld().getMinHeight() : -64);
                Location l = switch (getReferenceSpawner().getGroup()) {
                    case NORMAL -> new Location(c.getWorld(), x, hf + 1, z);
                    case CAVE -> gen.getMantle().findMarkers(c.getX(), c.getZ(), MarkerMatter.CAVE_FLOOR)
                            .convert((i) -> i.toLocation(c.getWorld()).add(0, 1, 0)).getRandom(rng);
                    case UNDERWATER, BEACH -> new Location(c.getWorld(), x, rng.i(h + 1, hf), z);
                };

                if (l != null) {
                    if (referenceSpawner.getAllowedLightLevels().getMin() > 0 || referenceSpawner.getAllowedLightLevels().getMax() < 15) {
                        if (referenceSpawner.getAllowedLightLevels().contains(l.getBlock().getLightLevel())) {
                            if (spawn100(gen, l) != null) {
                                s++;
                            }
                        }
                    } else {
                        if (spawn100(gen, l) != null) {
                            s++;
                        }
                    }
                }
            }
        }

        return s;
    }

    public int spawn(Engine gen, IrisPosition c, RNG rng) {
        int spawns = minSpawns == maxSpawns ? minSpawns : rng.i(Math.min(minSpawns, maxSpawns), Math.max(minSpawns, maxSpawns));
        int s = 0;

        if (!gen.getWorld().tryGetRealWorld()) {
            return 0;
        }

        World world = gen.getWorld().realWorld();
        if (spawns > 0) {

            if (referenceMarker != null) {
                gen.getMantle().getMantle().remove(c.getX(), c.getY(), c.getZ(), MatterMarker.class);
            }

            for (int id = 0; id < spawns; id++) {
                Location l = c.toLocation(world).add(0, 1, 0);

                if (referenceSpawner.getAllowedLightLevels().getMin() > 0 || referenceSpawner.getAllowedLightLevels().getMax() < 15) {
                    if (referenceSpawner.getAllowedLightLevels().contains(l.getBlock().getLightLevel())) {
                        if (spawn100(gen, l, true) != null) {
                            s++;
                        }
                    }
                } else {
                    if (spawn100(gen, l, true) != null) {
                        s++;
                    }
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

        if (rng.aquire(() -> new RNG(g.getSeedManager().getEntity())).i(1, getRarity()) == 1) {
            return spawn100(g, at);
        }

        return null;
    }

    private Entity spawn100(Engine g, Location at) {
        return spawn100(g, at, false);
    }

    private Entity spawn100(Engine g, Location at, boolean ignoreSurfaces) {
        try {
            IrisEntity irisEntity = getRealEntity(g);
            if (irisEntity == null) { // No entity
                Iris.debug("      You are trying to spawn an entity that does not exist!");
                return null;
            }

            if (!ignoreSurfaces && !irisEntity.getSurface().matches(at.clone().subtract(0, 1, 0).getBlock())) {
                return null;
            }

            Entity e = irisEntity.spawn(g, at.add(0.5, 0, 0.5), rng.aquire(() -> new RNG(g.getSeedManager().getEntity())));
            if (e != null) {
                Iris.debug("Spawned " + C.DARK_AQUA + "Entity<" + getEntity() + "> " + C.GREEN + e.getType() + C.LIGHT_PURPLE + " @ " + C.GRAY + e.getLocation().getX() + ", " + e.getLocation().getY() + ", " + e.getLocation().getZ());
            }


            return e;
        } catch (Throwable e) {
            Iris.reportError(e);
            e.printStackTrace();
            Iris.error("      Failed to retrieve real entity @ " + at + " (entity: " + getEntity() + ")");
            return null;
        }
    }
}
