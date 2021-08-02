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

import com.volmit.iris.core.project.loader.IrisData;
import com.volmit.iris.engine.IrisComplex;
import com.volmit.iris.engine.data.DataProvider;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

public interface EngineFramework extends DataProvider {
    Engine getEngine();

    IrisComplex getComplex();

    EngineParallaxManager getEngineParallax();

    default IrisData getData() {
        return getComplex().getData();
    }

    default void recycle() {
        getEngine().getParallax().cleanup();
        getData().getObjectLoader().clean();
    }

    EngineActuator<BlockData> getTerrainActuator();

    EngineActuator<BlockData> getDecorantActuator();

    EngineActuator<Biome> getBiomeActuator();

    EngineModifier<BlockData> getCaveModifier();

    EngineModifier<BlockData> getRavineModifier();

    EngineModifier<BlockData> getDepositModifier();

    EngineModifier<BlockData> getPostModifier();

    void close();
}
