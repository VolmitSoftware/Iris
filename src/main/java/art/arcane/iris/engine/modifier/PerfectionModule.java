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
import art.arcane.iris.core.modules.IrisChunk;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.procedural.IrisProceduralBlocks;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.stage.ChunkStageContext;
import com.volmit.stage.StageGraph;

import java.util.ArrayList;
import java.util.HashMap;
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
    private static final char PROPERTIES_START = '[';

    private static final class States {
        private static final PlatformBlockState AIR = B.getState("AIR");
        private static final Map<String, PlatformBlockState> ORE_BASES = oreBases();
    }

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:perfection", "Perfection", VERSION);
    }

    @Override
    public void install(StageGraph graph) {
        graph.define(IrisStages.Perfection.SURFACE).requires(IrisStages.Floating.DECORATE).run(PerfectionModule::perfect);
    }

    private static void perfect(ChunkStageContext context) {
        IrisChunk chunk = IrisChunk.current();
        Engine engine = chunk.engine();
        IrisDimension dimension = engine.getDimension();
        PrecisionStopwatch stopwatch = PrecisionStopwatch.start();
        Hunk<PlatformBlockState> blocks = chunk.blocks();
        if (dimension.isHideOresForHiddenOre()) {
            hideOres(blocks);
        }
        boolean removed;
        do {
            removed = removeUnsupportedDecorants(blocks);
        } while (removed);
        engine.getMetrics().getPerfection().put(stopwatch.getMilliseconds());
    }

    private static void hideOres(Hunk<PlatformBlockState> blocks) {
        for (int x = 0; x < blocks.getWidth(); x++) {
            for (int z = 0; z < blocks.getDepth(); z++) {
                for (int y = blocks.getHeight() - 1; y >= 0; y--) {
                    PlatformBlockState block = blocks.get(x, y, z);
                    PlatformBlockState base = block == null ? null : States.ORE_BASES.get(materialOf(block));
                    if (base != null) {
                        blocks.set(x, y, z, base);
                    }
                }
            }
        }
    }

    /** One pass over every column; whether it removed anything. */
    private static boolean removeUnsupportedDecorants(Hunk<PlatformBlockState> blocks) {
        boolean removed = false;
        for (int x = 0; x < blocks.getWidth(); x++) {
            for (int z = 0; z < blocks.getDepth(); z++) {
                for (int surface : surfaces(blocks, x, z)) {
                    removed |= removeIfUnsupported(blocks, x, surface, z);
                }
            }
        }
        return removed;
    }

    /** The topmost solid block of a column and each solid block with open space above it below that. */
    private static List<Integer> surfaces(Hunk<PlatformBlockState> blocks, int x, int z) {
        List<Integer> surfaces = new ArrayList<>();
        int top = topSolid(blocks, x, z);
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

    private static boolean removeIfUnsupported(Hunk<PlatformBlockState> blocks, int x, int y, int z) {
        PlatformBlockState tip = blocks.get(x, y, z);
        if (tip == null || !B.isDecorant(tip)) {
            return false;
        }
        PlatformBlockState below = blocks.get(x, y - 1, z);
        if (below == null || !B.canPlaceOnto(tip, below)) {
            blocks.set(x, y, z, States.AIR);
            return true;
        }
        if (!IrisProceduralBlocks.hasProperty(below, HALF)) {
            return false;
        }
        PlatformBlockState belowBelow = blocks.get(x, y - 2, z);
        if (belowBelow != null && B.canPlaceOnto(below, belowBelow)) {
            return false;
        }
        blocks.set(x, y, z, States.AIR);
        blocks.set(x, y - 1, z, States.AIR);
        return true;
    }

    private static int topSolid(Hunk<PlatformBlockState> blocks, int x, int z) {
        for (int y = blocks.getHeight() - 1; y >= 0; y--) {
            if (isSolid(blocks.get(x, y, z))) {
                return y;
            }
        }
        return 0;
    }

    private static boolean isSolid(PlatformBlockState block) {
        return block != null && !(B.isAir(block) || B.isFluid(block));
    }

    private static String materialOf(PlatformBlockState state) {
        String key = state.key();
        int properties = key.indexOf(PROPERTIES_START);
        return properties < 0 ? key : key.substring(0, properties);
    }

    private static Map<String, PlatformBlockState> oreBases() {
        Map<String, PlatformBlockState> bases = new HashMap<>();
        PlatformBlockState stone = B.getState("STONE");
        PlatformBlockState deepslate = B.getState("DEEPSLATE");
        PlatformBlockState netherrack = B.getState("NETHERRACK");
        PlatformBlockState blackstone = B.getState("BLACKSTONE");
        for (String ore : List.of("coal", "copper", "iron", "gold", "redstone", "lapis", "diamond", "emerald")) {
            bases.put("minecraft:" + ore + "_ore", stone);
            bases.put("minecraft:deepslate_" + ore + "_ore", deepslate);
        }
        bases.put("minecraft:nether_gold_ore", netherrack);
        bases.put("minecraft:nether_quartz_ore", netherrack);
        bases.put("minecraft:ancient_debris", netherrack);
        bases.put("minecraft:gilded_blackstone", blackstone);
        return bases;
    }
}
