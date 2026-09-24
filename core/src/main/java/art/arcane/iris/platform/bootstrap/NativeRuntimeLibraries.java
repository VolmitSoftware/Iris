package art.arcane.iris.platform.bootstrap;

import art.arcane.volmlib.nativelib.NativeVersion;
import art.arcane.volmlib.nativelib.NativeAdapters;
import io.github.slimjar.app.builder.ApplicationBuilder;
import io.github.slimjar.downloader.DependencyDownloader;
import io.github.slimjar.downloader.URLDependencyDownloader;
import io.github.slimjar.downloader.strategy.FilePathStrategy;
import io.github.slimjar.downloader.verify.FileChecksumCalculator;
import io.github.slimjar.relocation.helper.RelocationHelper;
import io.github.slimjar.relocation.helper.VerifyingRelocationHelperFactory;
import io.github.slimjar.relocation.meta.FlatFileMetaMediatorFactory;
import io.github.slimjar.exceptions.DownloaderException;
import io.github.slimjar.resolver.data.Dependency;
import io.github.slimjar.resolver.data.DependencyData;
import io.github.slimjar.resolver.data.Repository;
import io.github.slimjar.resolver.reader.dependency.DependencyDataProvider;
import io.github.slimjar.resolver.reader.dependency.DependencyReader;
import io.github.slimjar.resolver.reader.dependency.WrappingDependencyDataProviderFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

public final class NativeRuntimeLibraries {
    private static final String MANIFEST = "META-INF/iris/native-runtime.properties";
    private final Map<Dependency, String> dependencies;
    private final Repository repository;

    private NativeRuntimeLibraries(Map<Dependency, String> dependencies, Repository repository) {
        this.dependencies = dependencies;
        this.repository = repository;
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

    static NativeRuntimeLibraries from(Properties manifest, String minecraftVersion) throws IOException {
        String storage = manifest.getProperty("storage", "repository");
        if (!storage.equals("repository") && !storage.equals("embedded")) {
            throw new IllegalStateException("Invalid native runtime storage: " + storage);
        }
        boolean embedded = storage.equals("embedded");
        NativeVersion version = NativeVersion.resolve(minecraftVersion).orElseThrow(
                () -> new UnsupportedOperationException("No native provider for Minecraft " + minecraftVersion));
        List<String> modules = List.of(manifest.getProperty("modules", "").split(","));
        Map<Dependency, String> selected = new LinkedHashMap<>();
        for (String module : List.of("native-common", "native-" + version.packageName())) {
            if (!modules.contains(module)) {
                throw new UnsupportedOperationException("Native provider is not distributed: " + module);
            }
            String[] coordinate = manifest.getProperty(module + ".coordinate", "").split(":");
            String digest = manifest.getProperty(module + ".sha256", "");
            if (coordinate.length != 3 || !coordinate[0].equals("com.github.VolmitSoftware.VolmLib")
                    || !coordinate[1].equals(module) || coordinate[2].isBlank() || !digest.matches("[0-9a-f]{64}")
                    || embedded && !coordinate[2].equals("embedded-" + digest)) {
                throw new IllegalStateException("Invalid native dependency manifest entry: " + module);
            }
            selected.put(new Dependency(coordinate[0], coordinate[1], coordinate[2], null, List.of()), digest);
        }
        Repository repository = embedded ? null : new Repository(URI.create(manifest.getProperty("repository")).toURL());
        return new NativeRuntimeLibraries(Map.copyOf(selected), repository);
    }

    public <T extends ApplicationBuilder<?>> T configure(T builder, Path downloads) {
        WrappingDependencyDataProviderFactory defaults = new WrappingDependencyDataProviderFactory(DependencyReader.DEFAULT);
        builder.dataProviderFactory(url -> {
            DependencyDataProvider provider = defaults.create(url);
            return () -> merge(provider.get());
        });
        VerifyingRelocationHelperFactory relocation = new VerifyingRelocationHelperFactory(
                new FileChecksumCalculator("SHA-256"),
                FilePathStrategy.createRelocationStrategy(downloads.toFile(), "Iris"),
                new FlatFileMetaMediatorFactory());
        builder.relocationHelperFactory(relocator -> {
            RelocationHelper helper = relocation.create(relocator);
            return (dependency, file) -> dependencies.containsKey(dependency) ? file : helper.relocate(dependency, file);
        });
        builder.downloaderFactory((output, resolver, verifier) -> {
            DependencyDownloader delegate = new URLDependencyDownloader(output, resolver, verifier);
            return dependency -> {
                Optional<File> result = delegate.download(dependency);
                String expected = dependencies.get(dependency);
                if (expected != null) {
                    File file = result.orElseThrow(() -> new DownloaderException("Native dependency has no jar: " + dependency));
                    verify(file, expected);
                }
                return result;
            };
        });
        return builder;
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

    DependencyData merge(DependencyData original) {
        if (repository == null) {
            return original;
        }
        List<Dependency> combined = new ArrayList<>(original.dependencies());
        combined.addAll(dependencies.keySet());
        List<Repository> repositories = new ArrayList<>(original.repositories());
        if (!repositories.contains(repository)) {
            repositories.add(repository);
        }
        return new DependencyData(original.mirrors(), repositories, combined, original.relocations());
    }

    List<URL> providerUrls(Path downloads, ClassLoader resources) throws IOException {
        List<URL> urls = new ArrayList<>(dependencies.size());
        for (Map.Entry<Dependency, String> entry : dependencies.entrySet()) {
            Dependency dependency = entry.getKey();
            Path artifact = downloads.resolve(dependency.groupId().replace('.', '/'))
                    .resolve(dependency.artifactId()).resolve(dependency.version())
                    .resolve(dependency.artifactId() + "-" + dependency.version() + ".jar");
            if (repository == null) {
                extractEmbedded(resources, dependency.artifactId(), artifact, entry.getValue());
            } else {
                verify(artifact.toFile(), entry.getValue());
            }
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
                verify(temporary.toFile(), expected);
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

    static void verify(File file, String expected) {
        try {
            String actual = digest(file.toPath());
            if (!actual.equals(expected)) {
                throw new DownloaderException("Native dependency does not match this Iris build: " + file.getName());
            }
        } catch (IOException exception) {
            throw new DownloaderException("Cannot verify native dependency: " + file, exception);
        }
    }

}
