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

import art.arcane.iris.pack.PackFingerprints;

import art.arcane.iris.pack.BrokenPackException;
import art.arcane.iris.pack.PackDirectoryResolver;
import art.arcane.iris.pack.PackDownloader;
import art.arcane.iris.pack.PackValidationRegistry;
import art.arcane.iris.pack.PackValidationCache;
import art.arcane.iris.pack.PackValidationResult;
import art.arcane.iris.pack.PackValidator;
import art.arcane.iris.modded.command.ModdedPackCommands;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.world.safeguard.GenerationRefusalNotice;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeProtocolPlayer;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ModdedStartup {
    private static final int COMPAT_BOOT_KEY_CAP = 3;
    private static final AtomicBoolean PREPARED = new AtomicBoolean(false);
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static final List<String> DIMENSION_FAILURES = new CopyOnWriteArrayList<>();

    private ModdedStartup() {
    }

    public static void reset() {
        PREPARED.set(false);
        STARTED.set(false);
        DIMENSION_FAILURES.clear();
    }

    public static void prepareForStartup() {
        if (!PREPARED.compareAndSet(false, true)) {
            return;
        }
        validateAllPacks();
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

    public static void runOnce(NativeModdedServer server) {
        if (server == null || !server.hasPlayerList()) {
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
                PackValidationResult result = validatePack(packDir);
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
        Path packRoot = packDir.toPath();
        try {
            PackValidationResult cached = PackValidationRegistry.getMatching(
                    packRoot, PackFingerprints.computePackTreeFingerprint(packDir));
            if (cached != null) {
                return PackValidationRegistry.requireLoadable(packRoot);
            }
            validatePack(packDir);
            return PackValidationRegistry.requireLoadable(packRoot);
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

    public static PackValidationResult validatePack(File packDir) {
        Path packRoot = packDir.toPath();
        PackValidationRegistry.ValidationTicket ticket = PackValidationRegistry.tryBeginValidation(packRoot);
        if (ticket == null) {
            throw new BrokenPackException(packDir.getName(), List.of("Pack content is currently being changed."));
        }
        String contentFingerprint = PackFingerprints.computePackTreeFingerprint(packDir);
        String contextFingerprint = PackValidationCache.contextFingerprint();
        PackValidationResult result;
        try {
            result = PackValidator.validate(packDir);
        } catch (Throwable failure) {
            ModdedIrisLog.error("Iris pack validation failed for '{}'", packDir.getName(), failure);
            String detail = failure.getMessage();
            if (detail == null || detail.isBlank()) {
                detail = failure.getClass().getSimpleName();
            }
            result = new PackValidationResult(
                    packDir.getName(),
                    List.of("Pack validation failed with " + failure.getClass().getSimpleName() + ": " + detail),
                    List.of(),
                    System.currentTimeMillis());
        }
        if (!contentFingerprint.equals(PackFingerprints.computePackTreeFingerprint(packDir))
                || !contextFingerprint.equals(PackValidationCache.contextFingerprint())) {
            PackValidationRegistry.remove(packRoot);
            PackValidationRegistry.remove(packDir.getName());
            throw new BrokenPackException(packDir.getName(), List.of("Pack content or validation context changed during validation."));
        }
        if (!PackValidationRegistry.publishIfCurrent(ticket, result, contentFingerprint, contextFingerprint)) {
            throw new BrokenPackException(packDir.getName(), List.of("Pack content changed during validation."));
        }
        PackValidationRegistry.publish(result);
        return result;
    }

    static void reinjectPersistentDimensions(NativeModdedServer server) {
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
                reportDimensionFailure(dimension, e);
                if (e instanceof OutOfMemoryError outOfMemory) {
                    throw outOfMemory;
                }
            }
        }
        ModdedIrisLog.info("Iris re-injected {}/{} persistent dimension(s) at startup in {}ms",
                injected, dimensions.size(), System.currentTimeMillis() - startedAt);
    }

    private static void reportDimensionFailure(ModdedDimensionRegistryStore.PersistentDimension dimension, Throwable failure) {
        List<String> notice = GenerationRefusalNotice.compose(
                "Iris refused to load dimension '" + dimension.id() + "' (pack=" + dimension.pack()
                        + " dim=" + dimension.dimension() + " seed=" + dimension.seed() + ")",
                GenerationRefusalNotice.causes(failure),
                List.of(
                        "Iris does not generate this dimension and does not let vanilla or any other generator"
                                + " write it; no chunks are written.",
                        "It stays unloaded. Fix the cause, then start the server again."
                ));
        for (String line : notice) {
            ModdedIrisLog.error(line);
        }
        ModdedIrisLog.error("Iris failed to re-inject persistent dimension '{}' (pack={} dim={} seed={})",
                dimension.id(), dimension.pack(), dimension.dimension(), dimension.seed(), failure);
        DIMENSION_FAILURES.add("Iris dimension '" + dimension.id() + "' did not load and refuses to generate: "
                + GenerationRefusalNotice.summary(failure));
    }

    /**
     * True once the first tick with a player list has re-injected the persistent dimensions. Before that a runtime
     * dimension that is not loaded yet is still on its way.
     */
    public static boolean dimensionsRestored() {
        return STARTED.get();
    }

    /**
     * SP-6: a pack excluded by validation or a persistent dimension that did not load is otherwise only visible in
     * the console. Tell the operators who can actually act on it when they join.
     */
    public static void warnStartupFailuresTo(NativeProtocolPlayer player) {
        if (player == null || (!player.isGameMaster() && !player.isServerOwner())) {
            return;
        }
        for (String failure : DIMENSION_FAILURES) {
            player.sendMessage(failure);
        }
        for (Map.Entry<String, PackValidationResult> entry : PackValidationRegistry.snapshot().entrySet()) {
            PackValidationResult result = entry.getValue();
            if (result == null || result.isLoadable()) {
                continue;
            }
            String reason = result.getBlockingErrors().isEmpty()
                    ? "unknown validation failure"
                    : result.getBlockingErrors().getFirst();
            player.sendMessage("Iris pack '" + entry.getKey()
                    + "' failed validation and cannot be used: " + reason);
        }
    }

}
