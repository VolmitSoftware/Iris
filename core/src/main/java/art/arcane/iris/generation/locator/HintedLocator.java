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

package art.arcane.iris.generation.locator;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.GenerationSessionLease;
import art.arcane.iris.generation.runtime.WrongEngineBroException;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.runtime.DimensionStackContext;
import art.arcane.iris.generation.runtime.DimensionTerrainContext;
import art.arcane.iris.generation.runtime.IrisComplex;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.generation.concurrent.BurstExecutor;
import art.arcane.iris.generation.concurrent.MultiBurst;
import art.arcane.iris.generation.context.IrisContext;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.collection.KSet;
import art.arcane.volmlib.util.math.Position2;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

public final class HintedLocator<T> implements Locator<T> {
    private static final int BIOME_STRIDE_CHUNKS = 4;
    private static final int REGION_STRIDE_CHUNKS = 16;
    private static final int MAX_SAMPLE_RADIUS_CHUNKS = 100000;

    private final Locator<T> exact;
    private final Function<Engine, SearchPlan> planner;

    public HintedLocator(Locator<T> exact, Function<Engine, SearchPlan> planner) {
        this.exact = exact;
        this.planner = planner;
    }

    @FunctionalInterface
    public interface CoarseSample {
        boolean test(int blockX, int blockZ);
    }

    public static final class SearchPlan {
        private final boolean possible;
        private final CoarseSample coarse;
        private final int strideChunks;
        private final Locator<?> exactOverride;

        private SearchPlan(boolean possible, CoarseSample coarse, int strideChunks, Locator<?> exactOverride) {
            this.possible = possible;
            this.coarse = coarse;
            this.strideChunks = strideChunks;
            this.exactOverride = exactOverride;
        }

        public static SearchPlan impossible() {
            return new SearchPlan(false, null, 1, null);
        }

        public static SearchPlan unpruned() {
            return new SearchPlan(true, null, 1, null);
        }

        public static SearchPlan of(CoarseSample coarse, int strideChunks, Locator<?> exactOverride) {
            return new SearchPlan(true, coarse, strideChunks, exactOverride);
        }

        public boolean isPossible() {
            return possible;
        }

        public CoarseSample getCoarse() {
            return coarse;
        }

        public int getStrideChunks() {
            return strideChunks;
        }

        public Locator<?> getExactOverride() {
            return exactOverride;
        }
    }

    @Override
    public boolean matches(Engine engine, Position2 chunk) {
        return exact.matches(engine, chunk);
    }

    @Override
    public boolean matchesForSearch(Engine engine, Position2 chunk) {
        return exact.matchesForSearch(engine, chunk);
    }

    @Override
    public Position2 nearestRecorded(Engine engine, Position2 origin, int maxChunkRadius) {
        return exact.nearestRecorded(engine, origin, maxChunkRadius);
    }

    @Override
    public SearchCandidate nearestRecordedCandidate(Engine engine, Position2 origin, int maxChunkRadius) {
        return exact.nearestRecordedCandidate(engine, origin, maxChunkRadius);
    }

    @Override
    public SearchCandidate candidateForMatchedChunk(Engine engine, Position2 chunk) {
        return exact.candidateForMatchedChunk(engine, chunk);
    }

    @Override
    public CompletableFuture<Position2> find(Engine engine, Position2 pos, long timeout, Consumer<Integer> checks) throws WrongEngineBroException {
        if (engine.isClosed()) {
            throw new WrongEngineBroException();
        }

        SearchCandidate recorded = nearestRecordedCandidate(engine, pos, MAX_SAMPLE_RADIUS_CHUNKS);
        int activeRadius = Locator.activeSearchRadius(pos, recorded, MAX_SAMPLE_RADIUS_CHUNKS);

        AtomicBoolean stop = new AtomicBoolean(false);
        CompletableFuture<Position2> search = MultiBurst.burst.completeValueAsync(() -> {
            try (GenerationSessionLease lease = engine.acquireGenerationLease("hinted_locator_search");
                 IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
                SearchPlan plan = planner.apply(engine);

                SearchCandidate nearest = search(engine, plan, pos, activeRadius, recorded, timeout, checks, stop);
                return nearest == null ? null : nearest.chunk();
            }
        });
        return LocatorCanceller.requestScoped(search, stop);
    }

