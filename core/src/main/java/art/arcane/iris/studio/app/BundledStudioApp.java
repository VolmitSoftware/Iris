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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** The studio app the jar carries, unpacked into a folder named by its checksum so each build gets its own copy. */
final class BundledStudioApp {
    static final String RESOURCE = "META-INF/iris/studio-app.zip";
    private static final String FOLDER_PREFIX = "app-";
    private static final int FOLDER_CHECKSUM_LENGTH = 12;

    private BundledStudioApp() {
    }

    static Path install(Path root, ClassLoader resources) throws IOException {
        Path archive = root.resolve("studio-app.zip.part");
        try {
            copyResource(resources, archive);
            Path folder = root.resolve(FOLDER_PREFIX + ElectronRuntime.sha256(archive).substring(0, FOLDER_CHECKSUM_LENGTH));
            if (!Files.isRegularFile(folder.resolve("package.json"))) {
                ZipArchives.extract(archive, folder);
            }
            return folder;
        } finally {
            Files.deleteIfExists(archive);
        }
    }

    static boolean isAppFolder(Path folder) {
        return folder.getFileName().toString().startsWith(FOLDER_PREFIX);
    }

    private static void copyResource(ClassLoader resources, Path archive) throws IOException {
        try (InputStream input = resources.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IOException("this build does not bundle the studio app; point studio.studioApp at a studio checkout");
            }
            Files.createDirectories(archive.getParent());
            Files.copy(input, archive, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
