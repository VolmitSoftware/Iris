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

package art.arcane.iris.modded.command;

import art.arcane.iris.modded.ModdedIrisLog;
import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.localization.IrisMessages;
import art.arcane.iris.modded.localization.ModdedCommandMessages;
import art.arcane.iris.localization.PackDownloadMessages;
import art.arcane.iris.localization.RuntimeUiMessages;
import art.arcane.iris.world.lifecycle.LifecycleOperationCoordinator;
import art.arcane.iris.pack.PackDownloadExecution;
import art.arcane.iris.pack.PackDownloader;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.modded.IrisModdedChunkGenerator;
import art.arcane.iris.modded.ModdedDimensionManager;
import art.arcane.iris.modded.ModdedEngineBootstrap;
import art.arcane.iris.modded.ModdedForcedDatapack;
import art.arcane.iris.modded.ModdedLoader;
import art.arcane.iris.modded.ModdedScheduler;
import art.arcane.iris.modded.ModdedServerLevels;
import art.arcane.iris.modded.ModdedWorldgenIds;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.collection.KMap;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.ChunkGenerator;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import art.arcane.volmlib.util.localization.MessageArgument;

public final class IrisModdedCommands {
    private static final long DOWNLOAD_SHUTDOWN_POLL_SECONDS = 15L;
    private static final Object DOWNLOAD_MONITOR = new Object();

    static final SuggestionProvider<CommandSourceStack> PACK_NAMES = ModdedCommandSuggestions.PACK_NAMES;
    private static PackDownloadExecution activeDownload;
    private static boolean downloadAdmissionOpen;

