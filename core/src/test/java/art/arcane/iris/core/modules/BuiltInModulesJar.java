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
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

/**
 * The built-ins jar as IrisModule's {@code builtinModulesJar} packages it, written from the classes this test run compiled:
 * every class {@code builtin-modules.txt} lists ({@code module} lines with their nested classes, {@code include} patterns)
 * and the entry points in order. Tests load it from a pack's {@code modules} folder without building IrisModule.
 */
final class BuiltInModulesJar {
    private static final String LIST_PROPERTY = "iris.builtinModulesList";
    private static final String SERVICE_ENTRY = "META-INF/services/com.volmit.IrisModule";
    private static final String JAR_NAME = "iris-builtins.jar";

    private BuiltInModulesJar() {
    }

    /** Writes {@code <pack>/modules/iris-builtins.jar} and returns its entry classes. */
    static List<String> writeInto(Path pack) throws IOException {
        List<String> modules = new ArrayList<>();
        List<PathMatcher> classes = new ArrayList<>();
        for (String line : Files.readAllLines(Path.of(System.getProperty(LIST_PROPERTY)))) {
            String entry = line.replaceAll("#.*", "").trim();
            if (entry.startsWith("module ")) {
                String module = entry.substring("module ".length()).trim();
                modules.add(module);
                String path = module.replace('.', '/');
                classes.add(glob(path + ".class"));
                classes.add(glob(path + "$*.class"));
            } else if (entry.startsWith("include ")) {
                classes.add(glob(entry.substring("include ".length()).trim()));
            }
        }
        Path root = classRoot();
        Path jar = Files.createDirectories(pack.resolve("modules")).resolve(JAR_NAME);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar)); Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                Path relative = root.relativize(file);
                if (classes.stream().anyMatch(matcher -> matcher.matches(relative))) {
                    output.putNextEntry(new JarEntry(relative.toString().replace('\\', '/')));
                    output.write(Files.readAllBytes(file));
                    output.closeEntry();
                }
            }
            output.putNextEntry(new JarEntry(SERVICE_ENTRY));
            output.write((String.join("\n", modules) + "\n").getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return modules;
    }

    private static PathMatcher glob(String pattern) {
        return FileSystems.getDefault().getPathMatcher("glob:" + pattern);
    }

    private static Path classRoot() {
        try {
            return Path.of(IrisModuleHost.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Iris's classes are not in a folder this test can read", e);
        }
    }
}
