import org.gradle.api.GradleException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Pins the VolmLib noise natives the Bukkit jar leaves out. The runtime downloads the binary for its platform and
 * accepts it only when the size and SHA-256 recorded here match; the git blob id lets it fetch the exact bytes by
 * content even when the build had no VolmLib checkout to name a commit.
 */
public final class NoiseNativeArtifacts {
    public static final String MANIFEST = "META-INF/iris/noise-natives.properties";
    public static final String RESOURCE_PREFIX = "art/arcane/volmlib/native/";
    public static final String SOURCE_PATH = "shared/native/noise/prebuilt";

    private NoiseNativeArtifacts() {
    }

    public static List<String> generate(Collection<File> jars, File volmLibDirectory, File output) throws IOException {
        Map<String, NativeFile> natives = collect(jars);
        if (natives.isEmpty()) {
            throw new GradleException("No VolmLib noise natives under " + RESOURCE_PREFIX + " in " + jars);
        }
        List<String> warnings = new ArrayList<>();
        String commit = volmLibDirectory == null ? "" : commit(volmLibDirectory, natives, warnings);
        Properties manifest = new Properties();
        manifest.setProperty("commit", commit);
        manifest.setProperty("source", SOURCE_PATH);
        manifest.setProperty("platforms", String.join(",", natives.keySet()));
        for (NativeFile file : natives.values()) {
            manifest.setProperty(file.platform() + ".file", file.name());
            manifest.setProperty(file.platform() + ".size", String.valueOf(file.size()));
            manifest.setProperty(file.platform() + ".sha256", file.sha256());
            manifest.setProperty(file.platform() + ".blob", file.blob());
        }
        Files.createDirectories(output.toPath().getParent());
        try (Writer writer = Files.newBufferedWriter(output.toPath(), StandardCharsets.UTF_8)) {
            manifest.store(writer, null);
        }
        return warnings;
    }

    public static void verify(File artifact) throws IOException {
        try (JarFile jar = new JarFile(artifact)) {
            if (jar.getJarEntry(MANIFEST) == null) {
                throw new GradleException(artifact.getName() + " is missing " + MANIFEST);
            }
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith(RESOURCE_PREFIX) && !name.endsWith("/")) {
                    throw new GradleException(artifact.getName() + " bundles " + name
                            + "; the Bukkit jar downloads noise natives through " + MANIFEST);
                }
            }
        }
    }

    static String sha256(byte[] bytes) {
        return HexFormat.of().formatHex(digest("SHA-256", bytes));
    }

    static String blob(byte[] bytes) {
        byte[] header = ("blob " + bytes.length + "\0").getBytes(StandardCharsets.US_ASCII);
        byte[] object = new byte[header.length + bytes.length];
        System.arraycopy(header, 0, object, 0, header.length);
        System.arraycopy(bytes, 0, object, header.length, bytes.length);
        return HexFormat.of().formatHex(digest("SHA-1", object));
    }

    private static Map<String, NativeFile> collect(Collection<File> jars) throws IOException {
        Map<String, NativeFile> natives = new TreeMap<>();
        for (File file : jars) {
            if (!file.isFile()) {
                continue;
            }
            try (JarFile jar = new JarFile(file)) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (entry.isDirectory() || !name.startsWith(RESOURCE_PREFIX)) {
                        continue;
                    }
                    String[] parts = name.substring(RESOURCE_PREFIX.length()).split("/");
                    if (parts.length != 2) {
                        continue;
                    }
                    byte[] bytes;
                    try (InputStream input = jar.getInputStream(entry)) {
                        bytes = input.readAllBytes();
                    }
                    NativeFile resolved = new NativeFile(parts[0], parts[1], bytes.length, sha256(bytes), blob(bytes));
                    NativeFile previous = natives.putIfAbsent(parts[0], resolved);
                    if (previous != null && !previous.equals(resolved)) {
                        throw new GradleException("Conflicting noise natives for " + parts[0] + " in " + jars);
                    }
                }
            }
        }
        return natives;
    }

    private static String commit(File checkout, Map<String, NativeFile> natives, List<String> warnings) {
        String commit = git(checkout, "log", "-1", "--format=%H", "--", SOURCE_PATH);
        if (commit == null || commit.isEmpty()) {
            warnings.add("Noise natives: no commit touches " + SOURCE_PATH + " in " + checkout + "; downloads fall back to the blob id");
            return "";
        }
        for (NativeFile file : natives.values()) {
            String path = commit + ":" + SOURCE_PATH + "/" + file.platform() + "/" + file.name();
            if (!file.blob().equals(git(checkout, "rev-parse", path))) {
                warnings.add("Noise natives: " + file.platform() + " differs from " + commit.substring(0, 7)
                        + " (uncommitted rebuild?); downloads fall back to the blob id");
                return "";
            }
        }
        String remotes = git(checkout, "branch", "-r", "--contains", commit);
        if (remotes == null || remotes.isEmpty()) {
            warnings.add("Noise natives: " + commit.substring(0, 7) + " is not on any remote branch; downloads fail until it is pushed");
        }
        return commit;
    }

    private static String git(File directory, String... arguments) {
        List<String> command = new ArrayList<>(List.of("git", "-C", directory.getAbsolutePath()));
        command.addAll(List.of(arguments));
        try {
            Process process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0) {
                return null;
            }
            return output;
        } catch (IOException failure) {
            return null;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static byte[] digest(String algorithm, byte[] bytes) {
        try {
            return MessageDigest.getInstance(algorithm).digest(bytes);
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException(missing);
        }
    }

    private record NativeFile(String platform, String name, long size, String sha256, String blob) {
    }
}
