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

package art.arcane.iris.engine.framework;

import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.modules.ModuleServices;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.util.common.parallel.BurstExecutor;
import art.arcane.iris.util.common.parallel.MultiBurst;
import art.arcane.iris.util.project.context.IrisContext;
import com.volmit.service.ObjectsAt;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.collection.KSet;
import art.arcane.volmlib.util.math.Position2;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;

import java.util.ArrayDeque;
import java.util.Set;
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
    public CompletableFuture<Position2> find(Engine engine, Position2 pos, long timeout, Consumer<Integer> checks) throws WrongEngineBroException {
        if (engine.isClosed()) {
            throw new WrongEngineBroException();
        }

        AtomicBoolean stop = new AtomicBoolean(false);
        CompletableFuture<Position2> search = MultiBurst.burst.completeValueAsync(() -> {
            try (GenerationSessionLease lease = engine.acquireGenerationLease("hinted_locator_search");
                 IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
                SearchPlan plan = planner.apply(engine);

                if (!plan.isPossible()) {
                    return null;
                }

                return search(engine, plan, pos, timeout, checks, stop);
            }
        });
        return LocatorCanceller.requestScoped(search, stop);
    }

    private Position2 search(Engine engine, SearchPlan plan, Position2 pos, long timeout, Consumer<Integer> checks, AtomicBoolean stop) {
        Locator<?> verifier = plan.getExactOverride() != null ? plan.getExactOverride() : exact;
        int stride = Math.max(1, plan.getStrideChunks());
        int batchTarget = IrisSettings.getThreadCount(IrisSettings.get().getConcurrency().getParallelism()) * 32;
        int maxRing = Math.max(1, MAX_SAMPLE_RADIUS_CHUNKS / stride);
        PrecisionStopwatch stopwatch = PrecisionStopwatch.start();
        AtomicInteger covered = new AtomicInteger();
        KList<Position2> batch = new KList<>();

        for (int ring = 0; ring <= maxRing; ring++) {
            if (stop.get() || engine.isClosing() || stopwatch.getMilliseconds() >= timeout) {
                return null;
            }

            appendRing(batch, pos, ring, stride);

            if (batch.size() < batchTarget && ring < maxRing) {
                continue;
            }

            Position2 result = processBatch(engine, plan, verifier, batch, stride, timeout, stopwatch, covered, checks, stop);
            batch.clear();

            if (result != null) {
                return result;
            }
        }

        return null;
    }

    private Position2 processBatch(Engine engine, SearchPlan plan, Locator<?> verifier, KList<Position2> batch, int stride, long timeout, PrecisionStopwatch stopwatch, AtomicInteger covered, Consumer<Integer> checks, AtomicBoolean stop) {
        int size = batch.size();
        boolean[] hits = new boolean[size];
        boolean fine = stride <= 1;
        CoarseSample coarse = plan.getCoarse();
        AtomicBoolean matched = new AtomicBoolean(false);
        BurstExecutor executor = MultiBurst.burst.burst(size);

        for (int i = 0; i < size; i++) {
            int index = i;
            Position2 sample = batch.get(i);
            executor.queue(() -> {
                if (stop.get() || engine.isClosing() || (fine && matched.get())) {
                    return;
                }

                int blockX = (sample.getX() << 4) + 8;
                int blockZ = (sample.getZ() << 4) + 8;

                if (coarse != null && !coarse.test(blockX, blockZ)) {
                    return;
                }

                if (fine) {
                    if (verifier.matches(engine, sample)) {
                        hits[index] = true;
                        matched.set(true);
                    }

                    return;
                }

                hits[index] = true;
            });
        }

        executor.complete();
        covered.addAndGet(size * stride * stride);
        checks.accept(covered.get());

        for (int i = 0; i < size; i++) {
            if (!hits[i]) {
                continue;
            }

            if (fine) {
                return batch.get(i);
            }

            if (stop.get() || engine.isClosing() || stopwatch.getMilliseconds() >= timeout) {
                return null;
            }

            Position2 refined = refine(engine, verifier, batch.get(i), stride, timeout, stopwatch, stop);

            if (refined != null) {
                return refined;
            }
        }

        return null;
    }

    private Position2 refine(Engine engine, Locator<?> verifier, Position2 candidate, int stride, long timeout, PrecisionStopwatch stopwatch, AtomicBoolean stop) {
        KList<Position2> cells = new KList<>();

        for (int ring = 0; ring <= stride; ring++) {
            if (stop.get() || engine.isClosing() || stopwatch.getMilliseconds() >= timeout) {
                return null;
            }

            cells.clear();
            appendRing(cells, candidate, ring, 1);
            AtomicReference<Position2> found = new AtomicReference<>();
            BurstExecutor executor = MultiBurst.burst.burst(cells.size());

            for (Position2 cell : cells) {
                executor.queue(() -> {
                    if (stop.get() || engine.isClosing() || found.get() != null) {
                        return;
                    }

                    if (verifier.matches(engine, cell)) {
                        found.compareAndSet(null, cell);
                    }
                });
            }

            executor.complete();

            if (found.get() != null) {
                return found.get();
            }
        }

        return null;
    }

    static void appendRing(KList<Position2> batch, Position2 origin, int ring, int stride) {
        if (ring == 0) {
            batch.add(origin);
            return;
        }

        int offset = ring * stride;

        for (int dx = -ring; dx <= ring; dx++) {
            int x = origin.getX() + (dx * stride);
            batch.add(new Position2(x, origin.getZ() - offset));
            batch.add(new Position2(x, origin.getZ() + offset));
        }

        for (int dz = -ring + 1; dz <= ring - 1; dz++) {
            int z = origin.getZ() + (dz * stride);
            batch.add(new Position2(origin.getX() - offset, z));
            batch.add(new Position2(origin.getX() + offset, z));
        }
    }

    public static SearchPlan biomePlan(Engine engine, String biomeKey) {
        if (engine.getFocus() != null || engine.getFocusRegion() != null) {
            return SearchPlan.unpruned();
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
            return SearchPlan.of(surfaceCoarse(complex, surfaceRegions, landHosts, seaHosts, shoreHosts), BIOME_STRIDE_CHUNKS, null);
        }

        if (!caveRegions.isEmpty()) {
            CoarseSample coarse = caveCoarse(complex, caveRegions, biomeKey);
            Locator<IrisBiome> caveExact = (e, c) -> {
                IrisBiome biome = e.getCaveBiome((c.getX() << 4) + 8, (c.getZ() << 4) + 8);
                return biome != null && biomeKey.equals(biome.getLoadKey());
            };

            return SearchPlan.of(coarse, BIOME_STRIDE_CHUNKS, caveExact);
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
        ModuleServices.ObjectPlacers placers = ModuleServices.objectPlacers(engine, objectKey);
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

            if (placers.regions().contains(region.getLoadKey())) {
                directRegions.add(region.getLoadKey());
            }

            boolean hosted = false;

            for (String root : region.getLandBiomes()) {
                if (source.closureContainsAny(root, placers.biomes())) {
                    landHosts.add(root);
                    hosted = true;
                }
            }

            for (String root : region.getSeaBiomes()) {
                if (source.closureContainsAny(root, placers.biomes())) {
                    seaHosts.add(root);
                    hosted = true;
                }
            }

            for (String root : region.getShoreBiomes()) {
                if (source.closureContainsAny(root, placers.biomes())) {
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

        private boolean closureContainsAny(String rootKey, Set<String> targetKeys) {
            if (rootKey == null || targetKeys.isEmpty()) {
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

                if (targetKeys.contains(biome.getLoadKey())) {
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
