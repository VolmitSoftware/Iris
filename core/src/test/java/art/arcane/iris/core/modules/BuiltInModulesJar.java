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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;

/**
 * The built-ins jar Iris bundles (IrisModule builds it, Iris's {@code builtinModulesJar} checks it), for tests to load from a
 * pack's {@code modules} folder.
 */
public final class BuiltInModulesJar {
    private static final String JAR_PROPERTY = "iris.builtinModulesJar";
    private static final String SERVICE_ENTRY = "META-INF/services/com.volmit.IrisModule";
    private static final String JAR_NAME = "iris-builtins.jar";

    private BuiltInModulesJar() {
    }

    /** Copies the jar to {@code <pack>/modules/iris-builtins.jar} and returns its entry classes in the order it lists them. */
    public static List<String> writeInto(Path pack) throws IOException {
        Path built = Path.of(System.getProperty(JAR_PROPERTY));
        Path jar = Files.createDirectories(pack.resolve("modules")).resolve(JAR_NAME);
        Files.copy(built, jar);
        return entryClasses(jar);
    }

    private static List<String> entryClasses(Path jar) throws IOException {
        try (JarFile file = new JarFile(jar.toFile())) {
            String entries = new String(file.getInputStream(file.getJarEntry(SERVICE_ENTRY)).readAllBytes(), StandardCharsets.UTF_8);
            return entries.lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
        }
    }
}
