/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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

package art.arcane.iris.platform.bukkit;

import art.arcane.volmlib.nativelib.terrain.NativeBiome;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

/**
 * Bukkit adapter for a neutral world view backed by org.bukkit.World.
 */
public final class BukkitWorld implements NativeWorld {
    private final World world;

    public BukkitWorld(World world) {
        this.world = world;
    }

    @Override
    public String name() {
        return world.getName();
    }

    @Override
    public long seed() {
        return world.getSeed();
    }

    @Override
    public int minHeight() {
        return world.getMinHeight();
    }

    @Override
    public int maxHeight() {
        return world.getMaxHeight();
    }

    @Override
    public NativeBlockState getBlock(int x, int y, int z) {
        return BukkitBlockState.of(world.getBlockAt(x, y, z).getBlockData());
    }

    @Override
    public void setBlock(int x, int y, int z, NativeBlockState block, int flags) {
        world.getBlockAt(x, y, z).setBlockData((BlockData) block.nativeHandle(), (flags & 1) != 0);
    }

    @Override
    public NativeBiome getBiome(int x, int y, int z) {
        return BukkitBiome.of(world.getBiome(x, y, z));
    }

    @Override
    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        return world.isChunkLoaded(chunkX, chunkZ);
    }

    @Override
    public long getTime() {
        return world.getTime();
    }

    @Override
    public boolean isStorming() {
        return world.hasStorm();
    }

    @Override
    public boolean isThundering() {
        return world.isThundering();
    }

    @Override
    public Object nativeHandle() {
        return world;
    }
}
