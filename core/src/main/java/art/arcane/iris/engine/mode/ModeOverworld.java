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

package art.arcane.iris.engine.mode;

import art.arcane.iris.engine.actuator.IrisBiomeActuator;
import art.arcane.iris.engine.actuator.IrisDecorantActuator;
import art.arcane.iris.engine.actuator.IrisTerrainNormalActuator;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineMode;
import art.arcane.iris.engine.framework.EngineStage;
import art.arcane.iris.engine.framework.IrisEngineMode;
import art.arcane.iris.engine.modifier.*;
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
        var custom = new IrisCustomModifier(getEngine());
        EngineStage sBiome = (x, z, k, p, m, c) -> biome.actuate(x, z, p, m, c);
        EngineStage sGenMatter = (x, z, k, p, m, c) -> generateMatter(x >> 4, z >> 4, m, c);
        EngineStage sTerrain = (x, z, k, p, m, c) -> terrain.actuate(x, z, k, m, c);
        EngineStage sDecorant = (x, z, k, p, m, c) -> decorant.actuate(x, z, k, m, c);
        EngineStage sCave = (x, z, k, p, m, c) -> cave.modify(x >> 4, z >> 4, k, m, c);
        EngineStage sDeposit = (x, z, k, p, m, c) -> deposit.modify(x, z, k, m, c);
        EngineStage sPost = (x, z, k, p, m, c) -> post.modify(x, z, k, m, c);
        EngineStage sInsertMatter = (x, z, K, p, m, c) -> getMantle().insertMatter(x >> 4, z >> 4, BlockData.class, K, m);
        EngineStage sPerfection = (x, z, k, p, m, c) -> perfection.modify(x, z, k, m, c);
        EngineStage sCustom = (x, z, k, p, m, c) -> custom.modify(x, z, k, m, c);

        registerStage(burst(
                sGenMatter,
                sTerrain
        ));
        registerStage(burst(
                sCave,
                sPost
        ));
        registerStage(burst(
                sDeposit,
                sInsertMatter,
                sDecorant
        ));
        registerStage(sPerfection);
        registerStage(sCustom);
    }
}
