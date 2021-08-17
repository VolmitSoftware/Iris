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

package com.volmit.iris.core.service;

import com.volmit.iris.Iris;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.parallel.MultiBurst;
import com.volmit.iris.util.plugin.IrisService;

import java.util.concurrent.ExecutorService;

public class ExecutionSVC implements IrisService
{
    private KList<Thread> threads = new KList<>();
    private KList<MultiBurst> bursts = new KList<>();
    private KList<ExecutorService> services = new KList<>();

    public void register(Thread t)
    {
        threads.add(t);
    }

    public void register(MultiBurst burst)
    {
        bursts.add(burst);
    }

    public void register(ExecutorService service)
    {
        services.add(service);
    }

    @Override
    public void onEnable() {

    }

    @Override
    public void onDisable() {
        for(Thread i : threads)
        {
            if(i.isAlive())
            {
                try
                {
                    i.interrupt();
                    Iris.info("Shutdown Thread " + i.getName());
                }

                catch(Throwable e)
                {

                }
            }
        }

        for(MultiBurst i : bursts)
        {
            try
            {
                i.shutdownNow();
                Iris.info("Shutdown Multiburst " + i);
            }

            catch(Throwable e)
            {

            }
        }

        for(ExecutorService i : services)
        {
            try
            {
                i.shutdownNow();
                Iris.info("Shutdown Executor Service " + i);
            }

            catch(Throwable e)
            {

            }
        }
    }
}
