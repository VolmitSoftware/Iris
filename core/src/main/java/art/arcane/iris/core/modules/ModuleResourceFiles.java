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

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.core.loader.JsonSchemaValidator;
import art.arcane.volmlib.util.json.JSONObject;
import com.volmit.resource.ResourceKind;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The files of one module resource kind in a pack: every {@code .json} file below the kind's folder, keyed by its
 * path below the folder without the extension, and each file read into a new resource of the kind.
 * <p>
 * A file is read with the pack's module decoder, which hands Iris's own classes to the pack's Gson, so a built-in
 * kind such as structures reads exactly as Iris's loaders read. A resource that is one of Iris's registrants gets its
 * key, file and pack data, as a loader would set them. A file that fails to read is reported with the line at fault,
 * like a loader's, and gives no resource. Unknown keys are reported for Iris's own classes only: the check caches
 * field names per class, which would keep a pack module's classes loaded after the pack closes.
 */
public final class ModuleResourceFiles<T> {
    private static final String JSON_EXTENSION = ".json";
    private static final char FOLDER_SEPARATOR = '/';

    private final IrisData data;
    private final ResourceKind<T> kind;
    private final Path folder;
    private final String typeName;

    ModuleResourceFiles(IrisData data, ResourceKind<T> kind) {
        this.data = data;
        this.kind = kind;
        this.folder = data.getDataFolder().toPath().resolve(kind.folder());
        this.typeName = typeName(kind);
    }

    /** The key of every file of the kind, in key order; none when the pack has no folder for it. */
    List<String> keys() {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(folder)) {
            return files.filter(Files::isRegularFile)
                    .map(this::keyOf)
                    .filter(Objects::nonNull)
                    .sorted()
                    .toList();
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot list the files of resource kind " + kind.id() + " in " + folder, failure);
        }
    }

    /** The file of {@code key} read into a new resource, or null when it fails to read, which is reported. */
    T read(String key) {
        File file = folder.resolve(key + JSON_EXTENSION).toFile();
        String json = null;
        try {
            json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            reportUnknownKeys(json, file);
            T resource = data.getModules().settings().decodeResource(kind.type(), json);
            describeOrigin(resource, key, file);
            return resource;
        } catch (IOException | RuntimeException failure) {
            JsonSchemaValidator.reportLoadFailure(file, json, typeName, failure);
            return null;
        }
    }

    private String keyOf(Path file) {
        String relative = folder.relativize(file).toString().replace(file.getFileSystem().getSeparator(), String.valueOf(FOLDER_SEPARATOR));
        if (!relative.endsWith(JSON_EXTENSION) || relative.length() == JSON_EXTENSION.length()) {
            return null;
        }
        return relative.substring(0, relative.length() - JSON_EXTENSION.length());
    }

    private void reportUnknownKeys(String json, File file) {
        if (!IrisModuleHost.isHostType(kind.type())) {
            return;
        }
        JsonSchemaValidator.validateTopLevelKeys(new JSONObject(json), json, file, typeName, kind.type(), Set.of());
    }

    private void describeOrigin(T resource, String key, File file) {
        if (!(resource instanceof IrisRegistrant registrant)) {
            return;
        }
        registrant.setLoadKey(key);
        registrant.setLoadFile(file);
        registrant.setLoader(data);
    }

    /** What load reports and studio call a resource of {@code kind}: an Iris registrant's own type name, such as Structure, or else the kind's id. */
    public static String typeName(ResourceKind<?> kind) {
        if (!IrisRegistrant.class.isAssignableFrom(kind.type())) {
            return kind.id().toString();
        }
        try {
            return ((IrisRegistrant) kind.type().getConstructor().newInstance()).getTypeName();
        } catch (ReflectiveOperationException failure) {
            return kind.id().toString();
        }
    }
}
