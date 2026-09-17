/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.engine.framework;

import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.volmlib.util.collection.KList;

public interface EnginePlatformHooks {
    /**
     * World-space piece bounds of every native structure that will generate inside the given XZ rect. Platforms
     * without native structures return no volumes, which keeps the object veto free on those platforms.
     */
    default KList<NativeStructureVolume> nativeStructureVolumes(Engine engine, int minX, int minZ, int maxX, int maxZ) {
        return NativeStructureVolume.NONE;
    }

    default void refreshWorkspace(Engine engine) {
    }

    default void refreshDatapackWorkspace(Engine engine) {
    }

    default void reloadDatapacks(Engine engine) {
    }

    default void fireHotloadEvent(Engine engine) {
    }

    default void validateDimensionHotload(Engine engine, IrisDimension replacement) {
    }

    default void applyWorldBoundary(Engine engine) {
    }

    default boolean isPregeneratorActive(Engine engine) {
        return false;
    }

    default void shutdownPregenerator(Engine engine) {
    }

    default boolean shouldDisableChunkContextCache(Engine engine) {
        return false;
    }

    default boolean shouldSkipMantleCleanup(Engine engine) {
        return false;
    }

    default boolean shouldSkipMantleMarkerRead(Engine engine, int chunkX, int chunkZ) {
        return false;
    }

    default boolean shouldBypassMantleStages(Engine engine) {
        return false;
    }
}
