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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

/**
 * Represents a spiraler which can start from any point within Long.MAX_VALUE by Long.MAX_VALUE and iterate anywhere.
 */
@Data
@RequiredArgsConstructor
public class Spiral implements Iterable<Position2> {
    private final Position2 start;
    private final long max;

    @SuppressWarnings("ConstantConditions")
    public static Position2 next(Position2 p) {
        int x = p.getX();
        int z = p.getZ();
        int ax = Math.abs(x);
        int az = Math.abs(z);

        if (x == 0 && z == 0) {
            return p.add(1, 0);
        }

        if (ax == az) {
            if (x > 0 && z > 0) return left(p);
            else if (x < 0 && z > 0) return down(p);
            else if (x < 0 && z < 0) return right(p);
            else if (x > 0 && z < 0) return up(p);
        } else {
            if (x > z && ax > az) return up(p);
            else if (x < z && ax < az) return left(p);
            else if (x < z && ax > az) return down(p);
            else if (x > z && ax < az) return right(p);
        }

        return p;
    }

    public static Spiral from(Position2 p, long iterations) {
        return new Spiral(p, iterations);
    }

    private static Position2 down(Position2 p) {
        return p.add(0, -1);
    }

    private static Position2 up(Position2 p) {
        return p.add(0, 1);
    }

    private static Position2 left(Position2 p) {
        return p.add(-1, 0);
    }

    private static Position2 right(Position2 p) {
        return p.add(1, 0);
    }

    @NotNull
    @Override
    public Iterator<Position2> iterator() {
        return new SpiralIterator(this, 0, start);
    }

    @AllArgsConstructor
    static class SpiralIterator implements Iterator<Position2> {
        private final Spiral s;
        private long itr = 0;
        private Position2 cursor;

        @Override
        public boolean hasNext() {
            return itr < s.getMax();
        }

        @Override
        public Position2 next() {
            Position2 m = cursor;
            cursor = Spiral.next(cursor);
            itr++;
            return m;
        }
    }
}
