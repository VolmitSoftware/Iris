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
import art.arcane.iris.modules.BuiltInModulesVersion;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;
import com.volmit.ModuleSetup;
import com.volmit.block.BlockState;
import com.volmit.hunk.Hunk;
import com.volmit.geometry.BlockBox;
import com.volmit.stage.ChunkStageContext;
import com.volmit.world.TerrainSampler;

/**
 * Module {@code iris:matter}: copies the blocks the mantle planned for the chunk, its objects and structures,
 * into it (a dimension with {@code useMantle} off plans none). Where an upper dimension sits above, blocks at or
 * above where it begins are left out. It orders itself after deposits without requiring them, since deposits run
 * only where a level declares some.
 */
public final class MatterModule implements IrisModule {
    private static final String VERSION = BuiltInModulesVersion.VERSION;
    private static final int CHUNK_WIDTH = 16;
    private static final int NO_LIMIT = Integer.MAX_VALUE;

    @Override
    public ModuleInfo info() {
        return ModuleInfo.of("iris:matter", "Mantle blocks", VERSION);
    }

    @Override
    public void setup(ModuleSetup setup) {
        setup.stages().define(IrisStages.Matter.INSERT)
                .requires(IrisStages.Floating.SOLIDS)
                .softAfter(IrisStages.Deposit.ORES)
                .run(MatterModule::insert);
    }

    private static void insert(ChunkStageContext context) {
        int originX = context.originX();
        int originZ = context.originZ();
        Hunk<BlockState> blocks = context.blocks();
        int[] limits = upperLimits(context.terrain(), originX, originZ);
        BlockBox chunk = new BlockBox(originX, 0, originZ, originX + CHUNK_WIDTH, blocks.height(), originZ + CHUNK_WIDTH);
        context.mantle().blocks().visit(chunk, (x, y, z, block) -> {
            int localX = x - originX;
            int localZ = z - originZ;
            if (y < limits[column(localX, localZ)]) {
                blocks.set(localX, y, localZ, block);
            }
        });
    }

    /** Per column, the height from which the upper dimension takes over, or no limit. */
    static int[] upperLimits(TerrainSampler terrain, int originX, int originZ) {
        int[] limits = new int[CHUNK_WIDTH * CHUNK_WIDTH];
        for (int localX = 0; localX < CHUNK_WIDTH; localX++) {
            for (int localZ = 0; localZ < CHUNK_WIDTH; localZ++) {
                limits[column(localX, localZ)] = terrain.upperDimensionY(originX + localX, originZ + localZ).orElse(NO_LIMIT);
            }
        }
        return limits;
    }

    private static int column(int localX, int localZ) {
        return localX * CHUNK_WIDTH + localZ;
    }
}
