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

package com.volmit.iris.core.decrees;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.object.dimensional.IrisDimension;
import com.volmit.iris.util.decree.DecreeExecutor;
import com.volmit.iris.util.decree.annotations.Decree;
import com.volmit.iris.util.decree.annotations.Param;
import com.volmit.iris.util.format.C;

@Decree(name = "studio", aliases = {"std", "s"}, description = "Studio Commands", studio = true)
public class DecIrisStudio implements DecreeExecutor
{
    @Decree(description = "Open a new studio world", aliases = "o", sync = true)
    public void open(
            @Param(name = "dimension", defaultValue = "overworld", aliases = "dim", required = true)
                    IrisDimension dimension,
            @Param(name = "seed", defaultValue = "1337", aliases = "s")
                    long seed)
    {
        Iris.proj.open(sender(), seed, dimension.getLoadKey());
    }

    @Decree(description = "Close an open studio project", aliases = "x", sync = true)
    public void close()
    {
        if (!Iris.proj.isProjectOpen()) {
            sender().sendMessage(C.RED + "No open studio projects.");
            return;
        }

        Iris.proj.getActiveProject().getActiveProvider().getTarget().getWorld().evacuate();
        Iris.proj.close();
        sender().sendMessage(C.YELLOW + "Project Closed");
    }
}
