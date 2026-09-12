/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.modded;

import art.arcane.iris.generation.runtime.IrisEngineMantle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class MainWorldService {
    private static final String MARKER_NAME = "mainworld.pending";
    private static final String PROPERTIES_NAME = "server.properties";
    /**
     * Distinct exit status for the staged main-world restart, so a wrapper can tell it apart from a clean
     * operator stop (0) and from a crash. 64 is the conventional first application-defined status.
     */
    private static final int AUTO_RESTART_EXIT_STATUS = 64;
    private static final String[] VANILLA_DIMENSION_FOLDERS = {
            "region",
            "entities",
            "poi",
            IrisEngineMantle.STORAGE_FOLDER_NAME,
            "dimensions/minecraft/overworld",
            "dimensions/minecraft/the_nether",
            "dimensions/minecraft/the_end",
            "DIM-1",
            "DIM1"
    };

    private MainWorldService() {
    }

    public static String presetIdFor(String packRef) {
        String value = packRef.trim();
        int colon = value.indexOf(':');
        String pack = colon >= 0 ? value.substring(0, colon) : value;
        String dimension = colon >= 0 ? value.substring(colon + 1) : value;
        return ModdedWorldgenIds.presetRef(pack, dimension);
    }

    public static void reconcileEarly() {
        try {
            ModdedModConfig config = ModdedModConfig.get();
            String pack = config.mainWorldPack();
            if (pack == null || pack.isBlank()) {
                return;
            }
            Path instanceRoot = verifiedInstanceRoot("reconcile the Iris main world");
            if (instanceRoot == null) {
                return;
            }
            Path properties = instanceRoot.resolve(PROPERTIES_NAME);
            String target = presetIdFor(pack);
            String currentType = readProperty(properties, "level-type");
            if (!target.equals(currentType)) {
                writeLevelProperties(properties, target, config.mainWorldSeed());
                markPending();
                ModdedIrisLog.warn("Iris main world '{}' staged: {} level-type set to {}. Restart again to generate it (this boot still uses the previous overworld; player data is kept).",
                        pack, properties, target);
                if (config.mainWorldAutoRestart()) {
                    ModdedIrisLog.warn("Iris mainWorldAutoRestart is enabled; stopping the JVM now with exit status {} so a restart wrapper brings the server back on the new main world.",
                            AUTO_RESTART_EXIT_STATUS);
                    ModdedIrisLog.warn("Configure the start script to restart the server on exit status {} (status 0 means a clean stop, so it must not be reused for this).",
                            AUTO_RESTART_EXIT_STATUS);
                    System.exit(AUTO_RESTART_EXIT_STATUS);
                }
                return;
            }
            if (!isPending()) {
                return;
            }
            Path worldRoot;
            try {
                worldRoot = resolveWorldRoot(instanceRoot, properties);
            } catch (MissingWorldRootException missing) {
                // First boot of a brand new instance, or a --universe/--world layout Iris cannot see from mod
                // bootstrap: there is no prior overworld to move aside, so this is nothing to quarantine, not a
                // reason to refuse startup.
                clearPending();
                ModdedIrisLog.warn("Iris main world '{}' had nothing to quarantine: {} does not exist. Continuing boot; the overworld generates as {}.",
                        pack, missing.path(), target);
                return;
            }
            Path recovery = quarantineVanillaDimensions(worldRoot);
            clearPending();
            ModdedIrisLog.warn("Iris main world '{}' generated fresh: moved the prior overworld/nether/end data from {} to {} so this boot regenerates them as {} (player data kept).",
                    pack, worldRoot, recovery, target);
        } catch (Throwable e) {
            ModdedIrisLog.error("Iris main world reconciliation failed", e);
            throw new IllegalStateException(
                    "Iris refused startup after main-world reconciliation failed", e);
        }
    }

    public static boolean stage(String packRef, long seed) {
        if (ModdedEngineBootstrap.loader().clientEnvironment()) {
            ModdedIrisLog.error("Iris main-world replacement is only available on dedicated servers; use the Create World generator selector in singleplayer");
            return false;
        }
        Path instanceRoot = verifiedInstanceRoot("stage the Iris main world");
        if (instanceRoot == null) {
            return false;
        }
        try {
            writeLevelProperties(instanceRoot.resolve(PROPERTIES_NAME), presetIdFor(packRef), seed);
            markPending();
            return true;
        } catch (IOException e) {
            ModdedIrisLog.error("Iris failed to stage the main world in server.properties", e);
            return false;
        }
    }

    public static void clearOverride() {
        try {
            clearPending();
        } catch (IOException e) {
            ModdedIrisLog.error("Iris failed to clear the pending main world marker", e);
        }
    }

    static Path configuredWorldRootIfPresent() throws IOException {
        Path instanceRoot = Path.of("").toAbsolutePath().normalize();
        Path properties = instanceRoot.resolve(PROPERTIES_NAME);
        if (!Files.isRegularFile(properties)) {
            return null;
        }
        try {
            return resolveWorldRoot(instanceRoot, properties);
        } catch (MissingWorldRootException missing) {
            return null;
        }
    }

    /**
     * net.minecraft.server.Main reads server.properties as Paths.get("server.properties"), so the authoritative
     * instance root is the JVM working directory - not configDir().getParent(), which points somewhere else
     * entirely whenever the loader config tree is relocated (-Dfabric.configDir, a shared config mount, a
     * launcher that starts the server from another directory). Refuse loudly rather than write or move files
     * against a guessed root: every caller treats null as "not a dedicated instance we may touch".
     */
    private static Path verifiedInstanceRoot(String operation) {
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        if (Files.isRegularFile(workingDirectory.resolve(PROPERTIES_NAME))) {
            return workingDirectory;
        }
        ModdedIrisLog.error("Iris refuses to {}: no {} in the server working directory {}", operation, PROPERTIES_NAME, workingDirectory);
        ModdedIrisLog.error("Iris only edits main-world properties in the directory the dedicated server reads {} from, and it moves no world data outside it. Start the server from its instance directory, or clear mainWorldPack in irisworldgen/modded.json.", PROPERTIES_NAME);
        return null;
    }

    private static Path markerFile() {
        return ModdedEngineBootstrap.loader().configDir().resolve("irisworldgen").resolve(MARKER_NAME);
    }

    private static boolean isPending() {
        return Files.isRegularFile(markerFile());
    }

    private static void markPending() throws IOException {
        Path marker = markerFile();
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, "pending", StandardCharsets.UTF_8);
    }

    private static void clearPending() throws IOException {
        Files.deleteIfExists(markerFile());
    }

    private static String readProperty(Path properties, String key) throws IOException {
        if (!Files.isRegularFile(properties)) {
            return null;
        }
        List<String> lines = Files.readAllLines(properties, StandardCharsets.UTF_8);
        String prefix = key + "=";
        for (String line : lines) {
            if (line.startsWith(prefix)) {
                return unescape(line.substring(prefix.length()).trim());
            }
        }
        return null;
    }

    /**
     * Temp file plus ATOMIC_MOVE, the same publish shape ModdedForcedDatapack.writePublishedHash uses. A
     * truncated server.properties bricks the next boot, and this write happens during bootstrap where a crash
     * or a kill is entirely plausible.
     */
    private static void writeLevelProperties(Path properties, String target, long seed) throws IOException {
        List<String> lines = Files.isRegularFile(properties)
                ? new ArrayList<>(Files.readAllLines(properties, StandardCharsets.UTF_8))
                : new ArrayList<>();
        setProperty(lines, "level-type", escape(target));
        if (seed != 0L) {
            setProperty(lines, "level-seed", Long.toString(seed));
        }

        Path temp = properties.resolveSibling(PROPERTIES_NAME + ".iris-tmp-" + UUID.randomUUID());
        Files.write(temp, lines, StandardCharsets.UTF_8);
        try {
            Files.move(temp, properties, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicUnsupported) {
            Files.move(temp, properties, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void setProperty(List<String> lines, String key, String value) {
        String prefix = key + "=";
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(prefix)) {
                lines.set(i, prefix + value);
                return;
            }
        }
        lines.add(prefix + value);
    }

    /**
     * Mirrors net.minecraft.server.Main world resolution: the universe root is --universe (default the working
     * directory) and the world folder name is --world, falling back to the level-name property.
     */
    private static Path resolveWorldRoot(Path instanceRoot, Path properties) throws IOException {
        List<String> arguments = processArguments();
        Path universe = universeRoot(instanceRoot, commandLineOption(arguments, "universe"));
        String levelName = firstNonBlank(commandLineOption(arguments, "world"),
                firstNonBlank(readProperty(properties, "level-name"), "world"));
        return resolveWorldRoot(universe, levelName);
    }

    static Path universeRoot(Path instanceRoot, String universeOption) {
        return (universeOption == null || universeOption.isBlank()
                ? instanceRoot
                : instanceRoot.resolve(universeOption)).toAbsolutePath().normalize();
    }

    static Path resolveWorldRoot(Path universe, String levelName) throws IOException {
        if (!Files.isDirectory(universe)) {
            throw new MissingWorldRootException("Server universe directory does not exist: " + universe, universe);
        }
        Path worldRoot = universe.resolve(levelName).toAbsolutePath().normalize();
        if (worldRoot.equals(universe) || !worldRoot.startsWith(universe)) {
            throw new IOException("Unsafe world name outside the server universe " + universe + ": " + levelName);
        }
        if (!Files.isDirectory(worldRoot)) {
            throw new MissingWorldRootException("Server world directory does not exist: " + worldRoot, worldRoot);
        }
        return worldRoot;
    }

    /**
     * A universe or world directory that is simply absent. Separated from every other IO failure so
     * reconciliation can treat it as nothing-to-quarantine instead of refusing startup; an unsafe world name
     * stays a plain IOException and still refuses.
     */
    static final class MissingWorldRootException extends IOException {
        private static final long serialVersionUID = 1L;

        private final Path path;

        MissingWorldRootException(String message, Path path) {
            super(message);
            this.path = path;
        }

        Path path() {
            return path;
        }
    }

    /**
     * Best effort read of a dedicated-server launch option. The parsed OptionSet is not reachable from mod
     * bootstrap, so read the process arguments; when they are unavailable we fall back to the vanilla defaults,
     * which is what the unpatched code assumed unconditionally.
     */
    static String commandLineOption(List<String> arguments, String name) {
        String flag = "--" + name;
        for (int index = 0; index < arguments.size(); index++) {
            String argument = arguments.get(index);
            if (argument.equals(flag)) {
                return index + 1 < arguments.size() ? arguments.get(index + 1) : null;
            }
            if (argument.startsWith(flag + "=")) {
                return argument.substring(flag.length() + 1);
            }
        }
        return null;
    }

    private static List<String> processArguments() {
        try {
            Optional<String[]> arguments = ProcessHandle.current().info().arguments();
            if (arguments.isPresent() && arguments.get().length > 0) {
                return List.of(arguments.get());
            }
        } catch (RuntimeException unavailable) {
            ModdedIrisLog.debug("Iris could not read the process arguments", unavailable);
        }
        // Whitespace split only: sun.java.command is a flattened string with no quoting information, so a
        // --universe or --world value containing spaces cannot be recovered from it. Deliberately not parsed
        // further - a half-correct quote parser would hand world resolution a wrong directory, and the missing
        // world root path already degrades to the vanilla defaults instead of failing the boot.
        String command = System.getProperty("sun.java.command");
        if (command == null || command.isBlank()) {
            return List.of();
        }
        return List.of(command.trim().split("\\s+"));
    }

    private static Path quarantineVanillaDimensions(Path worldRoot) throws IOException {
        Path recovery = markerFile().getParent().resolve("mainworld-recovery-" + UUID.randomUUID());
        List<Path> moved = new ArrayList<>();
        moveToRecovery(worldRoot, worldRoot.resolve("level.dat"), recovery, moved);
        moveToRecovery(worldRoot, worldRoot.resolve("level.dat_old"), recovery, moved);
        for (String folder : VANILLA_DIMENSION_FOLDERS) {
            moveToRecovery(worldRoot, worldRoot.resolve(folder), recovery, moved);
        }
        if (moved.isEmpty()) {
            Files.deleteIfExists(recovery);
        }
        return recovery;
    }

    private static void moveToRecovery(Path worldRoot, Path source, Path recovery,
                                       List<Path> moved) throws IOException {
        if (!Files.exists(source)) {
            return;
        }
        Path relative = worldRoot.relativize(source);
        Path target = recovery.resolve(relative);
        Files.createDirectories(target.getParent());
        try {
            Files.move(source, target);
            moved.add(relative);
        } catch (IOException failure) {
            for (int index = moved.size() - 1; index >= 0; index--) {
                Path rollbackRelative = moved.get(index);
                Path rollbackSource = recovery.resolve(rollbackRelative);
                Path rollbackTarget = worldRoot.resolve(rollbackRelative);
                try {
                    Files.createDirectories(rollbackTarget.getParent());
                    Files.move(rollbackSource, rollbackTarget);
                } catch (IOException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            throw failure;
        }
    }

    private static String escape(String value) {
        return value.replace(":", "\\:");
    }

    private static String unescape(String value) {
        return value.replace("\\:", ":").replace("\\=", "=");
    }

    private static String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