    private IrisModdedCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> root = dispatcher.register(ModdedCommandTree.rootTree());
        dispatcher.register(Commands.literal("ir").redirect(root));
        dispatcher.register(Commands.literal("irs").redirect(root));
        IrisLogging.debug("Iris /iris command tree registered");
    }

    public static void openDownloadAdmission() {
        synchronized (DOWNLOAD_MONITOR) {
            downloadAdmissionOpen = true;
        }
    }

    public static void shutdownDownloads() {
        PackDownloadExecution execution;
        synchronized (DOWNLOAD_MONITOR) {
            downloadAdmissionOpen = false;
            execution = activeDownload;
        }
        if (execution == null) {
            return;
        }

        execution.cancel();
        boolean interrupted = false;
        boolean warned = false;
        while (!execution.isComplete()) {
            try {
                if (!execution.await(DOWNLOAD_SHUTDOWN_POLL_SECONDS, TimeUnit.SECONDS) && !warned) {
                    warned = true;
                    ModdedIrisLog.warn(execution.isPublishing()
                            ? "Waiting for atomic pack publication to finish before Iris shutdown."
                            : "Waiting for the active pack download to cancel before Iris shutdown.");
                }
            } catch (InterruptedException exception) {
                interrupted = true;
                execution.cancel();
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    static int tp(CommandSourceStack source, ServerLevel level, ServerPlayer target) {
        ServerPlayer player = target != null ? target : source.getPlayer();
        if (player == null) {
            fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_CONSOLE_MUST_NAME_PLAYER_IRIS_TP_DIMENSION_PLAYER));
            return 0;
        }
        if (!(level.getChunkSource().getGenerator() instanceof IrisModdedChunkGenerator)) {
            fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_IS_NOT_GENERATED_BY_IRIS, MessageArgument.untrusted("value", level.dimension().identifier())));
            return 0;
        }
        String dimensionId = level.dimension().identifier().toString();
        MinecraftServer server = source.getServer();
        CompletableFuture<Boolean> teleport = ModdedDimensionManager.teleportAsync(
                player,
                server,
                dimensionId,
                8.5D,
                Double.MIN_VALUE,
                8.5D);
        teleport.whenComplete((success, failure) -> {
            if (Boolean.TRUE.equals(success) && failure == null) {
                return;
            }
            if (failure != null) {
                ModdedIrisLog.error("Iris teleport into '{}' failed for {}",
                        dimensionId, player.getUUID(), failure);
            }
            server.execute(() -> fail(source, IrisLanguage.plain(
                    ModdedCommandMessages.IRIS_MODDED_COMMANDS_TELEPORT_FAILED_DIMENSION_IS_NOT_LOADED,
                    MessageArgument.untrusted("dimensionId", dimensionId))));
        });
        ok(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_TELEPORTING, MessageArgument.untrusted("value", player.getScoreboardName()), MessageArgument.untrusted("dimensionId", dimensionId)));
        return 1;
    }

    static int evacuate(CommandSourceStack source, ServerLevel target) {
        MinecraftServer server = source.getServer();
        ServerLevel level = target != null ? target : source.getLevel();
        if (!(level.getChunkSource().getGenerator() instanceof IrisModdedChunkGenerator)) {
            fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_IS_NOT_GENERATED_BY_IRIS_2, MessageArgument.untrusted("value", level.dimension().identifier())));
            return 0;
        }
        ServerLevel fallback = server.overworld();
        if (fallback == level) {
            fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_CANNOT_EVACUATE_PRIMARY_WORLD_THERE_IS_NOWHERE_SEND_PLAYERS));
            return 0;
        }
        int count = ModdedDimensionManager.evacuate(server, level);
        ok(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_EVACUATED_PLAYER_S_FROM, MessageArgument.untrusted("count", count), MessageArgument.untrusted("value", level.dimension().identifier()), MessageArgument.untrusted("value2", fallback.dimension().identifier())));
        return 1;
    }

    static int debug(CommandSourceStack source) {
        boolean to = !IrisSettings.get().getGeneral().isDebug();
        IrisSettings.get().getGeneral().setDebug(to);
        IrisSettings.get().forceSave();
        ok(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_SET_DEBUG, MessageArgument.untrusted("to", to)));
        return 1;
    }

    static int reload(CommandSourceStack source) {
        if (IrisSettings.settings != null) {
            IrisSettings.invalidate();
        }
        IrisSettings.get();
        // Forced-datapack regeneration trigger. Async: staging revalidates every pack and must never run on
        // the server thread.
        ModdedForcedDatapack.scheduleRegeneration("/iris reload");
        boolean localeLoaded = IrisLanguage.reload();
        if (localeLoaded) {
            ok(source, IrisLanguage.plain(
                    IrisMessages.COMMAND_RELOAD_SUCCESS,
                    MessageArgument.trusted("locale", IrisLanguage.activeLocale())
            ));
            return 1;
        }
        fail(source, IrisLanguage.plain(
                IrisMessages.COMMAND_RELOAD_FAILED,
                MessageArgument.untrusted("locale", IrisSettings.get().getGeneral().getLanguage()),
                MessageArgument.trusted("activeLocale", IrisLanguage.activeLocale())
        ));
        return 0;
    }

    static int height(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        IrisModdedCommands.ok(source, IrisLanguage.plain(
                RuntimeUiMessages.WORLD_HEIGHT_RANGE,
                MessageArgument.trusted("minY", level.getMinY()),
                MessageArgument.trusted("maxY", level.getMaxY())));
        IrisModdedCommands.ok(source, IrisLanguage.plain(
                RuntimeUiMessages.WORLD_HEIGHT_TOTAL,
                MessageArgument.trusted("height", level.getHeight())));
        return 1;
    }

    static int regen(CommandSourceStack source, int radius) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_THIS_COMMAND_CAN_ONLY_BE_USED_BY_PLAYERS));
            return 0;
        }
        ServerLevel level = source.getLevel();
        if (!(level.getChunkSource().getGenerator() instanceof IrisModdedChunkGenerator irisGenerator)) {
            fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_THIS_DIMENSION_IS_NOT_GENERATED_BY_IRIS_4));
            return 0;
        }
        Engine engine = engineFor(level);
        if (engine == null) {
            fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_THIS_DIMENSION_IS_NOT_GENERATED_BY_IRIS_5));
            return 0;
        }
        ModdedRegen.start(source, level, irisGenerator, engine, player, radius);
        return 1;
    }

    static int version(CommandSourceStack source) {
        ModdedLoader loader = ModdedEngineBootstrap.loader();
        int engines = engineCount(source.getServer());
        ok(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_IRIS_BY_VOLMIT_SOFTWARE_ON_MINECRAFT_IRIS_DIMENSION_S, MessageArgument.untrusted("value", loader.modVersion()), MessageArgument.untrusted("value2", loader.platformName()), MessageArgument.untrusted("value3", loader.minecraftVersion()), MessageArgument.untrusted("engines", engines)));
        return 1;
    }

    static int info(CommandSourceStack source, String filter) {
        MinecraftServer server = source.getServer();
        // The seed is the one field in this listing that is not free to hand a plain player, and /iris worlds
        // routes here too: emit it only for sources that pass the same gate /iris seed requires.
        boolean showSeed = ModdedCommandTree.isGamemaster(source);
        List<String> lines = new ArrayList<>();
        int total = 0;
        int iris = 0;
        for (ServerLevel level : ModdedServerLevels.levels(server)) {
            total++;
            ChunkGenerator generator = level.getChunkSource().getGenerator();
            if (!(generator instanceof IrisModdedChunkGenerator irisGenerator)) {
                continue;
            }
            iris++;
            String dimensionId = level.dimension().identifier().toString();
            String irisIdentity = ModdedWorldgenIds.generatorIdentity(irisGenerator.dimensionKey());
            if (filter != null && !dimensionId.contains(filter)
                    && !irisIdentity.contains(filter)
                    && !irisGenerator.dimensionKey().contains(filter)) {
                continue;
            }
            Engine engine = irisGenerator.engineIfBound();
            if (engine == null) {
                lines.add(irisIdentity + ": pack=" + irisGenerator.dimensionKey()
                        + " world=" + dimensionId + " (engine not started yet)");
                continue;
            }
            String featureStatus = irisGenerator.importedFeaturesStatus();
            lines.add(irisIdentity + ": pack=" + engine.getDimension().getLoadKey()
                    + " world=" + dimensionId
                    + (showSeed ? " seed=" + level.getSeed() : "")
                    + " height=" + engine.getMinHeight() + ".." + engine.getMaxHeight()
                    + " generated=" + engine.getGenerated()
                    + (featureStatus == null ? "" : " importedFeatures=" + featureStatus)
                    + " data=" + engine.getData().getDataFolder().getName());
        }
        ok(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_LOADED_DIMENSIONS_IRIS, MessageArgument.untrusted("total", total), MessageArgument.untrusted("iris", iris)));
        if (lines.isEmpty()) {
            if (filter == null) {
                ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_DATAPACK_COMMANDS_NO_IRIS_DIMENSIONS_ARE_LOADED));
            } else {
                ok(source, IrisLanguage.plain(RuntimeUiMessages.MODDED_NO_DIMENSION_MATCH, MessageArgument.untrusted("filter", filter)));
            }
            return 0;
        }
        for (String line : lines) {
            ok(source, line);
        }
        return 1;
    }

    static int seed(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Engine engine = engineFor(level);
        if (engine == null) {
            fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_THIS_DIMENSION_IS_NOT_GENERATED_BY_IRIS_14));
            return 0;
        }
        ok(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_WORLD_SEED, MessageArgument.untrusted("value", level.getSeed())));
        ok(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_ENGINE_SEED_MIXED, MessageArgument.untrusted("value", engine.getSeedManager().getSeed()), MessageArgument.untrusted("value2", engine.getSeedManager().getFullMixedSeed())));
        return 1;
    }

    static int goldenhash(CommandSourceStack source, int radius, int threads, ModdedGoldenHash.Mode mode) {
        ServerLevel level = source.getLevel();
        Engine engine = engineFor(level);
        if (engine == null) {
            fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_THIS_DIMENSION_IS_NOT_GENERATED_BY_IRIS_15));
            return 0;
        }
        ModdedGoldenHash.start(source, level, engine, radius, threads, mode);
        return 1;
    }

    static int download(CommandSourceStack source, String rawRequest) {
        DownloadRequest request = parseDownloadRequest(rawRequest);
        if (request == null) {
            fail(source, "Use /iris download pack=overworld, /iris download pack=underworld, or /iris download link=<zip-url>.");
            return 0;
        }
        String target = downloadDisplayTarget(request);
        String downloadSource = request.pack() == null ? "direct ZIP URL" : "built-in stable release";
        ModdedScheduler scheduler = ModdedEngineBootstrap.schedulerOrNull();
        if (scheduler == null) {
            fail(source, IrisLanguage.plain(
                    ModdedCommandMessages.IRIS_MODDED_COMMANDS_PACK_DOWNLOAD_FAILED_SEE_CONSOLE,
                    MessageArgument.untrusted("pack", target),
                    MessageArgument.untrusted("downloadSource", downloadSource)));
            return 0;
        }
        PackDownloadExecution execution;
        synchronized (DOWNLOAD_MONITOR) {
            if (!downloadAdmissionOpen) {
                fail(source, IrisLanguage.plain(
                        ModdedCommandMessages.IRIS_MODDED_COMMANDS_PACK_DOWNLOAD_FAILED_SEE_CONSOLE,
                        MessageArgument.untrusted("pack", target),
                        MessageArgument.untrusted("downloadSource", downloadSource)));
                return 0;
            }

            LifecycleOperationCoordinator.Lease lease;
            try {
                lease = LifecycleOperationCoordinator.get().acquire(
                        LifecycleOperationCoordinator.Domain.PACK_MUTATION,
                        LifecycleOperationCoordinator.OperationKind.PACK_DOWNLOAD,
                        target
                );
            } catch (LifecycleOperationCoordinator.BusyException error) {
                fail(source, downloadBusyMessage(error.currentOperation()));
                return 0;
            }

            execution = new PackDownloadExecution(
                    lease,
                    cancellation -> executeDownload(source, request, target, downloadSource, cancellation)
            );
            PackDownloadExecution trackedExecution = execution;
            execution.onCompletion(() -> clearActiveDownload(trackedExecution));
            activeDownload = execution;
            boolean accepted;
            try {
                accepted = scheduler.asyncIfRunning(execution, execution::cancel);
            } catch (Throwable error) {
                execution.cancel();
                ModdedIrisLog.error("Iris pack download dispatch failed for {}", target, error);
                fail(source, IrisLanguage.plain(
                        ModdedCommandMessages.IRIS_MODDED_COMMANDS_PACK_DOWNLOAD_FAILED_SEE_CONSOLE,
                        MessageArgument.untrusted("pack", target),
                        MessageArgument.untrusted("downloadSource", downloadSource)));
                return 0;
            }
            if (!accepted) {
                execution.cancel();
                ModdedIrisLog.error("Iris pack download dispatch rejected for {} because the scheduler is shut down", target);
                fail(source, IrisLanguage.plain(
                        ModdedCommandMessages.IRIS_MODDED_COMMANDS_PACK_DOWNLOAD_FAILED_SEE_CONSOLE,
                        MessageArgument.untrusted("pack", target),
                        MessageArgument.untrusted("downloadSource", downloadSource)));
                return 0;
            }
        }

        ok(source, IrisLanguage.plain(
                ModdedCommandMessages.IRIS_MODDED_COMMANDS_DOWNLOADING_IRISDIMENSIONS,
                MessageArgument.untrusted("pack", target),
                MessageArgument.untrusted("downloadSource", downloadSource)));
        return 1;
    }

    private static void executeDownload(
            CommandSourceStack source,
            DownloadRequest request,
            String target,
            String downloadSource,
            PackDownloader.DownloadCancellation cancellation
    ) throws PackDownloader.PackDownloadCancelledException {
        File packs = ModdedPackCommands.packsRoot();
        try {
            PackDownloader.PackInstallResult result = request.pack() == null
                    ? PackDownloader.downloadUrl(
                            packs,
                            request.url(),
                            false,
                            (String message) -> dispatchDownloadFeedback(source, () -> ok(source, message)),
                            cancellation
                    )
                    : PackDownloader.downloadBuiltIn(
                            packs,
                            request.pack(),
                            false,
                            (String message) -> dispatchDownloadFeedback(source, () -> ok(source, message)),
                            cancellation
                    );
            String completionMessage = downloadCompletionMessage(result);
            if (result != null) {
                if (completionMessage != null) {
                    dispatchDownloadFeedback(source, () -> ok(source, completionMessage));
                }
                return;
            }
        } catch (PackDownloader.PackDownloadCancelledException error) {
            throw error;
        } catch (PackDownloader.PackDownloadBusyException error) {
            dispatchDownloadFeedback(source, () -> fail(source, error.getMessage()));
            return;
        } catch (IOException | RuntimeException error) {
            ModdedIrisLog.error("Iris pack download failed for {}", target, error);
        }
        dispatchDownloadFeedback(source, () -> fail(source, IrisLanguage.plain(
                ModdedCommandMessages.IRIS_MODDED_COMMANDS_PACK_DOWNLOAD_FAILED_SEE_CONSOLE,
                MessageArgument.untrusted("pack", target),
                MessageArgument.untrusted("downloadSource", downloadSource))));
    }

    private static void dispatchDownloadFeedback(CommandSourceStack source, Runnable feedback) {
        source.getServer().execute(feedback);
    }

    static String downloadBusyMessage(LifecycleOperationCoordinator.ActiveOperation operation) {
        if (operation.domain() == LifecycleOperationCoordinator.Domain.PACK_MUTATION
                && operation.kind() == LifecycleOperationCoordinator.OperationKind.PACK_DOWNLOAD) {
            return IrisLanguage.plain(PackDownloadMessages.IN_PROGRESS);
        }
        return "Iris pack changes are busy with " + operation.kind().name().toLowerCase(Locale.ROOT)
                + " for '" + operation.target() + "'. Try again when it completes.";
    }

    static String downloadCompletionMessage(PackDownloader.PackInstallResult result) {
        if (result == null || !result.changed()) {
            return null;
        }
        return result.restartRequired()
                ? "Pack installed on disk. Restart the server before using it."
                : "Pack installed on disk.";
    }

    static String downloadDisplayTarget(DownloadRequest request) {
        return request.pack() == null
                ? IrisLanguage.plain(PackDownloadMessages.PROGRESS_SOURCE_REMOTE)
                : request.pack();
    }

    private static void clearActiveDownload(PackDownloadExecution execution) {
        synchronized (DOWNLOAD_MONITOR) {
            if (activeDownload == execution) {
                activeDownload = null;
            }
        }
    }

    static DownloadRequest parseDownloadRequest(String rawRequest) {
        if (rawRequest == null || rawRequest.isBlank()) {
            return null;
        }
        if (rawRequest.startsWith("pack=")) {
            String pack = rawRequest.substring("pack=".length()).trim().toLowerCase(Locale.ROOT);
            return PackDownloader.isBuiltInPack(pack) ? new DownloadRequest(pack, null) : null;
        }
        if (rawRequest.startsWith("link=")) {
            String url = rawRequest.substring("link=".length()).trim();
            return PackDownloader.isDirectZipUrl(url) ? new DownloadRequest(null, url) : null;
        }
        return null;
    }

    static int metrics(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Engine engine = engineFor(level);
        if (engine == null) {
            fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_THIS_DIMENSION_IS_NOT_GENERATED_BY_IRIS_16));
            return 0;
        }
        ok(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_GENERATED_CHUNK_S_S, MessageArgument.untrusted("value", engine.getGenerated()), MessageArgument.untrusted("value2", String.format("%.1f", engine.getGeneratedPerSecond()))));
        KMap<String, Double> pulled = engine.getMetrics().pull();
        Map<String, Double> sorted = new TreeMap<>(pulled);
        for (Map.Entry<String, Double> entry : sorted.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0D) {
                continue;
            }
            ok(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_MS, MessageArgument.untrusted("value", entry.getKey()), MessageArgument.untrusted("value2", String.format("%.2f", entry.getValue()))));
        }
        return 1;
    }

    static int verifyStructures(CommandSourceStack source, String keyRaw) {
        return ModdedLocateCommands.verifyStructures(source, keyRaw);
    }

    static CompletableFuture<Suggestions> suggestStructureKeys(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return ModdedCommandSuggestions.suggestStructureKeys(context, builder);
    }

    static void warnTabFailure(String suggestion, CommandSourceStack source, Throwable error) {
        ModdedCommandSuggestions.warnTabFailure(suggestion, source, error);
    }

    static Engine engineFor(ServerLevel level) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        if (generator instanceof IrisModdedChunkGenerator irisGenerator) {
            try {
                return irisGenerator.commandEngine();
            } catch (Throwable e) {
                ModdedIrisLog.error("Iris engine lookup failed for {}", level.dimension().identifier(), e);
                return null;
            }
        }
        return null;
    }

    private static int engineCount(MinecraftServer server) {
        int count = 0;
        for (ServerLevel level : ModdedServerLevels.levels(server)) {
            if (level.getChunkSource().getGenerator() instanceof IrisModdedChunkGenerator) {
                count++;
            }
        }
        return count;
    }

    static void ok(CommandSourceStack source, String message) {
        ModdedCommandFeedback.ok(source, message);
    }

    static void ok(CommandSourceStack source, Component component) {
        ModdedCommandFeedback.ok(source, component);
    }

    static void fail(CommandSourceStack source, String message) {
        ModdedCommandFeedback.fail(source, message);
    }

    record DownloadRequest(String pack, String url) {
    }
}
