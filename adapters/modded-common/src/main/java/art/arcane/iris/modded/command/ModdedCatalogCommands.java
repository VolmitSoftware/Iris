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

import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.modded.ModdedIrisLog;
import art.arcane.iris.modded.catalog.ModdedWorldgenCatalogs;
import art.arcane.iris.modded.catalog.ModdedWorldgenCatalogs.CaptureResult;
import art.arcane.iris.modded.catalog.WorldgenCatalogSummary;
import art.arcane.iris.modded.catalog.WorldgenCatalogSummary.NamespaceSummary;
import art.arcane.iris.modded.localization.ModdedCatalogMessages;
import art.arcane.iris.pack.PackDirectoryResolver;
import art.arcane.iris.pack.ingest.ModIngestReport;
import art.arcane.iris.pack.ingest.ModIngestRequest;
import art.arcane.iris.pack.ingest.ModIngester;
import art.arcane.iris.pack.ingest.catalog.WorldgenCatalog;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandRegistration;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandSource;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;
import art.arcane.volmlib.util.localization.MessageArgument;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

final class ModdedCatalogCommands {
    private static final Predicate<NativeCommandSource> GATE = NativeCommandRegistration.GAMEMASTERS;
    private static final int LIST_FEATURE_TYPES = 3;
    private static final int DETAIL_LIMIT = 24;
    private static final String NONE = "-";
    private static final AtomicReference<NamespaceCache> NAMESPACE_CACHE = new AtomicReference<>();
    private static final SuggestionProvider<NativeCommandSource> NAMESPACES = (CommandContext<NativeCommandSource> context,
                                                                             SuggestionsBuilder builder) ->
            NativeCommandRegistration.suggest(contentNamespaces(), builder);
    private static final SuggestionProvider<NativeCommandSource> INGEST_ARGUMENTS = ModdedCatalogCommands::suggestIngestArguments;

    private ModdedCatalogCommands() {
    }

