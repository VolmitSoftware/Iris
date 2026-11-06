/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.core.modules;

import art.arcane.iris.core.lifecycle.VanishedWorldStorage;
import com.volmit.ModuleLog;
import com.volmit.world.SavedKey;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * What modules keep with one running world ({@link com.volmit.world.RunningWorld#saved}): the world's one instance per
 * key, read from {@code <world>/iris/module-data/<namespace>/<path>.bin} when first asked for and written back by
 * {@link #saveAll()}. A file is the codec's version followed by the codec's payload.
 */
final class SavedWorldState {
    private static final String STORAGE_DIRECTORY = "iris/module-data";
    private static final String FILE_EXTENSION = ".bin";
    private static final String TEMPORARY_EXTENSION = ".tmp";

    private final Supplier<File> worldFolder;
    private final ModuleLog log;
    private final Map<String, Saved<?>> values = new ConcurrentHashMap<>();
    private final Object loading = new Object();

    SavedWorldState(Supplier<File> worldFolder, ModuleLog log) {
        this.worldFolder = worldFolder;
        this.log = log;
    }

    <T> T saved(SavedKey<T> key) {
        Saved<?> known = values.get(key.id());
        if (known == null) {
            known = loadOnce(key);
        }
        if (known.key().type() != key.type()) {
            throw new IllegalArgumentException("Saved state " + key.id() + " is kept as " + known.key().type().getName()
                    + ", not " + key.type().getName());
        }
        return key.type().cast(known.value());
    }

    /** Writes every value asked for so far; a value that cannot be written is reported and does not stop the others. */
    void saveAll() {
        List<Saved<?>> asked = List.copyOf(values.values());
        if (asked.isEmpty() || VanishedWorldStorage.vanished(worldFolder.get())) {
            return;
        }
        for (Saved<?> saved : asked) {
            save(saved);
        }
    }

    private <T> Saved<?> loadOnce(SavedKey<T> key) {
        synchronized (loading) {
            Saved<?> known = values.get(key.id());
            if (known != null) {
                return known;
            }
            Saved<T> loaded = new Saved<>(key, load(key));
            values.put(key.id(), loaded);
            return loaded;
        }
    }

    private <T> T load(SavedKey<T> key) {
        Path file = fileOf(key);
        byte[] stored;
        try {
            stored = Files.readAllBytes(file);
        } catch (NoSuchFileException none) {
            return key.initial().get();
        } catch (IOException unreadable) {
            log.warn("Saved state " + key.id() + " could not be read from " + file + " and starts over: " + unreadable);
            return key.initial().get();
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(stored))) {
            T value = key.codec().read(input, input.readInt());
            if (value == null) {
                throw new IOException("The codec read no value");
            }
            return value;
        } catch (IOException | RuntimeException rejected) {
            log.warn("Saved state " + key.id() + " in " + file + " was rejected and starts over: " + rejected);
            return key.initial().get();
        }
    }

    private void save(Saved<?> saved) {
        Path file = fileOf(saved.key());
        try {
            writeAtomically(file, saved.encode());
        } catch (IOException | RuntimeException failure) {
            log.error("Saved state " + saved.key().id() + " could not be written to " + file, failure);
        }
    }

    private Path fileOf(SavedKey<?> key) {
        String id = key.id();
        String namespace = id.substring(0, id.indexOf(':'));
        String path = id.substring(id.indexOf(':') + 1);
        return worldFolder.get().toPath().resolve(STORAGE_DIRECTORY).resolve(namespace).resolve(path + FILE_EXTENSION);
    }

    private static void writeAtomically(Path file, byte[] content) throws IOException {
        Path parent = file.getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, file.getFileName().toString(), TEMPORARY_EXTENSION);
        try {
            Files.write(temporary, content);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private record Saved<T>(SavedKey<T> key, T value) {
        byte[] encode() throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(key.codec().version());
                key.codec().write(output, value);
            }
            return bytes.toByteArray();
        }
    }
}