    private SearchCandidate search(
            Engine engine,
            SearchPlan plan,
            Position2 pos,
            int maximumRadius,
            SearchCandidate recorded,
            long timeout,
            Consumer<Integer> checks,
            AtomicBoolean stop
    ) {
        if (!plan.isPossible()
                && !GenerationLocatorPolicy.hasHistoricalFallbackInSquare(engine, pos, maximumRadius)) {
            return recorded;
        }
        Locator<?> verifier = plan.getExactOverride() != null ? plan.getExactOverride() : exact;
        int stride = Math.max(1, plan.getStrideChunks());
        int batchTarget = Math.max(1, IrisSettings.getThreadCount(IrisSettings.get().getConcurrency().getParallelism()) * 32);
        int maxRing = Math.max(0, (maximumRadius + stride - 1) / stride + 1);
        PrecisionStopwatch stopwatch = PrecisionStopwatch.start();
        AtomicInteger covered = new AtomicInteger();
        AtomicReference<SearchCandidate> nearest = new AtomicReference<>(recorded);
        KList<Position2> batch = new KList<>();

        for (int ring = 0; ring <= maxRing; ring++) {
            if (stop.get() || engine.isClosing() || stopwatch.getMilliseconds() >= timeout) {
                return nearest.get();
            }

            int cells = ring == 0 ? 1 : ring * 8;
            for (int index = 0; index < cells; index++) {
                Position2 sample = ringPosition(pos, ring, stride, index);
                if (sample == null) {
                    continue;
                }
                batch.add(sample);
                if (batch.size() < batchTarget) {
                    continue;
                }
                processBatch(engine, plan, verifier, batch, pos, maximumRadius, nearest,
                        stride, timeout, stopwatch, covered, checks, stop);
                batch.clear();
                if (stop.get() || engine.isClosing() || stopwatch.getMilliseconds() >= timeout) {
                    return nearest.get();
                }
            }

            int radius = Locator.activeSearchRadius(pos, nearest.get(), maximumRadius);
            if (ring == maxRing || nearest.get() != null && (long) ring * stride > radius) {
                if (!batch.isEmpty()) {
                    processBatch(engine, plan, verifier, batch, pos, maximumRadius, nearest,
                            stride, timeout, stopwatch, covered, checks, stop);
                }
                return nearest.get();
            }
        }

        return nearest.get();
    }

    private void processBatch(Engine engine, SearchPlan plan, Locator<?> verifier, KList<Position2> batch,
                              Position2 origin, int maximumRadius, AtomicReference<SearchCandidate> nearest,
                              int stride, long timeout, PrecisionStopwatch stopwatch, AtomicInteger covered,
                              Consumer<Integer> checks, AtomicBoolean stop) {
        batch.sort(Comparator.comparingLong(sample -> chunkDistanceSquared(origin, sample)));
        int size = batch.size();
        boolean[] hits = new boolean[size];
        boolean fine = stride <= 1;
        CoarseSample coarse = plan.getCoarse();
        int radius = Locator.activeSearchRadius(origin, nearest.get(), maximumRadius);
        BurstExecutor executor = MultiBurst.burst.burst(size);

        for (int i = 0; i < size; i++) {
            int index = i;
            Position2 sample = batch.get(i);
            executor.queue(() -> {
                if (stop.get() || engine.isClosing() || stopwatch.getMilliseconds() >= timeout
                        || !intersectsSearchRadius(origin, sample, fine ? 0 : stride, radius)) {
                    return;
                }

                int blockX = (sample.getX() << 4) + 8;
                int blockZ = (sample.getZ() << 4) + 8;
                boolean historicalFallback = GenerationLocatorPolicy.hasHistoricalFallbackInSquare(
                        engine,
                        sample,
                        fine ? 0 : stride
                );

                if (!historicalFallback && (!plan.isPossible()
                        || coarse != null && !coarse.test(blockX, blockZ))) {
                    return;
                }

                if (fine) {
                    if (verifier.matchesForSearch(engine, sample)) {
                        recordCandidate(engine, sample, origin, nearest);
                    }

                    return;
                }

                hits[index] = true;
            });
        }

        executor.complete();
        covered.updateAndGet(value -> (int) Math.min(Integer.MAX_VALUE,
                (long) value + (long) size * stride * stride));
        checks.accept(covered.get());

        if (fine) {
            return;
        }

        for (int i = 0; i < size; i++) {
            if (!hits[i]) {
                continue;
            }

            if (stop.get() || engine.isClosing() || stopwatch.getMilliseconds() >= timeout) {
                return;
            }

            int searchRadius = Locator.activeSearchRadius(origin, nearest.get(), maximumRadius);
            if (intersectsSearchRadius(origin, batch.get(i), stride, searchRadius)) {
                refine(engine, verifier, batch.get(i), origin, searchRadius, nearest,
                        stride, timeout, stopwatch, stop);
            }
        }
    }

