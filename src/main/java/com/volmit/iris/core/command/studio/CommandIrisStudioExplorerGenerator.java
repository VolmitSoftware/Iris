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

package com.volmit.iris.core.command.studio;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisDataManager;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.gui.NoiseExplorerGUI;
import com.volmit.iris.core.tools.IrisWorlds;
import com.volmit.iris.engine.object.IrisGenerator;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.plugin.MortarCommand;
import com.volmit.iris.util.plugin.VolmitSender;

public class CommandIrisStudioExplorerGenerator extends MortarCommand {
    public CommandIrisStudioExplorerGenerator() {
        super("generator", "gen");
        setDescription("Preview created noise noises generators");
        requiresPermission(Iris.perm.studio);
        setCategory("World");
    }

    @Override
    public void addTabOptions(VolmitSender sender, String[] args, KList<String> list) {
        if ((args.length == 0 || args.length == 1) && sender.isPlayer() && IrisWorlds.isIrisWorld(sender.player().getWorld())) {
            IrisDataManager data = IrisWorlds.access(sender.player().getWorld()).getData();
            if (data == null) {
                sender.sendMessage("Issue when loading tab completions. No data found (?)");
            } else if (args.length == 0) {
                list.add(data.getGeneratorLoader().getPossibleKeys());
            } else if (args.length == 1) {
                list.add(data.getGeneratorLoader().getPossibleKeys(args[0]));
            }
        }
    }

    @Override
    public boolean handle(VolmitSender sender, String[] args) {
        if (!IrisSettings.get().isStudio()) {
            sender.sendMessage("To use Iris Studio, please enable studio in Iris/settings.json");
            return true;
        }

        if (!IrisSettings.get().isUseServerLaunchedGuis()) {
            sender.sendMessage("To use Iris Guis, please enable serverLaunchedGuis in Iris/settings.json");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("Specify a generator to preview");
            return true;
        }

        IrisGenerator generator;
        long seed = 12345;

        if (Iris.proj.isProjectOpen()) {
            generator = Iris.proj.getActiveProject().getActiveProvider().getData().getGeneratorLoader().load(args[0]);
            seed = Iris.proj.getActiveProject().getActiveProvider().getTarget().getWorld().seed();
        } else {
            generator = IrisDataManager.loadAnyGenerator(args[0]);
        }

        if (generator != null) {
            long finalSeed = seed;
            NoiseExplorerGUI.launch((x, z) ->
                    generator.getHeight(x, z, new RNG(finalSeed).nextParallelRNG(3245).lmax()), "Gen: " + generator.getLoadKey());

            sender.sendMessage("Opening Noise Explorer for gen " + generator.getLoadKey() + " (" + generator.getLoader().getDataFolder().getName() + ")");
            return true;
        } else {
            sender.sendMessage("Invalid Generator");
        }

        return true;
    }

    @Override
    protected String getArgsUsage() {
        return "[generator]";
    }
}
