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

package art.arcane.iris.core.modules.manifest;

import art.arcane.iris.core.modules.BuiltInModuleJars;
import art.arcane.iris.core.modules.manifest.ShippedModules.ShippedJar;
import art.arcane.iris.core.modules.manifest.ShippedModules.ShippedModule;
import com.google.gson.Gson;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * The module jars of a later Iris release, for tests: the real built-in jars, with new bytes and a new version for the
 * modules a test names. The classes are the real ones, so the jars share code as the real ones do.
 */
final class ShippedRelease {
    private static final String INDEX_NAME = "index.json";
    private static final String REBUILD_MARKER_ENTRY = "META-INF/rebuilt-for-test";

    private ShippedRelease() {
    }

    /** Writes the release into {@code releaseFolder}: every jar that holds a module of {@code newVersions} is rebuilt and reports that version. */
    static ShippedModules withVersions(Path releaseFolder, Map<String, String> newVersions) throws IOException {
        ShippedModules real = BuiltInModuleJars.shipped();
        List<ShippedJar> jars = new ArrayList<>();
        for (ShippedJar jar : real.jars()) {
            List<ShippedModule> modules = jar.modules().stream()
                    .map(module -> new ShippedModule(module.id(), newVersions.getOrDefault(module.id(), module.version())))
                    .toList();
            boolean rebuilt = !modules.equals(jar.modules());
            byte[] bytes = rebuilt ? withMarker(real.bytesOf(jar), modules.toString()) : real.bytesOf(jar);
            Files.write(releaseFolder.resolve(jar.file()), bytes);
            jars.add(new ShippedJar(jar.file(), PackModuleUpdates.hashOf(bytes), modules));
        }
        Files.writeString(releaseFolder.resolve(INDEX_NAME), new Gson().toJson(Map.of("jars", jars)), StandardCharsets.UTF_8);
        return ShippedModules.in(releaseFolder);
    }

    /** The same jars with other bytes, as a rebuild of unchanged versions gives. */
    static ShippedModules rebuilt(Path releaseFolder, String moduleId) throws IOException {
        ShippedModules real = BuiltInModuleJars.shipped();
        List<ShippedJar> jars = new ArrayList<>();
        for (ShippedJar jar : real.jars()) {
            byte[] bytes = jar.holds(moduleId) ? withMarker(real.bytesOf(jar), "rebuilt") : real.bytesOf(jar);
            Files.write(releaseFolder.resolve(jar.file()), bytes);
            jars.add(new ShippedJar(jar.file(), PackModuleUpdates.hashOf(bytes), jar.modules()));
        }
        Files.writeString(releaseFolder.resolve(INDEX_NAME), new Gson().toJson(Map.of("jars", jars)), StandardCharsets.UTF_8);
        return ShippedModules.in(releaseFolder);
    }

    private static byte[] withMarker(byte[] jar, String marker) throws IOException {
        ByteArrayOutputStream rebuilt = new ByteArrayOutputStream();
        try (ZipInputStream source = new ZipInputStream(new ByteArrayInputStream(jar));
             ZipOutputStream target = new ZipOutputStream(rebuilt)) {
            for (ZipEntry entry = source.getNextEntry(); entry != null; entry = source.getNextEntry()) {
                target.putNextEntry(new ZipEntry(entry.getName()));
                source.transferTo(target);
                target.closeEntry();
            }
            target.putNextEntry(new ZipEntry(REBUILD_MARKER_ENTRY));
            target.write(marker.getBytes(StandardCharsets.UTF_8));
            target.closeEntry();
        }
        return rebuilt.toByteArray();
    }
}
