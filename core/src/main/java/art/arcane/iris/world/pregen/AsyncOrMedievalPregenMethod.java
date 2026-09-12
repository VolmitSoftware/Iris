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

package art.arcane.iris.world.pregen;

import art.arcane.volmlib.util.mantle.runtime.Mantle;
import io.papermc.lib.PaperLib;
import org.bukkit.World;

public class AsyncOrMedievalPregenMethod implements PregeneratorMethod {
    private final PregeneratorMethod method;

    public AsyncOrMedievalPregenMethod(World world, int threads) {
        if (PaperLib.isPaper()) {
            method = new AsyncPregenMethod(world, threads);
        } else {
            method = new MedievalPregenMethod(world);
        }
    }

    private AsyncOrMedievalPregenMethod(PregeneratorMethod method) {
        this.method = method;
    }

    public static AsyncOrMedievalPregenMethod strictSerial(World world) {
        if (!PaperLib.isPaper()) {
            throw new UnsupportedOperationException("Strict serial pregeneration requires Paper or a Paper-compatible server.");
        }

        return new AsyncOrMedievalPregenMethod(AsyncPregenMethod.strictSerial(world));
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
    public void onRegionBounds(int minRegionX, int minRegionZ, int maxRegionX, int maxRegionZ) {
        method.onRegionBounds(minRegionX, minRegionZ, maxRegionX, maxRegionZ);
    }

    @Override
    public void onPregenStart(int centerBlockX, int centerBlockZ) {
        method.onPregenStart(centerBlockX, centerBlockZ);
    }

    @Override
    public void onRegionSubmitted(int regionX, int regionZ) {
        method.onRegionSubmitted(regionX, regionZ);
    }

    @Override
    public Mantle getMantle() {
        return method.getMantle();
    }
}
