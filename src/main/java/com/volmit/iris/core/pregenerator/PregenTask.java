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
    private static final KList<Position2> ORDER_CENTER = computeChunkOrder();
    private static final KMap<Position2, KList<Position2>> ORDERS = new KMap<>();

    @Builder.Default
    private Position2 center = new Position2(0, 0);
    @Builder.Default
    private int width = 1;
    @Builder.Default
    private int height = 1;

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

    private static KList<Position2> computeChunkOrder() {
        Position2 center = new Position2(15, 15);
        KList<Position2> p = new KList<>();
        new Spiraler(33, 33, (x, z) -> {
            int xx = x + 15;
            int zz = z + 15;
            if (xx < 0 || xx > 31 || zz < 0 || zz > 31) {
                return;
            }

            p.add(new Position2(xx, zz));
        }).drain();
        p.sort(Comparator.comparing((i) -> i.distance(center)));
        return p;
    }

    public void iterateRegions(Spiraled s) {
        new Spiraler(getWidth() * 2, getHeight() * 2, s)
                .setOffset(center.getX(), center.getZ()).drain();
    }

    public void iterateAllChunks(Spiraled s) {
        new Spiraler(getWidth() * 2, getHeight() * 2, (x, z) -> iterateRegion(x, z, s))
                .setOffset(center.getX(), center.getZ()).drain();
    }
}
