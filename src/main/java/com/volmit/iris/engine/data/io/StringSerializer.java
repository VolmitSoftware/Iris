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

package com.volmit.iris.engine.data.io;

import java.io.*;

public interface StringSerializer<T> extends Serializer<T> {

    void toWriter(T object, Writer writer) throws IOException;

    default String toString(T object) throws IOException {
        Writer writer = new StringWriter();
        toWriter(object, writer);
        writer.flush();
        return writer.toString();
    }

    @Override
    default void toStream(T object, OutputStream stream) throws IOException {
        Writer writer = new OutputStreamWriter(stream);
        toWriter(object, writer);
        writer.flush();
    }

    @Override
    default void toFile(T object, File file) throws IOException {
        try (Writer writer = new FileWriter(file)) {
            toWriter(object, writer);
        }
    }
}
