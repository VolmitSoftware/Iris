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

package com.volmit.iris.core.command.what;

import com.google.gson.Gson;
import com.volmit.iris.Iris;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.feature.IrisFeaturePositional;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.json.JSONObject;
import com.volmit.iris.util.plugin.MortarCommand;
import com.volmit.iris.util.plugin.VolmitSender;
import org.bukkit.Chunk;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

public class CommandIrisWhatFeatures extends MortarCommand {
    public CommandIrisWhatFeatures() {
        super("features", "nf", "f");
        setDescription("Get the noise feature data in chunk.");
        requiresPermission(Iris.perm.studio);
        setCategory("Wut");
    }

    @Override
    public void addTabOptions(VolmitSender sender, String[] args, KList<String> list) {

    }

    @Override
    public boolean handle(VolmitSender sender, String[] args) {
        if (sender.isPlayer()) {
            BlockData bd;
            Player p = sender.player();
            Chunk c = p.getLocation().getChunk();

            if (IrisToolbelt.isIrisWorld(c.getWorld())) {
                int m = 1;
                for (IrisFeaturePositional i : ((Engine) IrisToolbelt.access(c.getWorld()).getEngine()).getEngineParallax().getFeaturesInChunk(c)) {
                    sender.sendMessage("#" + m++ + " " + new JSONObject(new Gson().toJson(i)).toString(4));
                }
            } else {
                sender.sendMessage("Iris worlds only.");
            }
        } else {
            sender.sendMessage("Players only.");
        }

        return true;
    }

    @Override
    protected String getArgsUsage() {
        return "";
    }
}
