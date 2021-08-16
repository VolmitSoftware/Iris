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
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.object.dimensional.IrisDimension;
import com.volmit.iris.util.decree.DecreeExecutor;
import com.volmit.iris.util.decree.DecreeOrigin;
import com.volmit.iris.util.decree.annotations.Decree;
import com.volmit.iris.util.decree.annotations.Param;
import com.volmit.iris.util.format.C;

import java.io.File;

@Decree(name = "irisd", aliases = {"ird"}, description = "Basic Command")
public class DecIris implements DecreeExecutor
{
    private DecStudio studio;

    private DecPregen pregen;

    private DecWhat what;

    @Decree(description = "Create a new world", aliases = "+")
    public void create(
            @Param(aliases = "world-name", description = "The name of the world to create", defaultValue = "IrisWorld")
            String name,
            @Param(aliases = "dimension", description = "The dimension type to create the world with", defaultValue = "overworld")
            IrisDimension type,
            @Param(description = "The seed to generate the world with", defaultValue = "1337")
            long seed
    ){
        if (name.equals("iris")) {
            sender().sendMessage(C.RED + "You cannot use the world name \"iris\" for creating worlds as Iris uses this directory for studio worlds.");
            sender().sendMessage(C.RED + "May we suggest the name \"IrisWorld\" instead?");
            return;
        }

        if (new File(name).exists()){
            sender().sendMessage(C.RED + "That folder already exists!");
            return;
        }

        try {
            IrisToolbelt.createWorld()
                    .dimension(type.getLoadKey())
                    .name(name)
                    .seed(seed)
                    .sender(sender())
                    .studio(false)
                    .create();
        } catch (Throwable e){
            sender().sendMessage(C.RED + "Exception raised during creation. See the console for more details.");
            Iris.error("Exception raised during world creation: " + e.getMessage());
            Iris.reportError(e);
            return;
        }

        sender().sendMessage(C.GREEN + "Successfully created your world!");
    }

    @Decree(description = "Print version information")
    public void version(){
        sender().sendMessage("Iris v" + Iris.instance.getDescription().getVersion() + " by Volmit Software");
    }

    @Decree(description = "Set aura spins")
    public void aura(
            @Param(description = "The h color value")
            int h,
            @Param(description = "The s color value")
            int s,
            @Param(description = "The b color value")
            int b
    ) {
        IrisSettings.get().getGeneral().setSpinh(h);
        IrisSettings.get().getGeneral().setSpins(s);
        IrisSettings.get().getGeneral().setSpinb(b);
        IrisSettings.get().forceSave();
        sender().sendMessage("<rainbow>Aura Spins updated to " + h + " " + s + " " + b);
    }

    @Decree(description = "Bitwise calculations")
    public void bitwise(
            @Param(description = "The first value to run calculations on")
            int value1,
            @Param(description = "The operator: | & ^ >> << %")
            String operator,
            @Param(description = "The second value to run calculations on")
            int value2
    ) {
        Integer v = null;
        switch(operator) {
            case "|" -> v = value1 | value2;
            case "&" -> v = value1 & value2;
            case "^" -> v = value1 ^ value2;
            case "%" -> v = value1 % value2;
            case ">>" -> v = value1 >> value2;
            case "<<" -> v = value1 << value2;
        };
        if (v == null){
            sender().sendMessage(C.RED + "The operator you entered: (" + operator + ") is invalid!");
            return;
        }
        sender().sendMessage(C.GREEN + "" + value1 + " " + operator + " " + value2 + " => " + v);
    }

    @Decree(description = "Toggle debug")
    public void debug(
            @Param(name = "on", description = "Whether or not debug should be on", defaultValue = "true")
            boolean on
    ) {
        IrisSettings.get().getGeneral().setDebug(on);
    }

    @Decree(description = "Download a project.")
    public void download(
            @Param(name = "pack", description = "The pack to download", defaultValue = "overworld", aliases = "project")
            String pack,
            @Param(name = "branch", description = "The branch to download from", defaultValue = "master")
            String branch,
            @Param(name = "trim", description = "Whether or not to download a trimmed version (do not enable when you're going to edit)")
            boolean trim,
            @Param(name = "overwrite", description = "Whether or not to overwrite the pack with the downloaded one", aliases = "force", defaultValue = "false")
            boolean overwrite
    ) {
        sender().sendMessage(C.GREEN + "Downloading pack: " + pack + "/" + branch + (trim ? " trimmed" : ""));
        Iris.proj.downloadSearch(sender(), "IrisDimensions/" + pack + "/" + branch, trim, overwrite);
    }

    @Decree(description = "Get metrics for your world", aliases = "measure", origin = DecreeOrigin.PLAYER)
    public void metrics() {
        if (!IrisToolbelt.isIrisWorld(world())){
            sender().sendMessage(C.RED + "You must be in an Iris world");
            return;
        }
        sender().sendMessage(C.GREEN + "Sending metrics...");
        engine().printMetrics(sender());
    }

    @Decree(description = "Reload configuration file (this is also done automatically)")
    public void reload() {
        IrisSettings.invalidate();
        IrisSettings.get();
        sender().sendMessage(C.GREEN + "Hotloaded settings");
    }
}
