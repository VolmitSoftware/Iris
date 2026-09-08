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
import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.gui.GuiHost;
import art.arcane.iris.core.gui.ImageMapStudioGUI;
import art.arcane.iris.core.gui.NoiseExplorerGUI;
import art.arcane.iris.core.gui.VisionGUI;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.pack.StructurePackageClosure;
import art.arcane.iris.core.pack.ImageMapPackageClosure;
import art.arcane.iris.core.pack.PackValidationResult;
import art.arcane.iris.core.pack.PackValidator;
import art.arcane.iris.core.project.IrisProjectCopier;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisBiomeGeneratorLink;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisEntitySpawn;
import art.arcane.iris.engine.object.IrisGenerator;
import art.arcane.iris.core.pack.PackExportClosure;
import art.arcane.iris.engine.object.IrisEntity;
import art.arcane.iris.engine.object.IrisMarker;
import art.arcane.iris.engine.object.IrisRegion;
import art.arcane.iris.engine.object.IrisSpawner;
import art.arcane.iris.engine.object.IrisStructurePlacement;
import art.arcane.iris.modded.ModdedDimensionManager;
import art.arcane.iris.modded.ModdedEngineBootstrap;
import art.arcane.iris.modded.ModdedScheduler;
import art.arcane.iris.modded.ModdedWorkspaceGenerator;
import art.arcane.iris.util.common.parallel.BurstExecutor;
import art.arcane.iris.util.common.parallel.MultiBurst;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.json.JSONObject;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.math.Spiraler;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.zeroturnaround.zip.ZipUtil;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import art.arcane.iris.core.localization.IrisLanguage;
import art.arcane.iris.modded.localization.ModdedCommandMessages;
import art.arcane.volmlib.util.localization.MessageArgument;
public final class ModdedStudioCommands {
    private static final Predicate<CommandSourceStack> GATE = Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);
    private static final Pattern PROJECT_NAME = Pattern.compile("[a-z0-9_-]+");
    private static final Pattern STUDIO_ID_SANITIZER = Pattern.compile("[^a-z0-9_-]");
    private static final String STUDIO_NAMESPACE = "irisworldgen";
    private static final String STUDIO_PREFIX = "studio_";
    private static final String DEFAULT_TEMPLATE = "example";
    private static final UUID CONSOLE_OWNER = new UUID(0L, 0L);
    private static final Map<UUID, String> STUDIOS = new ConcurrentHashMap<>();
    private static final ModdedStudioTransitionQueue TRANSITIONS = new ModdedStudioTransitionQueue();
    private static final SuggestionProvider<CommandSourceStack> GENERATOR_KEYS = (CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) -> {
        ModdedCommandFeedback.tab(context.getSource());
        try {
            Engine engine = IrisModdedCommands.engineFor(context.getSource().getLevel());
            if (engine != null) {
                return SharedSuggestionProvider.suggest(engine.getData().getGeneratorLoader().getPossibleKeys(), builder);
            }
        } catch (Throwable e) {
            IrisModdedCommands.warnTabFailure("generator keys", context.getSource(), e);
        }
        return builder.buildFuture();
    };

    private ModdedStudioCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> tree(String name) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name).requires(GATE);

        root.executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> ModdedCommandHelp.send(context.getSource(), name)));

        root.then(createTree("create"));
        root.then(createTree("+"));

        root.then(packageTree("package"));
        root.then(packageTree("pkg"));

        root.then(Commands.literal("version")
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> version(context.getSource(), null)))
                .then(Commands.argument("pack", StringArgumentType.word()).suggests(IrisModdedCommands.PACK_NAMES)
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> version(context.getSource(), StringArgumentType.getString(context, "pack"))))));

        root.then(Commands.literal("regions")
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> regions(context.getSource(), 500)))
                .then(Commands.argument("radius", IntegerArgumentType.integer(8, 1000))
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> regions(context.getSource(), IntegerArgumentType.getInteger(context, "radius"))))));

        root.then(openTree("open"));
        root.then(openTree("o"));
        root.then(Commands.literal("close")
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> close(context.getSource()))));
        root.then(Commands.literal("x")
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> close(context.getSource()))));
        root.then(Commands.literal("tpstudio")
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> tpStudio(context.getSource()))));
        root.then(Commands.literal("stp")
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> tpStudio(context.getSource()))));
        root.then(Commands.literal("status")
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> status(context.getSource()))));
        root.then(workspaceTree("vscode", true));
        root.then(workspaceTree("vsc", true));
        root.then(workspaceTree("update", false));
        root.then(message("importvanilla", "Vanilla tree/object/structure capture generates features in throwaway Bukkit worlds via NMS; run /iris studio importvanilla on a Bukkit server against this pack, then copy the pack folder over."));
        root.then(message("importv", "Vanilla tree/object/structure capture generates features in throwaway Bukkit worlds via NMS; run /iris studio importvanilla on a Bukkit server against this pack, then copy the pack folder over."));
        root.then(message("iv", "Vanilla tree/object/structure capture generates features in throwaway Bukkit worlds via NMS; run /iris studio importvanilla on a Bukkit server against this pack, then copy the pack folder over."));
        root.then(noiseTree("noise"));
        root.then(noiseTree("nmap"));
        root.then(mapTree("map"));
        root.then(mapTree("render"));
        root.then(imageMapTree("imagemap"));
        root.then(imageMapTree("imap"));
        root.then(message("loot", "Loot simulation opens a Bukkit chest inventory GUI; it is not available on modded servers."));
        root.then(message("profile", "Pack performance profiling is part of the Bukkit studio toolchain and is not ported to modded servers."));
        root.then(message("spawn", "Iris entity spawning uses the Bukkit entity pipeline and is not ported to modded servers."));
        root.then(message("summon", "Iris entity spawning uses the Bukkit entity pipeline and is not ported to modded servers."));
        root.then(message("objects", "The chunk object report reads Bukkit chunk data and is not ported to modded servers."));
        root.then(message("find-objects", "The chunk object report reads Bukkit chunk data and is not ported to modded servers."));

        return root;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createTree(String name) {
        return Commands.literal(name)
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) ->
                        create(context.getSource(), "studio", DEFAULT_TEMPLATE)))
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) ->
                                create(context.getSource(), StringArgumentType.getString(context, "name"), DEFAULT_TEMPLATE)))
                        .then(Commands.argument("template", StringArgumentType.word())
                                .suggests(IrisModdedCommands.PACK_NAMES)
                                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) ->
                                        create(context.getSource(),
                                                StringArgumentType.getString(context, "name"),
                                                StringArgumentType.getString(context, "template"))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> packageTree(String name) {
        return Commands.literal(name)
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> pkg(context.getSource(), null)))
                .then(Commands.argument("pack", StringArgumentType.word())
                        .suggests(IrisModdedCommands.PACK_NAMES)
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) ->
                                pkg(context.getSource(), StringArgumentType.getString(context, "pack")))));
    }

    public static void clear() {
        TRANSITIONS.clear();
        STUDIOS.clear();
    }

    private static LiteralArgumentBuilder<CommandSourceStack> openTree(String name) {
        return Commands.literal(name)
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> openHelp(context.getSource())))
                .then(Commands.argument("pack", StringArgumentType.word()).suggests(IrisModdedCommands.PACK_NAMES)
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> open(context.getSource(), StringArgumentType.getString(context, "pack"), 1337L)))
                        .then(Commands.argument("seed", LongArgumentType.longArg())
                                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> open(context.getSource(), StringArgumentType.getString(context, "pack"), LongArgumentType.getLong(context, "seed"))))));
    }

    private static int openHelp(CommandSourceStack source) {
        IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STUDIO_COMMANDS_PROVIDE_DIMENSION_PACK_IRIS_STUDIO_OPEN_PACK_SEED));
        return 0;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> noiseTree(String name) {
        return Commands.literal(name)
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> noise(context.getSource(), null, 12345L)))
                .then(Commands.argument("generator", StringArgumentType.word()).suggests(GENERATOR_KEYS)
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> noise(context.getSource(), StringArgumentType.getString(context, "generator"), 12345L)))
                        .then(Commands.argument("seed", LongArgumentType.longArg())
                                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> noise(context.getSource(), StringArgumentType.getString(context, "generator"), LongArgumentType.getLong(context, "seed"))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> mapTree(String name) {
        return Commands.literal(name)
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> map(context.getSource())));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> imageMapTree(String name) {
        return Commands.literal(name)
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> imageMap(context.getSource())));
    }

    private static int noise(CommandSourceStack source, String generatorKey, long seed) {
        ServerLevel level = source.getLevel();
        Engine engine = IrisModdedCommands.engineFor(level);
        if (!GuiHost.isAvailable() || !IrisSettings.get().getGui().isUseServerLaunchedGuis()) {
            IrisModdedCommands.fail(source, guiUnavailableMessage());
            return 0;
        }
        if (engine != null) {
            ServerPlayer player = source.getPlayer();
            ModdedGuiHost.bindContext(source.getServer(), level, engine, player == null ? null : player.getUUID());
        }
        if (generatorKey == null || generatorKey.isBlank()) {
            NoiseExplorerGUI.launch();
            IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STUDIO_COMMANDS_OPENING_NOISE_EXPLORER_ON_SERVER_DISPLAY));
            return 1;
        }
        if (engine == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STUDIO_COMMANDS_THIS_DIMENSION_IS_NOT_GENERATED_BY_IRIS_RUN_IRIS_STUDIO));
            return 0;
        }
        IrisGenerator generator = engine.getData().getGeneratorLoader().load(generatorKey.trim());
        if (generator == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STUDIO_COMMANDS_UNKNOWN_GENERATOR_PACK, MessageArgument.untrusted("generatorKey", generatorKey), MessageArgument.untrusted("value", engine.getDimension().getLoadKey())));
            return 0;
        }
        String selectedGeneratorKey = generatorKey.trim();
        NoiseExplorerGUI.launchGeneratorKey(selectedGeneratorKey, generator, seed);
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STUDIO_COMMANDS_OPENING_NOISE_EXPLORER_GENERATOR_SEED, MessageArgument.untrusted("value", generatorKey.trim()), MessageArgument.untrusted("seed", seed)));
        return 1;
    }

    private static int map(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Engine engine = IrisModdedCommands.engineFor(level);
        if (engine == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STUDIO_COMMANDS_THIS_DIMENSION_IS_NOT_GENERATED_BY_IRIS_STAND_IRIS_STUDIO));
            return 0;
        }
        if (!GuiHost.isAvailable() || !IrisSettings.get().getGui().isUseServerLaunchedGuis()) {
            IrisModdedCommands.fail(source, guiUnavailableMessage());
            return 0;
        }
        ServerPlayer player = source.getPlayer();
        ModdedGuiHost.bindContext(source.getServer(), level, engine, player == null ? null : player.getUUID());
        VisionGUI.launch(engine, player == null ? null : player.getUUID());
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STUDIO_COMMANDS_OPENING_VISION_MAP_ON_SERVER_DISPLAY, MessageArgument.untrusted("value", level.dimension().identifier())));
        return 1;
    }

    private static int imageMap(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Engine engine = IrisModdedCommands.engineFor(level);
        if (engine == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(
                    ModdedCommandMessages.MODDED_STUDIO_COMMANDS_IMAGE_MAP_REQUIRES_IRIS_DIMENSION
            ));
            return 0;
        }
        if (!ImageMapStudioGUI.isAvailable()) {
            IrisModdedCommands.fail(source, guiUnavailableMessage());
            return 0;
        }
        ServerPlayer player = source.getPlayer();
        ModdedGuiHost.bindContext(source.getServer(), level, engine, player == null ? null : player.getUUID());
        ImageMapStudioGUI.launch(engine);
        IrisModdedCommands.ok(source, IrisLanguage.plain(
                ModdedCommandMessages.MODDED_STUDIO_COMMANDS_OPENING_IMAGE_MAP_STUDIO_ON_SERVER_DISPLAY,
                MessageArgument.untrusted("value", level.dimension().identifier())
        ));
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> workspaceTree(String name, boolean open) {
        return Commands.literal(name)
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> workspace(context.getSource(), null, open)))
                .then(Commands.argument("pack", StringArgumentType.word()).suggests(IrisModdedCommands.PACK_NAMES)
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> workspace(context.getSource(), StringArgumentType.getString(context, "pack"), open))));
    }

    private static int workspace(CommandSourceStack source, String pack, boolean open) {
        File folder = resolvePack(source, pack);
        if (folder == null) {
            return 0;
        }
        File workspace;
        try {
            workspace = ModdedWorkspaceGenerator.writeWorkspace(IrisData.get(folder), folder, open);
        } catch (Throwable e) {
            ModdedIrisLog.error("Iris workspace write failed for {}", folder, e);
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STUDIO_COMMANDS_FAILED_WRITE_WORKSPACE, MessageArgument.untrusted("value", folder.getAbsolutePath()), MessageArgument.untrusted("value2", String.valueOf(e.getMessage()))));
            return 0;
        }
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STUDIO_COMMANDS_WORKSPACE_REGENERATED_WITH_JSON_SCHEMAS_AUTOCOMPLETE, MessageArgument.untrusted("value", workspace.getAbsolutePath())));
        if (!open) {
            return 1;
        }
        if (!GuiHost.isAvailable() || !Desktop.isDesktopSupported()) {
            IrisModdedCommands.fail(source, guiUnavailableMessage());
            return 0;
        }
        try {
            Desktop.getDesktop().open(workspace);
        } catch (Throwable e) {
            ModdedIrisLog.error("Iris workspace open failed for {}", workspace, e);
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STUDIO_COMMANDS_COULD_NOT_OPEN, MessageArgument.untrusted("value", workspace.getName()), MessageArgument.untrusted("value2", e.getClass().getSimpleName())));
            return 0;
        }
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STUDIO_COMMANDS_OPENING_YOUR_EDITOR, MessageArgument.untrusted("value", workspace.getName())));
        return 1;
    }

    private static String guiUnavailableMessage() {
        if (GuiHost.isDesktopSuppressed()) {
            return "Iris desktop GUIs are disabled in singleplayer/client to avoid crashing the game client; use the in-game map and chat output instead.";
        }
        if (!GuiHost.isAvailable()) {
            return "This server has no display (headless JVM); the Iris desktop GUIs need an AWT-capable session.";
        }
        return "Server-launched GUIs are disabled (gui.useServerLaunchedGuis=false in Iris settings).";
    }

    private static LiteralArgumentBuilder<CommandSourceStack> message(String name, String text) {
        return Commands.literal(name)
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> {
                    IrisModdedCommands.fail(context.getSource(), text);
                    return 0;
                }))
                .then(Commands.argument("args", StringArgumentType.greedyString())
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> {
                            IrisModdedCommands.fail(context.getSource(), text);
                            return 0;
                        })));
    }

    private static File resolvePack(CommandSourceStack source, String pack) {
        String name = pack;
        if (name == null || name.isBlank()) {
            Engine engine = IrisModdedCommands.engineFor(source.getLevel());
            if (engine == null || engine.getDimension() == null) {
                IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STUDIO_COMMANDS_THIS_DIMENSION_IS_NOT_GENERATED_BY_IRIS_SPECIFY_PACK_NAME));
                return null;
            }
            name = engine.getDimension().getLoadKey();
        }
        File folder = new File(ModdedPackCommands.packsRoot(), name);
        if (!folder.isDirectory() || !new File(folder, "dimensions").isDirectory()) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STUDIO_COMMANDS_PACK_NOT_FOUND_UNDER, MessageArgument.untrusted("name", name), MessageArgument.untrusted("value", ModdedPackCommands.packsRoot().getAbsolutePath())));
            return null;
        }
        return folder;
    }

    private static String studioDimensionId(ServerPlayer player) {
        String base = STUDIO_ID_SANITIZER.matcher(player.getScoreboardName().toLowerCase(Locale.ROOT)).replaceAll("_");
        if (base.isBlank()) {
            base = player.getUUID().toString().replace("-", "");
        }
        return STUDIO_NAMESPACE + ":" + STUDIO_PREFIX + base;
    }

    private static String studioConsoleDimensionId() {
        return STUDIO_NAMESPACE + ":" + STUDIO_PREFIX + "console";
    }

    private static int open(CommandSourceStack source, String pack, long seed) {
        if (pack == null || pack.isBlank()) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STUDIO_COMMANDS_PROVIDE_DIMENSION_PACK_IRIS_STUDIO_OPEN_PACK_SEED_2));
            return 0;
        }
        ServerPlayer player = source.getPlayer();
        UUID owner = player == null ? CONSOLE_OWNER : player.getUUID();
        String dimensionId = player == null ? studioConsoleDimensionId() : studioDimensionId(player);
        MinecraftServer server = source.getServer();
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STUDIO_COMMANDS_OPENING_STUDIO_SEED, MessageArgument.untrusted("pack", pack), MessageArgument.untrusted("seed", seed)));
        CompletableFuture<Void> transition = TRANSITIONS.submit(
                owner,
                () -> openTransition(source, server, owner, dimensionId, pack, seed));
        reportOpenFailure(source, server, dimensionId, pack, transition);
        return 1;
    }

    private static CompletableFuture<Void> openTransition(
            CommandSourceStack source,
            MinecraftServer server,
            UUID owner,
            String dimensionId,
            String pack,
            long seed
    ) {
        CompletableFuture<Void> transition = new CompletableFuture<>();
        ModdedScheduler scheduler = ModdedEngineBootstrap.schedulerOrNull();
        if (scheduler == null) {
            transition.completeExceptionally(new IllegalStateException(
                    "Iris modded scheduler is unavailable for Studio open."));
            return transition;
        }
        scheduler.asyncIfRunning(() -> prepareStudioOpen(
                        source,
                        server,
                        owner,
                        dimensionId,
                        pack,
                        seed,
                        transition),
                () -> transition.completeExceptionally(new IllegalStateException(
                        "Iris modded scheduler rejected Studio open.")));
        return transition;
    }

    private static void prepareStudioOpen(
            CommandSourceStack source,
            MinecraftServer server,
            UUID owner,
            String dimensionId,
            String pack,
            long seed,
            CompletableFuture<Void> transition
    ) {
        try {
            File packFolder = new File(ModdedPackCommands.packsRoot(), pack);
            if (!new File(packFolder, "dimensions/" + pack + ".json").isFile()) {
                throw new IllegalStateException("Required Studio pack '" + pack
                        + "' is not installed with dimensions/" + pack + ".json.");
            }
            IrisData data = IrisData.get(packFolder);
            IrisDimension dimension = data.getDimensionLoader().load(pack);
            if (dimension == null) {
                throw new IllegalStateException("Studio pack '" + pack
                        + "' has no dimensions/" + pack + ".json definition.");
            }
            ModdedWorkspaceGenerator.writeWorkspace(data, packFolder, true);
            server.execute(() -> executeStudioOpen(
                    source,
                    server,
                    owner,
                    dimensionId,
                    pack,
                    seed,
                    transition));
        } catch (Throwable failure) {
            transition.completeExceptionally(failure);
        }
    }

    private static void executeStudioOpen(
            CommandSourceStack source,
            MinecraftServer server,
            UUID owner,
            String dimensionId,
            String pack,
            long seed,
            CompletableFuture<Void> transition
    ) {
        ModdedDimensionManager.Handle handle = null;
        try {
            replaceExistingStudio(server, owner, dimensionId);
            handle = ModdedDimensionManager.createTransientStudio(server, dimensionId, pack, pack, seed);
            STUDIOS.put(owner, dimensionId);
            if (owner.equals(CONSOLE_OWNER)) {
                completeConsoleOpen(source, dimensionId, pack, seed, handle);
                transition.complete(null);
                return;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(owner);
            if (player == null) {
                throw new IllegalStateException("Studio owner disconnected before teleport.");
            }
            ServerLevel studio = handle.level();
            Engine engine = IrisModdedCommands.engineFor(studio);
            if (engine == null) {
                throw new IllegalStateException("Studio engine is unavailable for " + dimensionId + ".");
            }
            int surfaceY = engine.getMinHeight() + engine.getHeight(8, 8, false) + 2;
            CompletableFuture<Boolean> teleport = ModdedDimensionManager.teleportAsync(
                    player,
                    server,
                    studio,
                    8.5D,
                    surfaceY,
                    8.5D);
            teleport.whenComplete((success, failure) -> server.execute(() -> {
                if (transition.isDone()) {
                    return;
                }
                if (failure != null) {
                    transition.completeExceptionally(failure);
                    return;
                }
                if (!Boolean.TRUE.equals(success)) {
                    transition.completeExceptionally(new IllegalStateException(
                            "Studio native teleport did not complete successfully."));
                    return;
                }
                IrisModdedCommands.ok(source, IrisLanguage.plain(
                        ModdedCommandMessages.MODDED_STUDIO_COMMANDS_STUDIO_OPEN_NOW_RUNS_SEED_USE_IRIS_STUDIO_CLOSE_WHEN,
                        MessageArgument.untrusted("dimensionId", dimensionId),
                        MessageArgument.untrusted("pack", pack),
                        MessageArgument.untrusted("seed", seed)));
                transition.complete(null);
            }));
        } catch (Throwable failure) {
            transition.completeExceptionally(failure);
            if (handle != null && transition.isCompletedExceptionally()) {
                cleanupFailedOpen(server, owner, dimensionId, failure);
            }
        }
    }

    private static void replaceExistingStudio(MinecraftServer server, UUID owner, String dimensionId) {
        if (ModdedDimensionManager.level(server, dimensionId) != null
                || ModdedDimensionManager.handle(dimensionId) != null) {
            ModdedDimensionManager.remove(server, dimensionId, true);
        }
        STUDIOS.remove(owner, dimensionId);
    }

    private static void cleanupFailedOpen(
            MinecraftServer server,
            UUID owner,
            String dimensionId,
            Throwable failure
    ) {
        try {
            ModdedDimensionManager.remove(server, dimensionId, true);
            STUDIOS.remove(owner, dimensionId);
        } catch (Throwable cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
            ModdedIrisLog.error("Iris failed to clean up Studio '{}' after open failure",
                    dimensionId, cleanupFailure);
        }
    }

    private static void completeConsoleOpen(
            CommandSourceStack source,
            String dimensionId,
            String pack,
            long seed,
            ModdedDimensionManager.Handle handle
    ) {
        ServerLevel studio = handle.level();
        Engine engine = IrisModdedCommands.engineFor(studio);
        int surface = engine == null
                ? studio.getMinY() + 1
                : engine.getMinHeight() + engine.getHeight(8, 8, false) + 2;
        surface = Math.max(studio.getMinY() + 1, Math.min(studio.getMaxY() - 2, surface));
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STUDIO_COMMANDS_CONSOLE_STUDIO_OPEN_NOW_RUNS_SEED_TRANSIENT_NOT_WRITTEN_IRIS, MessageArgument.untrusted("dimensionId", dimensionId), MessageArgument.untrusted("pack", pack), MessageArgument.untrusted("seed", seed)));
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STUDIO_COMMANDS_ENTER_IT_WITH_EXECUTE_RUN_TP_S_8_5_8, MessageArgument.untrusted("dimensionId", dimensionId), MessageArgument.untrusted("surface", surface)));
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STUDIO_COMMANDS_PREGEN_IT_WITH_IRIS_PREGEN_START_RADIUS, MessageArgument.untrusted("dimensionId", dimensionId)));
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STUDIO_COMMANDS_REMOVE_IT_WITH_IRIS_STUDIO_CLOSE));
    }

    private static void reportOpenFailure(
            CommandSourceStack source,
            MinecraftServer server,
            String dimensionId,
            String pack,
            CompletableFuture<Void> transition
    ) {
        transition.whenComplete((ignored, failure) -> {
            if (failure == null) {
                return;
            }
            Throwable cause = unwrapFailure(failure);
            ModdedIrisLog.error("Iris Studio open failed for '{}' ({})", dimensionId, pack, cause);
            server.execute(() -> IrisModdedCommands.fail(source, IrisLanguage.plain(
                    ModdedCommandMessages.MODDED_STUDIO_COMMANDS_STUDIO_OPEN_FAILED,
                    MessageArgument.untrusted("value", cause.getClass().getSimpleName()),
                    MessageArgument.trusted("errorMessage", IrisLanguage.errorDetail(cause)))));
        });
    }

    private static int close(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        MinecraftServer server = source.getServer();
        UUID owner = player == null ? CONSOLE_OWNER : player.getUUID();
        TRANSITIONS.submit(owner, () -> closeTransition(source, server, owner));
        return 1;
    }

    private static CompletableFuture<Void> closeTransition(
            CommandSourceStack source,
            MinecraftServer server,
            UUID owner
    ) {
        CompletableFuture<Void> transition = new CompletableFuture<>();
        server.execute(() -> {
            String dimensionId = STUDIOS.get(owner);
            if (dimensionId == null) {
                IrisModdedCommands.fail(source, IrisLanguage.plain(
                        ModdedCommandMessages.MODDED_STUDIO_COMMANDS_YOU_DO_NOT_HAVE_OPEN_STUDIO_USE_IRIS_STUDIO_OPEN));
                transition.complete(null);
                return;
            }
            try {
                ModdedDimensionManager.remove(server, dimensionId, true);
                STUDIOS.remove(owner, dimensionId);
                IrisModdedCommands.ok(source, IrisLanguage.plain(
                        ModdedCommandMessages.MODDED_STUDIO_COMMANDS_STUDIO_CLOSED_WAS_EVACUATED_UNLOADED_ITS_REGION_DATA_DELETED,
                        MessageArgument.untrusted("dimensionId", dimensionId)));
                transition.complete(null);
            } catch (Throwable failure) {
                ModdedIrisLog.error("Iris Studio close failed for {}", dimensionId, failure);
                IrisModdedCommands.fail(source, IrisLanguage.plain(
                        ModdedCommandMessages.MODDED_STUDIO_COMMANDS_STUDIO_CLOSE_FAILED,
                        MessageArgument.untrusted("value", failure.getClass().getSimpleName()),
                        MessageArgument.trusted("errorMessage", IrisLanguage.errorDetail(failure))));
                transition.completeExceptionally(failure);
            }
        });
        return transition;
    }

    private static int status(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        List<ModdedDimensionManager.Handle> handles = ModdedDimensionManager.handles();
        List<ModdedDimensionManager.Handle> studios = new ArrayList<>();
        for (ModdedDimensionManager.Handle handle : handles) {
            if (handle.dimensionId().startsWith(STUDIO_NAMESPACE + ":" + STUDIO_PREFIX) && ModdedDimensionManager.level(server, handle.dimensionId()) != null) {
                studios.add(handle);
            }
        }
        if (studios.isEmpty()) {
            IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STUDIO_COMMANDS_NO_STUDIO_DIMENSIONS_ARE_CURRENTLY_OPEN));
            return 1;
        }
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STUDIO_COMMANDS_ACTIVE_STUDIO_DIMENSION_S, MessageArgument.untrusted("value", studios.size())));
        for (ModdedDimensionManager.Handle handle : studios) {
            UUID owner = ownerOf(handle.dimensionId());
            String ownerName = owner == null ? "unclaimed" : ownerName(server, owner);
            IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STUDIO_COMMANDS_PACK_SEED_OWNER, MessageArgument.untrusted("value", handle.dimensionId()), MessageArgument.untrusted("value2", handle.pack()), MessageArgument.untrusted("value3", handle.seed()), MessageArgument.untrusted("ownerName", ownerName)));
        }
        return 1;
    }

    private static UUID ownerOf(String dimensionId) {
        for (Map.Entry<UUID, String> entry : STUDIOS.entrySet()) {
            if (entry.getValue().equals(dimensionId)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static String ownerName(MinecraftServer server, UUID owner) {
        if (owner.equals(CONSOLE_OWNER)) {
            return "console";
        }
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        return player == null ? owner.toString() : player.getScoreboardName();
    }

    private static int tpStudio(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STUDIO_COMMANDS_THIS_COMMAND_CAN_ONLY_BE_USED_BY_PLAYERS));
            return 0;
        }
        MinecraftServer server = source.getServer();
        UUID owner = player.getUUID();
        CompletableFuture<Void> transition = TRANSITIONS.submit(
                owner,
                () -> teleportToStudio(source, server, owner));
        reportTeleportFailure(source, server, transition);
        return 1;
    }

    private static CompletableFuture<Void> teleportToStudio(
            CommandSourceStack source,
            MinecraftServer server,
            UUID owner
    ) {
        CompletableFuture<Void> transition = new CompletableFuture<>();
        server.execute(() -> {
            if (transition.isDone()) {
                return;
            }
            String dimensionId = STUDIOS.get(owner);
            if (dimensionId == null) {
                IrisModdedCommands.fail(source, IrisLanguage.plain(
                        ModdedCommandMessages.MODDED_STUDIO_COMMANDS_YOU_DO_NOT_HAVE_OPEN_STUDIO_USE_IRIS_STUDIO_OPEN_2));
                transition.complete(null);
                return;
            }
            ServerLevel studio = ModdedDimensionManager.level(server, dimensionId);
            if (studio == null) {
                STUDIOS.remove(owner, dimensionId);
                IrisModdedCommands.fail(source, IrisLanguage.plain(
                        ModdedCommandMessages.MODDED_STUDIO_COMMANDS_YOUR_STUDIO_DIMENSION_IS_NO_LONGER_LOADED_USE_IRIS_STUDIO));
                transition.complete(null);
                return;
            }
            ServerPlayer activePlayer = server.getPlayerList().getPlayer(owner);
            Engine engine = IrisModdedCommands.engineFor(studio);
            if (activePlayer == null || engine == null) {
                transition.completeExceptionally(new IllegalStateException(
                        "Studio player or engine is unavailable for teleport."));
                return;
            }
            int surfaceY = engine.getMinHeight() + engine.getHeight(8, 8, false) + 2;
            CompletableFuture<Boolean> teleport = ModdedDimensionManager.teleportAsync(
                    activePlayer,
                    server,
                    studio,
                    8.5D,
                    surfaceY,
                    8.5D);
            teleport.whenComplete((success, failure) -> server.execute(() -> {
                if (transition.isDone()) {
                    return;
                }
                if (failure != null) {
                    transition.completeExceptionally(failure);
                    return;
                }
                if (!Boolean.TRUE.equals(success)) {
                    transition.completeExceptionally(new IllegalStateException(
                            "Studio native teleport did not complete successfully."));
                    return;
                }
                IrisModdedCommands.ok(source, IrisLanguage.plain(
                        ModdedCommandMessages.MODDED_STUDIO_COMMANDS_TELEPORTED_YOUR_STUDIO,
                        MessageArgument.untrusted("dimensionId", dimensionId)));
                transition.complete(null);
            }));
        });
        return transition;
    }

    private static void reportTeleportFailure(
            CommandSourceStack source,
            MinecraftServer server,
            CompletableFuture<Void> transition
    ) {
        transition.whenComplete((ignored, failure) -> {
            if (failure == null) {
                return;
            }
            Throwable cause = unwrapFailure(failure);
            ModdedIrisLog.error("Iris Studio teleport failed", cause);
            server.execute(() -> IrisModdedCommands.fail(source, IrisLanguage.plain(
                    ModdedCommandMessages.MODDED_STUDIO_COMMANDS_STUDIO_OPEN_FAILED,
                    MessageArgument.untrusted("value", cause.getClass().getSimpleName()),
                    MessageArgument.trusted("errorMessage", IrisLanguage.errorDetail(cause)))));
        });
    }

    private static Throwable unwrapFailure(Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static int version(CommandSourceStack source, String pack) {
        File folder = resolvePack(source, pack);
        if (folder == null) {
            return 0;
        }
        IrisData data = IrisData.get(folder);
        IrisDimension dimension = data.getDimensionLoader().load(folder.getName());
        if (dimension == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STUDIO_COMMANDS_PACK_HAS_NO_DIMENSIONS_JSON_2, MessageArgument.untrusted("value", folder.getName()), MessageArgument.untrusted("value2", folder.getName())));
            return 0;
        }
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STUDIO_COMMANDS_PACK_HAS_VERSION, MessageArgument.untrusted("value", dimension.getName()), MessageArgument.untrusted("value2", dimension.getVersion())));
        return 1;
    }

    private static int create(CommandSourceStack source, String nameRaw, String template) {
        String name = nameRaw.toLowerCase(Locale.ROOT);
        if (!PROJECT_NAME.matcher(name).matches()) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STUDIO_COMMANDS_INVALID_PROJECT_NAME_ALLOWED_Z_0_9, MessageArgument.untrusted("nameRaw", nameRaw)));
            return 0;
        }
        File packsRoot = ModdedPackCommands.packsRoot();
        File target = new File(packsRoot, name);
        if (target.exists()) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STUDIO_COMMANDS_PACK_ALREADY_EXISTS_AT, MessageArgument.untrusted("name", name), MessageArgument.untrusted("value", target.getAbsolutePath())));
            return 0;
        }
        MinecraftServer server = source.getServer();
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STUDIO_COMMANDS_CREATING_PROJECT_FROM_TEMPLATE, MessageArgument.untrusted("name", name), MessageArgument.untrusted("template", template)));
        Thread thread = new Thread(() -> {
            try {
                File templateFolder = new File(packsRoot, template);
                if (!new File(templateFolder, "dimensions/" + template + ".json").isFile()) {
                    server.execute(() -> IrisModdedCommands.fail(source, IrisLanguage.plain(
                            ModdedCommandMessages.MODDED_STUDIO_COMMANDS_REQUIRED_PACK_IS_NOT_INSTALLED_INSTALL_THEN_RESTART,
                            MessageArgument.untrusted("pack", template))));
                    return;
                }
                IrisProjectCopier.copyProject(templateFolder, target, template, name);
                try {
                    ModdedWorkspaceGenerator.writeWorkspace(IrisData.get(target), target);
                } catch (IOException e) {
                    ModdedIrisLog.error("Iris studio create workspace generation failed for {}", name, e);
                }
                server.execute(() -> {
                    IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STUDIO_COMMANDS_CREATED_PROJECT_AT, MessageArgument.untrusted("name", name), MessageArgument.untrusted("value", target.getAbsolutePath())));
                    IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STUDIO_COMMANDS_EDIT_DIMENSIONS_JSON_REST_PACK_VSCODE_WORKSPACE_WITH_JSON_SCHEMA, MessageArgument.untrusted("name", name)));
                });
            } catch (Throwable e) {
                ModdedIrisLog.error("Iris studio create failed for {}", name, e);
                server.execute(() -> IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STUDIO_COMMANDS_PROJECT_CREATION_FAILED, MessageArgument.untrusted("value", e.getClass().getSimpleName()), MessageArgument.trusted("errorMessage", IrisLanguage.errorDetail(e)))));
            }
        }, "Iris Studio Create");
        thread.setDaemon(true);
        thread.start();
        return 1;
    }

    private static int pkg(CommandSourceStack source, String pack) {
        File folder = resolvePack(source, pack);
        if (folder == null) {
            return 0;
        }
        MinecraftServer server = source.getServer();
        String dimKey = folder.getName();
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STUDIO_COMMANDS_PACKAGING_DIMENSION, MessageArgument.untrusted("dimKey", dimKey)));
        Thread thread = new Thread(() -> {
            try {
                File result = compilePackage(folder, dimKey);
                server.execute(() -> IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STUDIO_COMMANDS_PACKAGE_COMPILED, MessageArgument.untrusted("value", result.getAbsolutePath()))));
            } catch (Throwable e) {
                ModdedIrisLog.error("Iris package failed for {}", dimKey, e);
                server.execute(() -> IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STUDIO_COMMANDS_PACKAGING_FAILED, MessageArgument.untrusted("value", e.getClass().getSimpleName()), MessageArgument.trusted("errorMessage", IrisLanguage.errorDetail(e)))));
            }
        }, "Iris Studio Package");
        thread.setDaemon(true);
        thread.start();
        return 1;
    }

    private static File compilePackage(File packFolder, String dimKey) throws IOException {
        PackValidationResult validation = PackValidator.validateForPackaging(packFolder);
        if (!validation.isLoadable()) {
            throw new IOException("Pack validation failed before packaging: "
                    + String.join("; ", validation.getBlockingErrors()));
        }
        IrisData dm = IrisData.get(packFolder);
        IrisDimension dimension = dm.getDimensionLoader().load(dimKey);
        if (dimension == null) {
            throw new IOException("Pack '" + dimKey + "' has no dimensions/" + dimKey + ".json");
        }
        File exports = ModdedEngineBootstrap.loader().configDir().resolve("irisworldgen").resolve("exports").toFile();
        File folder = new File(exports, dimension.getLoadKey());
        IO.delete(folder);
        folder.mkdirs();

        LinkedHashSet<String> regionKeys = new LinkedHashSet<>();
        LinkedHashSet<String> biomeKeys = new LinkedHashSet<>();
        LinkedHashSet<String> entityKeys = new LinkedHashSet<>();
        LinkedHashSet<String> spawnerKeys = new LinkedHashSet<>();
        LinkedHashSet<String> generatorKeys = new LinkedHashSet<>();
        LinkedHashSet<String> lootKeys = new LinkedHashSet<>();
        LinkedHashSet<String> objectKeys = new LinkedHashSet<>();
        LinkedHashSet<String> markerKeys = new LinkedHashSet<>();
        LinkedHashSet<String> structureKeys = new LinkedHashSet<>();
        List<IrisDimension> dimensions = new ArrayList<>();

        for (String dimensionKey : PackExportClosure.collectDimensionKeys(dimension).stream().sorted().toList()) {
            IrisDimension exportedDimension = dm.getDimensionLoader().load(dimensionKey);
            if (exportedDimension != null) {
                dimensions.add(exportedDimension);
            }
        }
        for (IrisDimension exportedDimension : dimensions) {
            exportedDimension.getAllRegions(() -> dm).forEach((IrisRegion region) -> {
                if (region != null && region.getLoadKey() != null) {
                    regionKeys.add(region.getLoadKey());
                }
            });
            exportedDimension.getReachableBiomes(() -> dm).forEach((IrisBiome biome) -> {
                if (biome != null && biome.getLoadKey() != null) {
                    biomeKeys.add(biome.getLoadKey());
                }
            });
            lootKeys.addAll(exportedDimension.getLoot().getTables());
            spawnerKeys.addAll(exportedDimension.getEntitySpawners());
            collectStructureKeys(structureKeys, exportedDimension.getStructures());
            objectKeys.addAll(PackExportClosure.collectStaticObjectKeys(exportedDimension.getStaticObjects()));
        }

        for (String regionKey : regionKeys) {
            IrisRegion region = dm.getRegionLoader().load(regionKey);
            if (region == null) {
                continue;
            }
            region.getAllBiomes(() -> dm).forEach((IrisBiome biome) -> {
                if (biome != null && biome.getLoadKey() != null) {
                    biomeKeys.add(biome.getLoadKey());
                }
            });
            lootKeys.addAll(region.getLoot().getTables());
            spawnerKeys.addAll(region.getEntitySpawners());
            collectStructureKeys(structureKeys, region.getStructures());
            objectKeys.addAll(PackExportClosure.collectObjectKeys(region.getObjects()));
            markerKeys.addAll(PackExportClosure.collectMarkerKeys(region.getObjects()));
        }
        for (String biomeKey : biomeKeys) {
            IrisBiome biome = dm.getBiomeLoader().load(biomeKey);
            if (biome == null) {
                continue;
            }
            biome.getGenerators().forEach((IrisBiomeGeneratorLink link) -> generatorKeys.add(link.getGenerator()));
            lootKeys.addAll(biome.getLoot().getTables());
            spawnerKeys.addAll(biome.getEntitySpawners());
            collectStructureKeys(structureKeys, biome.getStructures());
            objectKeys.addAll(PackExportClosure.collectObjectKeys(biome.getObjects()));
            markerKeys.addAll(PackExportClosure.collectMarkerKeys(biome.getObjects()));
        }
        for (String markerKey : markerKeys) {
            IrisMarker marker = dm.getMarkerLoader().load(markerKey);
            if (marker == null) {
                continue;
            }
            spawnerKeys.addAll(marker.getSpawners());
        }
        for (String spawnerKey : spawnerKeys) {
            IrisSpawner spawner = dm.getSpawnerLoader().load(spawnerKey);
            if (spawner == null) {
                continue;
            }
            spawner.getSpawns().forEach((IrisEntitySpawn spawn) -> entityKeys.add(spawn.getEntity()));
            spawner.getInitialSpawns().forEach((IrisEntitySpawn spawn) -> entityKeys.add(spawn.getEntity()));
        }
        for (String entityKey : entityKeys) {
            IrisEntity entity = dm.getEntityLoader().load(entityKey);
            if (entity == null) {
                continue;
            }
            lootKeys.addAll(entity.getLoot().getTables());
        }

        StringBuilder hashes = new StringBuilder();
        StructurePackageClosure structureClosure = StructurePackageClosure.collect(packFolder, structureKeys);
        if (!structureClosure.isValid()) {
            throw new IOException("Structure package closure is invalid: " + String.join("; ", structureClosure.errors()));
        }
        hashes.append(structureClosure.writeTo(folder, true));
        hashes.append(ImageMapPackageClosure.writeAll(dm, folder, true));
        for (String objectKey : objectKeys) {
            try {
                File objectFile = dm.getObjectLoader().findFile(objectKey);
                IO.copyFile(objectFile, new File(folder, "objects/" + objectKey + ".iob"));
                hashes.append(IO.hash(objectFile));
            } catch (Throwable e) {
                ModdedIrisLog.error("Iris package failed to copy object {}", objectKey, e);
            }
        }

        for (IrisDimension exportedDimension : dimensions) {
            String key = exportedDimension.getLoadKey();
            hashes.append(copyJson(folder, "dimensions", key, dm.getDimensionLoader().findFile(key)));
        }
        for (String key : generatorKeys) {
            hashes.append(copyJson(folder, "generators", key, dm.getGeneratorLoader().findFile(key)));
        }
        for (String key : Stream.of(dm.getExpressionLoader().getPossibleKeys()).sorted().toList()) {
            hashes.append(copyJson(folder, "expressions", key, dm.getExpressionLoader().findFile(key)));
        }
        hashes.append(PackExportClosure.copySnippets(packFolder, folder));
        for (String key : regionKeys) {
            hashes.append(copyJson(folder, "regions", key, dm.getRegionLoader().findFile(key)));
        }
        for (String key : dm.getBlockLoader().getPossibleKeys()) {
            hashes.append(copyJson(folder, "blocks", key, dm.getBlockLoader().findFile(key)));
        }
        for (String key : biomeKeys) {
            hashes.append(copyJson(folder, "biomes", key, dm.getBiomeLoader().findFile(key)));
        }
        for (String key : entityKeys) {
            hashes.append(copyJson(folder, "entities", key, dm.getEntityLoader().findFile(key)));
        }
        for (String key : lootKeys) {
            hashes.append(copyJson(folder, "loot", key, dm.getLootLoader().findFile(key)));
        }
        for (String key : spawnerKeys) {
            hashes.append(copyJson(folder, "spawners", key, dm.getSpawnerLoader().findFile(key)));
        }
        for (String key : markerKeys) {
            hashes.append(copyJson(folder, "markers", key, dm.getMarkerLoader().findFile(key)));
        }

        JSONObject meta = new JSONObject();
        meta.put("hash", IO.hash(hashes.toString()));
        meta.put("time", M.ms());
        meta.put("version", dimension.getVersion());
        IO.writeAll(new File(folder, "package.json"), meta.toString(0));

        File output = new File(exports, dimension.getLoadKey() + ".iris");
        ZipUtil.pack(folder, output, 9);
        IO.delete(folder);
        return output;
    }

    private static void collectStructureKeys(LinkedHashSet<String> keys, List<IrisStructurePlacement> placements) {
        if (placements == null) {
            return;
        }
        for (IrisStructurePlacement placement : placements) {
            if (placement == null || placement.getStructures() == null) {
                continue;
            }
            for (String structureKey : placement.getStructures()) {
                keys.add(structureKey);
            }
        }
    }

    private static String copyJson(File folder, String category, String key, File file) {
        if (file == null || !file.isFile()) {
            return "";
        }
        try {
            String json = new JSONObject(IO.readAll(file)).toString(0);
            IO.writeAll(new File(folder, category + "/" + key + ".json"), json);
            return IO.hash(json);
        } catch (Throwable e) {
            ModdedIrisLog.error("Iris package failed to write {}/{}", category, key, e);
            return "";
        }
    }

    private static int regions(CommandSourceStack source, int radius) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STUDIO_COMMANDS_THIS_COMMAND_CAN_ONLY_BE_USED_BY_PLAYERS_SAMPLING_IS));
            return 0;
        }
        Engine engine = IrisModdedCommands.engineFor(source.getLevel());
        if (engine == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STUDIO_COMMANDS_THIS_DIMENSION_IS_NOT_GENERATED_BY_IRIS));
            return 0;
        }
        MinecraftServer server = source.getServer();
        int blockX = player.blockPosition().getX();
        int blockZ = player.blockPosition().getZ();
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STUDIO_COMMANDS_SAMPLING_REGION_DISTRIBUTION_X_CHUNKS_AROUND_YOU, MessageArgument.untrusted("value", (radius * 2)), MessageArgument.untrusted("value2", (radius * 2))));
        Thread thread = new Thread(() -> {
            try {
                int diameter = radius * 2;
                int totalTasks = diameter * diameter;
                KMap<String, AtomicInteger> counts = new KMap<>();
                engine.getDimension().getRegions().forEach((String key) -> counts.put(key, new AtomicInteger(0)));
                // finally-scoped: a throw mid-scan previously leaked the sampler's whole
                // ForkJoinPool (close() is the only thing that shuts it down).
                MultiBurst burst = new MultiBurst("Region Sampler");
                try {
                    BurstExecutor executor = burst.burst(totalTasks);
                    new Spiraler(diameter, diameter, (int x, int z) -> executor.queue(() -> {
                        IrisRegion region = engine.getRegion((x << 4) + 8, (z << 4) + 8);
                        counts.computeIfAbsent(region.getLoadKey(), (String key) -> new AtomicInteger(0)).incrementAndGet();
                    })).setOffset(blockX >> 4, blockZ >> 4).drain();
                    executor.complete();
                } finally {
                    burst.close();
                }
                server.execute(() -> counts.forEach((String key, AtomicInteger count) -> {
                    IrisRegion region = engine.getData().getRegionLoader().load(key);
                    String rarity = region == null ? "?" : String.valueOf(region.getRarity());
                    IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STUDIO_COMMANDS_RARITY, MessageArgument.untrusted("key", key), MessageArgument.untrusted("rarity", rarity), MessageArgument.untrusted("value", Form.f((double) count.get() / totalTasks * 100, 2))));
                }));
            } catch (Throwable e) {
                ModdedIrisLog.error("Iris region sampling failed", e);
                server.execute(() -> IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STUDIO_COMMANDS_REGION_SAMPLING_FAILED, MessageArgument.untrusted("value", e.getClass().getSimpleName()))));
            }
        }, "Iris Region Sampler");
        thread.setDaemon(true);
        thread.start();
        return 1;
    }
}
