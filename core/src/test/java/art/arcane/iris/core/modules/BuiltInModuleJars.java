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

import art.arcane.iris.core.modules.manifest.ShippedModules;
import art.arcane.iris.core.modules.manifest.ShippedModules.ShippedJar;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.jar.JarFile;

/**
 * The built-in module jars Iris bundles (IrisModule builds them, Iris's {@code shippedModulesFolder} collects them), for
 * tests to load from a pack's {@code modules} folder.
 */
public final class BuiltInModuleJars {
    private static final String FOLDER_PROPERTY = "iris.shippedModulesFolder";
    private static final String SERVICE_ENTRY = "META-INF/services/com.volmit.IrisModule";
    private static final String MODULES_FOLDER = "modules";

    private BuiltInModuleJars() {
    }

    /** The jars as Iris ships them, with their index. */
    public static ShippedModules shipped() {
        return ShippedModules.in(Path.of(System.getProperty(FOLDER_PROPERTY)));
    }

    /**
     * Copies every jar into {@code <pack>/modules} and returns their entry classes in the order a pack loads them: by jar
     * file name, then as the jar lists them.
     */
    public static List<String> writeInto(Path pack) throws IOException {
        ShippedModules shipped = shipped();
        Path modules = Files.createDirectories(pack.resolve(MODULES_FOLDER));
        List<String> entryClasses = new ArrayList<>();
        for (ShippedJar shippedJar : shipped.jars().stream().sorted(Comparator.comparing(ShippedJar::file)).toList()) {
            Path jar = modules.resolve(shippedJar.file());
            Files.write(jar, shipped.bytesOf(shippedJar));
            entryClasses.addAll(entryClasses(jar));
        }
        return entryClasses;
    }

    /** {@link #writeInto} unless the pack has the jars already, for a test that writes a pack file by file. */
    public static void installInto(Path pack) throws IOException {
        Path firstJar = pack.resolve(MODULES_FOLDER).resolve(shipped().jars().getFirst().file());
        if (Files.exists(firstJar)) {
            return;
        }
        writeInto(pack);
    }

    private static List<String> entryClasses(Path jar) throws IOException {
        try (JarFile file = new JarFile(jar.toFile())) {
            String entries = new String(file.getInputStream(file.getJarEntry(SERVICE_ENTRY)).readAllBytes(), StandardCharsets.UTF_8);
            return entries.lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
        }
    }
}
