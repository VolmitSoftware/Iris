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

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.localization.BukkitCommandMessagesExtended;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.modded.ModdedDimensionRegistryStore;
import art.arcane.iris.modded.ModdedDimensionReplacement;
import art.arcane.iris.modded.ModdedDimensionReplacements;
import art.arcane.iris.modded.ModdedEngineBootstrap;
import art.arcane.iris.modded.ModdedForcedDatapack;
import art.arcane.iris.modded.ModdedIrisLog;
import art.arcane.iris.modded.ModdedModConfig;
import art.arcane.iris.modded.ModdedReplacementReconciler;
import art.arcane.iris.modded.catalog.ModdedWorldgenCatalogs;
import art.arcane.iris.modded.localization.ModdedCommandMessages;
import art.arcane.iris.pack.PackDirectoryResolver;
import art.arcane.iris.pack.PackDownloader;
import art.arcane.iris.pack.ingest.catalog.WorldgenCatalog;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandRegistration;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandSource;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.util.localization.MessageArgument;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

final class ModdedReplaceCommands {
    private static final Predicate<NativeCommandSource> GATE = NativeCommandRegistration.GAMEMASTERS;
    private static final String ARGUMENTS = "arguments";
    private static final String MISSING_TARGET = "/iris replace requires an existing exact world slot; use /iris create for a new world.";
    private static final String STAGE_FAILURE = "Could not stage the world replacement: ";
    private static final List<String> TARGET_NAMES = List.of("main", "nether", "end");
    private static final List<String> KEYS = List.of("target=", "type=", "seed=");

    private ModdedReplaceCommands() {
    }

