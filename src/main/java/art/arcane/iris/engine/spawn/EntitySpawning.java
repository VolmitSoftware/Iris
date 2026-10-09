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

package art.arcane.iris.engine.spawn;

import art.arcane.iris.core.modules.IrisPackWorld;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.LootResolver;
import art.arcane.iris.engine.loot.LootItems;
import art.arcane.iris.engine.object.entity.IrisEntity;
import art.arcane.iris.engine.object.entity.IrisEntitySpawn;
import art.arcane.iris.engine.object.entity.spawn.IrisSpawner;
import art.arcane.iris.engine.object.entity.spawn.IrisSurface;
import art.arcane.iris.engine.object.marker.IrisMarker;
import art.arcane.iris.engine.object.value.IRare;
import art.arcane.iris.engine.object.value.IrisPosition;
import art.arcane.iris.engine.object.value.IrisRange;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.PowerOfTwoCoordinates;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.slices.MarkerMatter;
import com.volmit.entity.EntitySpec;
import com.volmit.service.EntitySpawns;
import com.volmit.world.BlockPosition;
import com.volmit.world.PackWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * How {@code iris:spawn} spawns entities: the spawners of the dimension, region and surface biome that may fire in a
 * chunk, or the spawners of a marker, each with the entities it spawns and the places it picks for them. What the
 * platform decides (light, the surface, free space, the entity itself) is the host's, the plan only names it.
 */
public final class EntitySpawning implements EntitySpawns {
    private static final int BLOCKS_TO_CHUNK_CENTRE = 8;
    private static final int CHUNK_SIZE = 16;
    private static final String CAVE_FLOOR_MARKER = "cave_floor";
    private static final String CAVE_CEILING_MARKER = "cave_ceiling";
    private static final int AIR_ABOVE_MARKER = 1;

    @Override
    public Optional<Plan> plan(PackWorld world, Site site) {
        Engine engine = engine(world);
        return switch (site) {
            case Chunk chunk -> planChunk(world, engine, chunk);
            case Marker marker -> planMarker(world, engine, marker);
        };
    }

    @Override
    public void spawned(PackWorld world, Plan plan, int count) {
        if (count <= 0) {
            return;
        }
        IrisSpawner spawner = engine(world).getData().getSpawnerLoader().load(plan.spawner());
        if (spawner != null) {
            spawner.spawn(engine(world), plan.chunkX(), plan.chunkZ());
        }
    }

    @Override
    public Optional<EntitySpec> entity(PackWorld world, String key) {
        IrisEntity entity = engine(world).getData().getEntityLoader().load(key);
        if (entity == null) {
            return Optional.empty();
        }
        return Optional.of(resolution(world, engine(world)).resolve(entity, entityRandom(world).rng()));
    }

    @Override
    public Optional<MarkerRule> marker(PackWorld world, String tag) {
        if (tag.equals(CAVE_FLOOR_MARKER) || tag.equals(CAVE_CEILING_MARKER)) {
            return Optional.empty();
        }
        Engine engine = engine(world);
        IrisMarker marker = engine.getData().getMarkerLoader().load(tag);
        if (marker == null) {
            return Optional.empty();
        }

        List<String> spawners = new ArrayList<>();
        for (String key : marker.getSpawners()) {
            if (engine.getData().getSpawnerLoader().load(key) == null) {
                IrisLogging.error("Cannot load spawner: " + key + " for marker " + tag);
                continue;
            }
            spawners.add(key);
        }
        return Optional.of(new MarkerRule(spawners, marker.isEmptyAbove(), marker.isRemoveOnChange()));
    }

