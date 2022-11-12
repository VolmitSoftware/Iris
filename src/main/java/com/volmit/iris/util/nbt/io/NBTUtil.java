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

package com.volmit.iris.util.nbt.io;

import com.volmit.iris.util.nbt.tag.Tag;

import java.io.*;
import java.util.zip.GZIPInputStream;

public final class NBTUtil {

    private NBTUtil() {
    }

    public static void write(NamedTag tag, File file, boolean compressed) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            new NBTSerializer(compressed).toStream(tag, fos);
        }
    }

    public static void write(NamedTag tag, OutputStream out, boolean compressed) throws IOException {
        new NBTSerializer(compressed).toStream(tag, out);
    }

    public static void write(Tag<?> tag, OutputStream out, boolean compressed) throws IOException {
        write(new NamedTag(null, tag), out, compressed);
    }

    public static void write(NamedTag tag, String file, boolean compressed) throws IOException {
        write(tag, new File(file), compressed);
    }

    public static void write(NamedTag tag, File file) throws IOException {
        write(tag, file, true);
    }

    public static void write(NamedTag tag, String file) throws IOException {
        write(tag, new File(file), true);
    }

    public static void write(Tag<?> tag, File file, boolean compressed) throws IOException {
        write(new NamedTag(null, tag), file, compressed);
    }

    public static void write(Tag<?> tag, String file, boolean compressed) throws IOException {
        write(new NamedTag(null, tag), new File(file), compressed);
    }

    public static void write(Tag<?> tag, File file) throws IOException {
        write(new NamedTag(null, tag), file, true);
    }

    public static void write(Tag<?> tag, String file) throws IOException {
        write(new NamedTag(null, tag), new File(file), true);
    }

    public static NamedTag read(File file, boolean compressed) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            return new NBTDeserializer(compressed).fromStream(fis);
        }
    }

    public static NamedTag read(InputStream in, boolean compressed) throws IOException {
        return new NBTDeserializer(compressed).fromStream(in);
    }

    public static NamedTag read(String file, boolean compressed) throws IOException {
        return read(new File(file), compressed);
    }

    public static NamedTag read(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            return new NBTDeserializer(false).fromStream(detectDecompression(fis));
        }
    }

    public static NamedTag read(String file) throws IOException {
        return read(new File(file));
    }

    private static InputStream detectDecompression(InputStream is) throws IOException {
        PushbackInputStream pbis = new PushbackInputStream(is, 2);
        int signature = (pbis.read() & 0xFF) + (pbis.read() << 8);
        pbis.unread(signature >> 8);
        pbis.unread(signature & 0xFF);
        if (signature == GZIPInputStream.GZIP_MAGIC) {
            return new GZIPInputStream(pbis);
        }
        return pbis;
    }
}
