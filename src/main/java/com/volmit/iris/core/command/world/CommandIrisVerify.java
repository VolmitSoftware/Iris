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
import com.volmit.iris.util.nbt.mca.Chunk;
import com.volmit.iris.util.nbt.mca.MCAFile;
import com.volmit.iris.util.nbt.mca.MCAUtil;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.parallel.MultiBurst;
import com.volmit.iris.util.plugin.MortarCommand;
import com.volmit.iris.util.plugin.VolmitSender;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public class CommandIrisVerify extends MortarCommand {
    public CommandIrisVerify() {
        super("verifymca");
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
            File folder = a.getTarget().getWorld().worldFolder();
            File r = new File(folder, "region");
            BurstExecutor e = MultiBurst.burst.burst(r.listFiles().length);
            AtomicInteger f = new AtomicInteger(0);
            for (File i : r.listFiles()) {
                e.queue(() -> {
                    MCAFile file = null;
                    try {
                        file = MCAUtil.read(i);
                        int rx = Integer.valueOf(i.getName().split("\\Q.\\E")[1]);
                        int rz = Integer.valueOf(i.getName().split("\\Q.\\E")[2]);
                        for (int j = 0; j < 32; j++) {
                            for (int k = 0; k < 32; k++) {
                                f.incrementAndGet();
                                Chunk c = file.getChunk(j, k);
                                if (c == null) {
                                    sender.sendMessage("Found Missing Chunk " + i.getName() + ", chunk #" + j + "," + k + " (see " + (((rx << 5) << 4) + (j << 4)) + "," + (((rz << 5) << 4) + (k << 4)));
                                } else if (c.sectionCount() == 0) {
                                    sender.sendMessage("Found Missing Chunk (valid, but 0 sections) " + i.getName() + ", chunk #" + j + "," + k + " (see " + (((rx << 5) << 4) + (j << 4)) + "," + (((rz << 5) << 4) + (k << 4)));
                                }
                            }
                        }
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                        sender.sendMessage("Error loading region " + i.getName());
                    }
                });
            }

            e.complete();
            sender.sendMessage("Done! Checked " + f.get() + " chunks");
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