    private Optional<Plan> planChunk(PackWorld world, Engine engine, Chunk chunk) {
        Predicate<IrisSpawner> canSpawn = spawner -> spawner.canSpawn(engine, chunk.chunkX(), chunk.chunkZ());
        int blockX = PowerOfTwoCoordinates.chunkToBlock(chunk.chunkX());
        int blockZ = PowerOfTwoCoordinates.chunkToBlock(chunk.chunkZ());
        IrisBiome centreBiome = engine.getSurfaceBiome(blockX + BLOCKS_TO_CHUNK_CENTRE, blockZ + BLOCKS_TO_CHUNK_CENTRE);
        IrisRegion region = engine.getRegion(blockX, blockZ);
        IrisBiome biome = engine.getSurfaceBiome(blockX, blockZ);

        List<IrisSpawner> candidates = Stream.concat(
                        shuffled(engine, engine.getDimension().getEntitySpawners()).stream()
                                .filter(canSpawn).filter(spawner -> spawner.isValid(centreBiome)),
                        Stream.concat(
                                shuffled(engine, region.getEntitySpawners()).stream().filter(canSpawn),
                                shuffled(engine, biome.getEntitySpawners()).stream().filter(canSpawn)))
                .filter(spawner -> chunk.livingEntities() < spawner.getMaxEntitiesPerChunk())
                .toList();

        List<Candidate> spawns = candidates.stream().flatMap(spawner -> spawnsOf(spawner, chunk.initial())).collect(Collectors.toList());
        Candidate chosen = IRare.expandWeighted(spawns).getRandom();
        if (chosen == null) {
            return Optional.empty();
        }
        return planChunkSpawn(world, engine, chunk, chosen);
    }

