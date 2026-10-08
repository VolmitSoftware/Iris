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

import art.arcane.iris.builtin.deposit.DepositSettings;
import art.arcane.iris.builtin.deposit.DepositStages;
import art.arcane.iris.builtin.floating.FloatingStages;
import art.arcane.iris.builtin.terrain.TerrainStages;
import art.arcane.iris.core.modules.IrisChunk;
import art.arcane.iris.engine.object.terrain.deposit.IrisDepositGenerator;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.config.FeatureSettings;
import com.volmit.config.FeatureSettings.Entry;
import com.volmit.stage.ChunkStageContext;
import com.volmit.stage.StageGraph;

import java.util.List;

/**
 * Module {@code iris:deposit}: ores and other deposits. The dimension, a region and a biome each declare their
 * own {@link DepositSettings}, and they stack: a chunk gets every level's deposits at its centre column, the
 * dimension's first, then the region's, then the biome's. The stage runs where any column of the chunk has
 * deposits, but places only what the centre column declares, so a chunk's deposits do not depend on which
 * of its columns a biome border crosses.
 */
public final class DepositModule implements IrisModule {
    private static final String VERSION = "built-in";
    private static final int CHUNK_CENTRE = 7;
    private static final int CHUNK_SHIFT = 4;

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:deposit", "Deposits", VERSION);
    }

    @Override
    public void install(StageGraph graph) {
        graph.define(DepositStages.ORES)
                .when(DepositSettings.class)
                .requires(TerrainStages.SHAPE)
                .softAfter(FloatingStages.SOLIDS.ref())
                .run(DepositModule::place);
    }

    private static void place(ChunkStageContext context, List<Entry<DepositSettings>> inChunk) {
        IrisChunk chunk = IrisChunk.current();
        List<IrisDepositGenerator> generators = generatorsAtCentre(context.settings(), context.originX(), context.originZ());
        if (generators.isEmpty() || chunk.bypassesMantleStages()) {
            return;
        }
        chunk.state(DepositPlacer.class, DepositPlacer::new).generate(generators, chunk.blocks(),
                chunk.originX() >> CHUNK_SHIFT, chunk.originZ() >> CHUNK_SHIFT, chunk.multicore(), chunk.context());
    }

    /** Every level's generators at the centre column of the chunk whose first block is {@code originX}, {@code originZ}, in level order. */
    static List<IrisDepositGenerator> generatorsAtCentre(FeatureSettings settings, int originX, int originZ) {
        return settings.at(DepositSettings.class, originX + CHUNK_CENTRE, originZ + CHUNK_CENTRE).stream()
                .flatMap(entry -> entry.value().generators().stream())
                .toList();
    }
}
