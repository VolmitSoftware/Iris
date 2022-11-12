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

package com.volmit.iris.util.math;

import lombok.Data;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Objects;

@Data
public class BlockPosition {
    //Magic numbers
    private static final int m1 = 1 + MathHelper.f(MathHelper.c(30000000));
    private static final int m2 = 64 - (m1 * 2);
    private static final long m3 = m1 + m2;
    private static final long m5 = (1L << m2) - 1L;
    private static final long m4 = (1L << m1) - 1L;
    private static final long m6 = (1L << m1) - 1L;
    private int x;
    private int y;
    private int z;

    public BlockPosition(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static long toLong(int x, int y, int z) {
        long var3 = 0L;
        var3 |= (x & m4) << m3;
        var3 |= (y & m5);
        var3 |= (z & m6) << m2;
        return var3;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }

    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }

        if (o instanceof BlockPosition ot) {

            return ot.x == x && ot.y == y && ot.z == z;
        }

        return false;
    }

    public int getChunkX() {
        return x >> 4;
    }

    public int getChunkZ() {
        return z >> 4;
    }

    public boolean is(int x, int z) {
        return this.x == x && this.z == z;
    }

    public boolean is(int x, int y, int z) {
        return this.x == x && this.y == y && this.z == z;
    }

    public long asLong() {
        return toLong(getX(), getY(), getZ());
    }

    public Block toBlock(World world) {
        return world.getBlockAt(x, y, z);
    }

    public BlockPosition add(int x, int y, int z) {
        return new BlockPosition(this.x + x, this.y + y, this.z + z);
    }

    public void min(BlockPosition i) {
        setX(Math.min(i.getX(), getX()));
        setY(Math.min(i.getY(), getY()));
        setZ(Math.min(i.getZ(), getZ()));
    }

    public void max(BlockPosition i) {
        setX(Math.max(i.getX(), getX()));
        setY(Math.max(i.getY(), getY()));
        setZ(Math.max(i.getZ(), getZ()));
    }
}
