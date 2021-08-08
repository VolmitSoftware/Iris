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

package com.volmit.iris.util.context;

import com.volmit.iris.core.project.loader.IrisData;
import com.volmit.iris.engine.IrisComplex;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.scheduling.ChronoLatch;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class IrisContext {
    private static ChronoLatch cl = new ChronoLatch(60000);
    private static KMap<Thread, IrisContext> context = new KMap<>();
    private final Engine engine;

    public static IrisContext get() {
        return context.get(Thread.currentThread());
    }

    public static void touch(IrisContext c) {
        synchronized (context) {
            context.put(Thread.currentThread(), c);

            if (cl.flip()) {
                for (Thread i : context.k()) {
                    if (!i.isAlive()) {
                        context.remove(i);
                    }
                }
            }
        }
    }

    public void touch() {
        IrisContext.touch(this);
    }

    public IrisData getData() {
        return engine.getData();
    }

    public IrisComplex getComplex() {
        return engine.getComplex();
    }
}
