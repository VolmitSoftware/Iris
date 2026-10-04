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

package art.arcane.iris.world.entity;

import art.arcane.iris.generation.decoration.IrisSurface;
import art.arcane.iris.pack.value.IrisPosition;

import art.arcane.volmlib.util.math.Rarity;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.platform.bukkit.BukkitWorldBinding;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.volmlib.util.cache.AtomicCache;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.structure.placement.LootResolver;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.RegistryListResource;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.localization.C;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.math.Vector3d;
import art.arcane.volmlib.util.matter.MatterMarker;
import art.arcane.volmlib.util.matter.slices.MarkerMatter;
import art.arcane.iris.world.task.J;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;

import java.util.Objects;
import java.util.Comparator;

@Snippet("entity-spawn")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("Represents an entity spawn during initial chunk generation")
@Data
public class IrisEntitySpawn implements Rarity {
    private static final int CAVE_COLUMN_ATTEMPTS = 8;
    private static final int CAVE_VERTICAL_ATTEMPTS = 128;
    private final transient AtomicCache<IrisEntity> ent = new AtomicCache<>();
    @RegistryListResource(IrisEntity.class)
    @Required
    @Description("The entity")
    private String entity = "";
    @MinNumber(1)
    @Description("The 1 in RARITY chance for this entity to spawn")
    private int rarity = 1;
    @MinNumber(1)
    @Description("The minumum of this entity to spawn")
    private int minSpawns = 1;
    @MinNumber(1)
    @Description("The max of this entity to spawn")
    private int maxSpawns = 1;

    public int spawn(Engine gen, Chunk chunk, RNG rng, int remainingCapacity, SpawnContext context) {
        if (remainingCapacity <= 0) {
            return 0;
        }
        IrisEntity definition = getRealEntity(gen);
        if (definition == null) {
            return 0;
        }
        long batchSeed = rng.getSeed();
        int count = Math.min(remainingCapacity, LootResolver.inclusive(rng, minSpawns, maxSpawns));
        IrisSpawnGroup group = context.spawner().getGroup();
        KList<IrisPosition> caveMarkers = null;
        if (context.initial() && group == IrisSpawnGroup.CAVE && !J.isFolia()) {
            caveMarkers = gen.getMantle().findMarkers(chunk.getX(), chunk.getZ(), MarkerMatter.CAVE_FLOOR);
            caveMarkers.sort(Comparator.comparingInt(IrisPosition::getX)
                    .thenComparingInt(IrisPosition::getY).thenComparingInt(IrisPosition::getZ));
        }
        int spawned = 0;
        for (int ordinal = 0; ordinal < count; ordinal++) {
            RNG attemptRng = EntitySpawnSeed.entity(batchSeed, ordinal);
            RNG positionRng = attemptRng.nextParallelRNG(0x632BE59BD9B4E019L);
            Location location;
            if (group == IrisSpawnGroup.CAVE) {
                location = caveMarkers == null
                        ? findCaveSpawnLocation(gen, chunk, positionRng, definition.getSurface())
                        : selectCaveSpawnLocation(caveMarkers, chunk.getWorld(), positionRng);
            } else {
                int x = (chunk.getX() << 4) + positionRng.i(16);
                int z = (chunk.getZ() << 4) + positionRng.i(16);
                World world = chunk.getWorld();
                int floor = world.getHighestBlockYAt(x, z, HeightMap.OCEAN_FLOOR);
                int top = world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
                Integer y = selectSurfaceSpawnY(group, definition.getSurface(), floor, top, positionRng);
                location = y == null ? null : new Location(world, x, y, z);
            }
            if (location != null && lightAllowed(location, context)
                    && spawn100(gen, location, attemptRng, false) != null) {
                spawned++;
            }
        }
        return spawned;
    }

