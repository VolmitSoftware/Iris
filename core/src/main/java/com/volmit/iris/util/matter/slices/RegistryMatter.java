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

package com.volmit.iris.util.matter.slices;

import com.volmit.iris.core.loader.IrisRegistrant;
import com.volmit.iris.util.context.IrisContext;
import com.volmit.iris.util.data.palette.Palette;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class RegistryMatter<T extends IrisRegistrant> extends RawMatter<T> {
    public RegistryMatter(int width, int height, int depth, Class<T> c, T e) {
        super(width, height, depth, c);
    }

    @Override
    public Palette<T> getGlobalPalette() {
        return null;
    }

    @Override
    public void writeNode(T b, DataOutputStream dos) throws IOException {
        dos.writeUTF(b.getLoadKey());
    }

    @Override
    public T readNode(DataInputStream din) throws IOException {
        IrisContext context = IrisContext.get();
        return (T) context.getData().getLoaders().get(getType()).load(din.readUTF());
    }
}
