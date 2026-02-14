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

package art.arcane.iris.util.mantle.io;

import art.arcane.volmlib.util.mantle.io.IOWorkerCodecSupport;
import art.arcane.volmlib.util.mantle.io.IOWorkerRuntimeSupport;
import art.arcane.volmlib.util.mantle.io.IOWorkerSupport;
import art.arcane.iris.Iris;
import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.util.format.C;
import art.arcane.volmlib.util.format.Form;
import art.arcane.iris.util.mantle.TectonicPlate;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;
import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class IOWorker {
    private static final IOWorkerCodecSupport LZ4_CODEC = new IOWorkerCodecSupport() {
        @Override
        public InputStream decode(InputStream input) throws IOException {
            return new LZ4BlockInputStream(input);
        }

        @Override
        public OutputStream encode(OutputStream output) throws IOException {
            return new LZ4BlockOutputStream(output);
        }
    };

    private final int worldHeight;
    private final IOWorkerSupport support;
    private final IOWorkerRuntimeSupport runtime;

    public IOWorker(File root, int worldHeight) {
        this.worldHeight = worldHeight;
        this.support = new IOWorkerSupport(root, 128, (name, millis) ->
                Iris.debug("Acquired Channel for " + C.DARK_GREEN + name + C.RED + " in " + Form.duration(millis, 2))
        );
        this.runtime = new IOWorkerRuntimeSupport(support, LZ4_CODEC);
    }

    public TectonicPlate read(final String name) throws IOException {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        try {
            return runtime.read(name, (regionName, in) -> new TectonicPlate(worldHeight, in, regionName.startsWith("pv.")));
        } finally {
            if (TectonicPlate.hasError() && IrisSettings.get().getGeneral().isDumpMantleOnError()) {
                File dump = Iris.instance.getDataFolder("dump", name + ".bin");
                runtime.dumpDecoded(name, dump.toPath());
            } else {
                Iris.debug("Read Tectonic Plate " + C.DARK_GREEN + name + C.RED + " in " + Form.duration(p.getMilliseconds(), 2));
            }
        }
    }

    public void write(final String name, final TectonicPlate plate) throws IOException {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        runtime.writeAtomically(name, "iris", ".bin", plate, TectonicPlate::write);

        Iris.debug("Saved Tectonic Plate " + C.DARK_GREEN + name + C.RED + " in " + Form.duration(p.getMilliseconds(), 2));
    }

    public void close() throws IOException {
        support.close();
    }
}
