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

package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.builtin.IrisSeed;
import art.arcane.iris.builtin.IrisSeeds;
import art.arcane.iris.builtin.floating.FloatingChildBiome;
import art.arcane.iris.builtin.floating.FloatingChildSettings;
import art.arcane.iris.builtin.floating.FloatingHost;
import art.arcane.iris.builtin.floating.IslandFootprint;
import art.arcane.iris.builtin.objects.ObjectHost;
import art.arcane.iris.builtin.objects.ObjectPlacement;
import art.arcane.iris.builtin.objects.ObjectSize;
import art.arcane.iris.builtin.objects.PlaceableObject;
import art.arcane.iris.engine.object.terrain.floating.FloatingIslandBoundarySampler;
import art.arcane.iris.engine.object.terrain.floating.FloatingIslandSample;
import art.arcane.iris.engine.object.terrain.floating.FloatingWorld;
import art.arcane.iris.engine.object.terrain.floating.ResolvedFloatingChild;
import art.arcane.volmlib.util.cache.CacheKey;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.ModuleLog;
import com.volmit.config.FeatureSettings.Entry;
import com.volmit.config.Level;
import com.volmit.config.PackSettings;
import com.volmit.mantle.BlockBox;
import com.volmit.resource.ResourceId;
import com.volmit.stage.MantleStageContext;
import com.volmit.world.BlockPosition;
import com.volmit.world.PackWorld;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Places the objects of floating child biomes on and under their islands into the mantle for
 * {@link art.arcane.iris.engine.modifier.FloatingModule}. One per world. Its reach is the largest placement reach of any
 * floating object the dimension's biomes can place, at least 16 blocks.
 */
public final class FloatingObjectPlacing {
    private static final int INVERTED_PICK_ATTEMPTS = 8;
    private static final long CHUNK_RANDOM_SALT = 0x0FA710BEL;
    private static final int CHUNK_COLUMNS = 256;
    private static final int MIN_REACH_BLOCKS = 16;
    private static final int LARGE_REACH_WARNING_BLOCKS = 128;
    private static final int FLAT_TOLERANCE_BLOCKS = 2;
    private static final int LOOSE_FLAT_TOLERANCE_BLOCKS = 4;
    private static final int QUARTER_TURN_DEGREES = 90;
    private static final int QUARTER_TURNS = 4;
    private static final double CHANCE_JITTER = 0.005;
    private static final String BIOME_PATH_PREFIX = "biome/";

    private final FloatingWorld world;
    private final FloatingHost host;
    private final ObjectHost objects;
    private final long mantleSeed;
    private final ModuleLog log;

    FloatingObjectPlacing(FloatingWorld world, long mantleSeed, ModuleLog log) {
        this.world = world;
        this.host = world.host();
        this.objects = world.objects();
        this.mantleSeed = mantleSeed;
        this.log = log;
    }

    /** The floating object placing of {@code world}. */
    public static FloatingObjectPlacing of(PackWorld world, ModuleLog log) {
        return world.state(FloatingObjectPlacing.class,
                generating -> new FloatingObjectPlacing(FloatingWorld.of(generating),
                        generating.extension(IrisSeeds.class).orElseThrow().seed(IrisSeed.MANTLE), log));
    }

    /** Places what originates in the chunk of {@code context} into the mantle. */
    public void place(MantleStageContext context) {
        int minX = context.originX();
        int minZ = context.originZ();
        BlockBox bounds = context.terrain().bounds();
        int worldHeight = bounds.maxY() - bounds.minY();
        RNG chunkRng = new RNG(CacheKey.key(context.chunkX(), context.chunkZ()) + mantleSeed + CHUNK_RANDOM_SALT);
        FloatingIslandSampleResolver samples = new FloatingIslandSampleResolver(world, worldHeight,
                new FloatingIslandBoundarySampler(host::surfaceBiome), log);

        FloatingIslandSample.clearChunkMemo();
        Chunk chunk = new Chunk(context, minX, minZ,worldHeight, chunkRng, samples, sampleColumns(samples, minX, minZ));

        // The maps are lookup indices only. Iteration MUST follow the encounter order of the deterministic 0..255 column
        // scan: identity-hash order changes per JVM run, and the shared chunk random makes placement output depend on order.
        Map<ResolvedFloatingChild, KList<Integer>> entryColumns = new IdentityHashMap<>();
        Map<ResolvedFloatingChild, KList<Integer>> bottomEntryColumns = new IdentityHashMap<>();
        KList<ResolvedFloatingChild> entryOrder = new KList<>();
        KList<ResolvedFloatingChild> bottomEntryOrder = new KList<>();
        for (int i = 0; i < CHUNK_COLUMNS; i++) {
            FloatingIslandSample sample = chunk.samples[i];
            if (sample == null || sample.entry == null) {
                continue;
            }
            int column = i;
            entryColumns.computeIfAbsent(sample.entry, entry -> {
                entryOrder.add(entry);
                return new KList<>();
            }).add(column);
            ResolvedFloatingChild bottomEntry = sample.bottomEntry();
            if (bottomEntry != null) {
                bottomEntryColumns.computeIfAbsent(bottomEntry, entry -> {
                    bottomEntryOrder.add(entry);
                    return new KList<>();
                }).add(column);
            }
        }

        for (ResolvedFloatingChild entry : entryOrder) {
            placeOnTop(chunk, entry, entryColumns.get(entry));
        }
        for (ResolvedFloatingChild entry : bottomEntryOrder) {
            placeUnder(chunk, entry, bottomEntryColumns.get(entry));
        }
    }

