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

package com.volmit.iris.util.hunk.io;

import com.volmit.iris.util.data.IOAdapter;
import com.volmit.iris.util.function.Function3;
import com.volmit.iris.util.hunk.Hunk;
import com.volmit.iris.util.io.CustomOutputStream;
import com.volmit.iris.util.oldnbt.ByteArrayTag;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public interface HunkIOAdapter<T> extends IOAdapter<T> {
    void write(Hunk<T> t, OutputStream out) throws IOException;

    Hunk<T> read(Function3<Integer, Integer, Integer, Hunk<T>> factory, InputStream in) throws IOException;

    default void write(Hunk<T> t, File f) throws IOException {
        f.getParentFile().mkdirs();
        FileOutputStream fos = new FileOutputStream(f);
        GZIPOutputStream gzo = new CustomOutputStream(fos, 6);
        write(t, gzo);
    }

    default Hunk<T> read(Function3<Integer, Integer, Integer, Hunk<T>> factory, File f) throws IOException {
        return read(factory, new GZIPInputStream(new FileInputStream(f)));
    }

    default Hunk<T> read(Function3<Integer, Integer, Integer, Hunk<T>> factory, ByteArrayTag f) throws IOException {
        return read(factory, new ByteArrayInputStream(f.getValue()));
    }

    default ByteArrayTag writeByteArrayTag(Hunk<T> tHunk, String name) throws IOException {
        ByteArrayOutputStream boas = new ByteArrayOutputStream();
        write(tHunk, boas);
        return new ByteArrayTag(name, boas.toByteArray());
    }
}
