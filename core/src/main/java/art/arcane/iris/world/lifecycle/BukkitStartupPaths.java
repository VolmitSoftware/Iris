package art.arcane.iris.world.lifecycle;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

public final class BukkitStartupPaths {
    private static final String DEFAULT_BUKKIT_CONFIGURATION = "bukkit.yml";
    private static final String DEFAULT_LEVEL_NAME = "world";
    private static final String DEFAULT_SERVER_PROPERTIES = "server.properties";
    private static final String DEFAULT_WORLD_CONTAINER = ".";

    private final Path bukkitConfiguration;
    private final Path levelRoot;
    private final String levelName;
    private final Path serverProperties;
    private final Path serverRoot;
    private final Path worldContainer;

    private BukkitStartupPaths(
            Path serverRoot,
            Path serverProperties,
            Path bukkitConfiguration,
            Path worldContainer,
            String levelName,
            Path levelRoot
    ) {
        this.serverRoot = Objects.requireNonNull(serverRoot, "serverRoot");
        this.serverProperties = Objects.requireNonNull(serverProperties, "serverProperties");
        this.bukkitConfiguration = Objects.requireNonNull(bukkitConfiguration, "bukkitConfiguration");
        this.worldContainer = Objects.requireNonNull(worldContainer, "worldContainer");
        this.levelName = Objects.requireNonNull(levelName, "levelName");
        this.levelRoot = Objects.requireNonNull(levelRoot, "levelRoot");
    }

    public static BukkitStartupPaths resolveCurrent() throws IOException {
        return resolve(Path.of(""), currentArguments());
    }

    public static BukkitStartupPaths resolve(Path serverWorkingDirectory) throws IOException {
        return resolve(serverWorkingDirectory, currentArguments());
    }

    public static BukkitStartupPaths resolve(Path serverWorkingDirectory, String[] processArguments)
            throws IOException {
        Path serverRoot = canonicalDirectory(
                Objects.requireNonNull(serverWorkingDirectory, "serverWorkingDirectory").toAbsolutePath().normalize(),
                "Server working directory"
        );
        StartupArguments arguments = parseArguments(processArguments);
        Path serverProperties = resolveAgainstServerRoot(
                serverRoot,
                arguments.serverProperties(),
                DEFAULT_SERVER_PROPERTIES,
                "server properties"
        );
        Path bukkitConfiguration = resolveAgainstServerRoot(
                serverRoot,
                arguments.bukkitConfiguration(),
                DEFAULT_BUKKIT_CONFIGURATION,
                "Bukkit configuration"
        );
        String levelName = effectiveLevelName(serverProperties, arguments.levelName());
        Path worldContainer = canonicalPotentialDirectory(
                resolveAgainstServerRoot(
                        serverRoot,
                        arguments.worldContainer() == null
                                ? BukkitWorldConfiguration.readWorldContainer(bukkitConfiguration.toFile())
                                : arguments.worldContainer(),
                        DEFAULT_WORLD_CONTAINER,
                        "world container"
                ),
                "Configured world container"
        );
        Path configuredLevel = parsePath(levelName, "level name");
        Path levelRoot = canonicalPotentialDirectory(
                configuredLevel.isAbsolute()
                        ? configuredLevel
                        : worldContainer.resolve(configuredLevel),
                "Configured level root"
        );
        return new BukkitStartupPaths(
                serverRoot,
                serverProperties,
                bukkitConfiguration,
                worldContainer,
                levelName,
                levelRoot
        );
    }

    public Path bukkitConfiguration() {
        return bukkitConfiguration;
    }

    public Path levelRoot() {
        return levelRoot;
    }

    public String levelName() {
        return levelName;
    }

    public Path serverProperties() {
        return serverProperties;
    }

    public Path serverRoot() {
        return serverRoot;
    }

    public Path worldContainer() {
        return worldContainer;
    }

    private static String[] currentArguments() {
        return applicationArguments(ProcessHandle.current().info().arguments().orElse(new String[0]));
    }

    static String[] applicationArguments(String[] processArguments) {
        String[] arguments = Objects.requireNonNull(processArguments, "processArguments");
        for (int index = 0; index < arguments.length; index++) {
            if (arguments[index].equals("-jar")
                    || arguments[index].equals("-m")
                    || arguments[index].equals("--module")) {
                int applicationStart = Math.min(arguments.length, index + 2);
                return Arrays.copyOfRange(arguments, applicationStart, arguments.length);
            }
        }
        Set<String> optionsWithValues = Set.of(
                "-cp",
                "-classpath",
                "--class-path",
                "-p",
                "--module-path",
                "--upgrade-module-path",
                "--add-modules",
                "--enable-native-access",
                "--limit-modules",
                "--add-exports",
                "--add-opens",
                "--add-reads",
                "--patch-module",
                "--source"
        );
        for (int index = 0; index < arguments.length; index++) {
            String argument = arguments[index];
            if (optionsWithValues.contains(argument)) {
                index++;
                continue;
            }
            if (argument.startsWith("-") || argument.startsWith("@")) {
                continue;
            }
            return Arrays.copyOfRange(arguments, index + 1, arguments.length);
        }
        return new String[0];
    }

