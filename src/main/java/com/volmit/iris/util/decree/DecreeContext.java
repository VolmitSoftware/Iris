/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package com.volmit.iris.util.decree;

import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.plugin.VolmitSender;
import com.volmit.iris.util.scheduling.ChronoLatch;

public class DecreeContext {
    private static final ChronoLatch cl = new ChronoLatch(60000);
    private static final KMap<Thread, VolmitSender> context = new KMap<>();

    public static VolmitSender get() {
        return context.get(Thread.currentThread());
    }

    public static void touch(VolmitSender c) {
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
}
