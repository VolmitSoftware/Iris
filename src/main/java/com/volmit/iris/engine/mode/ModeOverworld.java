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

package com.volmit.iris.engine.mode;

import com.volmit.iris.engine.actuator.IrisBiomeActuator;
import com.volmit.iris.engine.actuator.IrisDecorantActuator;
import com.volmit.iris.engine.actuator.IrisTerrainNormalActuator;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.EngineMode;
import com.volmit.iris.engine.framework.IrisEngineMode;
import com.volmit.iris.engine.modifier.IrisCarveModifier;
import com.volmit.iris.engine.modifier.IrisDepositModifier;
import com.volmit.iris.engine.modifier.IrisPerfectionModifier;
import com.volmit.iris.engine.modifier.IrisPostModifier;
import org.bukkit.block.data.BlockData;


public class ModeOverworld extends IrisEngineMode implements EngineMode {
    public ModeOverworld(Engine engine) {
        super(engine);
        var terrain = new IrisTerrainNormalActuator(getEngine());
        var biome = new IrisBiomeActuator(getEngine());
        var decorant = new IrisDecorantActuator(getEngine());
        var cave = new IrisCarveModifier(getEngine());
        var post = new IrisPostModifier(getEngine());
        var deposit = new IrisDepositModifier(getEngine());
        var perfection = new IrisPerfectionModifier(getEngine());

        registerStage(burst(
            (x, z, k, p, m) -> generateMatter(x >> 4, z >> 4, m),
            (x, z, k, p, m) -> terrain.actuate(x, z, k, m),
            (x, z, k, p, m) -> biome.actuate(x, z, p, m)
        ));
        registerStage((x, z, k, p, m) -> cave.modify(x >> 4, z >> 4, k, m));
        registerStage((x, z, k, p, m) -> deposit.modify(x, z, k, m));
        registerStage((x, z, k, p, m) -> decorant.actuate(x, z, k, m));
        registerStage((x, z, k, p, m) -> post.modify(x, z, k, m));
        registerStage((x, z, K, p, m) -> getMantle().insertMatter(x >> 4, z >> 4, BlockData.class, K, m));
        registerStage((x, z, k, p, m) -> perfection.modify(x, z, k, m));
    }
}
