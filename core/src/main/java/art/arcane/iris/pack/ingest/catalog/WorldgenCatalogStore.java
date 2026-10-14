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


package art.arcane.iris.pack.ingest.catalog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

public final class WorldgenCatalogStore {
    public static final String FILE_NAME = "worldgen-catalog.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private WorldgenCatalogStore() {
    }

    public static WorldgenCatalog read(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            WorldgenCatalog catalog = GSON.fromJson(reader, WorldgenCatalog.class);
            if (catalog == null) {
                throw new IOException("Worldgen catalog is empty: " + file);
            }
            if (catalog.format() != WorldgenCatalog.FORMAT) {
                throw new IOException("Worldgen catalog " + file + " has format " + catalog.format() + ", expected " + WorldgenCatalog.FORMAT);
            }
            return catalog;
        }
    }

    public static void write(Path file, WorldgenCatalog catalog) throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(catalog, "catalog");
        Path parent = file.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, FILE_NAME, ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(catalog, writer);
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
