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

import art.arcane.iris.pack.BrokenPackException;
import art.arcane.iris.pack.PackDirectoryResolver;
import art.arcane.iris.pack.PackDownloader;
import art.arcane.iris.pack.PackValidationRegistry;
import art.arcane.iris.pack.PackValidationResult;
import art.arcane.iris.pack.PackValidator;
import art.arcane.iris.modded.command.ModdedPackCommands;
import art.arcane.iris.spi.IrisPlatforms;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

public final class ModdedStartup {
    private static final int COMPAT_BOOT_KEY_CAP = 3;
    private static final AtomicBoolean PREPARED = new AtomicBoolean(false);
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    private ModdedStartup() {
    }

    public static void reset() {
        PREPARED.set(false);
        STARTED.set(false);
    }

    public static void prepareForStartup() {
        if (!PREPARED.compareAndSet(false, true)) {
            return;
        }
        reportLegacyPacksDirectory();
        validateAllPacks();
    }

    /**
     * Older builds mkdir'd (and stale guidance sometimes populated) config/iris/packs, but modded
     * packs live under config/irisworldgen/packs. Never auto-move user content: warn loudly when
     * the legacy directory holds packs, and quietly remove it when it is empty.
     */
    private static void reportLegacyPacksDirectory() {
        try {
            File legacy = ModdedEngineBootstrap.loader().configDir().resolve("iris").resolve("packs").toFile();
            if (!legacy.isDirectory()) {
                return;
            }
            if (!PackDirectoryResolver.listVisiblePackDirectories(legacy).isEmpty()) {
                File real = art.arcane.iris.spi.IrisPlatforms.get().packsFolderNoCreate();
                ModdedIrisLog.warn("Iris found packs under the legacy directory {} - modded packs load from {} only. Move them there.",
                        legacy.getAbsolutePath(), real.getAbsolutePath());
                return;
            }
            String[] entries = legacy.list();
            if (entries == null || entries.length == 0) {
                legacy.delete();
            }
        } catch (Throwable e) {
            ModdedIrisLog.debug("Iris legacy packs directory check failed", e);
        }
    }

    /**
     * Boot trigger for the forced datapack. Runs on its own daemon thread rather than the Iris scheduler:
     * ModdedEngineBootstrap.start clears the async queue at SERVER_STARTING, which would silently drop this
     * one-shot task, and the datapack must be regenerated before the level PackRepository reload if it can.
     */
    public static void prefetchStartupDatapack() {
        Thread thread = new Thread(ModdedStartup::refreshDatapack, "iris-modded-datapack-prefetch");
        thread.setDaemon(true);
        thread.start();
    }

    private static void refreshDatapack() {
        try {
            ModdedForcedDatapack.regenerateIfStale("boot");
        } catch (Throwable failure) {
            ModdedIrisLog.error("Iris could not refresh the forced datapack at boot", failure);
        }
    }

    public static void runOnce(MinecraftServer server) {
        if (server == null || server.getPlayerList() == null) {
            return;
        }
        prepareForStartup();
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }
        ModdedForcedDatapack.verifyInjected();
        ModdedMixinAudit.runOnce();
        reinjectPersistentDimensions(server);

