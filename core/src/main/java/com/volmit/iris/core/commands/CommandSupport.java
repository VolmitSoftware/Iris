/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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
import com.volmit.iris.util.decree.DecreeExecutor;
import com.volmit.iris.util.decree.DecreeOrigin;
import com.volmit.iris.util.decree.annotations.Decree;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.misc.Hastebin;

@Decree(name = "Support", origin = DecreeOrigin.BOTH, description = "Iris World Manager", aliases = {"support"})
public class CommandSupport implements DecreeExecutor {

    @Decree(description = "report")
    public void report() {
        try {
            if (sender().isPlayer())
                sender().sendMessage(C.GOLD + "Creating report..");
            if (!sender().isPlayer()) Iris.info(C.GOLD + "Creating report..");
            Hastebin.enviornment(sender());

        } catch (Exception e) {
            Iris.info(C.RED + "Something went wrong: ");
            e.printStackTrace();
        }
    }


}