    public int spawn(Engine engine, Location candidate, RNG random, int remainingCapacity, SpawnContext context) {
        if (remainingCapacity <= 0) {
            return 0;
        }
        int count = Math.min(remainingCapacity, LootResolver.inclusive(random, minSpawns, maxSpawns));
        long batchSeed = random.getSeed();
        int spawned = 0;
        for (int ordinal = 0; ordinal < count; ordinal++) {
            if (lightAllowed(candidate, context)
                    && spawn100(engine, candidate, EntitySpawnSeed.entity(batchSeed, ordinal), false) != null) {
                spawned++;
            }
        }
        return spawned;
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

    public int spawn(Engine gen, IrisPosition position, RNG rng, SpawnContext context) {
        long batchSeed = rng.getSeed();
        int count = LootResolver.inclusive(rng, minSpawns, maxSpawns);
        if (count <= 0 || !BukkitWorldBinding.tryBind(gen.getWorld())) {
            return 0;
        }
        World world = BukkitWorldBinding.world(gen.getWorld());
        if (context.marker() != null && context.marker().shouldExhaust(EntitySpawnSeed.entity(batchSeed, -1))) {
            if (J.isFolia()) {
                J.a(() -> gen.getMantle().getMantle().remove(position.getX(),
                        position.getY() - gen.getWorld().minHeight(), position.getZ(), MatterMarker.class));
            } else {
                gen.getMantle().getMantle().remove(position.getX(),
                        position.getY() - gen.getWorld().minHeight(), position.getZ(), MatterMarker.class);
            }
        }
        int spawned = 0;
        for (int ordinal = 0; ordinal < count; ordinal++) {
            Location location = BukkitPlatform.toLocation(position, world).add(0, 1, 0);
            if (lightAllowed(location, context)
                    && spawn100(gen, location, EntitySpawnSeed.entity(batchSeed, ordinal), true) != null) {
                spawned++;
            }
        }
        return spawned;
    }

    private static boolean lightAllowed(Location location, SpawnContext context) {
        if (context.initial()) {
            return true;
        }
        IrisSpawner spawner = context.spawner();
        return spawner.getAllowedLightLevels().getMin() <= 0
                && spawner.getAllowedLightLevels().getMax() >= 15
                || spawner.getAllowedLightLevels().contains(location.getBlock().getLightLevel());
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

    public Entity spawn(Engine engine, Location at) {
        if (getRealEntity(engine) == null) {
            return null;
        }
        RNG rng = EntitySpawnSeed.marker(engine.getSeedManager().getEntity(),
                at.getBlockX(), at.getBlockY(), at.getBlockZ());
        return LootResolver.oneIn(rng, getRarity()) ? spawn100(engine, at, rng, false) : null;
    }

    static Location findCaveSpawnLocation(Engine engine, Chunk chunk, RNG rng, IrisSurface surface) {
        if (J.isFolia()) {
            if (!J.isOwnedByCurrentRegion(chunk.getWorld(), chunk.getX(), chunk.getZ())) {
                return null;
            }
            return findLiveCaveSpawnLocation(chunk, rng, surface);
        }
        KList<IrisPosition> markers = engine.getMantle().findMarkers(chunk.getX(), chunk.getZ(), MarkerMatter.CAVE_FLOOR);
        return selectCaveSpawnLocation(markers, chunk.getWorld(), rng);
    }

    public static Location findLiveCaveSpawnLocation(Chunk chunk, RNG rng, IrisSurface surface) {
        boolean fluid = surface.isFluid();
        World world = chunk.getWorld();
        int minimumY = world.getMinHeight() + 1;
        for (int attempt = 0; attempt < CAVE_COLUMN_ATTEMPTS; attempt++) {
            int localX = rng.i(1, 15);
            int localZ = rng.i(1, 15);
            int worldX = (chunk.getX() << 4) + localX;
            int worldZ = (chunk.getZ() << 4) + localZ;
            int maximumY = Math.min(world.getMaxHeight() - 3,
                    world.getHighestBlockYAt(worldX, worldZ, HeightMap.OCEAN_FLOOR) - 1);
            if (maximumY < minimumY) {
                continue;
            }
            int span = maximumY - minimumY + 1;
            int startY = LootResolver.inclusive(rng, minimumY, maximumY);
            int samples = Math.min(span, CAVE_VERTICAL_ATTEMPTS);
            for (int sample = 0; sample < samples; sample++) {
                int y = minimumY + Math.floorMod(startY - minimumY - sample, span);
                Block block = chunk.getBlock(localX, y, localZ);
                if (fluid ? surface.matches(block) : isAir(block.getType())
                        && chunk.getBlock(localX, y - 1, localZ).isSolid()
                        && isAir(chunk.getBlock(localX, y + 1, localZ).getType())) {
                    return new Location(world, worldX, y, worldZ);
                }
            }
        }
        return null;
    }

    static Location selectCaveSpawnLocation(KList<IrisPosition> markers, World world, RNG rng) {
        IrisPosition marker = markers.getRandom(rng);
        return marker == null ? null : BukkitPlatform.toLocation(marker, world);
    }

    private Entity spawn100(Engine g, Location at, RNG rng, boolean ignoreSurfaces) {
        try {
            IrisEntity irisEntity = getRealEntity(g);
            if (irisEntity == null) { // No entity
                IrisLogging.debug("      You are trying to spawn an entity that does not exist!");
                return null;
            }

            if (J.isFolia() && !J.isOwnedByCurrentRegion(at.getWorld(), at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
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

            Entity e = irisEntity.spawn(g, at.clone().add(0.5, surface.isFluid() ? 0.5 : 0, 0.5), rng);
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

    public record SpawnContext(IrisSpawner spawner, IrisMarker marker, boolean initial) {
        public SpawnContext {
            Objects.requireNonNull(spawner, "spawner");
        }
    }

    private static boolean isAir(Material material) {
        return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
    }

    private boolean isAreaClearForSpawn(Location center, Vector3d boundingBox, IrisSurface surface) {
        World world = center.getWorld();
        boolean fluid = surface.isFluid();
        int startX = (int) Math.floor(center.getX() + 0.5 - boundingBox.x / 2);
        int endX = (int) Math.floor(Math.nextDown(center.getX() + 0.5 + boundingBox.x / 2));
        double baseY = center.getY() + (fluid ? 0.5 : 0);
        int startY = (int) Math.floor(baseY);
        int endY = (int) Math.floor(Math.nextDown(baseY + boundingBox.y));
        int startZ = (int) Math.floor(center.getZ() + 0.5 - boundingBox.z / 2);
        int endZ = (int) Math.floor(Math.nextDown(center.getZ() + 0.5 + boundingBox.z / 2));

        if (startY < world.getMinHeight() || endY >= world.getMaxHeight()) {
            return false;
        }
        if (J.isFolia()) {
            for (int chunkX = startX >> 4; chunkX <= endX >> 4; chunkX++) {
                for (int chunkZ = startZ >> 4; chunkZ <= endZ >> 4; chunkZ++) {
                    if (!J.isOwnedByCurrentRegion(world, chunkX, chunkZ)) {
                        return false;
                    }
                }
            }
        }
        for (int x = startX; x <= endX; x++) {
            for (int y = startY; y <= endY; y++) {
                for (int z = startZ; z <= endZ; z++) {
                    if (fluid ? !surface.matches(world.getBlockAt(x, y, z))
                            : !isAir(world.getBlockAt(x, y, z).getType())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
