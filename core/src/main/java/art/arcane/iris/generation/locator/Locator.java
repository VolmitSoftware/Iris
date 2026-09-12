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

import art.arcane.iris.world.history.ChunkGenerationSemantics;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.GenerationSessionException;
import art.arcane.iris.generation.runtime.GenerationSessionLease;
import art.arcane.iris.generation.runtime.WrongEngineBroException;
import art.arcane.iris.structure.placement.IrisStructureLocator;

import art.arcane.iris.generation.hydrology.runtime.IrisHydrologyRuntime;
import art.arcane.iris.world.history.GenerationSemanticIndex;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.generation.context.IrisContext;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.volmlib.util.math.Position2;
import art.arcane.volmlib.util.matter.MatterCavern;

import java.math.BigInteger;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@FunctionalInterface
public interface Locator<T> {
    record SearchCandidate(Position2 chunk, int blockX, int blockZ) {
        public SearchCandidate {
            if (chunk == null) {
                throw new IllegalArgumentException("Locator candidate chunk cannot be null.");
            }
        }

        public static SearchCandidate atChunkCenter(Position2 chunk) {
            long blockX = (long) chunk.getX() * 16L + 8L;
            long blockZ = (long) chunk.getZ() * 16L + 8L;
            return new SearchCandidate(
                    chunk,
                    (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, blockX)),
                    (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, blockZ))
            );
        }
    }

    static Locator<IrisRegion> region(String loadKey) {
        Locator<IrisRegion> exact = new HistoryAwareLocator<>(
                GenerationSemanticIndex.SemanticKind.REGION,
                loadKey,
                (e, c) -> e.getRegion((c.getX() << 4) + 8, (c.getZ() << 4) + 8).getLoadKey().equals(loadKey)
        );
        return new HintedLocator<>(exact, (engine) -> HintedLocator.regionPlan(engine, loadKey));
    }

    static Locator<IrisObject> object(String loadKey) {
        Locator<IrisObject> exact = new HistoryAwareLocator<>(
                GenerationSemanticIndex.SemanticKind.OBJECT,
                loadKey,
                (e, c) -> e.getObjectsAt(c.getX(), c.getZ()).contains(loadKey)
        );
        return new HintedLocator<>(exact, (engine) -> HintedLocator.objectPlan(engine, loadKey));
    }

    static Locator<IrisBiome> surfaceBiome(String loadKey) {
        Locator<IrisBiome> exact = new HistoryAwareLocator<>(
                GenerationSemanticIndex.SemanticKind.SURFACE_BIOME,
                loadKey,
                (e, c) -> chunkContainsSurfaceBiome(e, c, loadKey)
        );
        return new HintedLocator<>(exact, (engine) -> HintedLocator.biomePlan(engine, loadKey));
    }

    static boolean chunkContainsSurfaceBiome(Engine engine, Position2 chunk, String loadKey) {
        int minimumX = chunk.getX() << 4;
        int minimumZ = chunk.getZ() << 4;
        IrisBiome center = engine.getSurfaceBiome(minimumX + 8, minimumZ + 8);
        if (center != null && loadKey.equals(center.getLoadKey())) {
            return true;
        }
        IrisHydrologyRuntime hydrology = engine.getComplex().getHydrologyRuntime();
        return hydrology != null
                && hydrology.hasAcceptedSurfaceBiomeInChunk(loadKey, chunk.getX(), chunk.getZ());
    }

    static Locator<art.arcane.iris.structure.placement.IrisStructure> structure(String key) {
        return new HistoryAwareLocator<>(
                GenerationSemanticIndex.SemanticKind.STRUCTURE,
                key,
                (e, c) -> IrisStructureLocator.startsInChunk(e, key, c.getX(), c.getZ())
        );
    }

    static Locator<ChunkGenerationSemantics.BlockPosition> poi(String type) {
        return (e, c) -> {
            Set<ChunkGenerationSemantics.PointOfInterest> pos = e.getPOIsAt(c.getX(), c.getZ());
            return pos.stream().anyMatch(p -> p.key().equals(type));
        };
    }

    static Locator<IrisBiome> caveBiome(String loadKey) {
        Locator<IrisBiome> exact = new HistoryAwareLocator<>(
                GenerationSemanticIndex.SemanticKind.CAVE_BIOME,
                loadKey,
                (e, c) -> e.getCaveBiome((c.getX() << 4) + 8, (c.getZ() << 4) + 8).getLoadKey().equals(loadKey)
        );
        return new HintedLocator<>(exact, (engine) -> HintedLocator.caveBiomePlan(engine, loadKey));
    }

    static Locator<IrisBiome> caveOrMantleBiome(String loadKey) {
        Locator<IrisBiome> active = (e, c) -> {
            AtomicBoolean found = new AtomicBoolean(false);
            try (GenerationSessionLease lease = e.acquireGenerationLease("locator_generate_matter")) {
                ChunkContext chunkContext = new ChunkContext(c.getX() << 4, c.getZ() << 4, e.getComplex(), lease.sessionId(), false, ChunkContext.PrefillPlan.NONE, null);
                try (IrisContext.Scope locatorScope = IrisContext.open(e, lease.sessionId(), chunkContext)) {
                    e.generateMatter(c.getX(), c.getZ(), true, chunkContext);
                }
            } catch (GenerationSessionException sessionException) {
                throw new IllegalStateException(sessionException);
            }
            e.getMantle().getMantle().iterateChunk(c.getX(), c.getZ(), MatterCavern.class, (x, y, z, t) -> {
                if (found.get()) {
                    return;
                }

                if (t != null && t.getCustomBiome().equals(loadKey)) {
                    found.set(true);
                }
            });

            return found.get();
        };
        return new HistoryAwareLocator<>(
                GenerationSemanticIndex.SemanticKind.CAVE_BIOME,
                loadKey,
                active
        );
    }

    boolean matches(Engine engine, Position2 chunk);

    default boolean matchesForSearch(Engine engine, Position2 chunk) {
        if (!GenerationLocatorPolicy.allowsActivePrediction(engine, chunk)) {
            return false;
        }
        return GenerationLocatorPolicy.evaluateScoped(
                engine,
                chunk,
                () -> matches(engine, chunk)
        );
    }

    default Position2 nearestRecorded(Engine engine, Position2 origin, int maxChunkRadius) {
        SearchCandidate candidate = nearestRecordedCandidate(engine, origin, maxChunkRadius);
        return candidate == null ? null : candidate.chunk();
    }

    default SearchCandidate nearestRecordedCandidate(Engine engine, Position2 origin, int maxChunkRadius) {
        return null;
    }

    default SearchCandidate candidateForMatchedChunk(Engine engine, Position2 chunk) {
        return SearchCandidate.atChunkCenter(chunk);
    }

    default CompletableFuture<Position2> find(Engine engine, Position2 pos, long timeout, Consumer<Integer> checks) throws WrongEngineBroException {
        return new HintedLocator<>(this, ignored -> HintedLocator.SearchPlan.unpruned())
                .find(engine, pos, timeout, checks);
    }

    static int activeSearchRadius(
            Position2 origin,
            SearchCandidate recorded,
            int maximumRadius
    ) {
        if (recorded == null) {
            return maximumRadius;
        }
        BigInteger distance = distanceSquared(origin, recorded).sqrt();
        BigInteger chunks = distance.add(BigInteger.valueOf(15L)).divide(BigInteger.valueOf(16L));
        return Math.min(maximumRadius, chunks.min(BigInteger.valueOf(Integer.MAX_VALUE - 2L)).intValue() + 2);
    }

    static void updateNearest(
            AtomicReference<SearchCandidate> nearest,
            SearchCandidate candidate,
            Position2 origin
    ) {
        SearchCandidate current = nearest.get();
        while (current == null || compareCandidates(origin, candidate, current) < 0) {
            if (nearest.compareAndSet(current, candidate)) {
                return;
            }
            current = nearest.get();
        }
    }

    static SearchCandidate nearer(
            Position2 origin,
            SearchCandidate first,
            SearchCandidate second
    ) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return compareCandidates(origin, first, second) <= 0 ? first : second;
    }

    private static int compareCandidates(
            Position2 origin,
            SearchCandidate first,
            SearchCandidate second
    ) {
        int distanceComparison = distanceSquared(origin, first).compareTo(distanceSquared(origin, second));
        if (distanceComparison != 0) {
            return distanceComparison;
        }
        int xComparison = Integer.compare(first.blockX(), second.blockX());
        if (xComparison != 0) {
            return xComparison;
        }
        int zComparison = Integer.compare(first.blockZ(), second.blockZ());
        if (zComparison != 0) {
            return zComparison;
        }
        int chunkXComparison = Integer.compare(first.chunk().getX(), second.chunk().getX());
        if (chunkXComparison != 0) {
            return chunkXComparison;
        }
        return Integer.compare(first.chunk().getZ(), second.chunk().getZ());
    }

    private static BigInteger distanceSquared(Position2 origin, SearchCandidate candidate) {
        BigInteger originX = BigInteger.valueOf((long) origin.getX() * 16L + 8L);
        BigInteger originZ = BigInteger.valueOf((long) origin.getZ() * 16L + 8L);
        BigInteger deltaX = originX.subtract(BigInteger.valueOf(candidate.blockX()));
        BigInteger deltaZ = originZ.subtract(BigInteger.valueOf(candidate.blockZ()));
        return deltaX.multiply(deltaX).add(deltaZ.multiply(deltaZ));
    }
}
