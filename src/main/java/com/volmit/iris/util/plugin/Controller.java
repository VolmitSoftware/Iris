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

package com.volmit.iris.util.plugin;

import com.volmit.iris.Iris;

public abstract class Controller implements IController {
    private final String name;
    private int tickRate;

    public Controller() {
        name = getClass().getSimpleName().replaceAll("Controller", "") + " Controller";
        tickRate = -1;
    }

    protected void setTickRate(@SuppressWarnings("SameParameterValue") int rate) {
        this.tickRate = rate;
    }

    protected void disableTicking() {
        setTickRate(-1);
    }

    @Override
    public void l(Object l) {
        Iris.info("[" + getName() + "]: " + l);
    }

    @Override
    public void w(Object l) {
        Iris.warn("[" + getName() + "]: " + l);
    }

    @Override
    public void f(Object l) {
        Iris.error("[" + getName() + "]: " + l);
    }

    @Override
    public void v(Object l) {
        Iris.verbose("[" + getName() + "]: " + l);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public abstract void start();

    @Override
    public abstract void stop();

    @Override
    public abstract void tick();

    @Override
    public int getTickInterval() {
        return tickRate;
    }
}
