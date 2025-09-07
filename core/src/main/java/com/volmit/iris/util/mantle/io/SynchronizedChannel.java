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

package com.volmit.iris.util.mantle.io;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.concurrent.Semaphore;

public class SynchronizedChannel implements Closeable {
    private final FileChannel channel;
    private final Semaphore lock;
    private transient boolean closed;

    SynchronizedChannel(FileChannel channel, Semaphore lock) {
        this.channel = channel;
        this.lock = lock;
    }

    public InputStream read() throws IOException {
        if (closed) throw new IOException("Channel is closed!");
        return DelegateStream.read(channel);
    }

    public OutputStream write() throws IOException {
        if (closed) throw new IOException("Channel is closed!");
        return DelegateStream.write(channel);
    }

    @Override
    public void close() throws IOException {
        if (closed) return;
        closed = true;
        lock.release();
    }
}
