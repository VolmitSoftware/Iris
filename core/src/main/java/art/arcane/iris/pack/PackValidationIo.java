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

package art.arcane.iris.pack;

import art.arcane.volmlib.util.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class PackValidationIo {
    private PackValidationIo() {
    }

    static JSONObject readJson(File file) {
        try {
            return new JSONObject(Files.readString(file.toPath(), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException parseFailure) {
            // Every folder scanned through readJson is also scanned by a readGraphJson pass
            // (validateUnsupportedStructureTransforms / validateStructureStartPools), which records the
            // parse failure as a blocking error. Reporting here too would duplicate it per scan.
            return null;
        }
    }

    static boolean isScannableJsonPath(Path path) {
        String name = path.getFileName().toString();
        if (!name.endsWith(".json")) {
            return false;
        }
        String str = path.toString().replace(File.separatorChar, '/');
        if (str.contains("/" + PackValidator.TRASH_ROOT + "/")) {
            return false;
        }
        if (str.contains("/" + PackValidator.DATAPACK_IMPORTS + "/")) {
            return false;
        }
        if (str.contains("/" + PackValidator.EXTERNAL_DATAPACKS + "/")) {
            return false;
        }
        if (str.contains("/" + PackValidator.INTERNAL_DATAPACKS + "/")) {
            return false;
        }
        if (str.contains("/" + PackValidator.DATAPACKS_FOLDER + "/")) {
            return false;
        }
        if (str.contains("/" + PackValidator.CACHE_FOLDER + "/")) {
            return false;
        }
        if (str.contains("/" + PackValidator.OBJECTS_FOLDER + "/")) {
            return false;
        }
        if (str.contains("/.iris/")) {
            return false;
        }
        return true;
    }

    static List<File> listJsonRecursive(File root) {
        List<File> out = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root.toPath())) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .forEach(p -> out.add(p.toFile()));
        } catch (Throwable ignored) {
        }
        return out;
    }

    static String deriveKey(File resourceFolder, File resourceFile) {
        Path relative = resourceFolder.toPath().relativize(resourceFile.toPath());
        String str = relative.toString().replace(File.separatorChar, '/');
        if (!str.endsWith(".json")) {
            return null;
        }
        return str.substring(0, str.length() - ".json".length());
    }

    static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }

    public static Set<String> listReferencedKeysFromCorpus(String corpus) {
        Set<String> keys = new HashSet<>();
        if (corpus == null) {
            return keys;
        }
        int i = 0;
        while (i < corpus.length()) {
            int start = corpus.indexOf('"', i);
            if (start < 0) {
                break;
            }
            int end = corpus.indexOf('"', start + 1);
            if (end < 0) {
                break;
            }
            keys.add(corpus.substring(start + 1, end));
            i = end + 1;
        }
        return keys;
    }
}
