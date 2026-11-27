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

package art.arcane.iris.studio.bridge;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * A pack the studio can open: its folder, and the name and version its dimension file gives.
 *
 * @param key the pack's folder, which names its dimension file too
 */
public record StudioPack(String key, String name, int version) {
    private static final int UNVERSIONED = 1;

    /** The packs in the workspace by name; a folder without a readable dimension file of its own name is no pack. */
    public static List<StudioPack> inWorkspace(File workspace) {
        File[] folders = workspace.listFiles(File::isDirectory);
        if (folders == null) {
            return List.of();
        }
        return Arrays.stream(folders)
                .map(StudioPack::read)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(StudioPack::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    static StudioPack read(File folder) {
        String key = folder.getName();
        File dimensionFile = new File(folder, "dimensions/" + key + ".json");
        if (!dimensionFile.isFile()) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(dimensionFile.toPath(), StandardCharsets.UTF_8)) {
            JsonObject dimension = JsonParser.parseReader(reader).getAsJsonObject();
            return new StudioPack(key, textOr(dimension.get("name"), key), versionOf(dimension.get("version")));
        } catch (IOException | JsonParseException | IllegalStateException | NumberFormatException unreadable) {
            return null;
        }
    }

    private static String textOr(JsonElement element, String fallback) {
        return element != null && element.isJsonPrimitive() && !element.getAsString().isBlank() ? element.getAsString() : fallback;
    }

    private static int versionOf(JsonElement element) {
        return element != null && element.isJsonPrimitive() ? element.getAsInt() : UNVERSIONED;
    }
}
