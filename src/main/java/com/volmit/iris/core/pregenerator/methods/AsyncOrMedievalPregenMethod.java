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

package com.volmit.iris.core.pregenerator.methods;

import com.volmit.iris.core.pregenerator.PregenListener;
import com.volmit.iris.core.pregenerator.PregeneratorMethod;
import com.volmit.iris.util.mantle.Mantle;
import io.papermc.lib.PaperLib;
import org.bukkit.World;

public class AsyncOrMedievalPregenMethod implements PregeneratorMethod {
    private final PregeneratorMethod method;

    public AsyncOrMedievalPregenMethod(World world, int threads) {
        method = PaperLib.isPaper() ? new AsyncPregenMethod(world, threads) : new MedievalPregenMethod(world);
    }

    @Override
    public void init() {
        method.init();
    }

    @Override
    public void close() {
        method.close();
    }

    @Override
    public void save() {
        method.save();
    }

    @Override
    public String getMethod(int x, int z) {
        return method.getMethod(x, z);
    }

    @Override
    public boolean supportsRegions(int x, int z, PregenListener listener) {
        return false;
    }

    @Override
    public void generateRegion(int x, int z, PregenListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void generateChunk(int x, int z, PregenListener listener) {
        method.generateChunk(x, z, listener);
    }

    @Override
    public Mantle getMantle() {
        return method.getMantle();
    }
}