    private void refine(Engine engine, Locator<?> verifier, Position2 candidate, Position2 origin,
                        int searchRadius, AtomicReference<SearchCandidate> nearest,
                        int stride, long timeout, PrecisionStopwatch stopwatch, AtomicBoolean stop) {
        KList<Position2> cells = new KList<>();

        for (int ring = 0; ring <= stride; ring++) {
            if (stop.get() || engine.isClosing() || stopwatch.getMilliseconds() >= timeout) {
                return;
            }

            cells.clear();
            appendRing(cells, candidate, ring, 1);
            BurstExecutor executor = MultiBurst.burst.burst(cells.size());

            for (Position2 cell : cells) {
                executor.queue(() -> {
                    if (stop.get() || engine.isClosing() || stopwatch.getMilliseconds() >= timeout
                            || !intersectsSearchRadius(origin, cell, 0, searchRadius)) {
                        return;
                    }

                    if (verifier.matchesForSearch(engine, cell)) {
                        recordCandidate(engine, cell, origin, nearest);
                    }
                });
            }

            executor.complete();

        }
    }

    private void recordCandidate(Engine engine, Position2 chunk, Position2 origin,
                                 AtomicReference<SearchCandidate> nearest) {
        SearchCandidate candidate = candidateForMatchedChunk(engine, chunk);
        if (candidate != null) {
            Locator.updateNearest(nearest, candidate, origin);
        }
    }

    private static long chunkDistanceSquared(Position2 origin, Position2 sample) {
        long deltaX = (long) origin.getX() - sample.getX();
        long deltaZ = (long) origin.getZ() - sample.getZ();
        return deltaX * deltaX + deltaZ * deltaZ;
    }

    private static boolean intersectsSearchRadius(Position2 origin, Position2 sample, int extent, int radius) {
        return Math.abs((long) sample.getX() - origin.getX()) <= (long) radius + extent
                && Math.abs((long) sample.getZ() - origin.getZ()) <= (long) radius + extent;
    }

    static void appendRing(KList<Position2> batch, Position2 origin, int ring, int stride) {
        int cells = ring == 0 ? 1 : ring * 8;
        for (int index = 0; index < cells; index++) {
            Position2 position = ringPosition(origin, ring, stride, index);
            if (position != null) {
                batch.add(position);
            }
        }
    }

