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

package com.volmit.iris.engine.mode;

import com.volmit.iris.engine.actuator.IrisBiomeActuator;
import com.volmit.iris.engine.actuator.IrisTerrainNormalActuator;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.EngineMode;
import com.volmit.iris.engine.framework.IrisEngineMode;

public class ModeEnclosure extends IrisEngineMode implements EngineMode {
    public ModeEnclosure(Engine engine) {
        super(engine);
        var terrain = new IrisTerrainNormalActuator(getEngine());
        var biome = new IrisBiomeActuator(getEngine());

        registerStage(burst(
                (x, z, k, p, m, c) -> terrain.actuate(x, z, k, m, c),
                (x, z, k, p, m, c) -> biome.actuate(x, z, p, m, c)
        ));
    }
}
