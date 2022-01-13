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

public interface PregenListener {
    void onTick(double chunksPerSecond, double chunksPerMinute, double regionsPerMinute, double percent, int generated, int totalChunks, int chunksRemaining, long eta, long elapsed, String method);

    void onChunkGenerating(int x, int z);

    void onChunkGenerated(int x, int z);

    void onRegionGenerated(int x, int z);

    void onRegionGenerating(int x, int z);

    void onChunkCleaned(int x, int z);

    void onRegionSkipped(int x, int z);

    void onNetworkStarted(int x, int z);

    void onNetworkFailed(int x, int z);

    void onNetworkReclaim(int revert);

    void onNetworkGeneratedChunk(int x, int z);

    void onNetworkDownloaded(int x, int z);

    void onClose();

    void onSaving();

    void onChunkExistsInRegionGen(int x, int z);
}