    static LiteralArgumentBuilder<NativeCommandSource> catalogTree() {
        return NativeCommandRegistration.literal("catalog").requires(GATE)
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> list(context.getSource())))
                .then(NativeCommandRegistration.literal("refresh")
                        .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> refresh(context.getSource()))))
                .then(NativeCommandRegistration.argument("namespace", StringArgumentType.word()).suggests(NAMESPACES)
                        .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) ->
                                detail(context.getSource(), StringArgumentType.getString(context, "namespace")))));
    }

    static LiteralArgumentBuilder<NativeCommandSource> ingestTree() {
        return NativeCommandRegistration.literal("ingest").requires(GATE)
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> ModdedCommandHelp.send(context.getSource(), "ingest")))
                .then(NativeCommandRegistration.literal("mods")
                        .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> ingest(context.getSource(), "")))
                        .then(NativeCommandRegistration.argument("arguments", StringArgumentType.greedyString()).suggests(INGEST_ARGUMENTS)
                                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) ->
                                        ingest(context.getSource(), StringArgumentType.getString(context, "arguments"))))));
    }

    private static int list(NativeCommandSource source) {
        Optional<WorldgenCatalog> latest = ModdedWorldgenCatalogs.latest();
        if (latest.isEmpty()) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCatalogMessages.MISSING));
            return 0;
        }
        WorldgenCatalog catalog = latest.get();
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCatalogMessages.HEADER,
                MessageArgument.untrusted("name", catalog.platform()),
                MessageArgument.untrusted("version", catalog.minecraftVersion()),
                MessageArgument.untrusted("count", catalog.namespaces().size()),
                MessageArgument.untrusted("value", catalog.biomes().size()),
                MessageArgument.untrusted("value2", catalog.placedFeatures().size()),
                MessageArgument.untrusted("value3", catalog.structures().size()),
                MessageArgument.untrusted("value4", catalog.dimensionTypes().size()),
                MessageArgument.untrusted("value5", catalog.levelStems().size())));
        int quiet = 0;
        for (NamespaceSummary summary : WorldgenCatalogSummary.namespaces(catalog)) {
            if (!summary.hasContent()) {
                quiet++;
                continue;
            }
            IrisModdedCommands.ok(source, namespaceLine(summary));
        }
        if (quiet > 0) {
            IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCatalogMessages.QUIET, MessageArgument.untrusted("count", quiet)));
        }
        return 1;
    }

    private static int detail(NativeCommandSource source, String namespace) {
        Optional<WorldgenCatalog> latest = ModdedWorldgenCatalogs.latest();
        if (latest.isEmpty()) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCatalogMessages.MISSING));
            return 0;
        }
        Optional<NamespaceSummary> found = WorldgenCatalogSummary.namespace(latest.get(), namespace);
        if (found.isEmpty() || !found.get().hasContent()) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCatalogMessages.UNKNOWN_NAMESPACE,
                    MessageArgument.untrusted("name", namespace)));
            return 0;
        }
        NamespaceSummary summary = found.get();
        IrisModdedCommands.ok(source, namespaceLine(summary));
        if (!summary.biomes().isEmpty()) {
            IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCatalogMessages.DETAIL_BIOMES,
                    MessageArgument.untrusted("count", summary.biomes().size()),
                    MessageArgument.untrusted("value", WorldgenCatalogSummary.limited(summary.biomes(), namespace, DETAIL_LIMIT))));
        }
        if (!summary.placedFeatures().isEmpty()) {
            IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCatalogMessages.DETAIL_FEATURES,
                    MessageArgument.untrusted("count", summary.placedFeatures().size()),
                    MessageArgument.untrusted("value", WorldgenCatalogSummary.featureTypes(summary.featureTypes(), DETAIL_LIMIT))));
        }
        if (!summary.structures().isEmpty()) {
            IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCatalogMessages.DETAIL_STRUCTURES,
                    MessageArgument.untrusted("count", summary.structures().size()),
                    MessageArgument.untrusted("value", WorldgenCatalogSummary.limited(summary.structures(), namespace, DETAIL_LIMIT))));
        }
        if (!summary.dimensionTypes().isEmpty() || !summary.levelStems().isEmpty()) {
            IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCatalogMessages.DETAIL_DIMENSIONS,
                    MessageArgument.untrusted("value", orNone(WorldgenCatalogSummary.limited(summary.dimensionTypes(), namespace, DETAIL_LIMIT))),
                    MessageArgument.untrusted("value2", orNone(WorldgenCatalogSummary.limited(summary.levelStems(), namespace, DETAIL_LIMIT)))));
        }
        return 1;
    }

    private static int refresh(NativeCommandSource source) {
        NativeModdedServer server = source.server();
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCatalogMessages.REFRESHING));
        ModdedWorldgenCatalogs.capture(server).whenComplete((CaptureResult result, Throwable failure) ->
                source.execute(() -> reportRefresh(source, result, failure)));
        return 1;
    }

    private static void reportRefresh(NativeCommandSource source, CaptureResult result, Throwable failure) {
        if (failure != null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCatalogMessages.REFRESH_FAILED,
                    MessageArgument.untrusted("reason", reason(failure))));
            return;
        }
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCatalogMessages.REFRESHED,
                MessageArgument.untrusted("duration", result.millis() + " ms"),
                MessageArgument.untrusted("path", result.file().toString())));
    }

    private static int ingest(NativeCommandSource source, String raw) {
        ModIngestArguments arguments;
        try {
            arguments = ModIngestArguments.parse(raw);
        } catch (IllegalArgumentException invalid) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCatalogMessages.INGEST_INVALID,
                    MessageArgument.untrusted("reason", invalid.getMessage())));
            return 0;
        }
        Optional<WorldgenCatalog> catalog = ModdedWorldgenCatalogs.latest();
        if (catalog.isEmpty()) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCatalogMessages.MISSING));
            return 0;
        }
        File packsRoot = ModdedPackCommands.packsRoot();
        File sourcePack = PackDirectoryResolver.resolveExisting(packsRoot, arguments.sourcePack());
        if (sourcePack == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCatalogMessages.INGEST_PACK_MISSING,
                    MessageArgument.untrusted("pack", arguments.sourcePack()),
                    MessageArgument.untrusted("path", packsRoot.getAbsolutePath())));
            return 0;
        }
        Path targetPack = packsRoot.toPath().toAbsolutePath().normalize().resolve(arguments.targetPack());
        ModIngestRequest request = new ModIngestRequest(sourcePack.toPath(), targetPack, arguments.dimension(),
                catalog.get(), arguments.namespaces(), arguments.replace());
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCatalogMessages.INGEST_STARTED,
                MessageArgument.untrusted("source", arguments.sourcePack()),
                MessageArgument.untrusted("target", arguments.targetPack()),
                MessageArgument.untrusted("dimension", arguments.dimension())));
        Thread worker = new Thread(() -> runIngest(source, request), "Iris Mod Ingest");
        worker.setDaemon(true);
        worker.start();
        return 1;
    }

    private static void runIngest(NativeCommandSource source, ModIngestRequest request) {
        try {
            ModIngestReport report = ModIngester.ingest(request);
            source.execute(() -> reportIngest(source, report));
        } catch (FileAlreadyExistsException exists) {
            source.execute(() -> IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCatalogMessages.INGEST_TARGET_EXISTS,
                    MessageArgument.untrusted("target", request.targetPack().getFileName().toString()))));
        } catch (IOException | RuntimeException failure) {
            ModdedIrisLog.error("Iris mod ingest into " + request.targetPack() + " failed", failure);
            String reason = reason(failure);
            source.execute(() -> IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCatalogMessages.INGEST_FAILED,
                    MessageArgument.untrusted("reason", reason))));
        }
    }

    private static void reportIngest(NativeCommandSource source, ModIngestReport report) {
        for (String line : report.summaryLines()) {
            IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCatalogMessages.INGEST_LINE, MessageArgument.untrusted("summary", line)));
        }
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCatalogMessages.INGEST_COMPLETE,
                MessageArgument.untrusted("path", report.targetPack().toString())));
    }

    private static String namespaceLine(NamespaceSummary summary) {
        String version = summary.namespace().version().isEmpty() ? NONE : summary.namespace().version();
        return IrisLanguage.plain(ModdedCatalogMessages.NAMESPACE,
                MessageArgument.untrusted("name", summary.namespace().namespace()),
                MessageArgument.untrusted("version", version),
                MessageArgument.untrusted("value", summary.biomes().size()),
                MessageArgument.untrusted("value2", summary.placedFeatures().size()),
                MessageArgument.untrusted("detail", orNone(WorldgenCatalogSummary.featureTypes(summary.featureTypes(), LIST_FEATURE_TYPES))),
                MessageArgument.untrusted("value3", summary.structures().size()),
                MessageArgument.untrusted("value4", summary.dimensionTypes().size()),
                MessageArgument.untrusted("value5", summary.levelStems().size()));
    }

    private static String orNone(String value) {
        return value.isEmpty() ? NONE : value;
    }

    private static String reason(Throwable failure) {
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    private static List<String> contentNamespaces() {
        Optional<WorldgenCatalog> latest = ModdedWorldgenCatalogs.latest();
        if (latest.isEmpty()) {
            return List.of();
        }
        NamespaceCache cached = NAMESPACE_CACHE.get();
        if (cached != null && cached.catalog() == latest.get()) {
            return cached.namespaces();
        }
        List<String> namespaces = new ArrayList<>();
        for (NamespaceSummary summary : WorldgenCatalogSummary.namespaces(latest.get())) {
            if (summary.hasContent()) {
                namespaces.add(summary.namespace().namespace());
            }
        }
        NamespaceCache created = new NamespaceCache(latest.get(), List.copyOf(namespaces));
        NAMESPACE_CACHE.set(created);
        return created.namespaces();
    }

    private static CompletableFuture<Suggestions> suggestIngestArguments(CommandContext<NativeCommandSource> context,
                                                                         SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        int lastSpace = remaining.lastIndexOf(' ');
        int bareArguments = 0;
        List<String> usedOptions = new ArrayList<>(3);
        for (String token : remaining.substring(0, Math.max(0, lastSpace)).trim().split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            int equals = token.indexOf('=');
            if (equals < 0) {
                bareArguments++;
            } else {
                usedOptions.add(token.substring(0, equals + 1));
            }
        }
        SuggestionsBuilder current = builder.createOffset(builder.getStart() + lastSpace + 1);
        if (bareArguments < 2) {
            List<String> packs = new ArrayList<>();
            for (File pack : PackDirectoryResolver.listVisiblePackDirectories(ModdedPackCommands.packsRoot())) {
                packs.add(pack.getName());
            }
            return NativeCommandRegistration.suggest(packs, current);
        }
        List<String> options = new ArrayList<>(ModIngestArguments.OPTION_SUGGESTIONS.size());
        for (String option : ModIngestArguments.OPTION_SUGGESTIONS) {
            if (!usedOptions.contains(option.substring(0, option.indexOf('=') + 1))) {
                options.add(option);
            }
        }
        return NativeCommandRegistration.suggest(options, current);
    }

    private record NamespaceCache(WorldgenCatalog catalog, List<String> namespaces) {
    }
}
