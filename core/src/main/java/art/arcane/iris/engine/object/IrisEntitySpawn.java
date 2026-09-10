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

package art.arcane.iris.engine.object;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.platform.bukkit.BukkitWorldBinding;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.engine.data.cache.AtomicCache;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.LootResolver;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.MinNumber;
import art.arcane.iris.engine.object.annotations.RegistryListResource;
import art.arcane.iris.engine.object.annotations.Required;
import art.arcane.iris.engine.object.annotations.Snippet;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.util.common.format.C;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.math.Vector3d;
import art.arcane.volmlib.util.matter.MatterMarker;
import art.arcane.volmlib.util.matter.slices.MarkerMatter;
import art.arcane.iris.util.common.scheduling.J;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
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
        IrisEntity definition = getRealEntity(gen);
        if (definition == null) {
            return 0;
        }
        int spawns = LootResolver.inclusive(rng, minSpawns, maxSpawns);
        int s = 0;

        if (spawns > 0) {
            for (int id = 0; id < spawns; id++) {
                int x = (c.getX() * 16) + rng.i(16);
                int z = (c.getZ() * 16) + rng.i(16);
                World world = c.getWorld();
                int h = world.getHighestBlockYAt(x, z, HeightMap.OCEAN_FLOOR);
                int hf = world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
                IrisSpawnGroup group = getReferenceSpawner().getGroup();
                Integer y = selectSurfaceSpawnY(group, definition.getSurface(), h, hf, rng);
                Location l = group == IrisSpawnGroup.CAVE ? findCaveSpawnLocation(gen, c, rng)
                        : y == null ? null : new Location(world, x, y, z);

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

    public static Integer selectSurfaceSpawnY(IrisSpawnGroup group, IrisSurface surface,
                                             int floor, int top, RNG rng) {
        if (group == IrisSpawnGroup.CAVE) {
            return null;
        }
        if (group == IrisSpawnGroup.NORMAL && !surface.isFluid()) {
            return top + 1;
        }
        return top > floor ? LootResolver.inclusive(rng, floor + 1, top) : null;
    }

    public int spawn(Engine gen, IrisPosition c, RNG rng) {
        int spawns = LootResolver.inclusive(rng, minSpawns, maxSpawns);
        int s = 0;

        if (!BukkitWorldBinding.tryBind(gen.getWorld())) {
            return 0;
        }

        World world = BukkitWorldBinding.world(gen.getWorld());
        if (spawns > 0) {

            if (referenceMarker != null && referenceMarker.shouldExhaust()) {
                if (J.isFolia()) {
                    J.a(() -> gen.getMantle().getMantle().remove(c.getX(), c.getY() - gen.getWorld().minHeight(), c.getZ(), MatterMarker.class));
                } else {
                    gen.getMantle().getMantle().remove(c.getX(), c.getY() - gen.getWorld().minHeight(), c.getZ(), MatterMarker.class);
                }
            }

            for (int id = 0; id < spawns; id++) {
                Location l = BukkitPlatform.toLocation(c, world).add(0, 1, 0);

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
        return ent.aquire(() -> resolveEntity(g.getData()));
    }

    /** The referenced entity, or null when it does not load or the version-content gate excluded it. */
    IrisEntity resolveEntity(IrisData data) {
        if (data == null || data.getEntityLoader() == null) {
            return null;
        }

        IrisEntity entity = data.getEntityLoader().load(getEntity());
        return entity == null || entity.isCompatExcluded() ? null : entity;
    }

    public Entity spawn(Engine g, Location at) {
        if (getRealEntity(g) == null) {
            return null;
        }

        if (LootResolver.oneIn(rng.aquire(() -> new RNG(g.getSeedManager().getEntity())), getRarity())) {
            return spawn100(g, at);
        }

        return null;
    }

    static Location findCaveSpawnLocation(Engine engine, Chunk chunk, RNG rng) {
        if (J.isFolia()) {
            return null;
        }
        KList<IrisPosition> markers = engine.getMantle().findMarkers(chunk.getX(), chunk.getZ(), MarkerMatter.CAVE_FLOOR);
        return selectCaveSpawnLocation(markers, chunk.getWorld(), rng);
    }

    static Location selectCaveSpawnLocation(KList<IrisPosition> markers, World world, RNG rng) {
        return markers.convert((marker) -> BukkitPlatform.toLocation(marker, world).add(0, 1, 0)).getRandom(rng);
    }

    private Entity spawn100(Engine g, Location at) {
        return spawn100(g, at, false);
    }

    private Entity spawn100(Engine g, Location at, boolean ignoreSurfaces) {
        try {
            IrisEntity irisEntity = getRealEntity(g);
            if (irisEntity == null) { // No entity
                IrisLogging.debug("      You are trying to spawn an entity that does not exist!");
                return null;
            }

            IrisSurface surface = irisEntity.getSurface();
            boolean checkPosition = !ignoreSurfaces || surface.isFluid();
            if (checkPosition && !surface.matches(at.clone().subtract(0, surface.isFluid() ? 0 : 1, 0).getBlock())) {
                return null;
            }

            Vector3d boundingBox = BukkitPlatform.entityBoundingBox(irisEntity.getBukkitType());
            if (checkPosition && boundingBox != null) {
                boolean isClearForSpawn = isAreaClearForSpawn(at, boundingBox, surface);
                if (!isClearForSpawn) {
                    return null;
                }
            }

            Entity e = irisEntity.spawn(g, at.clone().add(0.5, 0.5, 0.5), rng.aquire(() -> new RNG(g.getSeedManager().getEntity())));
            if (e != null) {
                IrisLogging.debug("Spawned " + C.DARK_AQUA + "Entity<" + getEntity() + "> " + C.GREEN + e.getType() + C.LIGHT_PURPLE + " @ " + C.GRAY + e.getLocation().getX() + ", " + e.getLocation().getY() + ", " + e.getLocation().getZ());
            }

            return e;
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            IrisLogging.error("      Failed to retrieve real entity @ " + at + " (entity: " + getEntity() + ")");
            return null;
        }
    }

    private boolean isAreaClearForSpawn(Location center, Vector3d boundingBox, IrisSurface surface) {
        World world = center.getWorld();
        boolean fluid = surface.isFluid();
        int startX = fluid ? (int) Math.floor(center.getX() + 0.5 - boundingBox.x / 2)
                : center.getBlockX() - (int) (boundingBox.x / 2);
        int endX = fluid ? (int) Math.floor(Math.nextDown(center.getX() + 0.5 + boundingBox.x / 2))
                : center.getBlockX() + (int) (boundingBox.x / 2);
        int startY = fluid ? (int) Math.floor(center.getY() + 0.5) : center.getBlockY();
        int endY = fluid ? (int) Math.floor(Math.nextDown(center.getY() + 0.5 + boundingBox.y))
                : center.getBlockY() + (int) boundingBox.y;
        int startZ = fluid ? (int) Math.floor(center.getZ() + 0.5 - boundingBox.z / 2)
                : center.getBlockZ() - (int) (boundingBox.z / 2);
        int endZ = fluid ? (int) Math.floor(Math.nextDown(center.getZ() + 0.5 + boundingBox.z / 2))
                : center.getBlockZ() + (int) (boundingBox.z / 2);

        for (int x = startX; x <= endX; x++) {
            for (int y = startY; y <= endY; y++) {
                for (int z = startZ; z <= endZ; z++) {
                    if (fluid ? !surface.matches(world.getBlockAt(x, y, z))
                            : world.getBlockAt(x, y, z).getType() != Material.AIR) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
