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

package com.volmit.iris.core.command.world;

import com.volmit.iris.Iris;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.math.Spiraler;
import com.volmit.iris.util.plugin.MortarCommand;
import com.volmit.iris.util.plugin.VolmitSender;
import com.volmit.iris.util.scheduling.J;

import java.util.concurrent.atomic.AtomicInteger;

public class CommandIrisFix extends MortarCommand {
    public CommandIrisFix() {
        super("fix");
        requiresPermission(Iris.perm.studio);
        setDescription("Fix nearby chunks");
        setCategory("Studio");
    }

    @Override
    public void addTabOptions(VolmitSender sender, String[] args, KList<String> list) {

    }

    @Override
    public boolean handle(VolmitSender sender, String[] args) {
        try {
            Engine a = IrisToolbelt.access(sender.player().getWorld()).getEngine();

            int viewDistance = args.length > 0 ? Integer.parseInt(args[0]) : -1;
            if (viewDistance <= 1) {
                J.a(() -> {
                    int fixed = a.getEngineParallax().repairChunk(sender.player().getLocation().getChunk());
                    sender.sendMessage("Fixed " + Form.f(fixed) + " blocks!");
                });
            } else {
                AtomicInteger v = new AtomicInteger();
                J.a(() -> {
                    new Spiraler(viewDistance, viewDistance, (x, z) -> v.set(v.get() + a.getEngineParallax().repairChunk(sender.player().getWorld().getChunkAt(x, z)))).drain();
                    sender.sendMessage("Fixed " + Form.f(v.get()) + " blocks in " + (viewDistance * viewDistance) + " chunks!");
                });
            }
        } catch (Throwable e) {
            Iris.reportError(e);
            sender.sendMessage("Not a valid Iris World (or bad argument)");
        }

        return true;
    }

    @Override
    protected String getArgsUsage() {
        return "[view-distance]";
    }
}
