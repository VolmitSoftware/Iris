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

package com.volmit.iris.core.pregenerator;

import com.volmit.iris.util.mantle.Mantle;

/**
 * Represents something that is capable of generating in chunks or regions, or both
 */
public interface PregeneratorMethod {
    /**
     * This is called before any generate methods are called. Setup your generator here
     */
    void init();

    /**
     * This is called after the pregenerator is done. Save your work and stop threads
     */
    void close();

    /**
     * This is called every X amount of chunks or regions. Save work,
     * but no need to save all of it. At the end, close() will still be called.
     */
    void save();

    /**
     * Return true if regions can be generated
     *
     * @param x the x region
     * @param z the z region
     * @return true if they can be
     */
    boolean supportsRegions(int x, int z, PregenListener listener);

    /**
     * Return the name of the method being used
     *
     * @param x the x region
     * @param z the z region
     * @return the name
     */
    String getMethod(int x, int z);

    /**
     * Called to generate a region. Execute sync, if multicore internally, wait
     * for the task to complete
     *
     * @param x        the x
     * @param z        the z
     * @param listener signal chunks generating & generated. Parallel capable.
     */
    void generateRegion(int x, int z, PregenListener listener);

    /**
     * Called to generate a chunk. You can go async so long as save will wait on the threads to finish
     *
     * @param x the x
     * @param z the z
     */
    void generateChunk(int x, int z, PregenListener listener);

    Mantle getMantle();
}
