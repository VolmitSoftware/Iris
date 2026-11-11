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

package art.arcane.iris.modulebuild;

import com.google.gson.GsonBuilder;
import com.volmit.IrisModule;
import com.volmit.ModuleInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Writes the index of the module jars Iris ships: per jar its file name, the hash of its bytes and the id and version each
 * of its modules reports. Iris reads the index to tell what it ships without loading a jar. The module classes are on this
 * tool's class path, so the id and version come from the class that reports them at run time.
 */
public final class ShippedModuleIndex {
    private static final String ENTRY_POINT_FILE = "META-INF/services/" + IrisModule.class.getName();
    private static final String CLASS_FILE_SUFFIX = ".class";
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final String USAGE = "Usage: ShippedModuleIndex <index file to write> <module jar>...";
    private static final int FAILED_EXIT_CODE = 1;
    private static final int MISSING_ARGUMENT_EXIT_CODE = 2;

    private record IndexedModule(String id, String version) {
    }

    private record IndexedJar(String file, String sha256, List<IndexedModule> modules) {
    }

    private record Index(List<IndexedJar> jars) {
    }

    private ShippedModuleIndex() {
    }

    public static void main(String[] arguments) throws IOException {
        if (arguments.length < 2) {
            System.err.println(USAGE);
            System.exit(MISSING_ARGUMENT_EXIT_CODE);
        }
        List<Path> jars = Arrays.stream(arguments).skip(1).map(Path::of).toList();
        try {
            requireEachClassInOneJar(jars);
            Files.writeString(Path.of(arguments[0]), toJson(indexOf(jars)), StandardCharsets.UTF_8);
        } catch (IllegalStateException problem) {
            System.err.println(problem.getMessage());
            System.exit(FAILED_EXIT_CODE);
        }
    }

    private static String toJson(Index index) {
        return new GsonBuilder().setPrettyPrinting().create().toJson(index) + "\n";
    }

    private static Index indexOf(List<Path> jars) throws IOException {
        List<IndexedJar> indexed = new ArrayList<>();
        for (Path jar : jars) {
            indexed.add(new IndexedJar(jar.getFileName().toString(), hashOf(jar), modulesOf(jar)));
        }
        return new Index(indexed);
    }

    /** A pack loads all its jars in one class loader, which refuses a class that two jars hold. */
    private static void requireEachClassInOneJar(List<Path> jars) throws IOException {
        Map<String, Path> owners = new HashMap<>();
        for (Path jar : jars) {
            for (String classEntry : classEntriesOf(jar)) {
                Path owner = owners.putIfAbsent(classEntry, jar);
                if (owner != null) {
                    throw new IllegalStateException(classEntry + " is in both " + owner.getFileName() + " and " + jar.getFileName()
                            + "; list it under one jar in builtin-modules.txt");
                }
            }
        }
    }

    private static List<String> classEntriesOf(Path jar) throws IOException {
        try (JarFile file = new JarFile(jar.toFile())) {
            return file.stream().map(JarEntry::getName).filter(name -> name.endsWith(CLASS_FILE_SUFFIX)).toList();
        }
    }

    private static List<IndexedModule> modulesOf(Path jar) throws IOException {
        List<IndexedModule> modules = new ArrayList<>();
        for (String entryClass : entryClassesOf(jar)) {
            ModuleInfo info = instantiate(entryClass).info();
            modules.add(new IndexedModule(info.id().toString(), info.version()));
        }
        return modules;
    }

    private static List<String> entryClassesOf(Path jar) throws IOException {
        try (JarFile file = new JarFile(jar.toFile())) {
            JarEntry entryPoints = file.getJarEntry(ENTRY_POINT_FILE);
            if (entryPoints == null) {
                throw new IllegalStateException(jar.getFileName() + " lists no module; give its jar a module line in builtin-modules.txt");
            }
            String listed = new String(file.getInputStream(entryPoints).readAllBytes(), StandardCharsets.UTF_8);
            return listed.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        }
    }

    private static IrisModule instantiate(String entryClass) {
        try {
            return (IrisModule) Class.forName(entryClass).getConstructor().newInstance();
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot create module " + entryClass + " to read its id and version: " + failure, failure);
        }
    }

    private static String hashOf(Path jar) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(HASH_ALGORITHM).digest(Files.readAllBytes(jar)));
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("This JDK has no " + HASH_ALGORITHM, missing);
        }
    }
}
