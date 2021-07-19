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

package com.volmit.iris.core.pregenerator.methods;

import com.volmit.iris.core.pregenerator.PregenListener;
import com.volmit.iris.core.pregenerator.PregeneratorMethod;
import com.volmit.iris.engine.IrisWorlds;
import com.volmit.iris.engine.headless.HeadlessWorld;
import org.bukkit.World;

import java.io.File;

public class HybridPregenMethod implements PregeneratorMethod {
    private final PregeneratorMethod headless;
    private final PregeneratorMethod inWorld;
    private final World world;

    public HybridPregenMethod(World world, int threads)
    {
        this.world = world;
        headless = supportsHeadless(world)
                ? new HeadlessPregenMethod(HeadlessWorld.from(world)) : new DummyPregenMethod();
        inWorld = new PaperOrMedievalPregenMethod(world, threads);
    }

    private boolean supportsHeadless(World world) {
        return IrisWorlds.access(world) != null;
    }

    @Override
    public String getMethod(int x, int z) {
        return "Hybrid<" + ((supportsRegions(x, z) ? headless.getMethod(x, z) : inWorld.getMethod(x, z)) + ">");
    }

    @Override
    public void init() {
        headless.init();
        inWorld.init();
    }

    @Override
    public void close() {
        headless.close();
        inWorld.close();
    }

    @Override
    public void save() {
        headless.save();
        inWorld.save();
    }

    @Override
    public boolean supportsRegions(int x, int z) {
        if (headless instanceof DummyPregenMethod) {
            return false;
        }

        return !new File(world.getWorldFolder(), "region/r." + x + "." + z + ".mca").exists();
    }

    @Override
    public void generateRegion(int x, int z, PregenListener listener) {
        headless.generateRegion(x, z, listener);
    }

    @Override
    public void generateChunk(int x, int z) {
        inWorld.generateChunk(x, z);
    }
}