        ModdedScheduler scheduler = ModdedEngineBootstrap.schedulerOrNull();
        if (scheduler == null) {
            refreshDatapack();
            return;
        }
        scheduler.async(ModdedStartup::refreshDatapack);
    }

    public static void validateAllPacks() {
        File packsRoot = ModdedPackCommands.packsRoot();
        List<File> packDirs = PackDirectoryResolver.listVisiblePackDirectories(packsRoot);
        PackValidationRegistry.clear();
        if (packDirs.isEmpty()) {
            ModdedIrisLog.info("Iris found no packs to validate under {}; install one with /iris download pack=overworld, /iris download pack=underworld, or /iris download link=<zip-url>",
                    packsRoot.getAbsolutePath());
            return;
        }
        for (File packDir : packDirs) {
            try {
                PackValidationResult result = PackValidator.validate(packDir);
                PackValidationRegistry.publish(result);
                String minecraftVersion = IrisPlatforms.isBound() ? IrisPlatforms.get().minecraftVersion() : null;
                String compatSummary = PackValidator.compatSummary(result, minecraftVersion);
                String compatSuffix = compatSummary.isEmpty() ? "" : " " + compatSummary;
                if (!result.isLoadable()) {
                    ModdedIrisLog.error("Iris pack '{}' FAILED validation with {} blocking error(s); world/studio creation will be refused. First error: {}",
                            result.getPackName(), result.getBlockingErrors().size(),
                            result.getBlockingErrors().getFirst());
                } else if (!result.getWarnings().isEmpty()) {
                    ModdedIrisLog.info("Iris pack '{}' validated ({} warning(s)).{}", result.getPackName(), result.getWarnings().size(), compatSuffix);
                    for (String warning : result.getWarnings()) {
                        ModdedIrisLog.warn("  [{}] {}", result.getPackName(), warning);
                    }
                } else {
                    ModdedIrisLog.info("Iris pack '{}' validated.{}", result.getPackName(), compatSuffix);
                }
                for (String line : PackValidator.compatBootLines(result, minecraftVersion, COMPAT_BOOT_KEY_CAP)) {
                    ModdedIrisLog.warn("{}", line);
                }
            } catch (Throwable e) {
                ModdedIrisLog.error("Iris pack validation failed for '{}'", packDir.getName(), e);
                String detail = e.getMessage();
                if (detail == null || detail.isBlank()) {
                    detail = e.getClass().getSimpleName();
                }
                PackValidationRegistry.publish(new PackValidationResult(
                        packDir.getName(),
                        List.of("Pack validation failed with " + e.getClass().getSimpleName() + ": " + detail),
                        List.of(),
                        System.currentTimeMillis()));
            }
        }
    }

    public static PackValidationResult requirePackForWorldCreation(String pack) {
        if (pack == null || pack.isBlank()) {
            throw new IllegalArgumentException("Pack name is required for world creation");
        }
        File packDir = PackDirectoryResolver.resolveExisting(ModdedPackCommands.packsRoot(), pack);
        if (packDir == null) {
            throw new BrokenPackException(pack, List.of(
                    "Pack folder does not exist under " + ModdedPackCommands.packsRoot().getAbsolutePath() + "."));
        }
        PackValidationResult cached = PackValidationRegistry.get(pack);
        if (cached != null && cached.getValidatedAtMillis() >= newestModificationMillis(packDir.toPath())) {
            // prepareForStartup already validated this pack and nothing in it changed since; re-validating per
            // persistent dimension at boot costs a full pack parse each time.
            return PackValidationRegistry.requireLoadable(pack);
        }
        try {
            PackValidationResult result = PackValidator.validate(packDir);
            PackValidationRegistry.publish(result);
            return PackValidationRegistry.requireLoadable(pack);
        } catch (BrokenPackException e) {
            throw e;
        } catch (Throwable e) {
            ModdedIrisLog.error("Iris required world-creation validation failed for '{}'", pack, e);
            String detail = e.getMessage();
            if (detail == null || detail.isBlank()) {
                detail = e.getClass().getSimpleName();
            }
            PackValidationResult failure = new PackValidationResult(
                    pack,
                    List.of("Pack validation failed with " + e.getClass().getSimpleName() + ": " + detail),
                    List.of(),
                    System.currentTimeMillis());
            PackValidationRegistry.publish(failure);
            throw new BrokenPackException(pack, failure.getBlockingErrors());
        }
    }

    private static void reinjectPersistentDimensions(MinecraftServer server) {
        List<ModdedDimensionRegistryStore.PersistentDimension> dimensions =
                ModdedDimensionRegistryStore.loadForStartup(server);
        if (dimensions.isEmpty()) {
            return;
        }
        int injected = 0;
        int index = 0;
        long startedAt = System.currentTimeMillis();
        for (ModdedDimensionRegistryStore.PersistentDimension dimension : dimensions) {
            index++;
            long dimensionStartedAt = System.currentTimeMillis();
            try {
                ModdedDimensionManager.restorePersistent(
                        server,
                        dimension.id(),
                        dimension.pack(),
                        dimension.dimension(),
                        dimension.seed()
                );
                injected++;
                ModdedIrisLog.info("Iris re-injected {}/{} '{}' (pack={} dim={}) in {}ms",
                        index, dimensions.size(), dimension.id(), dimension.pack(), dimension.dimension(),
                        System.currentTimeMillis() - dimensionStartedAt);
            } catch (Throwable e) {
                ModdedIrisLog.error("Iris failed to re-inject persistent dimension '{}' (pack={} dim={} seed={})", dimension.id(), dimension.pack(), dimension.dimension(), dimension.seed(), e);
                if (e instanceof OutOfMemoryError outOfMemory) {
                    throw outOfMemory;
                }
            }
        }
        ModdedIrisLog.info("Iris re-injected {}/{} persistent dimension(s) at startup in {}ms",
                injected, dimensions.size(), System.currentTimeMillis() - startedAt);
    }

    private static long newestModificationMillis(Path root) {
        long newest = 0L;
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : (Iterable<Path>) walk::iterator) {
                BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
                long modified = attributes.lastModifiedTime().toMillis();
                if (modified > newest) {
                    newest = modified;
                }
            }
        } catch (IOException | RuntimeException unreadable) {
            ModdedIrisLog.debug("Iris could not stat {} for validation reuse; revalidating", root, unreadable);
            return Long.MAX_VALUE;
        }
        return newest;
    }

    /**
     * SP-6: a pack excluded by validation is otherwise only visible in the console. Tell the operators who
     * can actually act on it when they join.
     */
    public static void warnPackFailuresTo(ServerPlayer player) {
        if (player == null || !Commands.LEVEL_GAMEMASTERS.check(player.permissions())) {
            return;
        }
        for (Map.Entry<String, PackValidationResult> entry : PackValidationRegistry.snapshot().entrySet()) {
            PackValidationResult result = entry.getValue();
            if (result == null || result.isLoadable()) {
                continue;
            }
            String reason = result.getBlockingErrors().isEmpty()
                    ? "unknown validation failure"
                    : result.getBlockingErrors().getFirst();
            player.sendSystemMessage(Component.literal("Iris pack '" + entry.getKey()
                    + "' failed validation and cannot be used: " + reason));
        }
    }

}