    private static FloatingIslandSample[] sampleColumns(FloatingIslandSampleResolver samples, int minX, int minZ) {
        FloatingIslandSample[] sampled = new FloatingIslandSample[CHUNK_COLUMNS];
        for (int xf = 0; xf < 16; xf++) {
            for (int zf = 0; zf < 16; zf++) {
                sampled[(zf << 4) | xf] = samples.sample(minX + xf, minZ + zf);
            }
        }
        return sampled;
    }

    private void placeOnTop(Chunk chunk, ResolvedFloatingChild entry, KList<Integer> columns) {
        if (columns.isEmpty()) {
            return;
        }
        ResourceId target = targetOf(chunk, entry, columns);

        for (ObjectPlacement placement : entry.settings().floatingObjects()) {
            placeFloating(chunk, placement, columns, entry);
        }

        KList<ObjectPlacement> surface = target != null ? entry.resolveTopObjects(target) : null;
        List<ObjectPlacement> extras = entry.settings().extraObjects();
        boolean hasSurface = surface != null && !surface.isEmpty();
        if (!hasSurface && extras.isEmpty()) {
            return;
        }
        KList<Integer> interior = interiorColumns(chunk, columns, entry, IslandFace.Side.TOP);
        if (hasSurface) {
            for (ObjectPlacement placement : surface) {
                placeAnchored(chunk, placement, columns, interior, entry);
            }
        }
        for (ObjectPlacement placement : extras) {
            placeAnchored(chunk, placement, columns, interior, entry);
        }
    }

    private void placeUnder(Chunk chunk, ResolvedFloatingChild entry, KList<Integer> columns) {
        if (columns.isEmpty()) {
            return;
        }
        ResourceId target = targetOf(chunk, entry, columns);
        KList<ObjectPlacement> bottom = target != null ? entry.resolveBottomObjects() : null;
        if (bottom == null || bottom.isEmpty()) {
            return;
        }
        KList<Integer> interior = interiorColumns(chunk, columns, entry, IslandFace.Side.BOTTOM);
        for (ObjectPlacement placement : bottom) {
            placeInverted(chunk, placement, columns, interior, entry);
        }
    }

    /** The biome the child's objects come from, its parent taken at the first of its columns. */
    private ResourceId targetOf(Chunk chunk, ResolvedFloatingChild entry, KList<Integer> columns) {
        int firstKey = columns.get(0);
        ResourceId parent = chunk.resolver.parent(chunk.minX + (firstKey & 15), chunk.minZ + (firstKey >> 4));
        return entry.realBiome(parent);
    }

    private void placeFloating(Chunk chunk, ObjectPlacement placement, KList<Integer> columns, ResolvedFloatingChild entry) {
        RNG rng = chunk.rng;
        int density = objects.density(placement, rng, chunk.minX, chunk.minZ);
        for (int i = 0; i < density; i++) {
            Optional<PlaceableObject> picked = pick(placement, entry, rng);
            if (picked.isEmpty()) {
                continue;
            }
            int key = columns.get(rng.i(columns.size()));
            host.placeFloating(chunk.context, picked.get(),chunk.minX + (key & 15), chunk.minZ + (key >> 4), placement, rng);
        }
    }

