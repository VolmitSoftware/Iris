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

package art.arcane.iris.generation.stage;

import art.arcane.iris.generation.decoration.IrisSpeleothems;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EngineAssignedModifier;
import art.arcane.iris.generation.decoration.IrisProceduralBlocks;
import art.arcane.iris.generation.block.BoundBlockState;
import art.arcane.iris.generation.context.ChunkContext;
import art.arcane.iris.generation.block.B;
import art.arcane.volmlib.util.hunk.Hunk;
import art.arcane.iris.generation.concurrent.BurstExecutor;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;
import art.arcane.iris.spi.PlatformBlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class IrisPerfectionModifier extends EngineAssignedModifier<PlatformBlockState> {
    private static final BoundBlockState AIR = BoundBlockState.of("AIR");
    private static final BoundBlockState WATER = BoundBlockState.of("WATER");
    private static final Map<String, BoundBlockState> ORE_BASES = buildOreBases();

    public IrisPerfectionModifier(Engine engine) {
        super(engine, "Perfection");
    }

    private static Map<String, BoundBlockState> buildOreBases() {
        Map<String, BoundBlockState> map = new HashMap<>();
        BoundBlockState stone = BoundBlockState.of("STONE");
        BoundBlockState deepslate = BoundBlockState.of("DEEPSLATE");
        BoundBlockState netherrack = BoundBlockState.of("NETHERRACK");
        BoundBlockState blackstone = BoundBlockState.of("BLACKSTONE");
        map.put("minecraft:coal_ore", stone);
        map.put("minecraft:copper_ore", stone);
        map.put("minecraft:iron_ore", stone);
        map.put("minecraft:gold_ore", stone);
        map.put("minecraft:redstone_ore", stone);
        map.put("minecraft:lapis_ore", stone);
        map.put("minecraft:diamond_ore", stone);
        map.put("minecraft:emerald_ore", stone);
        map.put("minecraft:deepslate_coal_ore", deepslate);
        map.put("minecraft:deepslate_copper_ore", deepslate);
        map.put("minecraft:deepslate_iron_ore", deepslate);
        map.put("minecraft:deepslate_gold_ore", deepslate);
        map.put("minecraft:deepslate_redstone_ore", deepslate);
        map.put("minecraft:deepslate_lapis_ore", deepslate);
        map.put("minecraft:deepslate_diamond_ore", deepslate);
        map.put("minecraft:deepslate_emerald_ore", deepslate);
        map.put("minecraft:nether_gold_ore", netherrack);
        map.put("minecraft:nether_quartz_ore", netherrack);
        map.put("minecraft:ancient_debris", netherrack);
        map.put("minecraft:gilded_blackstone", blackstone);
        return map;
    }

    private static String baseKey(PlatformBlockState state) {
        String key = state.key();
        int bracket = key.indexOf('[');
        return bracket < 0 ? key : key.substring(0, bracket);
    }

    @Override
    public void onModify(int x, int z, Hunk<PlatformBlockState> output, boolean multicore, ChunkContext context) {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        if (getDimension().isHideOresForHiddenOre()) {
            hideOres(output, multicore);
        }
        AtomicBoolean changed = new AtomicBoolean(true);
        BurstExecutor burst = burst().burst(multicore);
        while (changed.get()) {
            changed.set(false);
            for (int i = 0; i < 16; i++) {
                int finalI = i;
                burst.queue(() -> {
                    List<Integer> surfaces = new ArrayList<>();
                    List<Integer> ceilings = new ArrayList<>();
                    for (int j = 0; j < 16; j++) {
                        surfaces.clear();
                        ceilings.clear();
                        int top = getHeight(output, finalI, j);
                        boolean inside = true;
                        surfaces.add(top);

                        for (int k = top; k >= 0; k--) {
                            PlatformBlockState b = output.get(finalI, k, j);
                            if (IrisSpeleothems.isSpike(b)) {
                                b = normalizeSpike(b, output, finalI, j, k, AIR.get(), WATER.get());
                            }
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
                            PlatformBlockState tip = output.get(finalI, k, j);

                            if (tip == null) {
                                continue;
                            }

                            boolean remove = false;
                            boolean remove2 = false;

                            if (B.isDecorant(tip)) {
                                PlatformBlockState bel = output.get(finalI, k - 1, j);

                                if (bel == null) {
                                    remove = true;
                                } else if (!B.canPlaceOnto(tip, bel)) {
                                    remove = true;
                                } else if (IrisProceduralBlocks.hasProperty(bel, "half")) {
                                    PlatformBlockState bb = output.get(finalI, k - 2, j);
                                    if (bb == null || !B.canPlaceOnto(bel, bb)) {
                                        remove = true;
                                        remove2 = true;
                                    }
                                }

                                if (remove) {
                                    changed.set(true);
                                    output.set(finalI, k, j, AIR.get());

                                    if (remove2) {
                                        output.set(finalI, k - 1, j, AIR.get());
                                    }
                                }
                            }
                        }
                    }
                });
            }
            burst.complete();
        }

        getEngine().getMetrics().getPerfection().put(p.getMilliseconds());
    }

    static PlatformBlockState normalizeSpike(PlatformBlockState state, Hunk<PlatformBlockState> output,
                                              int x, int z, int y, PlatformBlockState air, PlatformBlockState water) {
        if (IrisSpeleothems.isSupported(state, output, x, z, y)) {
            IrisSpeleothems.finishAtTip(output, x, z, y);
            return output.get(x, y, z);
        }

        String material = IrisProceduralBlocks.materialKey(state);
        String direction = IrisProceduralBlocks.propertyValue(state, "vertical_direction");
        boolean upward = "up".equals(direction);
        int step = upward ? 1 : -1;
        int nextY = y;
        while (nextY >= 0 && nextY < output.getHeight()) {
            PlatformBlockState current = output.get(x, nextY, z);
            if (!IrisSpeleothems.isSpike(current)
                    || !material.equals(IrisProceduralBlocks.materialKey(current))
                    || !direction.equals(IrisProceduralBlocks.propertyValue(current, "vertical_direction"))) {
                break;
            }
            output.set(x, nextY, z, current.isWaterLogged() ? water : air);
            nextY += step;
        }

        if (nextY >= 0 && nextY < output.getHeight()) {
            PlatformBlockState retained = output.get(x, nextY, z);
            if (IrisSpeleothems.isSpike(retained)
                    && material.equals(IrisProceduralBlocks.materialKey(retained))
                    && !direction.equals(IrisProceduralBlocks.propertyValue(retained, "vertical_direction"))) {
                IrisSpeleothems.finishColumn(output, x, z, nextY, 1, !upward);
            }
        }
        return output.get(x, y, z);
    }

    private void hideOres(Hunk<PlatformBlockState> output, boolean multicore) {
        BurstExecutor burst = burst().burst(multicore);
        int height = output.getHeight();
        for (int i = 0; i < 16; i++) {
            int finalI = i;
            burst.queue(() -> {
                for (int j = 0; j < 16; j++) {
                    for (int k = height - 1; k >= 0; k--) {
                        PlatformBlockState block = output.get(finalI, k, j);
                        if (block == null) {
                            continue;
                        }
                        BoundBlockState base = ORE_BASES.get(baseKey(block));
                        if (base != null) {
                            output.set(finalI, k, j, base.get());
                        }
                    }
                }
            });
        }
        burst.complete();
    }

    private int getHeight(Hunk<PlatformBlockState> output, int x, int z) {
        for (int i = output.getHeight() - 1; i >= 0; i--) {
            PlatformBlockState b = output.get(x, i, z);

            if (b != null) {
                if (!B.isAir(b) && !B.isFluid(b)) {
                    return i;
                }
            }
        }

        return 0;
    }
}
