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

import art.arcane.iris.generation.chunk.ColumnExtent;
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
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;

import java.util.HashMap;
import java.util.Map;
import it.unimi.dsi.fastutil.ints.IntArrayList;

public class IrisPerfectionModifier extends EngineAssignedModifier<NativeBlockState> {
    private static final int SOLID = 1;
    private static final int SPIKE = 2;
    private static final int SLICE_TASKS = 16;
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

    private static String baseKey(NativeBlockState state) {
        String key = state.key();
        int bracket = key.indexOf('[');
        return bracket < 0 ? key : key.substring(0, bracket);
    }

    @Override
    public void onModify(int x, int z, Hunk<NativeBlockState> output, boolean multicore, ChunkContext context) {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        if (getDimension().isHideOresForHiddenOre()) {
            hideOres(output, multicore);
        }
        int tasks = multicore ? SLICE_TASKS : 1;
        int slicesPerTask = 16 / tasks;
        BurstExecutor burst = burst().burst(multicore);
        for (int task = 0; task < tasks; task++) {
            int firstSlice = task * slicesPerTask;
            burst.queue(() -> perfectSlices(output, firstSlice, firstSlice + slicesPerTask));
        }
        burst.complete();

        getEngine().getMetrics().getPerfection().put(p.getMilliseconds());
    }

    private void perfectSlices(Hunk<NativeBlockState> output, int firstSlice, int lastSlice) {
        IntArrayList surfaces = new IntArrayList();
        StateFlags states = new StateFlags();
        NativeBlockState air = AIR.get();
        NativeBlockState water = WATER.get();
        for (int x = firstSlice; x < lastSlice; x++) {
            for (int z = 0; z < 16; z++) {
                perfectColumn(output, x, z, surfaces, states, air, water);
            }
        }
    }

    private static void perfectColumn(Hunk<NativeBlockState> output, int x, int z, IntArrayList surfaces,
                                      StateFlags states, NativeBlockState air, NativeBlockState water) {
        do {
            surfaces.clear();
            int top = getHeight(output, x, z, states);
            boolean inside = true;
            surfaces.add(top);
            NativeBlockState classified = null;
            int classifiedFlags = 0;
            for (int y = top; y >= 0; y--) {
                NativeBlockState block = output.get(x, y, z);
                int flags = 0;
                if (block != null) {
                    if (block != classified) {
                        classified = block;
                        classifiedFlags = states.of(block);
                    }
                    flags = classifiedFlags;
                    if ((flags & SPIKE) != 0) {
                        block = normalizeSpike(block, output, x, z, y, air, water);
                        flags = block == null ? 0 : states.of(block);
                    }
                }
                boolean now = (flags & SOLID) != 0;
                if (now != inside) {
                    inside = now;
                    if (inside) {
                        surfaces.add(y);
                    }
                }
            }
        } while (removeUnsupportedDecorants(output, x, z, surfaces, air));
    }

    private static boolean removeUnsupportedDecorants(Hunk<NativeBlockState> output, int x, int z,
                                                      IntArrayList surfaces, NativeBlockState air) {
        boolean changed = false;
        for (int index = 0; index < surfaces.size(); index++) {
            int y = surfaces.getInt(index);
            NativeBlockState tip = output.get(x, y, z);
            if (!B.isDecorant(tip)) {
                continue;
            }
            NativeBlockState below = output.get(x, y - 1, z);
            boolean remove = below == null || !B.canPlaceOnto(tip, below);
            boolean removeBelow = false;
            if (!remove && IrisProceduralBlocks.hasProperty(below, "half")) {
                NativeBlockState support = output.get(x, y - 2, z);
                if (support == null || !B.canPlaceOnto(below, support)) {
                    remove = true;
                    removeBelow = true;
                }
            }
            if (remove) {
                changed = true;
                output.set(x, y, z, air);
                if (removeBelow) {
                    output.set(x, y - 1, z, air);
                }
            }
        }
        return changed;
    }

    static NativeBlockState normalizeSpike(NativeBlockState state, Hunk<NativeBlockState> output,
                                              int x, int z, int y, NativeBlockState air, NativeBlockState water) {
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
            NativeBlockState current = output.get(x, nextY, z);
            if (!IrisSpeleothems.isSpike(current)
                    || !material.equals(IrisProceduralBlocks.materialKey(current))
                    || !direction.equals(IrisProceduralBlocks.propertyValue(current, "vertical_direction"))) {
                break;
            }
            output.set(x, nextY, z, current.isWaterLogged() ? water : air);
            nextY += step;
        }

        if (nextY >= 0 && nextY < output.getHeight()) {
            NativeBlockState retained = output.get(x, nextY, z);
            if (IrisSpeleothems.isSpike(retained)
                    && material.equals(IrisProceduralBlocks.materialKey(retained))
                    && !direction.equals(IrisProceduralBlocks.propertyValue(retained, "vertical_direction"))) {
                IrisSpeleothems.finishColumn(output, x, z, nextY, 1, !upward);
            }
        }
        return output.get(x, y, z);
    }

    private void hideOres(Hunk<NativeBlockState> output, boolean multicore) {
        BurstExecutor burst = burst().burst(multicore);
        int height = output.getHeight();
        for (int i = 0; i < 16; i++) {
            int finalI = i;
            burst.queue(() -> {
                for (int j = 0; j < 16; j++) {
                    for (int k = height - 1; k >= 0; k--) {
                        NativeBlockState block = output.get(finalI, k, j);
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

    private static int getHeight(Hunk<NativeBlockState> output, int x, int z, StateFlags states) {
        NativeBlockState classified = null;
        int classifiedFlags = 0;
        for (int i = Math.min(output.getHeight() - 1, ColumnExtent.highestStoredY(output, x, z)); i >= 0; i--) {
            NativeBlockState state = output.get(x, i, z);
            if (state == null) {
                continue;
            }
            if (state != classified) {
                classified = state;
                classifiedFlags = states.of(state);
            }
            if ((classifiedFlags & SOLID) != 0) {
                return i;
            }
        }

        return 0;
    }

    private static final class StateFlags {
        private static final int SLOTS = 64;

        private final NativeBlockState[] states = new NativeBlockState[SLOTS];
        private final byte[] flags = new byte[SLOTS];

        private int of(NativeBlockState state) {
            int slot = System.identityHashCode(state) & (SLOTS - 1);
            if (states[slot] == state) {
                return flags[slot];
            }
            int computed = (state.isAir() || state.isFluid() ? 0 : SOLID) | (IrisSpeleothems.isSpike(state) ? SPIKE : 0);
            states[slot] = state;
            flags[slot] = (byte) computed;
            return computed;
        }
    }
}