    private void placeAnchored(Chunk chunk, ObjectPlacement placement, KList<Integer> columns, KList<Integer> interior,
                               ResolvedFloatingChild entry) {
        RNG rng = chunk.rng;
        int density = objects.density(placement, rng, chunk.minX, chunk.minZ);
        for (int i = 0; i < density; i++) {
            Optional<PlaceableObject> picked = pick(placement, entry, rng);
            if (picked.isEmpty()) {
                continue;
            }
            PlaceableObject object = picked.get();
            IslandFootprint footprint = host.footprint(object);

            KList<Integer> pool = interior.isEmpty() ? columns : interior;
            int pickedKey = pool.get(rng.i(pool.size()));
            int pickedXf = pickedKey & 15;
            int pickedZf = pickedKey >> 4;
            FloatingIslandSample pickedSample = chunk.samples[(pickedZf << 4) | pickedXf];
            if (pickedSample == null) {
                continue;
            }
            int pickTopY = pickedSample.topY();
            int pickedX = chunk.minX + pickedXf;
            int pickedZ = chunk.minZ + pickedZf;
            if (!isFootprintFlat(footprint, pickedX, pickedZ, pickTopY, chunk.resolver, entry, FLAT_TOLERANCE_BLOCKS)
                    && !isFootprintFlat(footprint, pickedX, pickedZ, pickTopY, chunk.resolver, entry, LOOSE_FLAT_TOLERANCE_BLOCKS)) {
                continue;
            }

            int x = pickedX - footprint.tallestX();
            int z = pickedZ - footprint.tallestZ();
            int y = pickTopY + 1 - footprint.lowestSolidY();
            host.placeOnTop(chunk.context, object,x, y, z, placement, IslandFace.top(chunk.resolver, entry, pickTopY, chunk.worldHeight), rng);
        }
    }

    private void placeInverted(Chunk chunk, ObjectPlacement placement, KList<Integer> columns, KList<Integer> interior,
                               ResolvedFloatingChild entry) {
        RNG rng = chunk.rng;
        int density = objects.density(placement, rng, chunk.minX, chunk.minZ);
        for (int i = 0; i < density; i++) {
            Optional<PlaceableObject> picked = pick(placement, entry, rng);
            if (picked.isEmpty()) {
                continue;
            }
            PlaceableObject object = picked.get();
            IslandFootprint footprint = host.footprint(object);
            UndersideTurn turn = new UndersideTurn(rng.i(QUARTER_TURNS) * QUARTER_TURN_DEGREES);

            Optional<BottomAnchor> anchor = pickBottomAnchor(chunk, footprint, turn, interior.isEmpty() ? columns : interior, entry);
            if (anchor.isEmpty()) {
                continue;
            }
            int pickedXf = anchor.get().xf();
            int pickedZf = anchor.get().zf();
            int pickBottomY = anchor.get().bottomY();
            int x = invertedBaseX(chunk.minX, pickedXf, footprint, turn);
            int z = invertedBaseZ(chunk.minZ, pickedZf, footprint, turn);
            int y = invertedBaseY(pickBottomY, footprint, turn);
            host.placeUnder(chunk.context, object,x, y, z, turn.yRotation(), placement,
                    IslandFace.bottom(chunk.resolver, entry, pickBottomY, chunk.worldHeight), rng);
        }
    }

    private Optional<BottomAnchor> pickBottomAnchor(Chunk chunk, IslandFootprint footprint, UndersideTurn turn, KList<Integer> pool,
                                                    ResolvedFloatingChild entry) {
        for (int attempt = 0; attempt < INVERTED_PICK_ATTEMPTS; attempt++) {
            int pickedKey = pool.get(chunk.rng.i(pool.size()));
            int candidateXf = pickedKey & 15;
            int candidateZf = pickedKey >> 4;
            FloatingIslandSample candidate = chunk.samples[(candidateZf << 4) | candidateXf];
            if (candidate == null) {
                continue;
            }
            int candidateBottomY = candidate.bottomY();
            if (candidateBottomY < 0) {
                continue;
            }
            int candidateX = chunk.minX + candidateXf;
            int candidateZ = chunk.minZ + candidateZf;
            if (!isFootprintFlatBottom(footprint, turn, candidateX, candidateZ, candidateBottomY, chunk.resolver, entry, FLAT_TOLERANCE_BLOCKS)
                    && !isFootprintFlatBottom(footprint, turn, candidateX, candidateZ, candidateBottomY, chunk.resolver, entry,
                    LOOSE_FLAT_TOLERANCE_BLOCKS)) {
                continue;
            }
            return Optional.of(new BottomAnchor(candidateXf, candidateZf, candidateBottomY));
        }
        return Optional.empty();
    }

