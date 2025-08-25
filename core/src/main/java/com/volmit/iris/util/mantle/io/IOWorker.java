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

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.io.CountingDataInputStream;
import com.volmit.iris.util.mantle.TectonicPlate;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.Objects;
import java.util.Set;

public class IOWorker {
    private static final Set<OpenOption> OPTIONS = Set.of(StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.SYNC);
    private static final int MAX_CACHE_SIZE = 128;

    private final Path root;
    private final File tmp;
    private final int worldHeight;

    private final Object2ObjectLinkedOpenHashMap<String, Holder> cache = new Object2ObjectLinkedOpenHashMap<>();

    public IOWorker(File root, int worldHeight) {
        this.root = root.toPath();
        this.tmp = new File(root, ".tmp");
        this.worldHeight = worldHeight;
    }

    public TectonicPlate read(final String name) throws IOException {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        try (var channel = getChannel(name)) {
            var raw = channel.read();
            var lz4 = new LZ4BlockInputStream(raw);
            var buffered = new BufferedInputStream(lz4);
            try (var in = CountingDataInputStream.wrap(buffered)) {
                return new TectonicPlate(worldHeight, in, name.startsWith("pv."));
            } finally {
                if (TectonicPlate.hasError() && IrisSettings.get().getGeneral().isDumpMantleOnError()) {
                    File dump = Iris.instance.getDataFolder("dump", name + ".bin");
                    Files.copy(new LZ4BlockInputStream(channel.read()), dump.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Iris.debug("Read Tectonic Plate " + C.DARK_GREEN + name + C.RED + " in " + Form.duration(p.getMilliseconds(), 2));
                }
            }
        }
    }

    public void write(final String name, final TectonicPlate plate) throws IOException {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        try (var channel = getChannel(name)) {
            tmp.mkdirs();
            File file = File.createTempFile("iris", ".bin", tmp);
            try {
                try (var tmp = new DataOutputStream(new LZ4BlockOutputStream(new FileOutputStream(file)))) {
                    plate.write(tmp);
                }

                try (var out = channel.write()) {
                    Files.copy(file.toPath(), out);
                    out.flush();
                }
            } finally {
                file.delete();
            }
        }
        Iris.debug("Saved Tectonic Plate " + C.DARK_GREEN + name + C.RED + " in " + Form.duration(p.getMilliseconds(), 2));
    }

    public void close() throws IOException {
        synchronized (cache) {
            for (Holder h : cache.values()) {
                h.close();
            }

            cache.clear();
        }
    }

    private SynchronizedChannel getChannel(final String name) throws IOException {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        try {
            synchronized (cache) {
                Holder holder = cache.getAndMoveToFirst(name);
                if (holder != null) {
                    var channel = holder.acquire();
                    if (channel != null) {
                        return channel;
                    }
                }

                if (cache.size() >= MAX_CACHE_SIZE) {
                    var last = cache.removeLast();
                    last.close();
                }


                holder = new Holder(FileChannel.open(root.resolve(name), OPTIONS));
                cache.putAndMoveToFirst(name, holder);
                return Objects.requireNonNull(holder.acquire());
            }
        } finally {
            Iris.debug("Acquired Channel for " + C.DARK_GREEN + name + C.RED + " in " + Form.duration(p.getMilliseconds(), 2));
        }
    }
}
