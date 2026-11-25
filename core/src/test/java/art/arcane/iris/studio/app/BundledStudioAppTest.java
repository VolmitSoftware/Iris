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

package art.arcane.iris.studio.app;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class BundledStudioAppTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void unpacksTheBundledAppIntoAFolderNamedByItsChecksum() throws IOException {
        Path root = temporary.newFolder("studio").toPath();

        try (URLClassLoader resources = jarResources(appArchive("out/main/index.js"))) {
            Path app = BundledStudioApp.install(root, resources);

            assertTrue(app.getFileName().toString().matches("app-[0-9a-f]{12}"));
            assertTrue(Files.isRegularFile(app.resolve("package.json")));
            assertTrue(Files.isRegularFile(app.resolve("out/main/index.js")));
            assertEquals(app, BundledStudioApp.install(root, resources));
            assertFalse(Files.exists(root.resolve("studio-app.zip.part")));
        }
    }

    @Test
    public void refusesAnArchiveEntryThatLeavesItsFolder() throws IOException {
        Path root = temporary.newFolder("studio").toPath();

        try (URLClassLoader resources = jarResources(appArchive("../../escaped.js"))) {
            assertThrows(IOException.class, () -> BundledStudioApp.install(root, resources));
        }
        assertFalse(Files.exists(root.getParent().resolve("escaped.js")));
    }

    @Test
    public void reportsABuildWithoutTheApp() throws IOException {
        Path root = temporary.newFolder("studio").toPath();

        try (URLClassLoader resources = new URLClassLoader(new URL[0], null)) {
            assertThrows(IOException.class, () -> BundledStudioApp.install(root, resources));
        }
    }

    private Path appArchive(String scriptEntry) throws IOException {
        Path archive = temporary.newFolder().toPath().resolve("studio-app.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("package.json"));
            zip.write("{\"main\":\"out/main/index.js\"}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(scriptEntry));
            zip.write("export {}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return archive;
    }

    private URLClassLoader jarResources(Path archive) throws IOException {
        Path classpath = temporary.newFolder().toPath();
        Path resource = classpath.resolve(BundledStudioApp.RESOURCE);
        Files.createDirectories(resource.getParent());
        Files.copy(archive, resource);
        return new URLClassLoader(new URL[]{classpath.toUri().toURL()}, null);
    }
}
