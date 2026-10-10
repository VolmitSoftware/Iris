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

package art.arcane.iris.engine.modifier;

import art.arcane.iris.builtin.IrisStages;
import art.arcane.iris.builtin.terrain.HideOresSettings;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockState;
import com.volmit.block.BlockTrait;
import com.volmit.hunk.Hunk;
import com.volmit.resource.ResourceId;
import com.volmit.stage.ChunkStageContext;
import com.volmit.stage.StageGraph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Module {@code iris:perfection}: turns ores back into the stone around them when the
 * dimension hides its ores, then removes decorations whose block below cannot hold them, again until none
 * is left, since removing one can strand the one above.
 */
public final class PerfectionModule implements IrisModule {
    private static final String VERSION = "built-in";
    private static final String HALF = "half";
    private static final int CENTER = 7;
    private static final BlockState AIR = new BlockState("minecraft:air");
    private static final Map<ResourceId, BlockState> ORE_BASES = oreBases();

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:perfection", "Perfection", VERSION);
    }

    @Override
    public void install(StageGraph graph) {
        graph.define(IrisStages.Perfection.SURFACE).requires(IrisStages.Floating.DECORATE).run(PerfectionModule::perfect);
    }

    private static void perfect(ChunkStageContext context) {
        Hunk<BlockState> blocks = context.hunk(BlockState.class);
        if (hidesOres(context)) {
            hideOres(blocks, new IdentityHashMap<>());
        }
        Surfaces surfaces = new Surfaces(blocks, context.resources().blocks(), new IdentityHashMap<>());
        boolean removed;
        do {
            removed = surfaces.removeUnsupportedDecorants();
        } while (removed);
    }

    /** The dimension's {@code hideOresForHiddenOre}, which every column of the chunk shares. */
    private static boolean hidesOres(ChunkStageContext context) {
        return context.settings().at(HideOresSettings.class, context.originX() + CENTER, context.originZ() + CENTER).stream()
                .anyMatch(entry -> entry.value().hide());
    }

    /** {@code hidden} remembers per state instance what it is hidden as, itself for a block that is no ore. */
    private static void hideOres(Hunk<BlockState> blocks, Map<BlockState, BlockState> hidden) {
        for (int x = 0; x < blocks.getWidth(); x++) {
            for (int z = 0; z < blocks.getDepth(); z++) {
                for (int y = blocks.getHeight() - 1; y >= 0; y--) {
                    BlockState block = blocks.get(x, y, z);
                    BlockState shown = hidden.computeIfAbsent(block, state -> ORE_BASES.getOrDefault(state.material(), state));
                    if (shown != block) {
                        blocks.set(x, y, z, shown);
                    }
                }
            }
        }
    }

    /**
     * The decorations of one chunk and the ground they stand on. Finding the surfaces asks about every block of the chunk,
     * which holds few distinct states, so whether a state is solid is asked once per state instance.
     */
    private record Surfaces(Hunk<BlockState> blocks, BlockRegistry registry, Map<BlockState, Boolean> solidStates) {
        /** One pass over every column; whether it removed anything. */
        boolean removeUnsupportedDecorants() {
            boolean removed = false;
            for (int x = 0; x < blocks.getWidth(); x++) {
                for (int z = 0; z < blocks.getDepth(); z++) {
                    for (int surface : surfaces(x, z)) {
                        removed |= removeIfUnsupported(x, surface, z);
                    }
                }
            }
            return removed;
        }

        /** The topmost solid block of a column and each solid block with open space above it below that. */
        private List<Integer> surfaces(int x, int z) {
            List<Integer> surfaces = new ArrayList<>();
            int top = topSolid(x, z);
            surfaces.add(top);
            boolean inside = true;
            for (int y = top; y >= 0; y--) {
                boolean solid = isSolid(blocks.get(x, y, z));
                if (solid != inside) {
                    inside = solid;
                    if (inside) {
                        surfaces.add(y);
                    }
                }
            }
            return surfaces;
        }

        private boolean removeIfUnsupported(int x, int y, int z) {
            BlockState tip = blocks.get(x, y, z);
            if (!registry.hasTrait(tip, BlockTrait.DECORATION)) {
                return false;
            }
            BlockState below = blockAt(x, y - 1, z);
            if (below == null || !registry.canPlaceOn(tip, below)) {
                blocks.set(x, y, z, AIR);
                return true;
            }
            if (!below.properties().containsKey(HALF)) {
                return false;
            }
            BlockState belowBelow = blockAt(x, y - 2, z);
            if (belowBelow != null && registry.canPlaceOn(below, belowBelow)) {
                return false;
            }
            blocks.set(x, y, z, AIR);
            blocks.set(x, y - 1, z, AIR);
            return true;
        }

        private int topSolid(int x, int z) {
            for (int y = blocks.getHeight() - 1; y >= 0; y--) {
                if (isSolid(blocks.get(x, y, z))) {
                    return y;
                }
            }
            return 0;
        }

        private boolean isSolid(BlockState block) {
            return solidStates.computeIfAbsent(block, state -> !(registry.hasTrait(state, BlockTrait.AIR) || registry.hasTrait(state, BlockTrait.FLUID)));
        }

        /** The block at {@code y}, or null below the chunk's bottom. */
        private BlockState blockAt(int x, int y, int z) {
            return y < 0 ? null : blocks.get(x, y, z);
        }
    }

    private static Map<ResourceId, BlockState> oreBases() {
        Map<ResourceId, BlockState> bases = new HashMap<>();
        BlockState stone = new BlockState("minecraft:stone");
        BlockState deepslate = new BlockState("minecraft:deepslate");
        BlockState netherrack = new BlockState("minecraft:netherrack");
        BlockState blackstone = new BlockState("minecraft:blackstone");
        for (String ore : List.of("coal", "copper", "iron", "gold", "redstone", "lapis", "diamond", "emerald")) {
            bases.put(ResourceId.parse("minecraft:" + ore + "_ore"), stone);
            bases.put(ResourceId.parse("minecraft:deepslate_" + ore + "_ore"), deepslate);
        }
        bases.put(ResourceId.parse("minecraft:nether_gold_ore"), netherrack);
        bases.put(ResourceId.parse("minecraft:nether_quartz_ore"), netherrack);
        bases.put(ResourceId.parse("minecraft:ancient_debris"), netherrack);
        bases.put(ResourceId.parse("minecraft:gilded_blackstone"), blackstone);
        return bases;
    }
}
