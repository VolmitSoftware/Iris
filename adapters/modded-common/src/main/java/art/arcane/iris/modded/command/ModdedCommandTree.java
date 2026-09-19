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

import art.arcane.volmlib.util.localization.LanguageAudience;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.server.level.ServerPlayer;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.function.Predicate;

final class ModdedCommandTree {
    private static final SuggestionProvider<CommandSourceStack> DOWNLOAD_SOURCES =
            (context, builder) ->
                    SharedSuggestionProvider.suggest(
                            List.of("pack=overworld", "pack=underworld", "link=", "pack=overworld overwrite=true", "pack=underworld overwrite=true"),
                            builder
                    );
    private static final Predicate<CommandSourceStack> GATE = Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);
    /**
     * SP-4: read-only inspection must work for an unopped player in a no-cheats singleplayer world, where the
     * what/height overlays are the only way to see what Iris generated. Everything that mutates a world,
     * downloads, opens studio or starts a pregen stays on GATE, and so does the world seed: it is the one
     * read-only value a plain player must not be handed, so /iris seed is gated and info/worlds omit the seed
     * field for anyone who fails {@link #isGamemaster(CommandSourceStack)}.
     */
    private static final Predicate<CommandSourceStack> READ_ONLY = Commands.hasPermission(Commands.LEVEL_ALL);

    private ModdedCommandTree() {
    }

    /**
     * Same gate the mutating subtrees use, for output that mixes gated and ungated fields in one command.
     */
    static boolean isGamemaster(CommandSourceStack source) {
        return GATE.test(source);
    }

    static LiteralArgumentBuilder<CommandSourceStack> rootTree() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("iris");

        root.executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> ModdedCommandHelp.send(context.getSource(), "")));
        root.then(helpTree());
        root.then(ModdedLanguageCommands.tree());

        root.then(Commands.literal("version").requires(READ_ONLY)
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> IrisModdedCommands.version(context.getSource()))));

        root.then(Commands.literal("info").requires(READ_ONLY)
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> IrisModdedCommands.info(context.getSource(), null)))
                .then(Commands.argument("dimension", StringArgumentType.greedyString()).suggests(ModdedCommandSuggestions.DIMENSION_NAMES)
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> IrisModdedCommands.info(context.getSource(), StringArgumentType.getString(context, "dimension"))))));

        // ModdedWhatCommands.tree() gates itself at LEVEL_GAMEMASTERS and keeps that gate:
        // "what markers" drives lease-gated 9x9 mantle scans, and the Bukkit twin puts the
        // whole tree behind an op-default permission. requires() would overwrite, not AND.
        root.then(ModdedWhatCommands.tree());

        root.then(teleportTree("teleport"));
        root.then(teleportTree("tp"));

        root.then(Commands.literal("evacuate").requires(GATE)
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> IrisModdedCommands.evacuate(context.getSource(), null)))
                .then(Commands.argument("dimension", DimensionArgument.dimension()).suggests(ModdedCommandSuggestions.DIMENSION_NAMES)
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> IrisModdedCommands.evacuate(context.getSource(), DimensionArgument.getDimension(context, "dimension"))))));

        root.then(Commands.literal("debug").requires(GATE)
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> IrisModdedCommands.debug(context.getSource()))));

        root.then(Commands.literal("reload").requires(GATE)
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> IrisModdedCommands.reload(context.getSource()))));
        root.then(Commands.literal("height").requires(READ_ONLY)
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> IrisModdedCommands.height(context.getSource()))));
        root.then(Commands.literal("worlds").requires(READ_ONLY)
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> IrisModdedCommands.info(context.getSource(), null))));
        root.then(Commands.literal("accesslist").requires(GATE)
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> IrisModdedCommands.info(context.getSource(), null))));

        root.then(gotoTree("goto"));
        root.then(gotoTree("find"));

        root.then(Commands.literal("seed").requires(GATE)
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> IrisModdedCommands.seed(context.getSource()))));

        root.then(goldenhashTree("goldenhash"));
        root.then(goldenhashTree("gold"));

        root.then(downloadTree("download"));
        root.then(downloadTree("dl"));

        root.then(metricsTree("metrics"));
        root.then(metricsTree("measure"));

        root.then(regenTree("regen"));
        root.then(regenTree("rg"));

        root.then(pregenTree("pregen"));
        root.then(pregenTree("pregenerate"));

        root.then(Commands.literal("wand").requires(GATE)
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> ModdedObjectCommands.giveWand(context.getSource()))));
        root.then(Commands.literal("dust").requires(GATE)
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> ModdedObjectCommands.giveDust(context.getSource()))));
        root.then(Commands.literal("d").requires(GATE)
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> ModdedObjectCommands.giveDust(context.getSource()))));
        root.then(ModdedObjectCommands.tree("object"));
        root.then(ModdedObjectCommands.tree("o"));
        root.then(editTree());

        root.then(createTree("create"));
        root.then(createTree("c"));

        root.then(ModdedStudioCommands.tree("studio"));
        root.then(ModdedStudioCommands.tree("std"));
        root.then(ModdedStudioCommands.tree("s"));
        root.then(ModdedPackCommands.tree("pack"));
        root.then(ModdedPackCommands.tree("pk"));
        root.then(ModdedWorldCommands.tree("world"));
        root.then(ModdedWorldCommands.tree("w"));
        root.then(ModdedDatapackCommands.tree("datapack"));
        root.then(ModdedDatapackCommands.tree("datapacks"));
        root.then(ModdedDatapackCommands.tree("dp"));
        root.then(ModdedStructureCommands.tree("structure"));
        root.then(ModdedStructureCommands.tree("struct"));
        root.then(ModdedStructureCommands.tree("str"));
        root.then(ModdedDeveloperCommands.tree("developer"));
        root.then(ModdedDeveloperCommands.tree("dev"));

        return root;
    }

    static Command<CommandSourceStack> localized(Command<CommandSourceStack> command) {
        return context -> {
            ServerPlayer player = context.getSource().getPlayer();
            try (LanguageAudience.Scope audience = LanguageAudience.open(player == null ? null : player.getUUID())) {
                return command.run(context);
            }
        };
    }

    private static LiteralArgumentBuilder<CommandSourceStack> createTree(String name) {
        return Commands.literal(name).requires(GATE)
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) ->
                                ModdedWorldCommands.createWorld(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name"),
                                        "overworld",
                                        1337L)))
                        .then(Commands.argument("pack", StringArgumentType.string()).suggests(ModdedCommandSuggestions.PACK_NAMES)
                                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> ModdedWorldCommands.createWorld(context.getSource(),
                                        StringArgumentType.getString(context, "name"),
                                        StringArgumentType.getString(context, "pack"),
                                        1337L)))
                                .then(Commands.argument("seed", LongArgumentType.longArg())
                                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> ModdedWorldCommands.createWorld(context.getSource(),
                                                StringArgumentType.getString(context, "name"),
                                                StringArgumentType.getString(context, "pack"),
                                                LongArgumentType.getLong(context, "seed")))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> teleportTree(String name) {
        return Commands.literal(name).requires(GATE)
                .then(Commands.argument("dimension", DimensionArgument.dimension()).suggests(ModdedCommandSuggestions.DIMENSION_NAMES)
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) ->
                                IrisModdedCommands.tp(context.getSource(), DimensionArgument.getDimension(context, "dimension"), null)))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) ->
                                        IrisModdedCommands.tp(context.getSource(), DimensionArgument.getDimension(context, "dimension"),
                                                EntityArgument.getPlayer(context, "player"))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> helpTree() {
        return Commands.literal("help").requires(READ_ONLY)
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> ModdedCommandHelp.send(context.getSource(), "")))
                .then(Commands.argument("section", StringArgumentType.greedyString())
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> ModdedCommandHelp.send(context.getSource(), StringArgumentType.getString(context, "section")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> downloadTree(String name) {
        return Commands.literal(name).requires(GATE)
                .then(Commands.argument("source", StringArgumentType.greedyString()).suggests(DOWNLOAD_SOURCES)
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> IrisModdedCommands.download(
                                context.getSource(),
                                StringArgumentType.getString(context, "source")
                        ))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> metricsTree(String name) {
        return Commands.literal(name).requires(READ_ONLY)
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> IrisModdedCommands.metrics(context.getSource())));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> regenTree(String name) {
        return Commands.literal(name).requires(GATE)
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> IrisModdedCommands.regen(context.getSource(), 0)))
                .then(Commands.argument("radius", IntegerArgumentType.integer(0, 64))
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> IrisModdedCommands.regen(context.getSource(), IntegerArgumentType.getInteger(context, "radius")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> gotoTree(String name) {
        return Commands.literal(name).requires(GATE)
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> ModdedCommandHelp.send(context.getSource(), name)))
                .then(Commands.literal("biome")
                        .then(Commands.argument("key", StringArgumentType.greedyString()).suggests(ModdedCommandSuggestions.BIOME_KEYS)
                                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> ModdedLocateCommands.gotoBiome(context.getSource(), StringArgumentType.getString(context, "key"))))))
                .then(Commands.literal("region")
                        .then(Commands.argument("key", StringArgumentType.greedyString()).suggests(ModdedCommandSuggestions.REGION_KEYS)
                                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> ModdedLocateCommands.gotoRegion(context.getSource(), StringArgumentType.getString(context, "key"))))))
                .then(Commands.literal("object")
                        .then(Commands.argument("key", StringArgumentType.greedyString()).suggests(ModdedCommandSuggestions.OBJECT_KEYS)
                                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> ModdedLocateCommands.gotoObject(context.getSource(), StringArgumentType.getString(context, "key"))))))
                .then(Commands.literal("river")
                        .then(Commands.argument("type", StringArgumentType.greedyString()).suggests(ModdedCommandSuggestions.HYDROLOGY_TYPES)
                                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> ModdedLocateCommands.gotoRiver(context.getSource(), StringArgumentType.getString(context, "type"))))))
                .then(Commands.literal("unregistered")
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) ->
                                ModdedUnregisteredStructures.print(context.getSource()))))
                .then(Commands.literal("structure")
                        .then(Commands.argument("key", StringArgumentType.greedyString()).suggests(ModdedCommandSuggestions.STRUCTURE_KEYS)
                                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> ModdedLocateCommands.gotoStructure(context.getSource(), StringArgumentType.getString(context, "key"))))))
                .then(Commands.literal("poi")
                        .then(Commands.argument("type", StringArgumentType.greedyString()).suggests(ModdedCommandSuggestions.POI_TYPES)
                                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> ModdedLocateCommands.gotoPoi(context.getSource(), StringArgumentType.getString(context, "type"))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> pregenTree(String name) {
        RequiredArgumentBuilder<CommandSourceStack, Integer> radius = Commands.argument("radius", IntegerArgumentType.integer(1, 100000))
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> ModdedPregenCommands.pregenStart(context, false, false, false, false, false)));
        attachPregenCenter(radius, false);
        attachPregenFlags(radius, false, false, false, false, false);
        RequiredArgumentBuilder<CommandSourceStack, Identifier> dimension = Commands.argument("dimension", DimensionArgument.dimension()).suggests(ModdedCommandSuggestions.DIMENSION_NAMES)
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> ModdedPregenCommands.pregenStart(context, true, false, false, false, false)));
        attachPregenCenter(dimension, true);
        attachPregenFlags(dimension, true, false, false, false, false);
        radius.then(dimension);

        return Commands.literal(name).requires(GATE)
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> ModdedCommandHelp.send(context.getSource(), name)))
                .then(Commands.literal("start")
                        .then(radius))
                .then(Commands.literal("stop")
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> ModdedPregenCommands.pregenStop(context.getSource()))))
                .then(Commands.literal("x")
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> ModdedPregenCommands.pregenStop(context.getSource()))))
                .then(Commands.literal("pause")
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> ModdedPregenCommands.pregenPause(context.getSource()))))
                .then(Commands.literal("resume")
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> ModdedPregenCommands.pregenPause(context.getSource()))))
                .then(Commands.literal("status")
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> ModdedPregenCommands.pregenStatus(context.getSource()))));
    }

    private static void attachPregenCenter(ArgumentBuilder<CommandSourceStack, ?> node, boolean withDimension) {
        RequiredArgumentBuilder<CommandSourceStack, Integer> z = Commands.argument("z", IntegerArgumentType.integer())
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> ModdedPregenCommands.pregenStart(context, withDimension, true, false, false, false)));
        attachPregenFlags(z, withDimension, true, false, false, false);
        node.then(Commands.literal("at")
                .then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(z)));
    }

    private static void attachPregenFlags(ArgumentBuilder<CommandSourceStack, ?> node, boolean withDimension, boolean withCenter, boolean gui, boolean sync, boolean nocache) {
        if (!gui) {
            node.then(pregenFlagNode("gui", withDimension, withCenter, true, sync, nocache));
        }
        if (!sync) {
            node.then(pregenFlagNode("sync", withDimension, withCenter, gui, true, nocache));
        }
        if (!nocache) {
            node.then(pregenFlagNode("nocache", withDimension, withCenter, gui, sync, true));
        }
    }

    private static LiteralArgumentBuilder<CommandSourceStack> pregenFlagNode(String name, boolean withDimension, boolean withCenter, boolean gui, boolean sync, boolean nocache) {
        LiteralArgumentBuilder<CommandSourceStack> flag = Commands.literal(name)
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> ModdedPregenCommands.pregenStart(context, withDimension, withCenter, gui, sync, nocache)));
        attachPregenFlags(flag, withDimension, withCenter, gui, sync, nocache);
        return flag;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> goldenhashTree(String name) {
        LiteralArgumentBuilder<CommandSourceStack> radiusAndThreads = Commands.literal(name).requires(GATE)
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> IrisModdedCommands.goldenhash(context.getSource(), 8, 8, ModdedGoldenHash.Mode.AUTO)));
        attachModes(radiusAndThreads, (CommandContext<CommandSourceStack> context) -> 8, (CommandContext<CommandSourceStack> context) -> 8);

        com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Integer> radius = Commands.argument("radius", IntegerArgumentType.integer(0, 256))
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> IrisModdedCommands.goldenhash(context.getSource(), IntegerArgumentType.getInteger(context, "radius"), 8, ModdedGoldenHash.Mode.AUTO)));
        attachModes(radius, (CommandContext<CommandSourceStack> context) -> IntegerArgumentType.getInteger(context, "radius"), (CommandContext<CommandSourceStack> context) -> 8);

        com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Integer> threads = Commands.argument("threads", IntegerArgumentType.integer(1, 64))
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> IrisModdedCommands.goldenhash(context.getSource(), IntegerArgumentType.getInteger(context, "radius"), IntegerArgumentType.getInteger(context, "threads"), ModdedGoldenHash.Mode.AUTO)));
        attachModes(threads, (CommandContext<CommandSourceStack> context) -> IntegerArgumentType.getInteger(context, "radius"), (CommandContext<CommandSourceStack> context) -> IntegerArgumentType.getInteger(context, "threads"));

        radius.then(threads);
        radiusAndThreads.then(radius);
        return radiusAndThreads;
    }

    private interface IntExtractor {
        int extract(CommandContext<CommandSourceStack> context);
    }

    private static void attachModes(com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> node, IntExtractor radius, IntExtractor threads) {
        node.then(Commands.literal("capture")
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> IrisModdedCommands.goldenhash(context.getSource(), radius.extract(context), threads.extract(context), ModdedGoldenHash.Mode.CAPTURE))));
        node.then(Commands.literal("verify")
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> IrisModdedCommands.goldenhash(context.getSource(), radius.extract(context), threads.extract(context), ModdedGoldenHash.Mode.VERIFY))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> editTree() {
        return Commands.literal("edit").requires(GATE)
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> ModdedCommandHelp.send(context.getSource(), "edit")))
                .then(Commands.literal("biome")
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> ModdedEditCommands.editBiome(context.getSource(), null)))
                        .then(Commands.argument("key", StringArgumentType.greedyString()).suggests(ModdedCommandSuggestions.BIOME_KEYS)
                                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> ModdedEditCommands.editBiome(context.getSource(), StringArgumentType.getString(context, "key"))))))
                .then(Commands.literal("b")
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> ModdedEditCommands.editBiome(context.getSource(), null)))
                        .then(Commands.argument("key", StringArgumentType.greedyString()).suggests(ModdedCommandSuggestions.BIOME_KEYS)
                                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> ModdedEditCommands.editBiome(context.getSource(), StringArgumentType.getString(context, "key"))))))
                .then(Commands.literal("region")
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> ModdedEditCommands.editRegion(context.getSource(), null)))
                        .then(Commands.argument("key", StringArgumentType.greedyString()).suggests(ModdedCommandSuggestions.REGION_KEYS)
                                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> ModdedEditCommands.editRegion(context.getSource(), StringArgumentType.getString(context, "key"))))))
                .then(Commands.literal("r")
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> ModdedEditCommands.editRegion(context.getSource(), null)))
                        .then(Commands.argument("key", StringArgumentType.greedyString()).suggests(ModdedCommandSuggestions.REGION_KEYS)
                                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> ModdedEditCommands.editRegion(context.getSource(), StringArgumentType.getString(context, "key"))))))
                .then(Commands.literal("dimension")
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> ModdedEditCommands.editDimension(context.getSource()))))
                .then(Commands.literal("d")
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> ModdedEditCommands.editDimension(context.getSource()))));
    }
}