    private static Position2 ringPosition(Position2 origin, int ring, int stride, int index) {
        if (ring == 0) {
            return origin;
        }
        int horizontalCells = (ring * 2 + 1) * 2;
        int deltaX = index < horizontalCells ? index / 2 - ring : ((index & 1) == 0 ? -ring : ring);
        int deltaZ = index < horizontalCells ? ((index & 1) == 0 ? -ring : ring)
                : (index - horizontalCells) / 2 - ring + 1;
        long chunkX = (long) origin.getX() + (long) deltaX * stride;
        long chunkZ = (long) origin.getZ() + (long) deltaZ * stride;
        if (chunkX < (Integer.MIN_VALUE >> 4) || chunkX > (Integer.MAX_VALUE >> 4)
                || chunkZ < (Integer.MIN_VALUE >> 4) || chunkZ > (Integer.MAX_VALUE >> 4)) {
            return null;
        }
        return new Position2((int) chunkX, (int) chunkZ);
    }

    public static SearchPlan biomePlan(Engine engine, String biomeKey) {
        if (engine.getFocus() != null || engine.getFocusRegion() != null) {
            return SearchPlan.unpruned();
        }
        if (engine.getDimensionStackContext() != null) {
            for (IrisBiome biome : engine.getAllBiomes()) {
                if (biome != null && biomeKey.equals(biome.getLoadKey())) {
                    return SearchPlan.unpruned();
                }
            }
        }

        IrisComplex complex = engine.getComplex();
        BiomeSource source = new BiomeSource(engine);
        KSet<String> landHosts = new KSet<>();
        KSet<String> seaHosts = new KSet<>();
        KSet<String> shoreHosts = new KSet<>();
        KSet<String> surfaceRegions = new KSet<>();
        KSet<String> caveRegions = new KSet<>();

        for (IrisRegion region : engine.getDimension().getAllRegions(engine)) {
            if (region == null) {
                continue;
            }

            boolean hosted = false;

            for (String root : region.getLandBiomes()) {
                if (source.closureContains(root, biomeKey)) {
                    landHosts.add(root);
                    hosted = true;
                }
            }

            for (String root : region.getSeaBiomes()) {
                if (source.closureContains(root, biomeKey)) {
                    seaHosts.add(root);
                    hosted = true;
                }
            }

            for (String root : region.getShoreBiomes()) {
                if (source.closureContains(root, biomeKey)) {
                    shoreHosts.add(root);
                    hosted = true;
                }
            }

            if (hosted) {
                surfaceRegions.add(region.getLoadKey());
            }

            if (region.getCaveBiomes().contains(biomeKey)) {
                caveRegions.add(region.getLoadKey());
            }
        }

        if (!surfaceRegions.isEmpty()) {
            return SearchPlan.of(
                    surfaceCoarse(complex, surfaceRegions, landHosts, seaHosts, shoreHosts),
                    BIOME_STRIDE_CHUNKS,
                    null
            );
        }

        if (!caveRegions.isEmpty()) {
            CoarseSample coarse = caveCoarse(complex, caveRegions, biomeKey);
            Locator<IrisBiome> caveExact = (e, c) -> {
                IrisBiome biome = e.getCaveBiome((c.getX() << 4) + 8, (c.getZ() << 4) + 8);
                return biome != null && biomeKey.equals(biome.getLoadKey());
            };

            return SearchPlan.of(coarse, BIOME_STRIDE_CHUNKS, caveExact);
        }

        for (IrisBiome reachableBiome : engine.getDimension().getReachableBiomes(engine)) {
            if (reachableBiome != null && biomeKey.equals(reachableBiome.getLoadKey())) {
                return SearchPlan.unpruned();
            }
        }
        return SearchPlan.impossible();
    }

    public static SearchPlan caveBiomePlan(Engine engine, String biomeKey) {
        if (engine.getFocus() != null || engine.getFocusRegion() != null) {
            return SearchPlan.unpruned();
        }

        IrisComplex complex = engine.getComplex();
        KSet<String> caveRegions = new KSet<>();

        for (IrisRegion region : engine.getDimension().getAllRegions(engine)) {
            if (region != null && region.getCaveBiomes().contains(biomeKey)) {
                caveRegions.add(region.getLoadKey());
            }
        }

        if (caveRegions.isEmpty()) {
            return SearchPlan.impossible();
        }

        return SearchPlan.of(caveCoarse(complex, caveRegions, biomeKey), BIOME_STRIDE_CHUNKS, null);
    }

