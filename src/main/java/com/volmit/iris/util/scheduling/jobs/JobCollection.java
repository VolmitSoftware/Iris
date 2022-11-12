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

import com.volmit.iris.util.collection.KList;

public class JobCollection implements Job {
    private final String name;
    private final KList<Job> jobs;
    private String status;

    public JobCollection(String name, Job... jobs) {
        this(name, new KList<>(jobs));
    }

    public JobCollection(String name, KList<Job> jobs) {
        this.name = name;
        status = null;
        this.jobs = new KList<>(jobs);
    }

    @Override
    public String getName() {
        return status == null ? name : (name + " 》" + status);
    }

    @Override
    public void execute() {
        for (Job i : jobs) {
            status = i.getName();
            i.execute();
        }

        status = null;
    }

    @Override
    public void completeWork() {

    }

    @Override
    public int getTotalWork() {
        return jobs.stream().mapToInt(Job::getTotalWork).sum();
    }

    @Override
    public int getWorkCompleted() {
        return jobs.stream().mapToInt(Job::getWorkCompleted).sum();
    }
}
