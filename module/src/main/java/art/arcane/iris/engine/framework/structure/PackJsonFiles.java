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

package art.arcane.iris.engine.framework.structure;

import art.arcane.volmlib.util.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** The JSON files of a pack folder as written, for the checks that read values the host would have read as defaults. */
final class PackJsonFiles {
    private static final String JSON_EXTENSION = ".json";

    private PackJsonFiles() {
    }

    /** Every {@code .json} file below {@code root}, in no particular order; none when it cannot be listed. */
    static List<File> listRecursive(File root) {
        List<File> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root.toPath())) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(JSON_EXTENSION))
                    .forEach(path -> files.add(path.toFile()));
        } catch (Throwable ignored) {
            return files;
        }
        return files;
    }

    /** The JSON object {@code file} holds, or null when it cannot be read or parsed. */
    static JSONObject read(File file) {
        try {
            return new JSONObject(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        } catch (java.io.IOException | RuntimeException unreadable) {
            return null;
        }
    }

    /** The key of {@code file} in {@code folder}: its path below it without {@code .json}, or null for other files. */
    static String keyOf(File folder, File file) {
        String relative = folder.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/');
        if (!relative.endsWith(JSON_EXTENSION)) {
            return null;
        }
        return relative.substring(0, relative.length() - JSON_EXTENSION.length());
    }
}