    private Optional<Plan> planChunkSpawn(PackWorld world, Engine engine, Chunk chunk, Candidate chosen) {
        IrisSpawner spawner = chosen.spawner();
        IrisEntitySpawn spawn = chosen.spawn();
        int count = LootResolver.inclusive(RNG.r, spawn.getMinSpawns(), spawn.getMaxSpawns());
        List<Attempt> attempts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int x = (chunk.chunkX() * CHUNK_SIZE) + RNG.r.i(CHUNK_SIZE);
            int z = (chunk.chunkZ() * CHUNK_SIZE) + RNG.r.i(CHUNK_SIZE);
            BlockPosition at = spawnPosition(engine, spawner, chunk.chunkX(), chunk.chunkZ(), x, z);
            if (at != null) {
                addAttempt(attempts, world, engine, chosen, at, true);
            }
        }
        return attempts.isEmpty() ? Optional.empty() : Optional.of(new Plan(spawner.getLoadKey(), chunk.chunkX(), chunk.chunkZ(), false, attempts));
    }

    /** Where a spawner of its group puts an entity at the column; null when it has no place for one. */
    private static BlockPosition spawnPosition(Engine engine, IrisSpawner spawner, int chunkX, int chunkZ, int x, int z) {
        int height = engine.getHeight(x, z, true);
        int heightWithFluid = engine.getHeight(x, z, false);
        return switch (spawner.getGroup()) {
            case NORMAL -> new BlockPosition(x, heightWithFluid + 1, z);
            case CAVE -> caveSpawnPosition(engine, chunkX, chunkZ);
            case UNDERWATER, BEACH -> new BlockPosition(x, RNG.r.i(height + 1, heightWithFluid), z);
        };
    }

    static BlockPosition caveSpawnPosition(Engine engine, int chunkX, int chunkZ) {
        if (J.isFolia()) {
            return null;
        }
        KList<IrisPosition> floors = engine.getMantle().findMarkers(chunkX, chunkZ, MarkerMatter.CAVE_FLOOR);
        IrisPosition floor = floors.getRandom(RNG.r);
        return floor == null ? null : new BlockPosition(floor.getX(), floor.getY() - engine.getMinHeight() + AIR_ABOVE_MARKER, floor.getZ());
    }

    private Optional<Plan> planMarker(PackWorld world, Engine engine, Marker marker) {
        IrisSpawner spawner = engine.getData().getSpawnerLoader().load(marker.spawner());
        IrisMarker irisMarker = engine.getData().getMarkerLoader().load(marker.marker());
        if (spawner == null) {
            return Optional.empty();
        }

        KList<IrisEntitySpawn> candidates = marker.initial() ? spawner.getInitialSpawns() : spawner.getSpawns();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        IrisEntitySpawn spawn = IRare.expandWeighted(candidates).getRandom();

        BlockPosition position = marker.position();
        int chunkX = PowerOfTwoCoordinates.blockToChunkFloor(position.x());
        int chunkZ = PowerOfTwoCoordinates.blockToChunkFloor(position.z());
        if (!spawner.canSpawn(engine, chunkX, chunkZ)) {
            return Optional.empty();
        }

        int count = LootResolver.inclusive(RNG.r, spawn.getMinSpawns(), spawn.getMaxSpawns());
        if (count <= 0) {
            return Optional.empty();
        }
        boolean exhaustsMarker = irisMarker != null && irisMarker.shouldExhaust();
        List<Attempt> attempts = new ArrayList<>();
        BlockPosition above = new BlockPosition(position.x(), position.y() + AIR_ABOVE_MARKER, position.z());
        for (int i = 0; i < count; i++) {
            addAttempt(attempts, world, engine, new Candidate(spawner, spawn), above, false);
        }
        return attempts.isEmpty() ? Optional.empty() : Optional.of(new Plan(spawner.getLoadKey(), chunkX, chunkZ, exhaustsMarker, attempts));
    }

    private void addAttempt(List<Attempt> attempts, PackWorld world, Engine engine, Candidate candidate, BlockPosition at, boolean checkSurface) {
        IrisEntity entity = candidate.spawn().getRealEntity(engine);
        if (entity == null) {
            IrisLogging.debug("      You are trying to spawn an entity that does not exist!");
            return;
        }

        IrisRange light = candidate.spawner().getAllowedLightLevels();
        EntitySpec spec = resolution(world, engine).resolve(entity, entityRandom(world).rng());
        attempts.add(new Attempt(at, spec, surfaceOf(entity.getSurface()),
                (int) Math.ceil(light.getMin()), (int) Math.floor(light.getMax()), checkSurface));
    }

    private static EntityResolution resolution(PackWorld world, Engine engine) {
        LootItems items = new LootItems(world.resources().items(), engine.isStudio());
        return new EntityResolution(items, engine.getWorld().platformWorld());
    }

    private static EntityRandom entityRandom(PackWorld world) {
        return world.state(EntityRandom.class, EntityRandom::new);
    }

    private static Surface surfaceOf(IrisSurface surface) {
        return Surface.valueOf(surface.name());
    }

    private static KList<IrisSpawner> shuffled(Engine engine, KList<String> keys) {
        return engine.getData().getSpawnerLoader().loadAll(keys).shuffleCopy(RNG.r);
    }

    private static Stream<Candidate> spawnsOf(IrisSpawner spawner, boolean initial) {
        KList<IrisEntitySpawn> spawns = initial ? spawner.getInitialSpawns() : spawner.getSpawns();
        return spawns.stream().map(spawn -> new Candidate(spawner, spawn));
    }

    /** A spawn of a spawner, which picks as often as the spawn's rarity says. */
    private record Candidate(IrisSpawner spawner, IrisEntitySpawn spawn) implements IRare {
        @Override
        public int getRarity() {
            return spawn.getRarity();
        }
    }

    private static Engine engine(PackWorld world) {
        return IrisPackWorld.of(world).engine();
    }

    /** The random stream entities are made from, seeded from the world like Iris always seeded it; one per world. */
    private static final class EntityRandom {
        private final RNG rng;

        private EntityRandom(PackWorld world) {
            this.rng = new RNG(IrisPackWorld.of(world).engine().getSeedManager().getEntity());
        }

        private RNG rng() {
            return rng;
        }
    }
}
