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

package com.volmit.iris.util.mantle;

import com.volmit.iris.Iris;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.matter.IrisMatter;
import com.volmit.iris.util.matter.Matter;
import com.volmit.iris.util.matter.MatterSlice;
import com.volmit.iris.util.matter.Sliced;

import java.io.DataInputStream;
import java.io.IOException;

public class MantleMatter extends IrisMatter
{
    protected static final KMap<Class<?>, MatterSlice<?>> slicers = buildSlicers();

    public MantleMatter(int width, int height, int depth) {
        super(width, height, depth);
    }

    public static MantleMatter read(DataInputStream din) throws IOException, ClassNotFoundException {
        return (MantleMatter) Matter.read(din, (b) -> new MantleMatter(b.getX(), b.getY(), b.getZ()));
    }

    @Override
    public <T> MatterSlice<T> createSlice(Class<T> type, Matter m) {
        MatterSlice<?> slice = slicers.get(type);

        if (slice == null) {
            return null;
        }

        try {
            return slice.getClass().getConstructor(int.class, int.class, int.class).newInstance(getWidth(), getHeight(), getDepth());
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return null;
    }

    private static KMap<Class<?>, MatterSlice<?>> buildSlicers() {
        KMap<Class<?>, MatterSlice<?>> c = new KMap<>();
        for (Object i : Iris.initialize("com.volmit.iris.util.mantle.slices", Sliced.class)) {
            MatterSlice<?> s = (MatterSlice<?>) i;
            c.put(s.getType(), s);
        }

        return c;
    }
}
