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

import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.math.Position2;
import com.volmit.iris.util.math.Spiraled;
import com.volmit.iris.util.math.Spiraler;
import lombok.Builder;
import lombok.Data;

import java.util.Comparator;

@Builder
@Data
public class PregenTask {
    private static final Position2 ZERO = new Position2(0, 0);
    private static final KMap<Position2, KList<Position2>> ORDERS = new KMap<>();

    @Builder.Default
    private final boolean gui = false;
    @Builder.Default
    private final Position2 center = new Position2(0, 0);
    @Builder.Default
    private final int radiusX = 1;
    @Builder.Default
    private final int radiusZ = 1;

    private final Bounds bounds = new Bounds();

    protected PregenTask(boolean gui, Position2 center, int radiusX, int radiusZ) {
        this.gui = gui;
        this.center = new ProxiedPos(center);
        this.radiusX = radiusX;
        this.radiusZ = radiusZ;
        bounds.update();
    }

    public static void iterateRegion(int xr, int zr, Spiraled s, Position2 pull) {
        for (Position2 i : ORDERS.computeIfAbsent(pull, PregenTask::computeOrder)) {
            s.on(i.getX() + (xr << 5), i.getZ() + (zr << 5));
        }
    }

    public static void iterateRegion(int xr, int zr, Spiraled s) {
        iterateRegion(xr, zr, s, new Position2(-(xr << 5), -(zr << 5)));
    }

    private static KList<Position2> computeOrder(Position2 pull) {
        KList<Position2> p = new KList<>();
        new Spiraler(33, 33, (x, z) -> {
            int xx = (x + 15);
            int zz = (z + 15);
            if (xx < 0 || xx > 31 || zz < 0 || zz > 31) {
                return;
            }

            p.add(new Position2(xx, zz));
        }).drain();
        p.sort(Comparator.comparing((i) -> i.distance(pull)));

        return p;
    }

    public void iterateRegions(Spiraled s) {
        var bound = bounds.region();
        new Spiraler(bound.sizeX, bound.sizeZ, ((x, z) -> {
            if (bound.check(x, z)) s.on(x, z);
        })).setOffset(center.getX() >> 9, center.getZ() >> 9).drain();
    }

    public void iterateChunks(int rX, int rZ, Spiraled s) {
        var bound = bounds.chunk();
        iterateRegion(rX, rZ, ((x, z) -> {
            if (bound.check(x, z)) s.on(x, z);
        }));
    }

    public void iterateAllChunks(Spiraled s) {
        iterateRegions(((rX, rZ) -> iterateChunks(rX, rZ, s)));
    }

    private class Bounds {
        private Bound chunk = null;
        private Bound region = null;

        public void update() {
            int maxX = center.getX() + radiusX;
            int maxZ = center.getZ() + radiusZ;
            int minX = center.getX() - radiusX;
            int minZ = center.getZ() - radiusZ;

            chunk = new Bound(minX >> 4, minZ >> 4, Math.ceilDiv(maxX, 16), Math.ceilDiv(maxZ, 16));
            region = new Bound(minX >> 9, minZ >> 9, Math.ceilDiv(maxX, 512), Math.ceilDiv(maxZ, 512));
        }

        public Bound chunk() {
            if (chunk == null) update();
            return chunk;
        }

        public Bound region() {
            if (region == null) update();
            return region;
        }
    }

    private record Bound(int minX, int maxX, int minZ, int maxZ, int sizeX, int sizeZ) {
        private Bound(int minX, int minZ, int maxX, int maxZ) {
            this(minX, maxX, minZ, maxZ, maxZ - minZ + 1, maxZ - minZ + 1);
        }

        boolean check(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }
    }

    private static class ProxiedPos extends Position2 {
        public ProxiedPos(Position2 p) {
            super(p.getX(), p.getZ());
        }

        @Override
        public void setX(int x) {
            throw new IllegalStateException("This Position2 may not be modified");
        }

        @Override
        public void setZ(int z) {
            throw new IllegalStateException("This Position2 may not be modified");
        }
    }
}
