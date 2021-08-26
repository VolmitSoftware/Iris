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
import com.volmit.iris.engine.platform.PlatformChunkGenerator;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.parallel.MultiBurst;
import com.volmit.iris.util.plugin.MortarCommand;
import com.volmit.iris.util.plugin.VolmitSender;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.jobs.Job;
import com.volmit.iris.util.scheduling.jobs.JobCollection;
import com.volmit.iris.util.scheduling.jobs.QueueJob;
import org.bukkit.Chunk;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class CommandIrisRegen extends MortarCommand {
    public CommandIrisRegen() {
        super("regen");
        requiresPermission(Iris.perm.studio);
        setDescription("Regenerate nearby chunks");
        setCategory("Studio");
    }

    @Override
    public void addTabOptions(VolmitSender sender, String[] args, KList<String> list) {

    }

    @Override
    public boolean handle(VolmitSender sender, String[] args) {
        if (!sender.isPlayer())
        {
            sender.sendMessage("Must be in an iris world.");
        }

        if(IrisToolbelt.isIrisWorld(sender.player().getWorld()))
        {
            PlatformChunkGenerator plat = IrisToolbelt.access(sender.player().getWorld());
            Engine engine = plat.getEngine();
            try
            {
                int vd = Integer.parseInt(args[0]);
                int rg = 0;
                Chunk cx = sender.player().getLocation().getChunk();
                KList<Runnable> js = new KList<>();
                BurstExecutor b = MultiBurst.burst.burst();
                b.setMulticore(false);
                int rad = engine.getMantle().getRealRadius();
                for(int i = -(vd+rad); i <= vd+rad; i++) {
                    for (int j = -(vd+rad); j <= vd+rad; j++) {
                        engine.getMantle().getMantle().deleteChunk(i + cx.getX(), j + cx.getZ());
                    }
                }

                for(int i = -vd; i <= vd; i++)
                {
                    for(int j = -vd; j <= vd; j++)
                    {
                        int finalJ = j;
                        int finalI = i;
                        b.queue(() -> plat.injectChunkReplacement(sender.player().getWorld(), finalI + cx.getX(), finalJ + cx.getZ(), (f) -> {
                            synchronized (js)
                            {
                                js.add(f);
                            }
                        }));
                    }
                }

                b.complete();
                sender.sendMessage("Regenerating " + Form.f(js.size()) + " Sections");
                QueueJob<Runnable> r = new QueueJob<>() {
                    final KList<Future<?>> futures = new KList<>();

                    @Override
                    public void execute(Runnable runnable) {
                        futures.add(J.sfut(runnable));

                        if(futures.size() > 64)
                        {
                            while(futures.isNotEmpty())
                            {
                                try {
                                    futures.remove(0).get();
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                } catch (ExecutionException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }

                    @Override
                    public String getName() {
                        return "Regenerating";
                    }
                };
                r.queue(js);
                r.execute(sender);
            }

            catch(Throwable e)
            {
                sender.sendMessage("Unable to parse view-distance");
            }
        }

        return true;
    }

    @Override
    protected String getArgsUsage() {
        return "[view-distance]";
    }
}
