import org.gradle.api.GradleException;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
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

public final class NativeRuntimeArtifacts {
    public static final String MANIFEST = "META-INF/iris/native-runtime.properties";
    private static final String GROUP = "com.github.VolmitSoftware.VolmLib";
    private static final String NATIVE = "art/arcane/volmlib/nativelib/";

    private NativeRuntimeArtifacts() {
    }

    public static Map<String, File> publishedArtifacts(Map<ComponentIdentifier, File> resolved,
                                                       List<String> modules, String version) {
        Map<String, File> artifacts = new TreeMap<>();
        for (Map.Entry<ComponentIdentifier, File> entry : resolved.entrySet()) {
            if (!(entry.getKey() instanceof ModuleComponentIdentifier component)
                    || !GROUP.equals(component.getGroup()) || !version.equals(component.getVersion())
                    || !modules.contains(component.getModule())) {
                throw new GradleException("Native runtime providers must resolve to published coordinates at "
                        + version + ": " + entry.getKey().getDisplayName());
            }
            if (artifacts.put(component.getModule(), entry.getValue()) != null) {
                throw new GradleException("Duplicate native runtime provider: " + component.getModule());
            }
        }
        if (!artifacts.keySet().equals(new TreeSet<>(modules))) {
            throw new GradleException("Published native runtime providers differ: " + artifacts.keySet()
                    + "; expected " + modules);
        }
        return artifacts;
    }

    public static void generate(Map<String, File> artifacts, String version, File output, File rules) throws IOException {
        List<String> manifest = new ArrayList<>();
        manifest.add("modules=" + String.join(",", artifacts.keySet()));
        manifest.add("repository=https://jitpack.io/");
        Set<String> retained = new TreeSet<>();
        for (Map.Entry<String, File> artifact : artifacts.entrySet()) {
            String module = artifact.getKey();
            manifest.add(module + ".coordinate=" + GROUP + ":" + module + ":" + version);
            manifest.add(module + ".sha256=" + digest(artifact.getValue()));
            try (JarFile jar = new JarFile(artifact.getValue())) {
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

    public static void verify(File artifact, List<String> adapters, String version) throws IOException {
        try (JarFile jar = new JarFile(artifact)) {
            JarEntry entry = jar.getJarEntry(MANIFEST);
            if (entry == null) {
                throw new GradleException("Missing native runtime dependency manifest");
            }
            Properties manifest = new Properties();
            try (InputStream input = jar.getInputStream(entry)) {
                manifest.load(input);
            }
            Set<String> expected = new TreeSet<>();
            expected.add("native-common");
            for (String adapter : adapters) {
                expected.add("native-" + adapter);
            }
            Set<String> actual = new TreeSet<>(List.of(manifest.getProperty("modules", "").split(",")));
            if (!expected.equals(actual)) {
                throw new GradleException("Native dependency modules differ: " + actual + "; expected " + expected);
            }
            for (String module : expected) {
                String coordinate = GROUP + ":" + module + ":" + version;
                if (!coordinate.equals(manifest.getProperty(module + ".coordinate"))
                        || !manifest.getProperty(module + ".sha256", "").matches("[0-9a-f]{64}")) {
                    throw new GradleException("Invalid native runtime dependency: " + module);
                }
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

    private static boolean implementation(String name) {
        if (!name.startsWith(NATIVE)) {
            return false;
        }
        String relative = name.substring(NATIVE.length());
        return relative.startsWith("common/") || relative.startsWith("minecraft")
                || relative.matches("v[0-9_]+R[0-9]+/.*");
    }

    private static String digest(File file) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file.toPath())));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
