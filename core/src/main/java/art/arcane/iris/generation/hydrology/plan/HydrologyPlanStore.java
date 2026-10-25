package art.arcane.iris.generation.hydrology.plan;

import art.arcane.iris.spi.IrisLogging;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class HydrologyPlanStore {
    private static final Pattern SCOPE = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Pattern PLAN_FILE = Pattern.compile("[wr]-(-?\\d+)-(-?\\d+)\\.bin\\.gz(\\..+\\.tmp)?");
    private static final Pattern STAGED_FILE = Pattern.compile("[wr]-(-?\\d+)-(-?\\d+)\\.bin\\.gz\\..+\\.tmp");
    private static final long MAX_FILE_BYTES = 64L << 20;
    private static final int MAX_PLAN_BYTES = 256 << 20;
    private static final int BUFFER = 1 << 16;
    private static final String RUN = UUID.randomUUID().toString().replace("-", "");

    private final HydrologySettings settings;
    private final Path directory;

    public HydrologyPlanStore(Path root, String scope, int keepScopes, HydrologySettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(scope, "scope");
        if (!SCOPE.matcher(scope).matches() || scope.equals(".") || scope.equals("..")) {
            throw new IllegalArgumentException("Hydrology plan scope '" + scope + "' is not a plain directory name.");
        }
        if (keepScopes < 1) {
            throw new IllegalArgumentException("Hydrology plan store must keep at least one scope, got " + keepScopes);
        }
        Path base = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        this.directory = base.resolve(scope);
        try {
            Files.createDirectories(directory);
            Files.setLastModifiedTime(directory, FileTime.from(Instant.now()));
        } catch (IOException failure) {
            throw new UncheckedIOException("Hydrology plan directory " + directory + " could not be opened.", failure);
        }
        sweepStaged(directory);
        prune(base, scope, keepScopes);
    }

    public Optional<WindowPlan> readWindow(WindowKey key) {
        return read(file('w', key.wx(), key.wz()))
                .flatMap(data -> HydrologyPlanCodec.decodeWindow(settings, data))
                .filter(plan -> plan.key().equals(key));
    }

    public void writeWindow(WindowPlan plan) {
        write(file('w', plan.key().wx(), plan.key().wz()), HydrologyPlanCodec.encode(settings, plan));
    }

    public Optional<RegionPlan> readRegion(RegionKey key) {
        return read(file('r', key.rx(), key.rz()))
                .flatMap(data -> HydrologyPlanCodec.decodeRegion(settings, data))
                .filter(plan -> plan.key().equals(key));
    }

    public void writeRegion(RegionPlan plan) {
        write(file('r', plan.key().rx(), plan.key().rz()), HydrologyPlanCodec.encode(settings, plan));
    }

    private Path file(char kind, int x, int z) {
        return directory.resolve(kind + "-" + x + "-" + z + ".bin.gz");
    }

    private static Optional<byte[]> read(Path file) {
        try {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > MAX_FILE_BYTES) {
                return Optional.empty();
            }
            try (InputStream input = new GZIPInputStream(Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS), BUFFER)) {
                byte[] data = input.readNBytes(MAX_PLAN_BYTES + 1);
                return data.length > MAX_PLAN_BYTES ? Optional.empty() : Optional.of(data);
            }
        } catch (IOException failure) {
            return Optional.empty();
        }
    }

    private void write(Path target, byte[] data) {
        try {
            Files.createDirectories(directory);
            Path staged = Files.createTempFile(directory, target.getFileName() + "." + RUN + ".", ".tmp");
            try {
                try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(staged), BUFFER)) {
                    output.write(data);
                }
                move(staged, target);
            } finally {
                Files.deleteIfExists(staged);
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("Hydrology plan " + target + " could not be saved.", failure);
        }
    }

    private static void move(Path staged, Path target) throws IOException {
        try {
            Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void sweepStaged(Path directory) {
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                if (STAGED_FILE.matcher(name).matches() && !name.contains(".bin.gz." + RUN + ".")
                        && Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                    Files.deleteIfExists(entry);
                }
            }
        } catch (IOException failure) {
            IrisLogging.warn("Unfinished hydrology plan files in %s could not be removed: %s", directory, failure.getMessage());
        }
    }

    private static void prune(Path base, String scope, int keepScopes) {
        List<Scope> others = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(base)) {
            for (Path entry : entries) {
                if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS) && !entry.getFileName().toString().equals(scope)) {
                    others.add(new Scope(entry, modified(entry)));
                }
            }
        } catch (IOException failure) {
            IrisLogging.warn("Old hydrology plan scopes in %s could not be listed: %s", base, failure.getMessage());
            return;
        }
        others.sort(Comparator.comparing(Scope::modified).reversed().thenComparing(other -> other.path().getFileName().toString()));
        for (int index = keepScopes - 1; index < others.size(); index++) {
            delete(others.get(index));
        }
    }

    private static FileTime modified(Path directory) {
        try {
            return Files.getLastModifiedTime(directory, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException failure) {
            return FileTime.fromMillis(0L);
        }
    }

    private static void delete(Scope scope) {
        try {
            boolean foreign = false;
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(scope.path())) {
                for (Path entry : entries) {
                    if (PLAN_FILE.matcher(entry.getFileName().toString()).matches() && Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) {
                        Files.deleteIfExists(entry);
                    } else {
                        foreign = true;
                    }
                }
            }
            if (foreign) {
                Files.setLastModifiedTime(scope.path(), scope.modified());
            } else {
                Files.deleteIfExists(scope.path());
            }
        } catch (IOException failure) {
            IrisLogging.warn("Old hydrology plan scope %s could not be removed: %s", scope.path(), failure.getMessage());
        }
    }

    private record Scope(Path path, FileTime modified) {
    }
}
