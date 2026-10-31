package art.arcane.iris.platform;

import art.arcane.iris.spi.VanillaDataSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class ClasspathVanillaDataSource implements VanillaDataSource {
    private static final String MARKER = "/data/.mcassetsroot";
    private static final String DATA_DIRECTORY = "data";

    private final Supplier<Class<?>> anchor;
    private volatile Path root;

    public ClasspathVanillaDataSource(Supplier<Class<?>> anchor) {
        this.anchor = Objects.requireNonNull(anchor, "anchor");
    }

    @Override
    public Optional<byte[]> read(String dataPath) {
        Path file = resolve(segments(dataPath));
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(file));
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not read vanilla data " + dataPath, failure);
        }
    }

    @Override
    public List<String> list(String prefix) {
        Objects.requireNonNull(prefix, "prefix");
        String directoryPath = prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix;
        Path directory = resolve(segments(directoryPath));
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        Path base = root();
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile)
                    .map(file -> dataPath(base, file))
                    .sorted()
                    .toList();
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not list vanilla data under " + prefix, failure);
        }
    }

    private Path resolve(List<String> segments) {
        Path resolved = root();
        for (String segment : segments) {
            resolved = resolved.resolve(segment);
        }
        return resolved;
    }

    private Path root() {
        Path resolved = root;
        if (resolved != null) {
            return resolved;
        }
        Class<?> type = Objects.requireNonNull(anchor.get(), "Vanilla data anchor class");
        URL marker = type.getResource(MARKER);
        if (marker == null) {
            throw new IllegalStateException("Vanilla data marker " + MARKER + " is not visible from " + type.getName());
        }
        resolved = markerPath(marker).getParent().getParent();
        root = resolved;
        return resolved;
    }

    private static Path markerPath(URL marker) {
        URI uri = markerUri(marker);
        try {
            return Path.of(uri);
        } catch (FileSystemNotFoundException unopened) {
            return openArchivePath(uri);
        }
    }

    private static URI markerUri(URL marker) {
        try {
            return marker.toURI();
        } catch (URISyntaxException failure) {
            throw new IllegalStateException("Vanilla data marker has an invalid location " + marker, failure);
        }
    }

    private static Path openArchivePath(URI uri) {
        try {
            return FileSystems.newFileSystem(uri, Map.of()).provider().getPath(uri);
        } catch (FileSystemAlreadyExistsException opened) {
            return Path.of(uri);
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not open the vanilla data archive " + uri, failure);
        }
    }

    private static List<String> segments(String dataPath) {
        Objects.requireNonNull(dataPath, "dataPath");
        String[] parts = dataPath.split("/", -1);
        if (parts.length < 2 || !DATA_DIRECTORY.equals(parts[0]) || dataPath.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Vanilla data paths have the form data/<namespace>/<path>: " + dataPath);
        }
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                throw new IllegalArgumentException("Vanilla data path has an empty or relative segment: " + dataPath);
            }
        }
        return List.of(parts);
    }

    private static String dataPath(Path base, Path file) {
        Path relative = base.relativize(file);
        List<String> names = new ArrayList<>(relative.getNameCount());
        for (Path name : relative) {
            names.add(name.toString());
        }
        return String.join("/", names);
    }
}
