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
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.plugin.MortarCommand;
import com.volmit.iris.util.plugin.VolmitSender;
import org.bukkit.entity.Player;

import java.awt.*;
import java.io.File;

public class CommandIrisStudioEditBiome extends MortarCommand {
    public CommandIrisStudioEditBiome() {
        super("editbiome", "ebiome", "eb");
        setDescription("Open this biome file in vscode");
        requiresPermission(Iris.perm.studio);
        setCategory("Studio");
    }

    @Override
    public void addTabOptions(VolmitSender sender, String[] args, KList<String> list) {

    }

    @Override
    public boolean handle(VolmitSender sender, String[] args) {
        if (!IrisSettings.get().isStudio()) {
            sender.sendMessage("To use Iris Studio, please enable studio in Iris/settings.json");
            return true;
        }

        if (!Iris.proj.isProjectOpen()) {
            sender.sendMessage("There is not a studio currently loaded.");
            return true;
        }

        if (sender.isPlayer()) {
            Player p = sender.player();

            try {
                File f = Iris.proj.getActiveProject().getActiveProvider().getEngine().getBiome(p.getLocation().getBlockX(), p.getLocation().getBlockY(), p.getLocation().getBlockZ()).getLoadFile();
                Desktop.getDesktop().open(f);
            } catch (Throwable e) {
                Iris.reportError(e);
                sender.sendMessage("Cant find the file. Are you in an Iris Studio world?");
            }

            return true;
        } else {
            sender.sendMessage("Players only.");
        }

        return true;
    }

    @Override
    protected String getArgsUsage() {
        return "[width]";
    }
}
