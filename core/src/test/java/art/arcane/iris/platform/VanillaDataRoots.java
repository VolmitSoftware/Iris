package art.arcane.iris.platform;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

final class VanillaDataRoots {
    private static final String MARKER = "data/.mcassetsroot";

    private VanillaDataRoots() {
    }

    static URLClassLoader directory(Path folder, Map<String, String> files) throws IOException {
        return directoryLoader(folder, entries(files, true));
    }

    static URLClassLoader withoutMarker(Path folder) throws IOException {
        return directoryLoader(folder, entries(Map.of(), false));
    }

    static URLClassLoader jar(Path jar, Map<String, String> files) throws IOException {
        try (OutputStream output = Files.newOutputStream(jar); JarOutputStream archive = new JarOutputStream(output)) {
            for (Map.Entry<String, byte[]> entry : entries(files, true).entrySet()) {
                archive.putNextEntry(new JarEntry(entry.getKey()));
                archive.write(entry.getValue());
                archive.closeEntry();
            }
        }
        return new URLClassLoader(new URL[]{jar.toUri().toURL()}, null);
    }

    static Class<?> anchor(ClassLoader loader) throws ClassNotFoundException {
        return Class.forName(VanillaDataRoots.class.getName(), false, loader);
    }

    private static URLClassLoader directoryLoader(Path folder, Map<String, byte[]> entries) throws IOException {
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            Path target = folder.resolve(entry.getKey());
            Files.createDirectories(target.getParent());
            Files.write(target, entry.getValue());
        }
        return new URLClassLoader(new URL[]{folder.toUri().toURL()}, null);
    }

    private static Map<String, byte[]> entries(Map<String, String> files, boolean marker) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        String anchorEntry = VanillaDataRoots.class.getName().replace('.', '/') + ".class";
        try (InputStream input = VanillaDataRoots.class.getClassLoader().getResourceAsStream(anchorEntry)) {
            if (input == null) {
                throw new IOException("Missing compiled anchor " + anchorEntry);
            }
            entries.put(anchorEntry, input.readAllBytes());
        }
        if (marker) {
            entries.put(MARKER, new byte[0]);
        }
        for (Map.Entry<String, String> file : files.entrySet()) {
            entries.put(file.getKey(), file.getValue().getBytes(StandardCharsets.UTF_8));
        }
        return entries;
    }
}
