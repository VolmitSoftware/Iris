/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

import com.volmit.iris.engine.IrisComplex;
import com.volmit.iris.engine.mantle.EngineMantle;
import com.volmit.iris.util.documentation.BlockCoordinates;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.parallel.MultiBurst;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

public interface EngineMode extends Staged {
    void close();

    Engine getEngine();

    default MultiBurst burst() {
        return getEngine().burst();
    }

    default EngineStage burst(EngineStage... stages) {
        return (x, z, blocks, biomes, multicore) -> {
            BurstExecutor e = burst().burst(stages.length);
            e.setMulticore(multicore);

            for(EngineStage i : stages) {
                e.queue(() -> i.generate(x, z, blocks, biomes, multicore));
            }

            e.complete();
        };
    }

    default IrisComplex getComplex() {
        return getEngine().getComplex();
    }

    default EngineMantle getMantle() {
        return getEngine().getMantle();
    }

    default void generateMatter(int x, int z, boolean multicore) {
        getMantle().generateMatter(x, z, multicore);
    }

    @BlockCoordinates
    default void generate(int x, int z, Hunk<BlockData> blocks, Hunk<Biome> biomes, boolean multicore) {
        for(EngineStage i : getStages()) {
            i.generate(x, z, blocks, biomes, multicore);
        }
    }
}
