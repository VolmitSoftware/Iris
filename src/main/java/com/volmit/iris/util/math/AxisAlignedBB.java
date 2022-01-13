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

import com.volmit.iris.engine.object.IrisPosition;
import com.volmit.iris.util.data.Cuboid;
import org.bukkit.World;
import org.bukkit.util.BlockVector;

public class AxisAlignedBB {
    private final double xa;
    private final double xb;
    private final double ya;
    private final double yb;
    private final double za;
    private final double zb;

    public AxisAlignedBB(double xa, double xb, double ya, double yb, double za, double zb) {
        this.xa = xa;
        this.xb = xb;
        this.ya = ya;
        this.yb = yb;
        this.za = za;
        this.zb = zb;
    }

    public AxisAlignedBB(AlignedPoint a, AlignedPoint b) {
        this(a.getX(), b.getX(), a.getY(), b.getY(), a.getZ(), b.getZ());
    }

    public AxisAlignedBB(IrisPosition a, IrisPosition b) {
        this(a.getX(), b.getX(), a.getY(), b.getY(), a.getZ(), b.getZ());
    }

    public AxisAlignedBB shifted(IrisPosition p) {
        return shifted(p.getX(), p.getY(), p.getZ());
    }

    public AxisAlignedBB shifted(double x, double y, double z) {
        return new AxisAlignedBB(min().add(new IrisPosition((int) x, (int) y, (int) z)), max().add(new IrisPosition((int) x, (int) y, (int) z)));
    }

    public boolean contains(AlignedPoint p) {
        return p.getX() >= xa && p.getX() <= xb && p.getY() >= ya && p.getZ() <= yb && p.getZ() >= za && p.getZ() <= zb;
    }

    public boolean contains(IrisPosition p) {
        return p.getX() >= xa && p.getX() <= xb && p.getY() >= ya && p.getZ() <= yb && p.getZ() >= za && p.getZ() <= zb;
    }

    public boolean intersects(AxisAlignedBB s) {
        return this.xb >= s.xa && this.yb >= s.ya && this.zb >= s.za && s.xb >= this.xa && s.yb >= this.ya && s.zb >= this.za;
    }

    public IrisPosition max() {
        return new IrisPosition((int) xb, (int) yb, (int) zb);
    }


    public BlockVector maxbv() {
        return new BlockVector((int) xb, (int) yb, (int) zb);
    }

    public IrisPosition min() {
        return new IrisPosition((int) xa, (int) ya, (int) za);
    }

    public BlockVector minbv() {
        return new BlockVector((int) xa, (int) ya, (int) za);
    }

    public Cuboid toCuboid(World world) {
        return new Cuboid(min().toLocation(world), max().toLocation(world));
    }
}