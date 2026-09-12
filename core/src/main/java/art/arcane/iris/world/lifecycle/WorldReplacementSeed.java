package art.arcane.iris.world.lifecycle;

import art.arcane.volmlib.util.nbt.io.NBTUtil;
import art.arcane.volmlib.util.nbt.io.NamedTag;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import art.arcane.volmlib.util.nbt.tag.LongTag;
import art.arcane.volmlib.util.nbt.tag.Tag;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.OptionalLong;

public final class WorldReplacementSeed {
    private static final Path WORLD_GEN_SETTINGS = Path.of("data/minecraft/world_gen_settings.dat");

    private WorldReplacementSeed() {
    }

    public static long readAuthoritativeSeed(Path worldDirectory) throws IOException {
        Path requiredWorldDirectory = Objects.requireNonNull(worldDirectory, "worldDirectory")
                .toAbsolutePath()
                .normalize();
        Path settings = requiredWorldDirectory.resolve(WORLD_GEN_SETTINGS);
        NamedTag namedTag = readSettings(settings);
        return requireData(namedTag, settings).getLongTag("seed").asLong();
    }

    public static long stageAuthoritativeSeed(
            Path sourceWorldDirectory,
            Path stagedWorldDirectory,
            OptionalLong requestedSeed
    ) throws IOException {
        Path sourceWorld = Objects.requireNonNull(sourceWorldDirectory, "sourceWorldDirectory")
                .toAbsolutePath()
                .normalize();
        Path stagedWorld = Objects.requireNonNull(stagedWorldDirectory, "stagedWorldDirectory")
                .toAbsolutePath()
                .normalize();
        OptionalLong requiredRequestedSeed = Objects.requireNonNull(requestedSeed, "requestedSeed");
        Path source = sourceWorld.resolve(WORLD_GEN_SETTINGS);
        NamedTag namedTag = readSettings(source);
        CompoundTag data = requireData(namedTag, source);
        long retainedSeed = data.getLongTag("seed").asLong();
        long effectiveSeed = requiredRequestedSeed.orElse(retainedSeed);
        writeSettings(stagedWorld, namedTag, data, effectiveSeed);
        return effectiveSeed;
    }

    public static void copyWithAuthoritativeSeed(
            Path sourceWorldDirectory,
            Path targetWorldDirectory,
            long seed
    ) throws IOException {
        Path sourceWorld = Objects.requireNonNull(sourceWorldDirectory, "sourceWorldDirectory")
                .toAbsolutePath()
                .normalize();
        Path targetWorld = Objects.requireNonNull(targetWorldDirectory, "targetWorldDirectory")
                .toAbsolutePath()
                .normalize();
        Path source = sourceWorld.resolve(WORLD_GEN_SETTINGS);
        NamedTag namedTag = readSettings(source);
        CompoundTag data = requireData(namedTag, source);
        writeSettings(targetWorld, namedTag, data, seed);
    }

    private static void writeSettings(
            Path targetWorld,
            NamedTag namedTag,
            CompoundTag data,
            long seed
    ) throws IOException {
        Path target = targetWorld.resolve(WORLD_GEN_SETTINGS);
        data.putLong("seed", seed);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(target)) {
            throw new IOException("Staged Paper world generation settings already exist: " + target);
        }
        Path parent = target.getParent();
        if (parent == null) {
            throw new IOException("Staged Paper world generation settings have no parent: " + target);
        }
        Files.createDirectories(parent);
        Path staged = Files.createTempFile(parent, ".world-gen-settings-", ".dat");
        try {
            NBTUtil.write(namedTag, staged.toFile(), true);
            try (FileChannel channel = FileChannel.open(staged, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(staged, target);
            }
            forceSettingsHierarchy(targetWorld, parent);
        } finally {
            Files.deleteIfExists(staged);
        }

        long writtenSeed = readAuthoritativeSeed(targetWorld);
        if (writtenSeed != seed) {
            throw new IOException("Staged Paper world generation settings did not retain the requested seed.");
        }
    }

    private static void forceSettingsHierarchy(Path targetWorld, Path settingsParent) throws IOException {
        Path directory = settingsParent;
        while (directory != null && directory.startsWith(targetWorld)) {
            DirectoryDurability.forceDirectoryRequired(directory);
            if (directory.equals(targetWorld)) {
                return;
            }
            directory = directory.getParent();
        }
        throw new IOException("Staged Paper world generation settings escaped their target directory.");
    }

    private static NamedTag readSettings(Path settings) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                settings,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS
        );
        if (attributes.isSymbolicLink() || !attributes.isRegularFile()) {
            throw new IOException("Paper world generation settings are not a regular file: " + settings);
        }

        try {
            return NBTUtil.read(settings.toFile());
        } catch (IOException failure) {
            throw new IOException("Could not read Paper world generation settings: " + settings, failure);
        }
    }

    private static CompoundTag requireData(NamedTag namedTag, Path settings) throws IOException {
        Tag<?> rootTag = namedTag.getTag();
        if (!(rootTag instanceof CompoundTag root)) {
            throw new IOException("Paper world generation settings must have a compound root: " + settings);
        }
        Tag<?> dataTag = root.get("data");
        if (dataTag == null) {
            throw new IOException("Paper world generation settings are missing data: " + settings);
        }
        if (!(dataTag instanceof CompoundTag data)) {
            throw new IOException("Paper world generation settings data must be a compound tag: " + settings);
        }
        Tag<?> seedTag = data.get("seed");
        if (seedTag == null) {
            throw new IOException("Paper world generation settings are missing data.seed: " + settings);
        }
        if (!(seedTag instanceof LongTag seed)) {
            throw new IOException("Paper world generation settings data.seed must be a long tag: " + settings);
        }
        return data;
    }
}