    static LiteralArgumentBuilder<NativeCommandSource> tree(String name) {
        return NativeCommandRegistration.literal(name).requires(GATE)
                .then(NativeCommandRegistration.argument(ARGUMENTS, StringArgumentType.greedyString())
                        .suggests(ModdedReplaceCommands::suggest)
                        .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> replace(
                                context.getSource(),
                                StringArgumentType.getString(context, ARGUMENTS)))));
    }

    private static int replace(NativeCommandSource source, String raw) {
        NativeModdedServer server = source.server();
        ModdedReplaceArguments arguments;
        ModdedReplaceTarget target;
        try {
            arguments = ModdedReplaceArguments.parse(raw);
            target = ModdedReplaceTarget.resolve(arguments.target(), levelName(server));
        } catch (IllegalArgumentException invalid) {
            IrisModdedCommands.fail(source, invalid.getMessage());
            return 0;
        }
        if (ModdedEngineBootstrap.loader().clientEnvironment()) {
            IrisModdedCommands.fail(source, "Iris replaces dimensions only on dedicated servers.");
            return 0;
        }
        if (arguments.off()) {
            return turnOff(source, server, target);
        }
        String resolvedType = arguments.type().equalsIgnoreCase(ModdedReplaceArguments.DEFAULT_TYPE)
                ? IrisSettings.get().getGenerator().getDefaultWorldType()
                : arguments.type();
        String pack = packOf(resolvedType);
        IrisDimension dimension = installedDimension(pack, packDimensionOf(resolvedType));
        if (dimension == null) {
            reportDimensionNotFound(source, resolvedType);
            return 0;
        }
        if (ModdedWorldCommands.blockIfPackBroken(source, target.levelKey(), pack)) {
            return 0;
        }
        try {
            ModdedReplaceTarget.requireCompatibleEnvironment(target.kind(), dimension.getEnvironment());
            ModdedDimensionReplacement requested = ModdedDimensionReplacements.canonical(
                    ModdedDimensionReplacement.parse(target.levelKey(), resolvedType, arguments.seed()),
                    ModdedPackCommands.packsRoot().toPath());
            ModdedDimensionReplacement staged = target.kind() == ModdedReplaceTarget.Kind.IRIS_WORLD
                    ? stageIrisWorld(server, requested)
                    : stageLevel(server, requested);
            String seedDetail = arguments.seed().isEmpty()
                    ? " preserving seed " + staged.seed().getAsLong()
                    : " using seed " + staged.seed().getAsLong();
            IrisModdedCommands.ok(source, "Staged Iris replacement for " + staged.levelKey() + seedDetail
                    + ". Restart once to publish it. On that restart the current terrain of the dimension moves to "
                    + "a replacement-recovery folder under config/irisworldgen; player data stays in the save.");
            return 1;
        } catch (IllegalArgumentException rejected) {
            IrisModdedCommands.fail(source, STAGE_FAILURE + rejected.getMessage());
            return 0;
        } catch (IOException | RuntimeException failure) {
            ModdedIrisLog.error("Iris failed to stage the world replacement for {}", target.levelKey(), failure);
            IrisModdedCommands.fail(source, STAGE_FAILURE + IrisLanguage.errorDetail(failure));
            return 0;
        }
    }

    private static ModdedDimensionReplacement stageLevel(NativeModdedServer server, ModdedDimensionReplacement requested)
            throws IOException {
        NativeWorld level = server.world(requested.levelKey());
        if (level == null) {
            throw new IllegalArgumentException(MISSING_TARGET);
        }
        Path worldRoot = server.root();
        requireReplaceableLevel(worldRoot, requested.levelKey());
        requireNothingPending(worldRoot, requested.levelKey());
        long seed = requested.seed().orElse(ModdedReplacementReconciler.applied(worldRoot, requested.levelKey())
                .map((ModdedDimensionReplacement applied) -> applied.seed().getAsLong())
                .orElse(level.seed()));
        ModdedDimensionReplacement staged = requested.withSeed(seed);
        ModdedReplacementReconciler.stagePending(worldRoot, staged);
        try {
            if (ModdedModConfig.setDimensionReplacement(staged)) {
                ModdedForcedDatapack.scheduleRegeneration("dimension replacement of " + staged.levelKey());
            }
        } catch (RuntimeException failure) {
            ModdedReplacementReconciler.cancelPending(worldRoot, staged.levelKey());
            throw failure;
        }
        return staged;
    }

    private static ModdedDimensionReplacement stageIrisWorld(NativeModdedServer server, ModdedDimensionReplacement requested)
            throws IOException {
        ModdedDimensionRegistryStore.PersistentDimension registered =
                ModdedDimensionRegistryStore.get(server, requested.levelKey());
        if (registered == null) {
            throw new IllegalArgumentException(MISSING_TARGET);
        }
        Path worldRoot = server.root();
        requireNothingPending(worldRoot, requested.levelKey());
        ModdedDimensionReplacement staged = requested.withSeed(requested.seed().orElse(registered.seed()));
        ModdedReplacementReconciler.stagePending(worldRoot, staged);
        return staged;
    }

    private static void requireReplaceableLevel(Path worldRoot, String levelKey) throws IOException {
        Optional<WorldgenCatalog> catalog = ModdedWorldgenCatalogs.latest();
        Set<String> applied = ModdedReplacementReconciler.applied(worldRoot, levelKey).isPresent()
                ? Set.of(levelKey)
                : Set.of();
        ModdedDimensionReplacements.ReplaceableLevels levels = catalog.isEmpty()
                ? ModdedDimensionReplacements.ReplaceableLevels.unknown()
                : ModdedDimensionReplacements.ReplaceableLevels.of(catalog.get().levelStems(), applied);
        if (levels.replaceable(levelKey)) {
            return;
        }
        if (!levels.known()) {
            throw new IllegalArgumentException(IrisLanguage.plain(
                    ModdedCommandMessages.MODDED_WORLD_COMMANDS_REPLACE_REGISTRY_NOT_READY));
        }
        throw new IllegalArgumentException(IrisLanguage.plain(
                ModdedCommandMessages.MODDED_WORLD_COMMANDS_REPLACE_RUNTIME_LEVEL,
                MessageArgument.untrusted("dimensionId", levelKey)));
    }

    private static void requireNothingPending(Path worldRoot, String levelKey) throws IOException {
        Map<String, ModdedDimensionReplacement> configured = ModdedModConfig.get().dimensionReplacements();
        if (ModdedReplacementReconciler.pending(worldRoot, levelKey, configured)) {
            throw new IllegalArgumentException("A replacement is already pending for " + levelKey + ".");
        }
    }

    private static int turnOff(NativeCommandSource source, NativeModdedServer server, ModdedReplaceTarget target) {
        String levelKey = target.levelKey();
        try {
            Path worldRoot = server.root();
            boolean cancelled = ModdedReplacementReconciler.cancelPending(worldRoot, levelKey);
            boolean applied = target.kind() != ModdedReplaceTarget.Kind.IRIS_WORLD
                    && ModdedReplacementReconciler.applied(worldRoot, levelKey).isPresent();
            boolean cleared = target.kind() != ModdedReplaceTarget.Kind.IRIS_WORLD
                    && ModdedModConfig.clearDimensionReplacement(levelKey);
            if (cleared) {
                ModdedForcedDatapack.scheduleRegeneration("dimension replacement of " + levelKey + " turned off");
            }
            if (cleared && applied) {
                IrisModdedCommands.ok(source, "Turned off the Iris replacement of " + levelKey
                        + ". Restart once to return the dimension to its own generator; its Iris terrain moves to "
                        + "a replacement-recovery folder under config/irisworldgen.");
                return 1;
            }
            if (cleared || cancelled) {
                IrisModdedCommands.ok(source, "Cancelled the staged Iris replacement of " + levelKey + ".");
                return 1;
            }
            if (applied) {
                IrisModdedCommands.ok(source, "The Iris replacement of " + levelKey
                        + " is already turned off. Restart once to return the dimension to its own generator.");
                return 1;
            }
            IrisModdedCommands.fail(source, "No Iris replacement is staged or applied for " + levelKey + ".");
            return 0;
        } catch (IOException | RuntimeException failure) {
            ModdedIrisLog.error("Iris failed to turn off the replacement of {}", levelKey, failure);
            IrisModdedCommands.fail(source, "Could not turn off the world replacement: " + IrisLanguage.errorDetail(failure));
            return 0;
        }
    }

    private static String levelName(NativeModdedServer server) {
        Path levelFolder = server.root().toAbsolutePath().normalize().getFileName();
        return levelFolder == null ? "" : levelFolder.toString();
    }

    private static IrisDimension installedDimension(String pack, String packDimension) {
        if (!pack.matches("[A-Za-z0-9_.-]+") || pack.contains("..")
                || !packDimension.matches("[A-Za-z0-9_/.-]+") || packDimension.contains("..")) {
            return null;
        }
        File packFolder = new File(ModdedPackCommands.packsRoot(), pack);
        if (!packFolder.isDirectory()) {
            return null;
        }
        return IrisData.get(packFolder).getDimensionLoader().load(packDimension);
    }

    private static void reportDimensionNotFound(NativeCommandSource source, String resolvedType) {
        IrisModdedCommands.fail(source, IrisLanguage.plain(
                BukkitCommandMessagesExtended.COMMAND_IRIS_DIMENSION_NOT_FOUND,
                MessageArgument.untrusted("dimension", resolvedType)));
        List<String> installed = installedTypes(ModdedPackCommands.packsRoot());
        if (!installed.isEmpty()) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(
                    ModdedCommandMessages.MODDED_WORLD_COMMANDS_REPLACE_INSTALLED_TYPES,
                    MessageArgument.untrusted("value", String.join(", ", installed))));
        }
        IrisModdedCommands.fail(source, IrisLanguage.plain(
                BukkitCommandMessagesExtended.COMMAND_IRIS_INSTALL_PACK_AND_RESTART,
                MessageArgument.untrusted("command", PackDownloader.downloadCommandFor(packOf(resolvedType)))));
    }

    static List<String> installedTypes(File packsRoot) {
        Set<String> types = new TreeSet<>();
        for (File pack : PackDirectoryResolver.listVisiblePackDirectories(packsRoot)) {
            List<String> dimensionKeys = dimensionKeys(pack.toPath().resolve("dimensions"));
            if (dimensionKeys.isEmpty()) {
                continue;
            }
            types.add(pack.getName());
            for (String dimensionKey : dimensionKeys) {
                if (!dimensionKey.equals(pack.getName())) {
                    types.add(pack.getName() + ":" + dimensionKey);
                }
            }
        }
        return List.copyOf(types);
    }

    private static List<String> dimensionKeys(Path dimensions) {
        if (!Files.isDirectory(dimensions)) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        try (Stream<Path> files = Files.walk(dimensions)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String relative = dimensions.relativize(file).toString().replace(File.separatorChar, '/');
                if (relative.endsWith(".json")) {
                    keys.add(relative.substring(0, relative.length() - ".json".length()));
                }
            }
        } catch (IOException | RuntimeException unreadable) {
            ModdedIrisLog.warn("Iris could not list the dimensions of pack folder {}", dimensions.getParent(), unreadable);
        }
        return keys;
    }

    private static String packOf(String type) {
        String value = type.trim();
        int colon = value.indexOf(':');
        return colon >= 0 ? value.substring(0, colon) : value;
    }

    private static String packDimensionOf(String type) {
        String value = type.trim();
        int colon = value.indexOf(':');
        return colon >= 0 ? value.substring(colon + 1) : value;
    }

    private static CompletableFuture<Suggestions> suggest(CommandContext<NativeCommandSource> context,
                                                          SuggestionsBuilder builder) throws CommandSyntaxException {
        String remaining = builder.getRemaining();
        int tokenStart = remaining.lastIndexOf(' ') + 1;
        String token = remaining.substring(tokenStart);
        int positional = 0;
        for (String previous : remaining.substring(0, tokenStart).trim().split("\\s+")) {
            if (!previous.isEmpty() && previous.indexOf('=') < 0) {
                positional++;
            }
        }
        int equals = token.indexOf('=');
        if (equals >= 0) {
            String key = token.substring(0, equals);
            SuggestionsBuilder valueBuilder = builder.createOffset(builder.getStart() + tokenStart + equals + 1);
            return switch (key) {
                case "target", "world-name" -> NativeCommandRegistration.suggest(targetNames(context.getSource()), valueBuilder);
                case "type", "dimension", "pack" -> typeSuggestions(context, valueBuilder);
                default -> builder.buildFuture();
            };
        }
        SuggestionsBuilder tokenBuilder = builder.createOffset(builder.getStart() + tokenStart);
        if (positional == 0) {
            List<String> targets = new ArrayList<>(targetNames(context.getSource()));
            targets.addAll(KEYS);
            return NativeCommandRegistration.suggest(targets, tokenBuilder);
        }
        if (positional == 1) {
            return typeSuggestions(context, tokenBuilder);
        }
        return NativeCommandRegistration.suggest(List.of(ModdedReplaceArguments.PRESERVE_SEED), tokenBuilder);
    }

    private static CompletableFuture<Suggestions> typeSuggestions(CommandContext<NativeCommandSource> context,
                                                                  SuggestionsBuilder builder)
            throws CommandSyntaxException {
        if (ModdedReplaceArguments.OFF.startsWith(builder.getRemainingLowerCase())) {
            builder.suggest(ModdedReplaceArguments.OFF);
        }
        return ModdedCommandSuggestions.PACK_NAMES.getSuggestions(context, builder);
    }

    private static List<String> targetNames(NativeCommandSource source) {
        List<String> names = new ArrayList<>(TARGET_NAMES);
        for (NativeWorld level : source.server().worlds()) {
            names.add(level.name());
        }
        return names;
    }
}
