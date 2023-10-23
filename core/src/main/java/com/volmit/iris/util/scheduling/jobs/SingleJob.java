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

package com.volmit.iris.util.scheduling.jobs;

public class SingleJob implements Job {
    private final String name;
    private final Runnable runnable;
    private boolean done;

    public SingleJob(String name, Runnable runnable) {
        this.name = name;
        done = false;
        this.runnable = runnable;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void execute() {
        runnable.run();
        completeWork();
    }

    @Override
    public void completeWork() {
        done = true;
    }

    @Override
    public int getTotalWork() {
        return 1;
    }

    @Override
    public int getWorkCompleted() {
        return done ? 1 : 0;
    }
}
