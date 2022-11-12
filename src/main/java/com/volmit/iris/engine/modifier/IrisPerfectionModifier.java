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

package com.volmit.iris.engine.modifier;

import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.EngineAssignedModifier;
import com.volmit.iris.util.context.ChunkContext;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class IrisPerfectionModifier extends EngineAssignedModifier<BlockData> {
    private static final BlockData AIR = B.get("AIR");
    private static final BlockData WATER = B.get("WATER");

    public IrisPerfectionModifier(Engine engine) {
        super(engine, "Perfection");
    }

    @Override
    public void onModify(int x, int z, Hunk<BlockData> output, boolean multicore, ChunkContext context) {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        AtomicBoolean changed = new AtomicBoolean(true);
        int passes = 0;
        AtomicInteger changes = new AtomicInteger();
        List<Integer> surfaces = new ArrayList<>();
        List<Integer> ceilings = new ArrayList<>();
        BurstExecutor burst = burst().burst(multicore);
        while (changed.get()) {
            passes++;
            changed.set(false);
            for (int i = 0; i < 16; i++) {
                int finalI = i;
                burst.queue(() -> {
                    for (int j = 0; j < 16; j++) {
                        surfaces.clear();
                        ceilings.clear();
                        int top = getHeight(output, finalI, j);
                        boolean inside = true;
                        surfaces.add(top);

                        for (int k = top; k >= 0; k--) {
                            BlockData b = output.get(finalI, k, j);
                            boolean now = b != null && !(B.isAir(b) || B.isFluid(b));

                            if (now != inside) {
                                inside = now;

                                if (inside) {
                                    surfaces.add(k);
                                } else {
                                    ceilings.add(k + 1);
                                }
                            }
                        }

                        for (int k : surfaces) {
                            BlockData tip = output.get(finalI, k, j);

                            if (tip == null) {
                                continue;
                            }

                            boolean remove = false;
                            boolean remove2 = false;

                            if (B.isDecorant(tip)) {
                                BlockData bel = output.get(finalI, k - 1, j);

                                if (bel == null) {
                                    remove = true;
                                } else if (!B.canPlaceOnto(tip.getMaterial(), bel.getMaterial())) {
                                    remove = true;
                                } else if (bel instanceof Bisected) {
                                    BlockData bb = output.get(finalI, k - 2, j);
                                    if (bb == null || !B.canPlaceOnto(bel.getMaterial(), bb.getMaterial())) {
                                        remove = true;
                                        remove2 = true;
                                    }
                                }

                                if (remove) {
                                    changed.set(true);
                                    changes.getAndIncrement();
                                    output.set(finalI, k, j, AIR);

                                    if (remove2) {
                                        changes.getAndIncrement();
                                        output.set(finalI, k - 1, j, AIR);
                                    }
                                }
                            }
                        }
                    }
                });
            }
        }

        getEngine().getMetrics().getPerfection().put(p.getMilliseconds());
    }

    private int getHeight(Hunk<BlockData> output, int x, int z) {
        for (int i = output.getHeight() - 1; i >= 0; i--) {
            BlockData b = output.get(x, i, z);

            if (b != null && !B.isAir(b) && !B.isFluid(b)) {
                return i;
            }
        }

        return 0;
    }
}
