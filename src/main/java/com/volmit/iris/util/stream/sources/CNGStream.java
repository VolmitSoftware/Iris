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

package com.volmit.iris.util.stream.sources;

import com.volmit.iris.util.noise.CNG;
import com.volmit.iris.util.stream.BasicLayer;
import com.volmit.iris.util.stream.ProceduralStream;

public class CNGStream extends BasicLayer implements ProceduralStream<Double> {
    private final CNG cng;

    public CNGStream(CNG cng) {
        this.cng = cng;
    }

    public CNGStream(CNG cng, double zoom, double offsetX, double offsetY, double offsetZ) {
        super(1337, zoom, offsetX, offsetY, offsetZ);
        this.cng = cng;
    }

    public CNGStream(CNG cng, double zoom) {
        super(1337, zoom);
        this.cng = cng;
    }

    @Override
    public double toDouble(Double t) {
        return t;
    }

    @Override
    public Double fromDouble(double d) {
        return d;
    }

    @Override
    public ProceduralStream<Double> getTypedSource() {
        return null;
    }

    @Override
    public ProceduralStream<?> getSource() {
        return null;
    }

    @Override
    public Double get(double x, double z) {
        return cng.noise((x + getOffsetX()) / getZoom(), (z + getOffsetZ()) / getZoom());
    }

    @Override
    public Double get(double x, double y, double z) {
        return cng.noise((x + getOffsetX()) / getZoom(), (y + getOffsetY()) / getZoom(), (z + getOffsetZ()) * getZoom());
    }

}
