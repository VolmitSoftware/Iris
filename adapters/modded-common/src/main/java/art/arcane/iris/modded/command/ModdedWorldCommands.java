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
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.BrokenPackException;
import art.arcane.iris.pack.PackValidationRegistry;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.modded.IrisModdedChunkGenerator;
import art.arcane.iris.modded.MainWorldService;
import art.arcane.iris.modded.ModdedDimensionManager;
import art.arcane.iris.modded.ModdedEngineBootstrap;
import art.arcane.iris.modded.ModdedModConfig;
import art.arcane.iris.modded.ModdedPrimaryWorldRouter;
import art.arcane.iris.modded.ModdedServerLevels;
import art.arcane.iris.modded.ModdedStartup;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.modded.localization.ModdedCommandMessages;
import art.arcane.iris.localization.RuntimeUiMessages;
import art.arcane.volmlib.util.localization.MessageArgument;
public final class ModdedWorldCommands {
    private static final Predicate<CommandSourceStack> GATE = Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);
    private static final String DEFAULT_NAMESPACE = "irisworldgen";
    private static final long DEFAULT_SEED = 1337L;
    private static final SuggestionProvider<CommandSourceStack> LOADED_DIMENSIONS = (CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) -> SharedSuggestionProvider.suggest(loadedIrisDimensions(context.getSource().getServer()), builder);

    private ModdedWorldCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> tree(String name) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name).requires(GATE);

        root.executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> ModdedCommandHelp.send(context.getSource(), name)));

        root.then(Commands.literal("status")
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> status(context.getSource()))));
        root.then(Commands.literal("list")
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> list(context.getSource()))));
        root.then(Commands.literal("ls")
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> list(context.getSource()))));

        root.then(enableTree("enable"));
        root.then(enableTree("create"));
        root.then(updateTree());
        root.then(replaceOverworldTree());
        root.then(mainWorldTree());

        root.then(disableTree());
        root.then(deleteTree("delete"));
        root.then(deleteTree("remove"));
        root.then(deleteTree("rm"));

        return root;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> enableTree(String name) {
        return Commands.literal(name)
                .then(Commands.argument("dimension", IdentifierArgument.id())
                        .then(Commands.argument("pack", StringArgumentType.string()).suggests(IrisModdedCommands.PACK_NAMES)
                                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> enable(context.getSource(),
                                        dimensionArgument(context),
                                        StringArgumentType.getString(context, "pack"),
                                        null)))
                                .then(Commands.argument("seed", StringArgumentType.word())
                                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> enable(context.getSource(),
                                                dimensionArgument(context),
                                                StringArgumentType.getString(context, "pack"),
                                                StringArgumentType.getString(context, "seed")))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> disableTree() {
        return Commands.literal("disable")
                .then(Commands.argument("dimension", IdentifierArgument.id()).suggests(LOADED_DIMENSIONS)
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> disable(context.getSource(), dimensionArgument(context), false))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> updateTree() {
        return Commands.literal("update")
                .then(Commands.argument("dimension", IdentifierArgument.id()).suggests(LOADED_DIMENSIONS)
                        .then(Commands.argument("pack", StringArgumentType.string())
                                .suggests(IrisModdedCommands.PACK_NAMES)
                                .executes(ModdedCommandTree.localized(
                                        (CommandContext<CommandSourceStack> context) -> update(
                                                context.getSource(),
                                                dimensionArgument(context),
                                                StringArgumentType.getString(context, "pack")
                                        )
                                ))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> deleteTree(String name) {
        return Commands.literal(name)
                .then(Commands.argument("dimension", IdentifierArgument.id()).suggests(LOADED_DIMENSIONS)
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> disable(context.getSource(), dimensionArgument(context), true))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> replaceOverworldTree() {
        return Commands.literal("replace-overworld")
                .then(Commands.argument("pack", StringArgumentType.string()).suggests(IrisModdedCommands.PACK_NAMES)
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> replaceOverworld(context.getSource(),
                                StringArgumentType.getString(context, "pack"),
                                null)))
                        .then(Commands.argument("seed", StringArgumentType.word())
                                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> replaceOverworld(context.getSource(),
                                        StringArgumentType.getString(context, "pack"),
                                        StringArgumentType.getString(context, "seed"))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> mainWorldTree() {
        return Commands.literal("mainworld")
                .then(Commands.literal("off")
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> clearMainWorld(context.getSource()))))
                .then(Commands.argument("pack", StringArgumentType.string()).suggests(IrisModdedCommands.PACK_NAMES)
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> mainWorld(context.getSource(),
                                StringArgumentType.getString(context, "pack"),
                                null)))
                        .then(Commands.argument("seed", StringArgumentType.word())
                                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> mainWorld(context.getSource(),
                                        StringArgumentType.getString(context, "pack"),
                                        StringArgumentType.getString(context, "seed"))))));
    }

    public static int createWorld(CommandSourceStack source, String name, String pack, long seed) {
        String[] packRef = parsePackRef(pack);
        return enable(source, name, packRef[0], packRef[1], seed);
    }

    private static int enable(CommandSourceStack source, String targetDimension, String packRaw, String seedRaw) {
        Long seed = parseSeed(source, seedRaw);
        if (seed == null) {
            return 0;
        }
        String[] packRef = parsePackRef(packRaw);
        return enable(source, targetDimension, packRef[0], packRef[1], seed);
    }

    private static int enable(CommandSourceStack source, String targetDimension, String pack, String packDimension, long seed) {
        MinecraftServer server = source.getServer();
        String dimensionId;
        try {
            dimensionId = normalizeDimensionId(targetDimension);
        } catch (IllegalArgumentException e) {
            IrisModdedCommands.fail(source, e.getMessage());
            return 0;
        }
        if (!validPackRef(source, pack, packDimension)) {
            return 0;
        }
        File packFolder = new File(ModdedPackCommands.packsRoot(), pack);
        if (packFolder.isDirectory()) {
            return enableInstalled(source, server, dimensionId, pack, packDimension, seed);
        }
        IrisModdedCommands.fail(source, IrisLanguage.plain(
                ModdedCommandMessages.MODDED_WORLD_COMMANDS_REQUIRED_PACK_IS_NOT_INSTALLED_INSTALL_THEN_RESTART,
                MessageArgument.untrusted("pack", pack)));
        return 0;
    }

    private static int enableInstalled(CommandSourceStack source, MinecraftServer server, String dimensionId, String pack, String packDimension, long seed) {
        if (blockIfPackBroken(source, dimensionId, pack)) {
            return 0;
        }
        if (!loadPackDimension(source, pack, packDimension)) {
            return 0;
        }
        try {
            ModdedDimensionManager.createPersistent(server, dimensionId, pack, packDimension, seed);
        } catch (Throwable e) {
            ModdedIrisLog.error("Iris world injection failed for {} (pack={} dim={})", dimensionId, pack, packDimension, e);
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_WORLD_COMMANDS_FAILED_INJECT_IRIS_WORLD, MessageArgument.untrusted("dimensionId", dimensionId), MessageArgument.untrusted("value", e.getClass().getSimpleName()), MessageArgument.trusted("errorMessage", IrisLanguage.errorDetail(e))));
            return 0;
        }
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_WORLD_COMMANDS_CREATED_IRIS_WORLD_FROM_PACK_DIMENSION_SEED, MessageArgument.untrusted("dimensionId", dimensionId), MessageArgument.untrusted("pack", pack), MessageArgument.untrusted("packDimension", packDimension), MessageArgument.untrusted("seed", seed)));
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_WORLD_COMMANDS_IT_IS_LIVE_NOW_RE_INJECTED_ON_EVERY_STARTUP_TELEPORT));
        return 1;
    }

    private static int update(CommandSourceStack source, String targetDimension, String packRaw) {
        String dimensionId;
        try {
            dimensionId = normalizeDimensionId(targetDimension);
        } catch (IllegalArgumentException failure) {
            IrisModdedCommands.fail(source, failure.getMessage());
            return 0;
        }
        String[] packRef = parsePackRef(packRaw);
        String pack = packRef[0];
        String packDimension = packRef[1];
        if (!validPackRef(source, pack, packDimension)
                || blockIfPackBroken(source, dimensionId, pack)
                || !loadPackDimension(source, pack, packDimension)) {
            return 0;
        }
        try {
            ModdedDimensionManager.UpdateResult result = ModdedDimensionManager.stagePersistentUpdate(
                    source.getServer(),
                    dimensionId,
                    pack,
                    packDimension
            );
            if (result.restartRequired()) {
                IrisModdedCommands.ok(source, "Iris world update staged for " + dimensionId
                        + " from " + pack + ":" + packDimension
                        + ". Restart the server to activate it; the running world remains on its current pack.");
            } else {
                IrisModdedCommands.ok(source, "Iris world " + dimensionId
                        + " already uses this immutable pack; no restart is required.");
            }
            return 1;
        } catch (Throwable failure) {
            ModdedIrisLog.error("Iris world update staging failed for {} (pack={} dim={})",
                    dimensionId, pack, packDimension, failure);
            IrisModdedCommands.fail(source, "Iris world update failed for " + dimensionId
                    + ": " + IrisLanguage.errorDetail(failure));
            return 0;
        }
    }

    private static int replaceOverworld(CommandSourceStack source, String packRaw, String seedRaw) {
        MinecraftServer server = source.getServer();
        Long seed = parseSeed(source, seedRaw);
        if (seed == null) {
            return 0;
        }
        String[] packRef = parsePackRef(packRaw);
        String pack = packRef[0];
        String packDimension = packRef[1];
        if (!validPackRef(source, pack, packDimension)) {
            return 0;
        }
        String dimensionId = DEFAULT_NAMESPACE + ":primary";
        if (blockIfPackBroken(source, dimensionId, pack)) {
            return 0;
        }
        if (!loadPackDimension(source, pack, packDimension)) {
            return 0;
        }
        try {
            ModdedDimensionManager.createPersistent(server, dimensionId, pack, packDimension, seed);
        } catch (Throwable e) {
            ModdedIrisLog.error("Iris primary world injection failed for {} (pack={} dim={})", dimensionId, pack, packDimension, e);
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_WORLD_COMMANDS_FAILED_INJECT_IRIS_PRIMARY_WORLD, MessageArgument.untrusted("value", e.getClass().getSimpleName()), MessageArgument.trusted("errorMessage", IrisLanguage.errorDetail(e))));
            return 0;
        }
        ModdedModConfig.setPrimaryWorld(dimensionId);
        ModdedPrimaryWorldRouter.clear();
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_WORLD_COMMANDS_IRIS_PRIMARY_WORLD_SET_PACK_DIMENSION_SEED, MessageArgument.untrusted("dimensionId", dimensionId), MessageArgument.untrusted("pack", pack), MessageArgument.untrusted("packDimension", packDimension), MessageArgument.untrusted("seed", seed)));
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_WORLD_COMMANDS_VANILLA_OVERWORLD_GENERATOR_CANNOT_BE_HOT_SWAPPED_SO_THIS_DOES));
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_WORLD_COMMANDS_INSTEAD_IS_NOW_CONFIGURED_PRIMARY_WORLD_PLAYERS_VANILLA_OVERWORLD_ARE, MessageArgument.untrusted("dimensionId", dimensionId)));
        return 1;
    }

    private static int clearMainWorld(CommandSourceStack source) {
        ModdedModConfig.setMainWorld("", 0L);
        MainWorldService.clearOverride();
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_WORLD_COMMANDS_IRIS_MAIN_WORLD_OVERRIDE_CLEARED_OVERWORLD_KEEPS_ITS_CURRENT_GENERATOR));
        return 1;
    }

    private static int mainWorld(CommandSourceStack source, String packRaw, String seedRaw) {
        MinecraftServer server = source.getServer();
        long seed;
        if (seedRaw == null || seedRaw.isBlank()) {
            seed = 0L;
        } else if (seedRaw.equalsIgnoreCase("random")) {
            long rolled = ThreadLocalRandom.current().nextLong();
            seed = rolled == 0L ? 1L : rolled;
        } else {
            try {
                seed = Long.parseLong(seedRaw.trim());
            } catch (NumberFormatException e) {
                IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_WORLD_COMMANDS_INVALID_SEED_USE_NUMBER_RANDOM, MessageArgument.untrusted("seedRaw", seedRaw)));
                return 0;
            }
        }
        String[] packRef = parsePackRef(packRaw);
        String pack = packRef[0];
        String packDimension = packRef[1];
        if (!validPackRef(source, pack, packDimension)) {
            return 0;
        }
        File packFolder = new File(ModdedPackCommands.packsRoot(), pack);
        if (packFolder.isDirectory()) {
            return applyMainWorld(source, pack, packDimension, packRaw, seed);
        }
        IrisModdedCommands.fail(source, IrisLanguage.plain(
                ModdedCommandMessages.MODDED_WORLD_COMMANDS_REQUIRED_PACK_IS_NOT_INSTALLED_INSTALL_THEN_RESTART,
                MessageArgument.untrusted("pack", pack)));
        return 0;
    }

    private static int applyMainWorld(CommandSourceStack source, String pack, String packDimension, String packRef, long seed) {
        if (blockIfPackBroken(source, "the main world", pack)) {
            return 0;
        }
        try {
            if (!loadPackDimension(source, pack, packDimension)) {
                return 0;
            }
        } catch (Throwable e) {
            ModdedIrisLog.error("Iris main world pack load failed for {} (dim={})", pack, packDimension, e);
            if (PackValidationRegistry.get(pack) == null) {
                IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_WORLD_COMMANDS_PACK_IS_NOT_READY_YET_STILL_LOADING_VALIDATING_TRY_COMMAND, MessageArgument.untrusted("pack", pack)));
                return 0;
            }
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_PACK_COMMANDS_VALIDATION_FAILED,
                    MessageArgument.untrusted("value", pack + ":" + packDimension),
                    MessageArgument.trusted("value2", e.getClass().getSimpleName() + IrisLanguage.errorDetail(e))));
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_WORLD_COMMANDS_FIX_PACK_RUN_IRIS_PACK_VALIDATE_REVALIDATE, MessageArgument.untrusted("pack", pack)));
            return 0;
        }
        ModdedModConfig.setMainWorld(packRef, seed);
        if (!MainWorldService.stage(packRef, seed)) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_WORLD_COMMANDS_FAILED_WRITE_SERVER_PROPERTIES_CHECK_FILE_PERMISSIONS_SET_LEVEL_TYPE));
            return 0;
        }
        String preset = MainWorldService.presetIdFor(packRef);
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_WORLD_COMMANDS_IRIS_MAIN_WORLD_SET_PRESET_SEED, MessageArgument.untrusted("pack", pack), MessageArgument.untrusted("preset", preset), MessageArgument.untrusted("value", seed == 0L ? IrisLanguage.plain(RuntimeUiMessages.STATUS_RANDOM) : Long.toString(seed))));
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_WORLD_COMMANDS_SERVER_PROPERTIES_LEVEL_TYPE_IS_NOW_ON_NEXT_RESTART_OVERWORLD, MessageArgument.untrusted("preset", preset)));
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_WORLD_COMMANDS_PLAYER_DATA_INVENTORIES_ADVANCEMENTS_STATS_IS_KEPT_EXISTING_TERRAIN_THOSE));
        if (ModdedModConfig.get().mainWorldAutoRestart()) {
            IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_WORLD_COMMANDS_MAINWORLDAUTORESTART_IS_ENABLED_STOPPING_SERVER_NOW_SO_YOUR_RESTART_WRAPPER));
            source.getServer().halt(false);
        } else {
            IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_WORLD_COMMANDS_RESTART_SERVER_NOW_GENERATE_IT_SET_MAINWORLDAUTORESTART_TRUE_MODDED_JSON));
        }
        return 1;
    }

    private static boolean blockIfPackBroken(CommandSourceStack source, String dimensionId, String pack) {
        try {
            ModdedStartup.requirePackForWorldCreation(pack);
            return false;
        } catch (BrokenPackException e) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_WORLD_COMMANDS_REFUSING_CREATE_WORLD_USING_PACK_BECAUSE_REQUIRED_VALIDATION_FAILED, MessageArgument.untrusted("dimensionId", dimensionId), MessageArgument.untrusted("pack", pack)));
            for (String reason : e.getReasons()) {
                IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_WORLD_COMMANDS_MESSAGE, MessageArgument.untrusted("reason", reason)));
            }
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_WORLD_COMMANDS_FIX_PACK_RUN_IRIS_PACK_VALIDATE_REVALIDATE, MessageArgument.untrusted("pack", pack)));
            return true;
        }
    }

    private static boolean loadPackDimension(CommandSourceStack source, String pack, String packDimension) {
        File packFolder = new File(ModdedPackCommands.packsRoot(), pack);
        if (!packFolder.isDirectory()) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_WORLD_COMMANDS_PACK_WAS_NOT_FOUND_UNDER, MessageArgument.untrusted("pack", pack), MessageArgument.untrusted("value", ModdedPackCommands.packsRoot().getAbsolutePath())));
            return false;
        }
        IrisData data = IrisData.get(packFolder);
        IrisDimension dimension = data.getDimensionLoader().load(packDimension);
        if (dimension == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_WORLD_COMMANDS_PACK_DOES_NOT_CONTAIN_DIMENSIONS_JSON, MessageArgument.untrusted("pack", pack), MessageArgument.untrusted("packDimension", packDimension)));
            return false;
        }
        return true;
    }

    private static String[] parsePackRef(String raw) {
        String value = raw.trim();
        int colon = value.indexOf(':');
        if (colon >= 0) {
            return new String[]{value.substring(0, colon), value.substring(colon + 1)};
        }
        return new String[]{value, value};
    }

    private static boolean validPackRef(CommandSourceStack source, String pack, String packDimension) {
        if (pack.matches("[A-Za-z0-9_.-]+") && !pack.contains("..")
                && packDimension.matches("[A-Za-z0-9_/.-]+") && !packDimension.contains("..")) {
            return true;
        }
        IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_WORLD_COMMANDS_INVALID_PACK_REFERENCE_USE_PACK_PACK_DIMENSIONKEY, MessageArgument.untrusted("pack", pack), MessageArgument.untrusted("value", (pack.equals(packDimension) ? "" : ":" + packDimension))));
        return false;
    }

    private static Long parseSeed(CommandSourceStack source, String seedRaw) {
        if (seedRaw == null || seedRaw.isBlank()) {
            return DEFAULT_SEED;
        }
        String value = seedRaw.trim();
        if (value.equalsIgnoreCase("random")) {
            return ThreadLocalRandom.current().nextLong();
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_WORLD_COMMANDS_INVALID_SEED_USE_NUMBER_RANDOM_2, MessageArgument.untrusted("seedRaw", seedRaw)));
            return null;
        }
    }

    private static int disable(CommandSourceStack source, String targetDimension, boolean wipeStorage) {
        MinecraftServer server = source.getServer();
        String dimensionId;
        try {
            dimensionId = normalizeDimensionId(targetDimension);
        } catch (IllegalArgumentException e) {
            IrisModdedCommands.fail(source, e.getMessage());
            return 0;
        }
        boolean removed;
        try {
            removed = ModdedDimensionManager.removePersistent(server, dimensionId, wipeStorage);
        } catch (Throwable e) {
            ModdedIrisLog.error("Iris world removal failed for {}", dimensionId, e);
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_WORLD_COMMANDS_FAILED_REMOVE_IRIS_WORLD, MessageArgument.untrusted("dimensionId", dimensionId), MessageArgument.untrusted("value", e.getClass().getSimpleName()), MessageArgument.trusted("errorMessage", IrisLanguage.errorDetail(e))));
            return 0;
        }
        if (dimensionId.equals(ModdedModConfig.get().primaryWorld())) {
            ModdedModConfig.setPrimaryWorld("");
            ModdedPrimaryWorldRouter.clear();
        }
        if (!removed) {
            if (wipeStorage) {
                IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_WORLD_COMMANDS_IRIS_WORLD_WAS_NOT_LOADED_CLEARED_ITS_PERSISTENT_REGISTRY_ENTRY, MessageArgument.untrusted("dimensionId", dimensionId)));
            } else {
                IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_WORLD_COMMANDS_IRIS_WORLD_WAS_NOT_LOADED_CLEARED_ITS_PERSISTENT_REGISTRY_ENTRY_2, MessageArgument.untrusted("dimensionId", dimensionId)));
            }
            return 1;
        }
        if (wipeStorage) {
            IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_WORLD_COMMANDS_DELETED_IRIS_WORLD_EVACUATED_UNLOADED_CHUNK_MANTLE_DATA_WIPED_DROPPED, MessageArgument.untrusted("dimensionId", dimensionId)));
        } else {
            IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_WORLD_COMMANDS_DISABLED_IRIS_WORLD_EVACUATED_UNLOADED_DROPPED_FROM_STARTUP_REGISTRY, MessageArgument.untrusted("dimensionId", dimensionId)));
            IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_WORLD_COMMANDS_WORLD_DATA_ON_DISK_IS_KEPT_RE_ENABLE_WITH_IRIS, MessageArgument.untrusted("dimensionId", dimensionId), MessageArgument.untrusted("dimensionId2", dimensionId)));
        }
        return 1;
    }

    private static int status(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        int loaded = 0;
        for (ServerLevel level : ModdedServerLevels.levels(server)) {
            if (level.getChunkSource().getGenerator() instanceof IrisModdedChunkGenerator generator) {
                loaded++;
                IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_WORLD_COMMANDS_LOADED_IRIS_LEVEL_PACK_DIMENSION, MessageArgument.untrusted("value", level.dimension().identifier()), MessageArgument.untrusted("value2", generator.activePack()), MessageArgument.untrusted("value3", generator.activeDimensionKey())));
            }
        }
        String primary = ModdedModConfig.get().primaryWorld();
        if (!primary.isBlank()) {
            IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_WORLD_COMMANDS_PRIMARY_WORLD, MessageArgument.untrusted("primary", primary), MessageArgument.trusted("value", IrisLanguage.plain(ModdedModConfig.get().routePlayersToPrimaryWorld() ? RuntimeUiMessages.PRIMARY_PLAYERS_ROUTED_SUFFIX : RuntimeUiMessages.PRIMARY_ROUTING_DISABLED_SUFFIX))));
        }
        if (loaded == 0) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_WORLD_COMMANDS_NO_IRIS_DIMENSIONS_ARE_CURRENTLY_LOADED_CREATE_ONE_WITH_IRIS));
        }
        return loaded > 0 ? 1 : 0;
    }

    private static int list(CommandSourceStack source) {
        List<String> dimensions = loadedIrisDimensions(source.getServer());
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_WORLD_COMMANDS_LOADED_IRIS_DIMENSIONS, MessageArgument.untrusted("value", dimensions.size())));
        for (String dimension : dimensions) {
            IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_WORLD_COMMANDS_MESSAGE_2, MessageArgument.untrusted("dimension", dimension)));
        }
        if (dimensions.isEmpty()) {
            IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_WORLD_COMMANDS_USE_IRIS_WORLD_CREATE_NAME_PACK_INJECT_ONE_WITHOUT_RESTARTING));
        }
        return 1;
    }

    private static List<String> loadedIrisDimensions(MinecraftServer server) {
        List<String> dimensions = new ArrayList<>();
        for (ServerLevel level : ModdedServerLevels.levels(server)) {
            if (level.getChunkSource().getGenerator() instanceof IrisModdedChunkGenerator) {
                dimensions.add(level.dimension().identifier().toString());
            }
        }
        return dimensions;
    }

    private static String dimensionArgument(CommandContext<CommandSourceStack> context) {
        for (ParsedCommandNode<CommandSourceStack> node : context.getNodes()) {
            if ("dimension".equals(node.getNode().getName())) {
                return node.getRange().get(context.getInput());
            }
        }
        return IdentifierArgument.getId(context, "dimension").toString();
    }

    private static String normalizeDimensionId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing dimension id.");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        String namespace = DEFAULT_NAMESPACE;
        String path = normalized;
        int colon = normalized.indexOf(':');
        if (colon >= 0) {
            namespace = normalized.substring(0, colon);
            path = normalized.substring(colon + 1);
        }
        if (!namespace.matches("[a-z0-9_.-]+") || !path.matches("[a-z0-9_./-]+") || path.startsWith("/") || path.endsWith("/") || path.contains("..")) {
            throw new IllegalArgumentException("Invalid dimension id '" + value + "'. Use name or namespace:path.");
        }
        return namespace + ":" + path;
    }
}