    /** One draw of the chance, then the object the placement picks, scaled, and shrunk as the island shrinks its objects. */
    private Optional<PlaceableObject> pick(ObjectPlacement placement, ResolvedFloatingChild entry, RNG rng) {
        if (!rng.chance(placement.chance() + rng.d(-CHANCE_JITTER, CHANCE_JITTER))) {
            return Optional.empty();
        }
        Optional<PlaceableObject> scaled = objects.pickScaled(placement, rng);
        if (scaled.isEmpty() || !entry.hasObjectShrink()) {
            return scaled;
        }
        return host.shrink(scaled.get(), entry.shrinkFactor(), rng);
    }

    static boolean isFootprintFlatBottom(IslandFootprint footprint, UndersideTurn turn, int pickedX, int pickedZ, int pickBottomY,
                                         IslandFace.SampleProvider samples, ResolvedFloatingChild entry, int tolerance) {
        BlockPosition anchor = invertedFootprintAnchor(footprint, turn);
        long[] cells = footprint.columns();
        if (cells.length == 0) {
            return false;
        }
        for (long encoded : cells) {
            BlockPosition cell = turn.turn(IslandFootprint.columnX(encoded), 0, IslandFootprint.columnZ(encoded));
            int columnX = pickedX + cell.x() - anchor.x();
            int columnZ = pickedZ + cell.z() - anchor.z();
            FloatingIslandSample sample = samples.sample(columnX, columnZ);
            if (!IslandFace.matchesAnchor(sample, entry, IslandFace.Side.BOTTOM)) {
                return false;
            }
            int bottomY = sample.bottomY();
            if (bottomY < 0 || Math.abs(bottomY - pickBottomY) > tolerance) {
                return false;
            }
        }
        return true;
    }

    static int invertedBaseX(int minX, int pickedXf, IslandFootprint footprint, UndersideTurn turn) {
        return minX + pickedXf - invertedFootprintAnchor(footprint, turn).x();
    }

    static int invertedBaseY(int pickBottomY, IslandFootprint footprint, UndersideTurn turn) {
        return pickBottomY - 1 - invertedSolidAnchor(footprint, turn).y();
    }

    static int invertedBaseZ(int minZ, int pickedZf, IslandFootprint footprint, UndersideTurn turn) {
        return minZ + pickedZf - invertedFootprintAnchor(footprint, turn).z();
    }

    private static BlockPosition invertedFootprintAnchor(IslandFootprint footprint, UndersideTurn turn) {
        return turn.turn(footprint.tallestX(), 0, footprint.tallestZ());
    }

    private static BlockPosition invertedSolidAnchor(IslandFootprint footprint, UndersideTurn turn) {
        return turn.turn(footprint.tallestX(), footprint.lowestSolidY(), footprint.tallestZ());
    }

    static boolean isFootprintFlat(IslandFootprint footprint, int pickedX, int pickedZ, int pickTopY,
                                   IslandFace.SampleProvider samples, ResolvedFloatingChild entry, int tolerance) {
        long[] cells = footprint.columns();
        if (cells.length == 0) {
            return false;
        }
        for (long encoded : cells) {
            int columnX = pickedX + (IslandFootprint.columnX(encoded) - footprint.tallestX());
            int columnZ = pickedZ + (IslandFootprint.columnZ(encoded) - footprint.tallestZ());
            FloatingIslandSample sample = samples.sample(columnX, columnZ);
            if (!IslandFace.matchesAnchor(sample, entry, IslandFace.Side.TOP) || Math.abs(sample.topY() - pickTopY) > tolerance) {
                return false;
            }
        }
        return true;
    }

    private static KList<Integer> interiorColumns(Chunk chunk, KList<Integer> columns, ResolvedFloatingChild entry, IslandFace.Side side) {
        KList<Integer> interior = new KList<>();
        for (int key : columns) {
            int x = chunk.minX + (key & 15);
            int z = chunk.minZ + (key >> 4);
            if (IslandFace.matchesAnchor(chunk.resolver.sample(x + 1, z), entry, side)
                    && IslandFace.matchesAnchor(chunk.resolver.sample(x - 1, z), entry, side)
                    && IslandFace.matchesAnchor(chunk.resolver.sample(x, z + 1), entry, side)
                    && IslandFace.matchesAnchor(chunk.resolver.sample(x, z - 1), entry, side)) {
                interior.add(key);
            }
        }
        return interior;
    }

