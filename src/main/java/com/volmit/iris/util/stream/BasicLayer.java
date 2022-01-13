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

package com.volmit.iris.util.stream;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BasicLayer implements ProceduralLayer {
    private final long seed;
    private final double zoom;
    private final double offsetX;
    private final double offsetY;
    private final double offsetZ;

    public BasicLayer(long seed, double zoom) {
        this(seed, zoom, 0D, 0D, 0D);
    }

    public BasicLayer(long seed) {
        this(seed, 1D);
    }

    public BasicLayer() {
        this(1337);
    }
}
