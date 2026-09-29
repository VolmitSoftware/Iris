package art.arcane.iris.platform.bootstrap;

import art.arcane.volmlib.nativelib.NativeVersion;
import art.arcane.volmlib.nativelib.NativeAdapters;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;

public final class NativeRuntimeLibraries {
    private static final String MANIFEST = "META-INF/iris/native-runtime.properties";
    private static final String GROUP = "com.github.VolmitSoftware.VolmLib";
    private final List<Provider> providers;

    private NativeRuntimeLibraries(List<Provider> providers) {
        this.providers = providers;
    }

    public static NativeRuntimeLibraries load(String minecraftVersion) {
        try (InputStream input = NativeRuntimeLibraries.class.getClassLoader().getResourceAsStream(MANIFEST)) {
            if (input == null) {
                throw new IllegalStateException("Missing native runtime dependency manifest");
            }
            Properties manifest = new Properties();
            manifest.load(input);
            return from(manifest, minecraftVersion);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read native runtime dependency manifest", exception);
        }
    }

    static NativeRuntimeLibraries from(Properties manifest, String minecraftVersion) {
        String storage = manifest.getProperty("storage");
        if (!"embedded".equals(storage)) {
            throw new IllegalStateException("Invalid native runtime storage: " + storage);
        }
        NativeVersion version = NativeVersion.resolve(minecraftVersion).orElseThrow(
                () -> new UnsupportedOperationException("No native provider for Minecraft " + minecraftVersion));
        List<String> modules = List.of(manifest.getProperty("modules", "").split(","));
        List<Provider> selected = new ArrayList<>(2);
        for (String module : List.of("native-common", "native-" + version.packageName())) {
            if (!modules.contains(module)) {
                throw new UnsupportedOperationException("Native provider is not distributed: " + module);
            }
            String digest = manifest.getProperty(module + ".sha256", "");
            String coordinate = GROUP + ":" + module + ":embedded-" + digest;
            if (!digest.matches("[0-9a-f]{64}") || !coordinate.equals(manifest.getProperty(module + ".coordinate"))) {
                throw new IllegalStateException("Invalid native dependency manifest entry: " + module);
            }
            selected.add(new Provider(module, "embedded-" + digest, digest));
        }
        return new NativeRuntimeLibraries(List.copyOf(selected));
    }

    public URLClassLoader openProviderLoader(Path downloads) {
        try {
            List<URL> urls = providerUrls(downloads, NativeRuntimeLibraries.class.getClassLoader());
            URLClassLoader loader = new URLClassLoader("Iris native providers", urls.toArray(URL[]::new),
                    NativeAdapters.class.getClassLoader());
            NativeAdapters.registerProviderLoader(loader);
            return loader;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot open native provider loader", exception);
        }
    }

    List<URL> providerUrls(Path downloads, ClassLoader resources) throws IOException {
        List<URL> urls = new ArrayList<>(providers.size());
        for (Provider provider : providers) {
            Path artifact = downloads.resolve(GROUP.replace('.', '/'))
                    .resolve(provider.module()).resolve(provider.version())
                    .resolve(provider.module() + "-" + provider.version() + ".jar");
            extractEmbedded(resources, provider.module(), artifact, provider.sha256());
            urls.add(artifact.toUri().toURL());
        }
        return urls;
    }

    private static void extractEmbedded(ClassLoader resources, String module, Path artifact, String expected) throws IOException {
        if (Files.isRegularFile(artifact) && digest(artifact).equals(expected)) {
            return;
        }
        String resource = "META-INF/iris/native/" + module + ".jar";
        try (InputStream input = resources.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Missing embedded native provider: " + resource);
            }
            Files.createDirectories(artifact.getParent());
            Path temporary = Files.createTempFile(artifact.getParent(), module + "-", ".tmp");
            try {
                Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
                verify(temporary, expected);
                Files.move(temporary, artifact, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static String digest(Path file) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    static void verify(Path file, String expected) throws IOException {
        if (!digest(file).equals(expected)) {
            throw new IOException("Native dependency does not match this Iris build: " + file.getFileName());
        }
    }

    private record Provider(String module, String version, String sha256) {
    }
}
