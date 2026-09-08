package art.arcane.iris.testsupport;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.ProtectionDomain;

public final class ProjectPaths {
    private static final Path MODULE_ROOT = resolveModuleRoot();
    private static final Path REPOSITORY_ROOT = resolveRepositoryRoot(MODULE_ROOT);

    private ProjectPaths() {
    }

    public static Path moduleRoot() {
        return MODULE_ROOT;
    }

    public static Path repositoryRoot() {
        return REPOSITORY_ROOT;
    }

    public static Path moduleFile(String relative) {
        return MODULE_ROOT.resolve(relative).normalize();
    }

    public static Path repositoryFile(String relative) {
        return REPOSITORY_ROOT.resolve(relative).normalize();
    }

    public static File moduleFileAt(String relative) {
        return moduleFile(relative).toFile();
    }

    public static File repositoryFileAt(String relative) {
        return repositoryFile(relative).toFile();
    }

    private static Path resolveModuleRoot() {
        ProtectionDomain domain = ProjectPaths.class.getProtectionDomain();
        CodeSource source = domain == null ? null : domain.getCodeSource();
        URL location = source == null ? null : source.getLocation();

        if (location != null) {
            try {
                Path current = Path.of(location.toURI()).toAbsolutePath().normalize();

                while (current != null) {
                    Path name = current.getFileName();

                    if (name != null && "build".equals(name.toString()) && current.getParent() != null) {
                        return current.getParent();
                    }

                    current = current.getParent();
                }
            } catch (URISyntaxException | IllegalArgumentException ignored) {
                return Path.of("").toAbsolutePath().normalize();
            }
        }

        return Path.of("").toAbsolutePath().normalize();
    }

    private static Path resolveRepositoryRoot(Path moduleRoot) {
        Path current = moduleRoot;

        while (current != null) {
            if (Files.isRegularFile(current.resolve("settings.gradle"))
                    && Files.isDirectory(current.resolve("core/src/main/java"))) {
                return current;
            }

            current = current.getParent();
        }

        return moduleRoot;
    }
}
