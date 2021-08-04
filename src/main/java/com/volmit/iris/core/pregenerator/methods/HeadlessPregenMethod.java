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
import com.volmit.iris.engine.framework.headless.HeadlessGenerator;
import com.volmit.iris.engine.framework.headless.HeadlessWorld;
import lombok.Getter;

public class HeadlessPregenMethod implements PregeneratorMethod {
    private final HeadlessWorld world;

    @Getter
    private final HeadlessGenerator generator;

    public HeadlessPregenMethod(HeadlessWorld world) {
        this(world, world.generate());
    }

    public HeadlessPregenMethod(HeadlessWorld world, HeadlessGenerator generator) {
        this.world = world;
        this.generator = generator;
    }

    @Override
    public void init() {

    }

    @Override
    public void close() {
        generator.close();
    }

    @Override
    public void save() {
        generator.save();
    }

    @Override
    public boolean supportsRegions(int x, int z, PregenListener listener) {
        return true;
    }

    @Override
    public String getMethod(int x, int z) {
        return "Headless";
    }

    @Override
    public void generateRegion(int x, int z, PregenListener listener) {
        generator.generateRegion(x, z, listener);
    }

    @Override
    public void generateChunk(int x, int z, PregenListener listener) {
        throw new UnsupportedOperationException();
    }
}
