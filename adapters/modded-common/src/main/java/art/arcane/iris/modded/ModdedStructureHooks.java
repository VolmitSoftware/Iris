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

package art.arcane.iris.modded;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.PlatformStructureHooks;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeStructureOperations;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.nativelib.terrain.JigsawSourceMetadata;
import java.util.List;

import java.util.function.Supplier;

public final class ModdedStructureHooks implements PlatformStructureHooks {
    /**
     * Hard ceiling on the chunk grid a single capture placement may touch. placeChunks loads every chunk in
     * the grid synchronously on the server thread, so a structure with a runaway bounding box (or maxSpan 0,
     * which disables the span check entirely) would otherwise stall the server for thousands of chunk loads.
     */
    private static final int MAX_PLACEMENT_CHUNKS = 1024;

    private final NativeStructureOperations operations;

    public ModdedStructureHooks(Supplier<NativeModdedServer> server) {
        operations = new NativeStructureOperations(new NativeStructureOperations.Options(
                server, MAX_PLACEMENT_CHUNKS, IrisLogging::reportError, IrisLogging::warn));
    }

    @Override
    public List<String> structureKeys() {
        return operations.structureKeys();
    }

    @Override
    public List<String> jigsawStructureKeys() {
        return operations.jigsawStructureKeys();
    }

    @Override
    public List<String> templatePoolKeys() {
        return operations.templatePoolKeys();
    }

    @Override
    public JigsawSourceMetadata jigsawSourceMetadata(String structureKey) {
        return operations.jigsawSourceMetadata(structureKey);
    }

    @Override
    public int templatePoolHorizontalSpan(String templatePoolKey) {
        return operations.templatePoolHorizontalSpan(templatePoolKey);
    }

    @Override
    public int jigsawStartPoolHorizontalSpan(String structureKey, String templatePoolKey) {
        return operations.jigsawStartPoolHorizontalSpan(structureKey, templatePoolKey);
    }

    @Override
    public List<String> structureSetKeys() {
        return operations.structureSetKeys();
    }

    @Override
    public List<String> structureBiomeKeys(String structureKey) {
        return operations.structureBiomeKeys(structureKey);
    }

    @Override
    public List<String> objectFeatureKeys() {
        return operations.objectFeatureKeys();
    }

    @Override
    public List<String> reachableStructureKeys(NativeWorld world) {
        return operations.reachableStructureKeys(world);
    }

    @Override
    public List<String> possibleBiomeKeys(NativeWorld world) {
        return operations.possibleBiomeKeys(world);
    }

    @Override
    public boolean placeFeature(NativeWorld world, int x, int y, int z, String featureKey, long seed) {
        return operations.placeFeature(world, x, y, z, featureKey, seed);
    }

    @Override
    public int[] placeStructure(NativeWorld world, int chunkX, int chunkZ, String structureKey, long seed, int maxSpan) {
        return operations.placeStructure(world, chunkX, chunkZ, structureKey, seed, maxSpan);
    }

    @Override
    public boolean supportsStructurePlacement() {
        return operations.supportsStructurePlacement();
    }
}