    public static SearchPlan regionPlan(Engine engine, String regionKey) {
        if (engine.getFocus() != null || engine.getFocusRegion() != null) {
            return SearchPlan.unpruned();
        }

        DimensionStackContext stackContext = engine.getDimensionStackContext();
        if (stackContext != null) {
            for (DimensionTerrainContext terrainContext : stackContext.getLayersTopToBottom()) {
                for (IrisRegion region : terrainContext.getDimension().getAllRegions(terrainContext)) {
                    if (region != null && regionKey.equals(region.getLoadKey())) {
                        return SearchPlan.unpruned();
                    }
                }
            }
        }

        if (!engine.getDimension().getRegions().contains(regionKey)) {
            return SearchPlan.impossible();
        }

        IrisComplex complex = engine.getComplex();
        CoarseSample coarse = (x, z) -> {
            IrisRegion region = complex.getRegionStream().get(x, z);
            return region != null && regionKey.equals(region.getLoadKey());
        };

        return SearchPlan.of(coarse, REGION_STRIDE_CHUNKS, null);
    }

    public static SearchPlan objectPlan(Engine engine, String objectKey) {
        if (engine.getFocus() != null || engine.getFocusRegion() != null) {
            return SearchPlan.unpruned();
        }

        IrisComplex complex = engine.getComplex();
        BiomeSource source = new BiomeSource(engine);
        KSet<String> landHosts = new KSet<>();
        KSet<String> seaHosts = new KSet<>();
        KSet<String> shoreHosts = new KSet<>();
        KSet<String> directRegions = new KSet<>();
        KSet<String> hostedRegions = new KSet<>();

        for (IrisRegion region : engine.getDimension().getAllRegions(engine)) {
            if (region == null) {
                continue;
            }

            if (placesObject(region.getSurfaceObjects(), objectKey, engine.getData())) {
                directRegions.add(region.getLoadKey());
            }

            boolean hosted = false;

            for (String root : region.getLandBiomes()) {
                if (source.closurePlacesObject(root, objectKey)) {
                    landHosts.add(root);
                    hosted = true;
                }
            }

            for (String root : region.getSeaBiomes()) {
                if (source.closurePlacesObject(root, objectKey)) {
                    seaHosts.add(root);
                    hosted = true;
                }
            }

            for (String root : region.getShoreBiomes()) {
                if (source.closurePlacesObject(root, objectKey)) {
                    shoreHosts.add(root);
                    hosted = true;
                }
            }

            if (hosted) {
                hostedRegions.add(region.getLoadKey());
            }
        }

        if (directRegions.isEmpty() && hostedRegions.isEmpty()) {
            return SearchPlan.impossible();
        }

        CoarseSample surface = hostedRegions.isEmpty() ? null : surfaceCoarse(complex, hostedRegions, landHosts, seaHosts, shoreHosts);
        CoarseSample coarse = (x, z) -> {
            if (!directRegions.isEmpty()) {
                IrisRegion region = complex.getRegionStream().get(x, z);

                if (region != null && directRegions.contains(region.getLoadKey())) {
                    return true;
                }
            }

            return surface != null && surface.test(x, z);
        };

        return SearchPlan.of(coarse, 1, null);
    }

    private static CoarseSample surfaceCoarse(IrisComplex complex, KSet<String> regions, KSet<String> landHosts, KSet<String> seaHosts, KSet<String> shoreHosts) {
        return (x, z) -> {
            IrisRegion region = complex.getRegionStream().get(x, z);

            if (region == null || !regions.contains(region.getLoadKey())) {
                return false;
            }

            if (!landHosts.isEmpty()) {
                IrisBiome biome = complex.getLandBiomeStream().get(x, z);

                if (biome != null && landHosts.contains(biome.getLoadKey())) {
                    return true;
                }
            }

            if (!seaHosts.isEmpty()) {
                IrisBiome biome = complex.getSeaBiomeStream().get(x, z);

                if (biome != null && seaHosts.contains(biome.getLoadKey())) {
                    return true;
                }
            }

            if (!shoreHosts.isEmpty()) {
                IrisBiome biome = complex.getShoreBiomeStream().get(x, z);

                if (biome != null && shoreHosts.contains(biome.getLoadKey())) {
                    return true;
                }
            }

            return false;
        };
    }

