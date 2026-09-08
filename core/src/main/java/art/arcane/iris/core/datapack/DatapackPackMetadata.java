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

package art.arcane.iris.core.datapack;

import art.arcane.iris.core.ServerConfigurator;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisImportedStructureControl;
import art.arcane.volmlib.util.io.IO;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.LinkOption;
import java.nio.file.Files;
import java.util.stream.Stream;

final class DatapackPackMetadata {
    static final String OVERRIDES_STRIPPED_MARKER = ".iris-overrides-stripped";

    static final long MAX_METADATA_BYTES = 1024L * 1024L;

    private DatapackPackMetadata() {
    }

    static boolean resolveStripOverrides() {
        try (Stream<IrisData> stream = ServerConfigurator.allPacks()) {
            return stream.anyMatch(DatapackPackMetadata::packDisablesOverrides);
        }
    }

    static boolean packDisablesOverrides(IrisData data) {
        if (data == null || data.getDimensionLoader() == null) {
            return false;
        }
        for (IrisDimension dimension : data.getDimensionLoader().loadAll(data.getDimensionLoader().getPossibleKeys())) {
            if (dimension == null) {
                continue;
            }
            IrisImportedStructureControl control = dimension.getImportedStructures();
            if (control != null && !control.isDatapackOverrides()) {
                return true;
            }
        }
        return false;
    }

    static void stripVanillaStructureOverrides(File datapackRoot) throws IOException {
        stripVanillaStructureOverrides(datapackRoot, IO::delete);
    }

    static void stripVanillaStructureOverrides(
            File datapackRoot,
            DirectoryDeleter deleter
    ) throws IOException {
        File minecraftData = new File(new File(datapackRoot, "data"), "minecraft");
        if (!minecraftData.isDirectory()) {
            return;
        }
        String[] relativeTrees = {
                "worldgen" + File.separator + "structure_set",
                "worldgen" + File.separator + "structure",
                "worldgen" + File.separator + "template_pool",
                "structure"
        };
        for (String tree : relativeTrees) {
            File dir = new File(minecraftData, tree);
            if (dir.exists()) {
                deleter.delete(dir);
                if (Files.exists(dir.toPath(), LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Could not remove vanilla structure override tree " + dir.getPath());
                }
            }
        }
    }

    static void writeMarker(File marker) throws IOException {
        Files.writeString(marker.toPath(), "stripped", StandardCharsets.UTF_8);
    }

    static void validatePackMetadata(File datapackRoot) throws IOException {
        File metadata = new File(datapackRoot, "pack.mcmeta");
        if (!metadata.isFile() || Files.isSymbolicLink(metadata.toPath())) {
            throw new IOException("Datapack is missing a regular pack.mcmeta");
        }
        if (metadata.length() > MAX_METADATA_BYTES) {
            throw new IOException("Datapack pack.mcmeta exceeds 1 MiB");
        }
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(DatapackSupport.readBoundedUtf8(
                    metadata.toPath(), MAX_METADATA_BYTES, "Datapack pack.mcmeta"));
        } catch (RuntimeException e) {
            throw new IOException("Datapack pack.mcmeta is not valid JSON", e);
        }
        if (!parsed.isJsonObject()) {
            throw new IOException("Datapack pack.mcmeta root must be an object");
        }
        JsonObject root = parsed.getAsJsonObject();
        if (!root.has("pack") || !root.get("pack").isJsonObject()) {
            throw new IOException("Datapack pack.mcmeta must contain a pack object");
        }
        JsonObject pack = root.getAsJsonObject("pack");
        boolean hasPackFormat = pack.has("pack_format") && pack.get("pack_format").isJsonPrimitive()
                && pack.getAsJsonPrimitive("pack_format").isNumber();
        boolean hasRange = validFormatValue(pack.get("min_format")) && validFormatValue(pack.get("max_format"));
        if (!hasPackFormat && !hasRange) {
            throw new IOException("Datapack pack.mcmeta must declare numeric pack_format or valid min_format/max_format");
        }
        if (!pack.has("description") || pack.get("description").isJsonNull()) {
            throw new IOException("Datapack pack.mcmeta must declare a description");
        }
        File data = new File(datapackRoot, "data");
        if (data.exists() && (!data.isDirectory() || Files.isSymbolicLink(data.toPath()))) {
            throw new IOException("Datapack data path is not a regular directory");
        }
    }

    static boolean validFormatValue(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return false;
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            return true;
        }
        if (!value.isJsonArray() || value.getAsJsonArray().size() != 2) {
            return false;
        }
        for (JsonElement component : value.getAsJsonArray()) {
            if (!component.isJsonPrimitive() || !component.getAsJsonPrimitive().isNumber()) {
                return false;
            }
        }
        return true;
    }
}
