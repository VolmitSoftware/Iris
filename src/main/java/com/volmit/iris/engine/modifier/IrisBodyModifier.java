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

package com.volmit.iris.engine.modifier;

import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.EngineAssignedModifier;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.math.RNG;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

public class IrisBodyModifier extends EngineAssignedModifier<BlockData> {
    private final RNG rng;
    private final BlockData AIR = Material.CAVE_AIR.createBlockData();
    private final BlockData WATER = Material.WATER.createBlockData();
    private final BlockData LAVA = Material.LAVA.createBlockData();

    public IrisBodyModifier(Engine engine) {
        super(engine, "Bodies");
        rng = new RNG(getEngine().getSeedManager().getBodies());
    }

    @Override
    public void onModify(int x, int z, Hunk<BlockData> output, boolean multicore) {

    }
}