    private static StartupArguments parseArguments(String[] processArguments) throws IOException {
        String[] arguments = Objects.requireNonNull(processArguments, "processArguments");
        String serverProperties = null;
        String bukkitConfiguration = null;
        String levelName = null;
        String worldContainer = null;
        for (int index = 0; index < arguments.length; index++) {
            String argument = Objects.requireNonNull(arguments[index], "process argument");
            if (argument.equals("--")) {
                break;
            }
            ParsedArgument parsed = parseArgument(
                    argument,
                    index + 1 < arguments.length ? arguments[index + 1] : null
            );
            if (parsed == null) {
                continue;
            }
            switch (parsed.kind()) {
                case SERVER_PROPERTIES -> serverProperties = parsed.value();
                case BUKKIT_CONFIGURATION -> bukkitConfiguration = parsed.value();
                case LEVEL_NAME -> levelName = parsed.value();
                case WORLD_CONTAINER -> worldContainer = parsed.value();
            }
            if (parsed.consumedFollowing()) {
                index++;
            }
        }
        return new StartupArguments(serverProperties, bukkitConfiguration, levelName, worldContainer);
    }

    private static ParsedArgument parseArgument(String argument, String following) throws IOException {
        ParsedArgument parsed = parseArgument(
                argument,
                following,
                ArgumentKind.SERVER_PROPERTIES,
                "-c",
                "--config"
        );
        if (parsed != null) {
            return parsed;
        }
        parsed = parseArgument(
                argument,
                following,
                ArgumentKind.BUKKIT_CONFIGURATION,
                "-b",
                "--bukkit-settings"
        );
        if (parsed != null) {
            return parsed;
        }
        parsed = parseArgument(
                argument,
                following,
                ArgumentKind.WORLD_CONTAINER,
                "-W",
                "--world-dir",
                "--universe",
                "--world-container"
        );
        if (parsed != null) {
            return parsed;
        }
        return parseArgument(
                argument,
                following,
                ArgumentKind.LEVEL_NAME,
                "-w",
                "--world",
                "--level-name"
        );
    }

    private static ParsedArgument parseArgument(
            String argument,
            String following,
            ArgumentKind kind,
            String... keys
    ) throws IOException {
        for (String key : keys) {
            if (argument.equals(key)) {
                if (following == null || following.isBlank()) {
                    throw new IOException("Startup argument " + key + " requires a value");
                }
                return new ParsedArgument(kind, following, true);
            }
            if (key.length() == 2 && argument.startsWith(key) && argument.length() > key.length()) {
                String value = argument.substring(key.length());
                if (value.startsWith("=")) {
                    value = value.substring(1);
                }
                if (value.isBlank()) {
                    throw new IOException("Startup argument " + key + " requires a value");
                }
                return new ParsedArgument(kind, value, false);
            }
            String prefix = key + "=";
            if (argument.startsWith(prefix)) {
                String value = argument.substring(prefix.length());
                if (value.isBlank()) {
                    throw new IOException("Startup argument " + key + " requires a value");
                }
                return new ParsedArgument(kind, value, false);
            }
        }
        return null;
    }

    private static Path resolveAgainstServerRoot(
            Path serverRoot,
            String configuredValue,
            String defaultValue,
            String label
    ) throws IOException {
        String value = configuredValue == null ? defaultValue : configuredValue;
        Path configured = parsePath(value, label);
        return configured.isAbsolute()
                ? configured.normalize()
                : serverRoot.resolve(configured).normalize();
    }

    private static Path parsePath(String value, String label) throws IOException {
        if (value == null || value.isBlank()) {
            throw new IOException("Configured " + label + " is empty");
        }
        try {
            return Path.of(value);
        } catch (InvalidPathException exception) {
            throw new IOException("Configured " + label + " is invalid", exception);
        }
    }

    private static String effectiveLevelName(Path serverProperties, String argumentOverride) throws IOException {
        if (argumentOverride != null) {
            return requireValue(argumentOverride, "level name");
        }
        if (!Files.exists(serverProperties, LinkOption.NOFOLLOW_LINKS)) {
            return DEFAULT_LEVEL_NAME;
        }
        if (!Files.isRegularFile(serverProperties)) {
            throw new IOException("Configured server properties is not a regular file: " + serverProperties);
        }
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(serverProperties)) {
            properties.load(input);
        }
        return requireValue(properties.getProperty("level-name", DEFAULT_LEVEL_NAME), "level name");
    }

    private static String requireValue(String value, String label) throws IOException {
        if (value == null || value.isBlank()) {
            throw new IOException("Configured " + label + " is empty");
        }
        return value;
    }

    private static Path canonicalDirectory(Path path, String label) throws IOException {
        Path canonical = path.toRealPath();
        if (!Files.isDirectory(canonical)) {
            throw new IOException(label + " is not a directory: " + path);
        }
        return canonical;
    }

    private static Path canonicalPotentialDirectory(Path path, String label) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        ArrayList<Path> missing = new ArrayList<>();
        Path existing = normalized;
        while (!Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            Path fileName = existing.getFileName();
            Path parent = existing.getParent();
            if (fileName == null || parent == null) {
                throw new IOException(label + " has no existing filesystem ancestor: " + normalized);
            }
            missing.add(fileName);
            existing = parent;
        }
        Path canonical = canonicalDirectory(existing, label);
        for (int index = missing.size() - 1; index >= 0; index--) {
            canonical = canonical.resolve(missing.get(index));
        }
        return canonical.normalize();
    }

    private enum ArgumentKind {
        SERVER_PROPERTIES,
        BUKKIT_CONFIGURATION,
        LEVEL_NAME,
        WORLD_CONTAINER
    }

    private record ParsedArgument(ArgumentKind kind, String value, boolean consumedFollowing) {
    }

    private record StartupArguments(
            String serverProperties,
            String bukkitConfiguration,
            String levelName,
            String worldContainer
    ) {
    }
}