    /** How far, in blocks along X and Z, the floating objects of the pack {@code settings} describe may write beyond their chunk. */
    public static int reach(PackSettings settings, ModuleLog log) {
        Reach reach = new Reach(settings.extension(FloatingHost.class).orElseThrow(),
                settings.extension(ObjectHost.class).orElseThrow(), log);
        int maxObjectExtent = MIN_REACH_BLOCKS;
        try {
            for (Entry<FloatingChildSettings> declared : settings.declared(FloatingChildSettings.class)) {
                if (declared.level() != Level.BIOME) {
                    continue;
                }
                ResourceId biome = biomeIdOf(declared.source());
                for (FloatingChildBiome child : declared.value().children()) {
                    maxObjectExtent = Math.max(maxObjectExtent, reach.of(child, biome));
                }
            }
        } catch (Throwable e) {
            log.error("Failed to work out the reach of the floating biomes' objects", e);
        }
        return maxObjectExtent;
    }

    /** A settings entry's source names a biome as {@code biome/<key>}; the pack's resources name it by the key alone. */
    private static ResourceId biomeIdOf(ResourceId source) {
        String path = source.path();
        return path.startsWith(BIOME_PATH_PREFIX) ? new ResourceId(source.namespace(), path.substring(BIOME_PATH_PREFIX.length())) : source;
    }

    /** The reach of floating children's objects, each object sized once. */
    private static final class Reach {
        private final FloatingHost host;
        private final ObjectHost objects;
        private final ModuleLog log;
        private final Map<String, Optional<ObjectSize>> sizes = new HashMap<>();
        private final Set<String> warnedLargeObjects = new HashSet<>();

        private Reach(FloatingHost host, ObjectHost objects, ModuleLog log) {
            this.host = host;
            this.objects = objects;
            this.log = log;
        }

        int of(FloatingChildBiome child, ResourceId parent) {
            int reach = 0;
            reach = Math.max(reach, placementRadius(child.floatingObjects()));
            reach = Math.max(reach, placementRadius(child.extraObjects()));
            reach = Math.max(reach, placementRadius(child.topObjectOverrides()));
            reach = Math.max(reach, placementRadius(child.bottomObjectOverrides()));
            try {
                ResourceId target = child.biome().isBlank() ? parent : host.biome(child.biome()).orElse(parent);
                List<ObjectPlacement> surface = child.inheritObjects() ? FloatingWorld.surfaceObjects(objects, target) : List.of();
                reach = Math.max(reach, placementRadius(ResolvedFloatingChild.topObjects(child, surface)));
                reach = Math.max(reach, placementRadius(ResolvedFloatingChild.bottomObjects(child)));
            } catch (Throwable e) {
                log.error("Failed to work out the reach of a floating biome's objects", e);
            }
            return reach;
        }

        private int placementRadius(List<ObjectPlacement> placements) {
            int radius = 0;
            for (ObjectPlacement placement : placements) {
                if (placement == null) {
                    continue;
                }
                for (String objectKey : placement.place()) {
                    radius = Math.max(radius, objectReach(objectKey, placement));
                }
            }
            return radius;
        }

        private int objectReach(String objectKey, ObjectPlacement placement) {
            try {
                Optional<ObjectSize> size = sizes.computeIfAbsent(objectKey, objects::sizeOf);
                if (size.isEmpty()) {
                    return 0;
                }
                int reach = PlacementReach.of(size.get(), placement);
                if (reach > LARGE_REACH_WARNING_BLOCKS && warnedLargeObjects.add(objectKey)) {
                    log.warn("Floating object " + objectKey + " has a large placement reach (" + reach + " blocks) and may increase memory usage!");
                }
                return reach;
            } catch (Throwable e) {
                log.error("Failed to size a floating object", e);
                return 0;
            }
        }
    }

    private record BottomAnchor(int xf, int zf, int bottomY) {
    }

    /** What one chunk's placements share: where it is, its random, and its columns' island samples. */
    private record Chunk(MantleStageContext context, int minX, int minZ, int worldHeight, RNG rng,
                         FloatingIslandSampleResolver resolver, FloatingIslandSample[] samples) {
    }
}
