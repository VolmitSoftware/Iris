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

package com.volmit.iris.engine.framework;

import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisJigsawStructure;
import com.volmit.iris.engine.object.IrisObject;
import com.volmit.iris.engine.object.IrisRegion;
import com.volmit.iris.util.context.ChunkContext;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.math.Spiraler;
import com.volmit.iris.util.matter.MatterCavern;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.parallel.MultiBurst;
import com.volmit.iris.util.plugin.VolmitSender;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import com.volmit.iris.util.scheduling.jobs.SingleJob;
import net.minecraft.core.BlockPos;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import oshi.util.tuples.Pair;

import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@FunctionalInterface
public interface Locator<T> {
    static void cancelSearch() {
        if (LocatorCanceller.cancel != null) {
            LocatorCanceller.cancel.run();
            LocatorCanceller.cancel = null;
        }
    }

    static Locator<IrisRegion> region(String loadKey) {
        return (e, c) -> e.getRegion((c.getX() << 4) + 8, (c.getZ() << 4) + 8).getLoadKey().equals(loadKey);
    }

    static Locator<IrisJigsawStructure> jigsawStructure(String loadKey) {
        return (e, c) -> {
            IrisJigsawStructure s = e.getStructureAt(c.getX(), c.getZ());
            return s != null && s.getLoadKey().equals(loadKey);
        };
    }

    static Locator<IrisObject> object(String loadKey) {
        return (e, c) -> e.getObjectsAt(c.getX(), c.getZ()).contains(loadKey);
    }

    static Locator<IrisBiome> surfaceBiome(String loadKey) {
        return (e, c) -> e.getSurfaceBiome((c.getX() << 4) + 8, (c.getZ() << 4) + 8).getLoadKey().equals(loadKey);
    }

    static Locator<BlockPos> poi(String type) {
        return (e, c) -> {
            Set<Pair<String, BlockPos>> pos = e.getPOIsAt((c.getX() << 4) + 8, (c.getZ() << 4) + 8);
            return pos.stream().anyMatch(p -> p.getA().equals(type));
        };
    }

    static Locator<IrisBiome> caveBiome(String loadKey) {
        return (e, c) -> e.getCaveBiome((c.getX() << 4) + 8, (c.getZ() << 4) + 8).getLoadKey().equals(loadKey);
    }

    static Locator<IrisBiome> caveOrMantleBiome(String loadKey) {
        return (e, c) -> {
            AtomicBoolean found = new AtomicBoolean(false);
            e.generateMatter(c.getX(), c.getZ(), true, new ChunkContext(c.getX() << 4, c.getZ() << 4, e.getComplex(), false));
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

    default void find(Player player) {
        find(player, 30_000);
    }

    default void find(Player player, long timeout) {
        AtomicLong checks = new AtomicLong();
        long ms = M.ms();
        new SingleJob("Searching", () -> {
            try {
                Position2 at = find(IrisToolbelt.access(player.getWorld()).getEngine(), new Position2(player.getLocation().getBlockX() >> 4, player.getLocation().getBlockZ() >> 4), timeout, checks::set).get();

                if (at != null) {
                    J.s(() -> player.teleport(new Location(player.getWorld(), (at.getX() << 4) + 8,
                            IrisToolbelt.access(player.getWorld()).getEngine().getHeight(
                                    (at.getX() << 4) + 8,
                                    (at.getZ() << 4) + 8, false),
                            (at.getZ() << 4) + 8)));
                }
            } catch (WrongEngineBroException | InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }) {
            @Override
            public String getName() {
                return "Searched " + Form.f(checks.get()) + " Chunks";
            }

            @Override
            public int getTotalWork() {
                return (int) timeout;
            }

            @Override
            public int getWorkCompleted() {
                return (int) Math.min(M.ms() - ms, timeout - 1);
            }
        }.execute(new VolmitSender(player));
    }

    default Future<Position2> find(Engine engine, Position2 pos, long timeout, Consumer<Integer> checks) throws WrongEngineBroException {
        if (engine.isClosed()) {
            throw new WrongEngineBroException();
        }

        cancelSearch();

        return MultiBurst.burst.completeValue(() -> {
            int tc = IrisSettings.getThreadCount(IrisSettings.get().getConcurrency().getParallelism()) * 17;
            MultiBurst burst = MultiBurst.burst;
            AtomicBoolean found = new AtomicBoolean(false);
            Position2 cursor = pos;
            AtomicInteger searched = new AtomicInteger();
            AtomicBoolean stop = new AtomicBoolean(false);
            AtomicReference<Position2> foundPos = new AtomicReference<>();
            PrecisionStopwatch px = PrecisionStopwatch.start();
            LocatorCanceller.cancel = () -> stop.set(true);
            AtomicReference<Position2> next = new AtomicReference<>(cursor);
            Spiraler s = new Spiraler(100000, 100000, (x, z) -> next.set(new Position2(x, z)));
            s.setOffset(cursor.getX(), cursor.getZ());
            s.next();
            while (!found.get() && !stop.get() && px.getMilliseconds() < timeout) {
                BurstExecutor e = burst.burst(tc);

                for (int i = 0; i < tc; i++) {
                    Position2 p = next.get();
                    s.next();
                    e.queue(() -> {
                        if (matches(engine, p)) {
                            if (foundPos.get() == null) {
                                foundPos.set(p);
                            }

                            found.set(true);
                        }
                        searched.incrementAndGet();
                    });
                }

                e.complete();
                checks.accept(searched.get());
            }

            LocatorCanceller.cancel = null;

            if (found.get() && foundPos.get() != null) {
                return foundPos.get();
            }

            return null;
        });
    }
}
