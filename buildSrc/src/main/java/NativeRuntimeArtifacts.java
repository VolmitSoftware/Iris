import org.gradle.api.GradleException;
import org.tukaani.xz.XZInputStream;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class NativeRuntimeArtifacts {
    public static final String MANIFEST = "META-INF/iris/native-runtime.properties";
    public static final String RESOURCES = "META-INF/iris/native/";
    private static final String PAYLOAD = "META-INF/volmit/runtime.jar.xz";
    private static final String GROUP = "com.github.VolmitSoftware.VolmLib";
    private static final String NATIVE = "art/arcane/volmlib/nativelib/";

    private NativeRuntimeArtifacts() {
    }

    public static void generate(Map<String, File> natives, List<String> modules, File output, File rules) throws IOException {
        requireModules(natives, modules);
        Map<String, File> providers = new TreeMap<>(natives);
        List<String> manifest = new ArrayList<>();
        manifest.add("modules=" + String.join(",", providers.keySet()));
        manifest.add("storage=embedded");
        Set<String> retained = new TreeSet<>();
        for (Map.Entry<String, File> provider : providers.entrySet()) {
            String module = provider.getKey();
            String digest = digest(Files.readAllBytes(provider.getValue().toPath()));
            manifest.add(module + ".coordinate=" + GROUP + ":" + module + ":embedded-" + digest);
            manifest.add(module + ".sha256=" + digest);
            try (JarFile jar = new JarFile(provider.getValue())) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (!entry.getName().endsWith(".class")) {
                        continue;
                    }
                    try (InputStream input = jar.getInputStream(entry)) {
                        for (String reference : ClassReferences.read(input.readAllBytes())) {
                            if (reference.startsWith("art/arcane/volmlib/") && !implementation(reference)) {
                                retained.add(reference);
                            }
                        }
                    }
                }
            }
        }
        File manifestFile = new File(output, MANIFEST);
        Files.createDirectories(manifestFile.toPath().getParent());
        Files.write(manifestFile.toPath(), manifest, StandardCharsets.UTF_8);
        Path runtimeRoots = output.toPath().resolve("META-INF/volmit/runtime-roots.list");
        Files.createDirectories(runtimeRoots.getParent());
        Files.write(runtimeRoots, retained, StandardCharsets.UTF_8);
        List<String> keep = new ArrayList<>();
        keep.add("-keep class art.arcane.volmlib.nativelib.** { *; }");
        for (String reference : retained) {
            keep.add("-keep class " + reference.replace('/', '.') + " { *; }");
        }
        Files.createDirectories(rules.toPath().getParent());
        Files.write(rules.toPath(), keep, StandardCharsets.UTF_8);
    }

    public static void verify(File artifact, List<String> modules, Map<String, File> natives) throws IOException {
        requireModules(natives, modules);
        try (JarFile jar = new JarFile(artifact)) {
            JarEntry entry = jar.getJarEntry(MANIFEST);
            if (entry == null) {
                throw new GradleException("Missing native runtime dependency manifest");
            }
            try (InputStream input = jar.getInputStream(entry)) {
                verifyManifest(input.readAllBytes(), natives);
            }
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.endsWith(".class") && implementation(name)) {
                    throw new GradleException("Native runtime implementation is bundled: " + name);
                }
            }
        }
    }

    public static void verifyPacked(File artifact, List<String> modules, Map<String, File> natives) throws IOException {
        requireModules(natives, modules);
        Map<String, byte[]> contents;
        try (JarFile jar = new JarFile(artifact)) {
            JarEntry payload = jar.getJarEntry(PAYLOAD);
            if (payload == null) {
                contents = read(Files.newInputStream(artifact.toPath()));
            } else {
                try (InputStream input = new XZInputStream(jar.getInputStream(payload))) {
                    contents = read(new ByteArrayInputStream(input.readAllBytes()));
                }
                JarEntry outer = jar.getJarEntry(MANIFEST);
                byte[] embedded = contents.get(MANIFEST);
                if (outer != null && embedded != null) {
                    try (InputStream input = jar.getInputStream(outer)) {
                        if (!Arrays.equals(input.readAllBytes(), embedded)) {
                            throw new GradleException("Packed native runtime manifests differ");
                        }
                    }
                }
            }
        }
        byte[] manifest = contents.get(MANIFEST);
        if (manifest == null) {
            throw new GradleException("Missing native runtime dependency manifest");
        }
        verifyManifest(manifest, natives);
        for (String module : natives.keySet()) {
            byte[] provider = contents.get(RESOURCES + module + ".jar");
            if (provider == null) {
                throw new GradleException("Missing embedded native provider: " + module);
            }
            if (!digest(provider).equals(digest(Files.readAllBytes(natives.get(module).toPath())))) {
                throw new GradleException("Embedded native provider differs from the resolved build: " + module);
            }
        }
    }

    private static void verifyManifest(byte[] bytes, Map<String, File> natives) throws IOException {
        Properties manifest = new Properties();
        manifest.load(new ByteArrayInputStream(bytes));
        Set<String> actual = new TreeSet<>(List.of(manifest.getProperty("modules", "").split(",")));
        if (!natives.keySet().equals(actual)) {
            throw new GradleException("Native dependency modules differ: " + actual + "; expected " + natives.keySet());
        }
        if (!"embedded".equals(manifest.getProperty("storage"))) {
            throw new GradleException("Native runtime providers must be embedded: " + manifest.getProperty("storage"));
        }
        for (Map.Entry<String, File> provider : natives.entrySet()) {
            String module = provider.getKey();
            String digest = digest(Files.readAllBytes(provider.getValue().toPath()));
            if (!digest.equals(manifest.getProperty(module + ".sha256"))
                    || !(GROUP + ":" + module + ":embedded-" + digest).equals(manifest.getProperty(module + ".coordinate"))) {
                throw new GradleException("Native runtime manifest does not describe the embedded provider: " + module);
            }
        }
    }

    private static void requireModules(Map<String, File> natives, List<String> modules) {
        if (!natives.keySet().equals(new TreeSet<>(modules))) {
            throw new GradleException("Embedded native providers differ: " + natives.keySet() + "; expected " + modules);
        }
    }

    private static Map<String, byte[]> read(InputStream source) throws IOException {
        Map<String, byte[]> contents = new TreeMap<>();
        try (ZipInputStream archive = new ZipInputStream(source)) {
            for (ZipEntry entry = archive.getNextEntry(); entry != null; entry = archive.getNextEntry()) {
                if (!entry.isDirectory()) {
                    contents.put(entry.getName(), archive.readAllBytes());
                }
            }
        }
        return contents;
    }

    private static boolean implementation(String name) {
        if (!name.startsWith(NATIVE)) {
            return false;
        }
        String relative = name.substring(NATIVE.length());
        return relative.startsWith("common/") || relative.startsWith("minecraft")
                || relative.matches("v[0-9_]+R[0-9]+/.*");
    }

    private static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
