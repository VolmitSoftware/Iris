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
import art.arcane.iris.core.nms.container.BlockPos;
import art.arcane.iris.core.nms.container.Pair;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.util.project.context.IrisContext;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.volmlib.util.math.Position2;
import art.arcane.volmlib.util.math.Spiraler;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.iris.util.common.parallel.BurstExecutor;
import art.arcane.iris.util.common.parallel.MultiBurst;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@FunctionalInterface
public interface Locator<T> {
    static Locator<IrisRegion> region(String loadKey) {
        Locator<IrisRegion> exact = (e, c) -> e.getRegion((c.getX() << 4) + 8, (c.getZ() << 4) + 8).getLoadKey().equals(loadKey);
        return new HintedLocator<>(exact, (engine) -> HintedLocator.regionPlan(engine, loadKey));
    }

    static Locator<IrisObject> object(String loadKey) {
        Locator<IrisObject> exact = (e, c) -> e.getObjectsAt(c.getX(), c.getZ()).contains(loadKey);
        return new HintedLocator<>(exact, (engine) -> HintedLocator.objectPlan(engine, loadKey));
    }

    static Locator<IrisBiome> surfaceBiome(String loadKey) {
        Locator<IrisBiome> exact = (e, c) -> e.getSurfaceBiome((c.getX() << 4) + 8, (c.getZ() << 4) + 8).getLoadKey().equals(loadKey);
        return new HintedLocator<>(exact, (engine) -> HintedLocator.biomePlan(engine, loadKey));
    }

    static Locator<BlockPos> poi(String type) {
        return (e, c) -> {
            Set<Pair<String, BlockPos>> pos = e.getPOIsAt(c.getX(), c.getZ());
            return pos.stream().anyMatch(p -> p.getA().equals(type));
        };
    }

    static Locator<IrisBiome> caveBiome(String loadKey) {
        Locator<IrisBiome> exact = (e, c) -> e.getCaveBiome((c.getX() << 4) + 8, (c.getZ() << 4) + 8).getLoadKey().equals(loadKey);
        return new HintedLocator<>(exact, (engine) -> HintedLocator.caveBiomePlan(engine, loadKey));
    }

    static Locator<IrisBiome> caveOrMantleBiome(String loadKey) {
        return (e, c) -> {
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
    }

    boolean matches(Engine engine, Position2 chunk);

    default CompletableFuture<Position2> find(Engine engine, Position2 pos, long timeout, Consumer<Integer> checks) throws WrongEngineBroException {
        if (engine.isClosed()) {
            throw new WrongEngineBroException();
        }

        AtomicBoolean stop = new AtomicBoolean(false);
        CompletableFuture<Position2> search = MultiBurst.burst.completeValueAsync(() -> {
            try (GenerationSessionLease lease = engine.acquireGenerationLease("locator_search");
                 IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
                int tc = IrisSettings.getThreadCount(IrisSettings.get().getConcurrency().getParallelism()) * 32;
                MultiBurst burst = MultiBurst.burst;
                AtomicBoolean found = new AtomicBoolean(false);
                AtomicInteger searched = new AtomicInteger();
                AtomicReference<Position2> foundPos = new AtomicReference<>();
                PrecisionStopwatch px = PrecisionStopwatch.start();
                AtomicReference<Position2> next = new AtomicReference<>(pos);
                Spiraler s = new Spiraler(100000, 100000, (x, z) -> next.set(new Position2(x, z)));
                s.setOffset(pos.getX(), pos.getZ());
                s.next();
                while (!found.get() && !stop.get() && !engine.isClosing() && px.getMilliseconds() < timeout) {
                    BurstExecutor e = burst.burst(tc);

                    for (int i = 0; i < tc; i++) {
                        Position2 p = next.get();
                        s.next();
                        e.queue(() -> {
                            if (found.get() || stop.get() || engine.isClosing()) {
                                return;
                            }
                            if (matches(engine, p)) {
                                foundPos.compareAndSet(null, p);
                                found.set(true);
                            }
                            searched.incrementAndGet();
                        });
                    }

                    e.complete();
                    checks.accept(searched.get());
                }

                if (found.get() && foundPos.get() != null) {
                    return foundPos.get();
                }

                return null;
            }
        });
        return LocatorCanceller.requestScoped(search, stop);
    }
}
