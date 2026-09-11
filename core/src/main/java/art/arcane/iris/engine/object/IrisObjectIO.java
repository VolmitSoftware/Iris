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

package art.arcane.iris.engine.object;

import art.arcane.iris.core.compat.ContentGate;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.localization.IrisLanguage;
import art.arcane.iris.core.localization.RuntimeUiMessages;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.B;
import art.arcane.iris.util.common.math.IrisBlockVector;
import art.arcane.iris.util.common.math.Vector3i;
import art.arcane.iris.util.common.plugin.VolmitSender;
import art.arcane.iris.util.common.scheduling.jobs.Job;
import art.arcane.volmlib.util.collection.KList;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Binary (.iob) persistence for {@link IrisObject}. The field layout written here is pinned by the on-disk
 * format - do not reorder reads or writes.
 */
public final class IrisObjectIO {
    private static final String V2_HEADER = "Iris V2 IOB;";
    private static final int MAX_PALETTE_ENTRIES = 32_767;
    private static final int PALETTE_CACHE_LIMIT = 8192;
    private static final Map<String, List<String>> PALETTE_CACHE = new ConcurrentHashMap<>();

    private IrisObjectIO() {
    }

    /**
     * Reads only the V2 palette block-state keys out of an {@code .iob} header. Read-only pack-tooling hook: no
     * IrisObject is built and no block state is resolved, so it runs without a bound platform.
     * <p>
     * Returns an empty list for a legacy (V1) object, an unreadable file, or a truncated header - a scan must never
     * fail pack validation.
     */
    public static List<String> readPaletteKeys(File file) {
        if (file == null || !file.isFile()) {
            return List.of();
        }
        try (DataInputStream din = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            din.readInt();
            din.readInt();
            din.readInt();
            if (!V2_HEADER.equals(din.readUTF())) {
                return List.of();
            }
            int count = din.readShort();
            if (count <= 0 || count > MAX_PALETTE_ENTRIES) {
                return List.of();
            }
            List<String> palette = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                palette.add(din.readUTF());
            }
            return palette;
        } catch (Throwable e) {
            return List.of();
        }
    }

    /**
     * {@link #readPaletteKeys(File)} memoized on path, size and mtime. The version-content gate reads the same object
     * headers once per placement that lists them, which at boot is the same file dozens of times.
     */
    public static List<String> readPaletteKeysCached(File file) {
        if (file == null || !file.isFile()) {
            return List.of();
        }

        String key = file.getPath() + '@' + file.lastModified() + '#' + file.length();
        List<String> cached = PALETTE_CACHE.get(key);

        if (cached != null) {
            return cached;
        }

        List<String> palette = readPaletteKeys(file);

        if (PALETTE_CACHE.size() >= PALETTE_CACHE_LIMIT) {
            PALETTE_CACHE.clear();
        }

        PALETTE_CACHE.put(key, palette);
        return palette;
    }

    static IrisBlockVector sampleSize(File file) throws IOException {
        try (DataInputStream din = new DataInputStream(new FileInputStream(file))) {
            return new IrisBlockVector(din.readInt(), din.readInt(), din.readInt());
        }
    }

    static void readLegacy(IrisObject self, InputStream in) throws IOException {
        self.surfaceSupportOffsets.reset();
        self.floatingFootprint.reset();
        DataInputStream din = new DataInputStream(in);
        self.w = din.readInt();
        self.h = din.readInt();
        self.d = din.readInt();
        self.center = new Vector3i(self.w / 2, self.h / 2, self.d / 2);
        int s = din.readInt();

        for (int i = 0; i < s; i++) {
            IrisBlockVector pos = new IrisBlockVector(din.readShort(), din.readShort(), din.readShort());
            PlatformBlockState data = resolvePaletteState(self, din.readUTF());
            if (isExcludedObjectBlock(data)) {
                continue;
            }
            self.blocks.put(pos, data);
        }

        if (din.available() == 0)
            return;

        try {
            int size = din.readInt();

            for (int i = 0; i < size; i++) {
                readTile(self, din);
            }
        } catch (Throwable e) {
            IrisLogging.reportError(e);
        }
    }

    static void read(IrisObject self, InputStream in) throws Throwable {
        self.surfaceSupportOffsets.reset();
        self.floatingFootprint.reset();
        DataInputStream din = new DataInputStream(in);
        self.w = din.readInt();
        self.h = din.readInt();
        self.d = din.readInt();
        if (!din.readUTF().equals("Iris V2 IOB;")) {
            throw new HeaderException();
        }
        self.center = new Vector3i(self.w / 2, self.h / 2, self.d / 2);
        int s = din.readShort();
        int i;
        KList<String> palette = new KList<>();

        for (i = 0; i < s; i++) {
            palette.add(din.readUTF());
        }

        // Resolve the palette once: B.getState per BLOCK was a registry lookup times the
        // block count (tens of thousands) instead of times the palette size (hundreds).
        PlatformBlockState[] resolved = new PlatformBlockState[palette.size()];
        for (i = 0; i < resolved.length; i++) {
            resolved[i] = resolvePaletteState(self, palette.get(i));
        }

        s = din.readInt();

        for (i = 0; i < s; i++) {
            IrisBlockVector pos = new IrisBlockVector(din.readShort(), din.readShort(), din.readShort());
            PlatformBlockState data = resolved[din.readShort()];
            if (isExcludedObjectBlock(data)) {
                continue;
            }
            self.blocks.put(pos, data);
        }

        s = din.readInt();

        for (i = 0; i < s; i++) {
            readTile(self, din);
        }
    }

    private static void readTile(IrisObject self, DataInputStream input) throws IOException {
        IrisBlockVector position = new IrisBlockVector(input.readShort(), input.readShort(), input.readShort());
        TileData tile = TileData.read(input);
        if (self.blocks.get(position) != null) {
            self.states.put(position, tile);
        }
    }

    static void read(IrisObject self, File file) throws IOException {
        try (var fin = new BufferedInputStream(new FileInputStream(file))) {
            read(self, fin);
        } catch (Throwable e) {
            if (!(e instanceof HeaderException))
                IrisLogging.reportError(e);
            // The V2 parse populates blocks/states incrementally; a mid-file failure must not
            // leave those entries to be merged with the legacy parse of the same file.
            self.blocks.clear();
            self.states.clear();
            try (var fin = new BufferedInputStream(new FileInputStream(file))) {
                readLegacy(self, fin);
            }
        }
    }

    /**
     * Palette key to state. A key the server does not have goes through the pack's dimension {@code blockFallbacks}
     * before the plain lookup, so a declared fallback actually reaches the world instead of becoming air. Present
     * keys take exactly one registry lookup, as before.
     */
    private static PlatformBlockState resolvePaletteState(IrisObject self, String key) {
        PlatformBlockState direct = B.getStateOrNull(key, false);

        if (direct != null) {
            return direct;
        }

        IrisData data = self.getLoader();

        if (data != null) {
            try {
                ContentGate gate = data.getContentGate();

                if (gate != null && gate.ready()) {
                    PlatformBlockState viaGate = gate.resolveBlockOrPlaceholder(key);

                    if (viaGate != null) {
                        return viaGate;
                    }
                }
            } catch (Throwable e) {
                // The gate is advisory here; object loading must never fail because of it.
            }
        }

        return B.getState(key);
    }

    private static boolean isExcludedObjectBlock(PlatformBlockState data) {
        if (data == null) {
            return false;
        }
        String material = IrisObjectShaping.materialKey(data);
        return material.equals("minecraft:jigsaw") || material.equals("minecraft:structure_block")
                || material.equals("minecraft:structure_void") || material.equals("minecraft:moving_piston");
    }

    /**
     * The .iob V2 format stores the palette count, palette indices, and block coordinates as
     * shorts. Values beyond the short range used to wrap silently and corrupt the object; every
     * write path now rejects them with a descriptive error before any byte is written.
     */
    static void validateWritable(IrisObject self) throws IOException {
        Palette palette = buildPalette(self);
        if (palette.size() > MAX_PALETTE_ENTRIES) {
            throw new IOException("Object '" + self.getLoadKey() + "' has " + palette.size()
                    + " distinct block states; the .iob format supports at most " + MAX_PALETTE_ENTRIES + ".");
        }
        for (var entry : self.blocks) {
            requireShortCoordinates(self, "block", entry.getKey());
        }
        for (var entry : self.states) {
            requireShortCoordinates(self, "tile", entry.getKey());
        }
    }

    private static Palette buildPalette(IrisObject self) {
        Palette palette = new Palette();
        for (PlatformBlockState i : self.blocks.values()) {
            palette.add(i.key());
        }
        return palette;
    }

    private static void requireShortCoordinates(IrisObject self, String kind, IrisBlockVector position) throws IOException {
        requireShort(self, kind, "x", position.getBlockX(), position);
        requireShort(self, kind, "y", position.getBlockY(), position);
        requireShort(self, kind, "z", position.getBlockZ(), position);
    }

    private static void requireShort(IrisObject self, String kind, String axis, int value, IrisBlockVector position) throws IOException {
        if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
            throw new IOException("Object '" + self.getLoadKey() + "' " + kind + " at (" + position.getBlockX()
                    + "," + position.getBlockY() + "," + position.getBlockZ()
                    + ") exceeds the .iob coordinate range of ±32767 on the " + axis + " axis (" + value + ").");
        }
    }

    static void write(IrisObject self, OutputStream o) throws IOException {
        validateWritable(self);
        writeValidated(self, o);
    }

    private static void writeValidated(IrisObject self, OutputStream o) throws IOException {
        DataOutputStream dos = new DataOutputStream(o);
        writeHeader(self, dos);
        Palette palette = buildPalette(self);

        dos.writeShort(palette.size());

        for (String i : palette.keys()) {
            dos.writeUTF(i);
        }

        dos.writeInt(self.blocks.size());

        for (Map.Entry<IrisBlockVector, PlatformBlockState> entry : self.blocks) {
            writeBlock(dos, palette, entry);
        }

        dos.writeInt(self.states.size());
        for (Map.Entry<IrisBlockVector, TileData> entry : self.states) {
            writeState(dos, entry);
        }
    }

    static void write(IrisObject self, OutputStream o, VolmitSender sender) throws IOException {
        validateWritable(self);
        writeValidated(self, o, sender);
    }

    private static void writeValidated(IrisObject self, OutputStream o, VolmitSender sender) throws IOException {
        AtomicReference<IOException> ref = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        new Job() {
            private volatile int total = self.blocks.size() * 3 + self.states.size();
            private volatile int c = 0;

            @Override
            public String getName() {
                return IrisLanguage.text(RuntimeUiMessages.JOB_SAVING_OBJECT);
            }

            @Override
            public void execute() {
                try {
                    DataOutputStream dos = new DataOutputStream(o);
                    writeHeader(self, dos);

                    Palette palette = buildPalette(self);
                    c += self.blocks.size();
                    total -= self.blocks.size() - palette.size();

                    dos.writeShort(palette.size());

                    for (String i : palette.keys()) {
                        dos.writeUTF(i);
                        ++c;
                    }

                    dos.writeInt(self.blocks.size());

                    for (Map.Entry<IrisBlockVector, PlatformBlockState> entry : self.blocks) {
                        writeBlock(dos, palette, entry);
                        ++c;
                    }

                    dos.writeInt(self.states.size());
                    for (Map.Entry<IrisBlockVector, TileData> entry : self.states) {
                        writeState(dos, entry);
                        ++c;
                    }
                } catch (IOException e) {
                    ref.set(e);
                } finally {
                    latch.countDown();
                }
            }

            @Override
            public void completeWork() {}

            @Override
            public int getTotalWork() {
                return total;
            }

            @Override
            public int getWorkCompleted() {
                return c;
            }
        }.execute(sender, true, () -> {});

        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while writing object", interrupted);
        }
        if (ref.get() != null)
            throw ref.get();
    }

    private static void writeHeader(IrisObject self, DataOutputStream output) throws IOException {
        output.writeInt(self.w);
        output.writeInt(self.h);
        output.writeInt(self.d);
        output.writeUTF(V2_HEADER);
    }

    private static void writeBlock(DataOutputStream output, Palette palette,
                                   Map.Entry<IrisBlockVector, PlatformBlockState> entry) throws IOException {
        IrisBlockVector position = entry.getKey();
        output.writeShort(position.getBlockX());
        output.writeShort(position.getBlockY());
        output.writeShort(position.getBlockZ());
        output.writeShort(palette.indexOf(entry.getValue().key()));
    }

    private static void writeState(DataOutputStream output,
                                   Map.Entry<IrisBlockVector, TileData> entry) throws IOException {
        IrisBlockVector position = entry.getKey();
        output.writeShort(position.getBlockX());
        output.writeShort(position.getBlockY());
        output.writeShort(position.getBlockZ());
        entry.getValue().toBinary(output);
    }

    static void write(IrisObject self, File file) throws IOException {
        if (file == null) {
            return;
        }

        // Validate before opening the stream: FileOutputStream truncates, and a rejected
        // object must leave the existing .iob untouched.
        validateWritable(self);
        try (FileOutputStream out = new FileOutputStream(file)) {
            writeValidated(self, out);
        }
    }

    static void write(IrisObject self, File file, VolmitSender sender) throws IOException {
        if (file == null) {
            return;
        }

        validateWritable(self);
        try (FileOutputStream out = new FileOutputStream(file)) {
            writeValidated(self, out, sender);
        }
    }

    private static class HeaderException extends IOException {
        public HeaderException() {
            super("Invalid Header");
        }
    }

    private static final class Palette {
        private final KList<String> keys = new KList<>();
        private final Map<String, Integer> index = new HashMap<>();

        private void add(String key) {
            index.computeIfAbsent(key, k -> {
                keys.add(k);
                return keys.size() - 1;
            });
        }

        private int indexOf(String key) {
            Integer found = index.get(key);
            return found == null ? -1 : found;
        }

        private int size() {
            return keys.size();
        }

        private KList<String> keys() {
            return keys;
        }
    }
}
