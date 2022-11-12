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

package com.volmit.iris.util.nbt.mca;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;

public enum CompressionType {

    NONE(0, t -> t, t -> t),
    GZIP(1, GZIPOutputStream::new, GZIPInputStream::new),
    ZLIB(2, DeflaterOutputStream::new, InflaterInputStream::new);

    private final byte id;
    private final ExceptionFunction<OutputStream, ? extends OutputStream, IOException> compressor;
    private final ExceptionFunction<InputStream, ? extends InputStream, IOException> decompressor;

    CompressionType(int id,
                    ExceptionFunction<OutputStream, ? extends OutputStream, IOException> compressor,
                    ExceptionFunction<InputStream, ? extends InputStream, IOException> decompressor) {
        this.id = (byte) id;
        this.compressor = compressor;
        this.decompressor = decompressor;
    }

    public static CompressionType getFromID(byte id) {
        for (CompressionType c : CompressionType.values()) {
            if (c.id == id) {
                return c;
            }
        }
        return null;
    }

    public byte getID() {
        return id;
    }

    public OutputStream compress(OutputStream out) throws IOException {
        return compressor.accept(out);
    }

    public InputStream decompress(InputStream in) throws IOException {
        return decompressor.accept(in);
    }
}
