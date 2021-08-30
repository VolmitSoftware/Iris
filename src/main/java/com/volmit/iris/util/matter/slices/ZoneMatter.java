/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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

package com.volmit.iris.util.matter.slices;

import com.volmit.iris.engine.object.IrisFeaturePositional;
import com.volmit.iris.util.matter.Sliced;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

@Sliced
public class ZoneMatter extends RawMatter<IrisFeaturePositional> {
    public ZoneMatter() {
        this(1, 1, 1);
    }

    public ZoneMatter(int width, int height, int depth) {
        super(width, height, depth, IrisFeaturePositional.class);
    }

    @Override
    public void setRaw(int x, int y, int z, IrisFeaturePositional t) {
        for (int i = 0; i < getHeight(); i++) {
            if (get(x, i, z) == null) {
                super.setRaw(x, i, z, t);
                break;
            }
        }
    }

    @Override
    public void writeNode(IrisFeaturePositional b, DataOutputStream dos) throws IOException {
        b.write(dos);
    }

    @Override
    public IrisFeaturePositional readNode(DataInputStream din) throws IOException {
        return IrisFeaturePositional.read(din);
    }
}
