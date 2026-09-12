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

package art.arcane.iris.generation.mode;

import art.arcane.iris.generation.stage.IrisBiomeActuator;
import art.arcane.iris.generation.stage.IrisTerrainNormalActuator;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.EngineMode;
import art.arcane.iris.generation.runtime.IrisEngineMode;

public class ModeEnclosure extends IrisEngineMode implements EngineMode {
    public ModeEnclosure(Engine engine) {
        super(engine);
        IrisTerrainNormalActuator terrain = new IrisTerrainNormalActuator(getEngine());
        IrisBiomeActuator biome = new IrisBiomeActuator(getEngine());

        registerTerrainStage(burst(
                (x, z, k, p, m, c) -> terrain.actuate(x, z, k, m, c),
                (x, z, k, p, m, c) -> biome.actuate(x, z, p, m, c)
        ));
    }
}