    private static CoarseSample caveCoarse(IrisComplex complex, KSet<String> caveRegions, String biomeKey) {
        return (x, z) -> {
            IrisRegion region = complex.getRegionStream().get(x, z);

            if (region == null || !caveRegions.contains(region.getLoadKey())) {
                return false;
            }

            IrisBiome cave = complex.getCaveBiomeStream().get(x, z);
            return cave != null && biomeKey.equals(cave.getLoadKey());
        };
    }

    private static boolean placesObject(KList<IrisObjectPlacement> placements, String objectKey, IrisData data) {
        for (IrisObjectPlacement placement : placements) {
            if (placement != null && placement.compatPlace(data).contains(objectKey)) {
                return true;
            }
        }

        return false;
    }

    private static final class BiomeSource {
        private final Engine engine;
        private final KMap<String, IrisBiome> cache = new KMap<>();

        private BiomeSource(Engine engine) {
            this.engine = engine;
        }

        private IrisBiome load(String key) {
            if (key == null) {
                return null;
            }

            IrisBiome cached = cache.get(key);

            if (cached != null) {
                return cached;
            }

            IrisBiome loaded = engine.getData().getBiomeLoader().load(key);

            if (loaded != null) {
                cache.put(key, loaded);
            }

            return loaded;
        }

        private boolean closureContains(String rootKey, String targetKey) {
            if (rootKey == null || targetKey == null) {
                return false;
            }

            KSet<String> visited = new KSet<>();
            ArrayDeque<String> queue = new ArrayDeque<>();
            queue.add(rootKey);

            while (!queue.isEmpty()) {
                String key = queue.poll();

                if (!visited.add(key)) {
                    continue;
                }

                if (key.equals(targetKey)) {
                    return true;
                }

                IrisBiome biome = load(key);

                if (biome == null) {
                    continue;
                }

                for (String child : biome.getChildren()) {
                    if (child != null && !visited.contains(child)) {
                        queue.add(child);
                    }
                }
            }

            return false;
        }

        private KList<IrisBiome> closure(String rootKey) {
            KList<IrisBiome> biomes = new KList<>();
            if (rootKey == null) {
                return biomes;
            }

            KSet<String> visited = new KSet<>();
            ArrayDeque<String> queue = new ArrayDeque<>();
            queue.add(rootKey);

            while (!queue.isEmpty()) {
                String key = queue.poll();
                if (!visited.add(key)) {
                    continue;
                }

                IrisBiome biome = load(key);
                if (biome == null) {
                    continue;
                }
                biomes.add(biome);
                for (String child : biome.getChildren()) {
                    if (child != null && !visited.contains(child)) {
                        queue.add(child);
                    }
                }
            }
            return biomes;
        }

        private boolean closurePlacesObject(String rootKey, String objectKey) {
            if (rootKey == null || objectKey == null) {
                return false;
            }

            KSet<String> visited = new KSet<>();
            ArrayDeque<String> queue = new ArrayDeque<>();
            queue.add(rootKey);

            while (!queue.isEmpty()) {
                String key = queue.poll();

                if (!visited.add(key)) {
                    continue;
                }

                IrisBiome biome = load(key);

                if (biome == null) {
                    continue;
                }

                if (placesObject(biome.getSurfaceObjects(), objectKey, engine.getData())) {
                    return true;
                }

                for (String child : biome.getChildren()) {
                    if (child != null && !visited.contains(child)) {
                        queue.add(child);
                    }
                }
            }

            return false;
        }
    }
}
