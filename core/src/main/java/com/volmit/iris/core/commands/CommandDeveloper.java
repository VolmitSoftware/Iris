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

package com.volmit.iris.core.commands;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.mantle.EngineMantle;
import com.volmit.iris.util.decree.DecreeExecutor;
import com.volmit.iris.util.decree.DecreeOrigin;
import com.volmit.iris.util.decree.annotations.Decree;
import com.volmit.iris.util.decree.annotations.Param;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.mantle.Mantle;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.concurrent.atomic.AtomicInteger;

@Decree(name = "Developer", origin = DecreeOrigin.BOTH, description = "Iris World Manager", aliases = {"dev"})
public class CommandDeveloper implements DecreeExecutor {

    @Decree(description = "Get Loaded TectonicPlates Count", origin = DecreeOrigin.BOTH, sync = true)
    public void EngineStatus(
            @Param(description = "World")
            World world
    ) {
        if (!IrisToolbelt.isIrisWorld(world)) {
            sender().sendMessage(C.RED + "This is not an Iris world. Iris worlds: " + String.join(", ", Bukkit.getServer().getWorlds().stream().filter(IrisToolbelt::isIrisWorld).map(World::getName).toList()));
            return;
        }

        Engine engine = IrisToolbelt.access(world).getEngine();
        if(engine != null) {
            long lastUseSize = engine.getMantle().getLastUseMapMemoryUsage();

            Iris.info("-------------------------");
            Iris.info(C.DARK_PURPLE + "Engine Status");
            Iris.info(C.DARK_PURPLE + "Tectonic Threads: " + C.LIGHT_PURPLE + engine.getMantle().getDynamicThreads());
            Iris.info(C.DARK_PURPLE + "Tectonic Limit: " + C.LIGHT_PURPLE + engine.getMantle().getTectonicLimit());
            Iris.info(C.DARK_PURPLE + "Tectonic Loaded Plates: " + C.LIGHT_PURPLE + engine.getMantle().getLoadedRegionCount());
            Iris.info(C.DARK_PURPLE + "Tectonic Plates: " + C.LIGHT_PURPLE + engine.getMantle().getNotClearedLoadedRegions());
            Iris.info(C.DARK_PURPLE + "Tectonic ToUnload: " + C.LIGHT_PURPLE + engine.getMantle().getToUnload());
            Iris.info(C.DARK_PURPLE + "Tectonic Unload Duration: " + C.LIGHT_PURPLE + Form.duration((long) engine.getMantle().getTectonicDuration()));
            Iris.info(C.DARK_PURPLE + "Cache Size: " + C.LIGHT_PURPLE + Form.f(IrisData.cacheSize()));
            Iris.info(C.DARK_PURPLE + "LastUse Size: " + C.LIGHT_PURPLE + Form.mem(lastUseSize));
            Iris.info(C.DARK_PURPLE + "Agressive Unload: " + C.LIGHT_PURPLE + IrisSettings.get().getPerformance().AggressiveTectonicUnload);
            Iris.info("-------------------------");
        } else {
            Iris.info(C.RED + "Engine is null!");
        }
    }
    @Decree(description = "Test", origin = DecreeOrigin.BOTH)
    public void test(){
        Iris.info("Test Developer CMD Executed");
    }
}


