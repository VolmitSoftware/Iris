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

package art.arcane.iris.engine.object.placement;

import art.arcane.iris.builtin.IrisStages;
import art.arcane.iris.builtin.blocks.WrittenBlocks;
import art.arcane.iris.builtin.prepare.PreparedPlacement;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.block.BlockState;
import com.volmit.placement.PlacedBlock;
import com.volmit.stage.PlacementStageContext;
import com.volmit.stage.StageGraph;
import com.volmit.world.BlockPosition;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Module {@code iris:snow}: a random depth of snow on top of each column of the object, deeper the more
 * snow the placement asks for. Only the object's own written blocks carry snow.
 */
public final class SnowModule implements IrisModule {
    private static final int LAYERS_PER_UNIT_OF_SNOW = 7;
    private static final BlockState[] SNOW_LAYERS = IntStream.rangeClosed(1, 8)
            .mapToObj(layers -> new BlockState("minecraft:snow").withProperty("layers", Integer.toString(layers)))
            .toArray(BlockState[]::new);

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:snow", "Snow", PlacementModules.VERSION);
    }

    @Override
    public void install(StageGraph graph) {
        graph.define(IrisStages.Snow.COVER)
                .requires(IrisStages.Blocks.WRITE, IrisStages.Blocks.COMPLETE, IrisStages.Prepare.TRANSFORMS)
                .softAfter(IrisStages.Stilt.FILL.ref(), IrisStages.Vacuum.TERRAIN.ref())
                .run(SnowModule::cover);
    }

    private static void cover(PlacementStageContext context) {
        PreparedPlacement prepared = context.output(IrisStages.Prepare.TRANSFORMS);
        double snow = prepared.settings().snow();
        WrittenBlocks written = context.output(IrisStages.Blocks.WRITE);
        if (!(snow > 0) || written.isEmpty()) {
            return;
        }
        PlacementWorld world = new PlacementWorld(context.access(), prepared.traits());
        RNG rng = new RNG(context.random().nextLong());
        int maxLayer = SNOW_LAYERS.length - 1;
        for (BlockPosition top : columnTops(written).values()) {
            int layer = rng.i(0, (int) (snow * LAYERS_PER_UNIT_OF_SNOW));
            world.set(top.x(), top.y() + 1, top.z(), SNOW_LAYERS[Math.max(Math.min(layer, maxLayer), 0)]);
        }
    }

    /** The highest written block of each column, columns in the order they were first written. */
    private static Map<Long, BlockPosition> columnTops(WrittenBlocks written) {
        Map<Long, BlockPosition> tops = new LinkedHashMap<>();
        for (PlacedBlock block : written.blocks()) {
            BlockPosition position = block.position();
            tops.merge(columnKey(position), position, (current, next) -> next.y() > current.y() ? next : current);
        }
        return tops;
    }

    private static long columnKey(BlockPosition position) {
        return ((long) position.x() << Integer.SIZE) | (position.z() & 0xFFFFFFFFL);
    }
}
