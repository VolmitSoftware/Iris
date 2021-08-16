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
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.util.decree.DecreeExecutor;
import com.volmit.iris.util.decree.annotations.Decree;
import com.volmit.iris.util.decree.annotations.Param;
import com.volmit.iris.util.format.C;

@Decree(name = "irisd", aliases = {"ird"}, description = "Basic Command")
public class DecIris implements DecreeExecutor
{
    private DecIrisStudio studio;

    @Decree(description = "Print version information")
    public void version(){
        sender().sendMessage("Iris v" + Iris.instance.getDescription().getVersion() + " by Volmit Software");
    }

    @Decree(description = "Set aura spins")
    public void aura(
            @Param(name = "h", description = "The h color value")
                    int h,
            @Param(name = "s", description = "The s color value")
                    int s,
            @Param(name = "b", description = "The b color value")
                    int b
    ){
        IrisSettings.get().getGeneral().setSpinh(h);
        IrisSettings.get().getGeneral().setSpins(s);
        IrisSettings.get().getGeneral().setSpinb(b);
        IrisSettings.get().forceSave();
        sender().sendMessage("<rainbow>Aura Spins updated to " + h + " " + s + " " + b);
    }

    @Decree(description = "Bitwise calculations")
    public void bitwise(
            @Param(name = "value1", description = "The first value to run calculations on")
            int val1,
            @Param(name = "operator", description = "The operator: | & ^ >> << %")
            String operator,
            @Param(name = "value2", description = "The second value to run calculations on")
            int val2
    ){
        Integer v = null;
        switch(operator) {
            case "|" -> v = val1 | val2;
            case "&" -> v = val1 & val2;
            case "^" -> v = val1 ^ val2;
            case "%" -> v = val1 % val2;
            case ">>" -> v = val1 >> val2;
            case "<<" -> v = val1 << val2;
        };
        if (v == null){
            sender().sendMessage(C.RED + "The operator you entered: (" + operator + ") is invalid!");
            return;
        }
        sender().sendMessage(C.GREEN + "" + val1 + " " + operator + " " + val2 + " => " + v);
    }

    
}
