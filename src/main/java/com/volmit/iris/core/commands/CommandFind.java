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

import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IrisBiome;
import com.volmit.iris.engine.object.IrisJigsawStructure;
import com.volmit.iris.engine.object.IrisRegion;
import com.volmit.iris.util.decree.DecreeExecutor;
import com.volmit.iris.util.decree.DecreeOrigin;
import com.volmit.iris.util.decree.annotations.Decree;
import com.volmit.iris.util.decree.annotations.Param;
import com.volmit.iris.util.decree.specialhandlers.ObjectHandler;
import com.volmit.iris.util.format.C;

@Decree(name = "find", origin = DecreeOrigin.PLAYER, description = "Iris Find commands", aliases = "goto")
public class CommandFind implements DecreeExecutor {
    @Decree(description = "Find a biome")
    public void biome(
            @Param(description = "The biome to look for")
            IrisBiome biome
    ) {
        Engine e = engine();

        if (e == null) {
            sender().sendMessage(C.GOLD + "Not in an Iris World!");
            return;
        }

        e.gotoBiome(biome, player());
    }

    @Decree(description = "Find a region")
    public void region(
            @Param(description = "The region to look for")
            IrisRegion region
    ) {
        Engine e = engine();

        if (e == null) {
            sender().sendMessage(C.GOLD + "Not in an Iris World!");
            return;
        }

        e.gotoRegion(region, player());
    }

    @Decree(description = "Find a structure")
    public void structure(
            @Param(description = "The structure to look for")
            IrisJigsawStructure structure
    ) {
        Engine e = engine();

        if (e == null) {
            sender().sendMessage(C.GOLD + "Not in an Iris World!");
            return;
        }

        e.gotoJigsaw(structure, player());
    }

    @Decree(description = "Find a point of interest.")
    public void poi(
            @Param(description = "The type of PoI to look for.")
            String type
    ) {
        Engine e = engine();
        if (e == null) {
            sender().sendMessage(C.GOLD + "Not in an Iris World!");
            return;
        }

        e.gotoPOI(type, player());
    }

    @Decree(description = "Find an object")
    public void object(
            @Param(description = "The object to look for", customHandler = ObjectHandler.class)
            String object
    ) {
        Engine e = engine();

        if (e == null) {
            sender().sendMessage(C.GOLD + "Not in an Iris World!");
            return;
        }

        e.gotoObject(object, player());
    }
}
