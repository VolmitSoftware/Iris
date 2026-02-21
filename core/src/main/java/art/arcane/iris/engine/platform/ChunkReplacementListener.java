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

package art.arcane.iris.engine.platform;

public interface ChunkReplacementListener {
    ChunkReplacementListener NO_OP = new ChunkReplacementListener() {
    };

    default void onPhase(String phase, int chunkX, int chunkZ, long timestampMs) {
    }

    default void onOverlay(int chunkX, int chunkZ, int appliedBlocks, int objectKeys, long timestampMs) {
    }

    default void onFailurePhase(String phase, int chunkX, int chunkZ, Throwable error, long timestampMs) {
    }
}
