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

package art.arcane.iris.structure.object;

import art.arcane.iris.generation.block.TileData;

import art.arcane.iris.pack.validation.ContentGate;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.localization.RuntimeUiMessages;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.iris.generation.block.B;
import art.arcane.iris.generation.geometry.IrisBlockVector;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.iris.world.task.jobs.Job;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
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
        DataInputStream input = new DataInputStream(in);
        IrisObject parsed = readDimensions(input);
        int blocks = requireNonnegativeCount(input.readInt(), "block");
        for (int index = 0; index < blocks; index++) {
            IrisBlockVector position = new IrisBlockVector(input.readShort(), input.readShort(), input.readShort());
            NativeBlockState state = resolvePaletteState(self, input.readUTF());
            if (!isExcludedObjectBlock(state)) {
                parsed.blocks.put(position, state);
            }
        }

        int firstByte = input.read();
        if (firstByte != -1) {
            int tiles = requireNonnegativeCount((firstByte << 24)
                    | (input.readUnsignedByte() << 16)
                    | (input.readUnsignedByte() << 8)
                    | input.readUnsignedByte(), "tile");
            for (int index = 0; index < tiles; index++) {
                readTile(parsed, input);
            }
        }
        publishRead(self, parsed);
    }

    static void read(IrisObject self, InputStream in) throws IOException {
        DataInputStream input = new DataInputStream(in);
        IrisObject parsed = readDimensions(input);
        if (!V2_HEADER.equals(input.readUTF())) {
            throw new IOException("Invalid object header");
        }
        int paletteSize = requireNonnegativeCount(input.readShort(), "palette");
        KList<String> palette = new KList<>();
        for (int index = 0; index < paletteSize; index++) {
            palette.add(input.readUTF());
        }

        // Resolve the palette once: B.getState per BLOCK was a registry lookup times the
        // block count (tens of thousands) instead of times the palette size (hundreds).
        NativeBlockState[] resolved = new NativeBlockState[palette.size()];
        for (int index = 0; index < resolved.length; index++) {
            resolved[index] = resolvePaletteState(self, palette.get(index));
        }

        int blocks = requireNonnegativeCount(input.readInt(), "block");
        for (int index = 0; index < blocks; index++) {
            IrisBlockVector position = new IrisBlockVector(input.readShort(), input.readShort(), input.readShort());
            int paletteIndex = input.readShort();
            if (paletteIndex < 0 || paletteIndex >= resolved.length) {
                throw new IOException("Invalid object palette index " + paletteIndex);
            }
            NativeBlockState state = resolved[paletteIndex];
            if (!isExcludedObjectBlock(state)) {
                parsed.blocks.put(position, state);
            }
        }

        int tiles = requireNonnegativeCount(input.readInt(), "tile");
        for (int index = 0; index < tiles; index++) {
            readTile(parsed, input);
        }
        publishRead(self, parsed);
    }

    private static IrisObject readDimensions(DataInputStream input) throws IOException {
        int width = input.readInt();
        int height = input.readInt();
        int depth = input.readInt();
        requireValidDimensions(width, height, depth);
        return new IrisObject(width, height, depth);
    }

    private static void requireValidDimensions(int width, int height, int depth) throws IOException {
        if (width < 1 || height < 1 || depth < 1) {
            throw new IOException("Invalid object dimensions " + width + "x" + height + "x" + depth);
        }
    }

    private static int requireNonnegativeCount(int count, String kind) throws IOException {
        if (count < 0) {
            throw new IOException("Invalid object " + kind + " count " + count);
        }
        return count;
    }

    private static void publishRead(IrisObject self, IrisObject parsed) {
        // A mid-file failure must not leave parsed entries merged into the previous object contents.
        self.writeLock.lock();
        try {
            self.w = parsed.w;
            self.h = parsed.h;
            self.d = parsed.d;
            self.center = parsed.center;
            self.shrinkOffset = parsed.shrinkOffset;
            self.blocks = parsed.blocks;
            self.states = parsed.states;
            self.smartBored = false;
            self.smartBoreVariant = null;
            self.aabb.reset();
            self.surfaceSupportOffsets.reset();
            self.floatingFootprint.reset();
        } finally {
            self.writeLock.unlock();
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
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(file))) {
            if (hasV2Header(input)) {
                read(self, input);
            } else {
                readLegacy(self, input);
            }
        }
    }

    private static boolean hasV2Header(BufferedInputStream input) throws IOException {
        input.mark(3 * Integer.BYTES + Short.BYTES + V2_HEADER.length());
        try {
            DataInputStream header = new DataInputStream(input);
            header.readInt();
            header.readInt();
            header.readInt();
            int length = header.readUnsignedShort();
            return length == V2_HEADER.length()
                    && V2_HEADER.equals(new String(header.readNBytes(length), StandardCharsets.US_ASCII));
        } finally {
            input.reset();
        }
    }

    /**
     * Palette key to state. A key the server does not have goes through the pack's dimension {@code blockFallbacks}
     * before the plain lookup, so a declared fallback actually reaches the world instead of becoming air. Present
     * keys take exactly one registry lookup, as before.
     */
    private static NativeBlockState resolvePaletteState(IrisObject self, String key) {
        NativeBlockState direct = B.getStateOrNull(key, false);

        if (direct != null) {
            return direct;
        }

        IrisData data = self.getLoader();

        if (data != null) {
            try {
                ContentGate gate = data.getContentGate();

                if (gate != null && gate.ready()) {
                    NativeBlockState viaGate = gate.resolveBlockOrPlaceholder(key);

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

    private static boolean isExcludedObjectBlock(NativeBlockState data) {
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
        requireValidDimensions(self.w, self.h, self.d);
        Palette palette = buildPalette(self);
        if (palette.size() > MAX_PALETTE_ENTRIES) {
            throw new IOException("Object '" + self.getLoadKey() + "' has " + palette.size()
                    + " distinct block states; the .iob format supports at most " + MAX_PALETTE_ENTRIES + ".");
        }
        for (Map.Entry<IrisBlockVector, NativeBlockState> entry : self.blocks) {
            requireShortCoordinates(self, "block", entry.getKey());
        }
        for (Map.Entry<IrisBlockVector, TileData> entry : self.states) {
            requireShortCoordinates(self, "tile", entry.getKey());
        }
    }

    private static Palette buildPalette(IrisObject self) {
        Palette palette = new Palette();
        for (NativeBlockState i : self.blocks.values()) {
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

        for (Map.Entry<IrisBlockVector, NativeBlockState> entry : self.blocks) {
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
        AtomicReference<Throwable> ref = new AtomicReference<>();
        CompletableFuture<Void> completion = new Job() {
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

                    for (Map.Entry<IrisBlockVector, NativeBlockState> entry : self.blocks) {
                        writeBlock(dos, palette, entry);
                        ++c;
                    }

                    dos.writeInt(self.states.size());
                    for (Map.Entry<IrisBlockVector, TileData> entry : self.states) {
                        writeState(dos, entry);
                        ++c;
                    }
                } catch (Throwable failure) {
                    ref.set(failure);
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
            completion.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while writing object", interrupted);
        } catch (ExecutionException rejected) {
            ref.compareAndSet(null, rejected.getCause());
        }
        Throwable failure = ref.get();
        if (failure instanceof IOException ioFailure) {
            throw ioFailure;
        }
        if (failure instanceof RuntimeException runtimeFailure) {
            throw runtimeFailure;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure != null) {
            throw new IOException("Object save work failed", failure);
        }
    }

    private static void writeHeader(IrisObject self, DataOutputStream output) throws IOException {
        output.writeInt(self.w);
        output.writeInt(self.h);
        output.writeInt(self.d);
        output.writeUTF(V2_HEADER);
    }

    private static void writeBlock(DataOutputStream output, Palette palette,
                                   Map.Entry<IrisBlockVector, NativeBlockState> entry) throws IOException {
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

        // Validate before staging output: a rejected object must leave the existing .iob untouched.
        validateWritable(self);
        writeAtomically(file, out -> writeValidated(self, out));
    }

    static void write(IrisObject self, File file, VolmitSender sender) throws IOException {
        if (file == null) {
            return;
        }

        validateWritable(self);
        writeAtomically(file, out -> writeValidated(self, out, sender));
    }

    private static void writeAtomically(File file, ObjectWriter writer) throws IOException {
        Path target = resolveWriteTarget(file);
        Path parent = target.getParent();
        boolean posix = Files.getFileStore(parent).supportsFileAttributeView(PosixFileAttributeView.class);
        Set<PosixFilePermission> permissions = posix && Files.exists(target)
                ? Files.getPosixFilePermissions(target) : null;
        Path temporary = posix
                ? Files.createTempFile(parent, ".iris-object-", ".tmp",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-rw-rw-")))
                : Files.createTempFile(parent, ".iris-object-", ".tmp");
        try {
            if (permissions != null) {
                Files.setPosixFilePermissions(temporary, permissions);
            }
            try (FileOutputStream output = new FileOutputStream(temporary.toFile())) {
                writer.write(output);
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Path resolveWriteTarget(File file) throws IOException {
        Path target = file.toPath().toAbsolutePath();
        target = target.getParent().toRealPath().resolve(target.getFileName());
        Set<Path> visited = new HashSet<>();
        while (Files.isSymbolicLink(target)) {
            if (!visited.add(target)) {
                throw new IOException("Object destination contains a symbolic-link cycle: " + file);
            }
            Path link = Files.readSymbolicLink(target);
            target = link.isAbsolute() ? link : target.getParent().resolve(link);
            target = target.getParent().toRealPath().resolve(target.getFileName());
        }
        return target;
    }

    private interface ObjectWriter {
        void write(OutputStream output) throws IOException;
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
