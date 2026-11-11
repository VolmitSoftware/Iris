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

import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The module jars this Iris carries: IrisModule builds them with an {@code index.json} that names each jar's hash and the
 * id and version of its modules, and the plugin bundles that folder. Iris never loads them from here. It copies the ones a
 * pack's {@code modules.json} lists into the pack, which loads its own copies.
 */
public final class ShippedModules {
    /** Where the plugin carries the jars; {@code shippedModulesResourceFolder} in core's build names the same. */
    public static final String RESOURCE_FOLDER = "shipped-modules";
    private static final String INDEX_NAME = "index.json";
    private static final ShippedModules NONE = new ShippedModules(List.of(), name -> Optional.empty());

    /** @param version as the module reports it */
    public record ShippedModule(String id, String version) {
    }

    /** @param sha256 of the jar's bytes, in lower case hex */
    public record ShippedJar(String file, String sha256, List<ShippedModule> modules) {
        public boolean holds(String moduleId) {
            return modules.stream().anyMatch(module -> module.id().equals(moduleId));
        }
    }

    private record Index(List<ShippedJar> jars) {
    }

    /** Opens a file of the folder by name; empty when the folder has no such file. */
    private interface Folder {
        Optional<InputStream> open(String name) throws IOException;
    }

    private final List<ShippedJar> jars;
    private final Folder folder;

    private ShippedModules(List<ShippedJar> jars, Folder folder) {
        this.jars = List.copyOf(jars);
        this.folder = folder;
    }

    /** The jars this build carries; none when it carries no folder, as when Iris runs from its classes in its own tests. */
    public static ShippedModules bundled() {
        return Holder.BUNDLED;
    }

    /** The jars of a folder IrisModule built, such as {@code module/build/shipped-modules}. */
    public static ShippedModules in(Path builtFolder) {
        return read(name -> {
            Path file = builtFolder.resolve(name);
            return Files.isRegularFile(file) ? Optional.of(Files.newInputStream(file)) : Optional.empty();
        });
    }

    public List<ShippedJar> jars() {
        return jars;
    }

    public boolean isEmpty() {
        return jars.isEmpty();
    }

    public Optional<ShippedModule> module(String moduleId) {
        return jars.stream().flatMap(jar -> jar.modules().stream()).filter(module -> module.id().equals(moduleId)).findFirst();
    }

    public byte[] bytesOf(ShippedJar jar) throws IOException {
        try (InputStream stream = folder.open(jar.file()).orElseThrow(() -> new IOException("Iris lists " + jar.file()
                + " among the modules it ships but does not carry it"))) {
            return stream.readAllBytes();
        }
    }

    private static ShippedModules read(Folder folder) {
        try {
            Optional<InputStream> index = folder.open(INDEX_NAME);
            if (index.isEmpty()) {
                return NONE;
            }
            try (InputStream stream = index.get()) {
                String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                return new ShippedModules(new Gson().fromJson(json, Index.class).jars(), folder);
            }
        } catch (IOException unreadable) {
            throw new UncheckedIOException("Cannot read the index of the module jars Iris ships", unreadable);
        }
    }

    private static final class Holder {
        private static final ShippedModules BUNDLED = read(name ->
                Optional.ofNullable(ShippedModules.class.getResourceAsStream("/" + RESOURCE_FOLDER + "/" + name)));
    }
}
