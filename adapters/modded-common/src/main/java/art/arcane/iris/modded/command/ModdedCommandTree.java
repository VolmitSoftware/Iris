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
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandSource;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandRegistration;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandArguments;

import java.util.List;
import java.util.function.Predicate;

final class ModdedCommandTree {
    private static final SuggestionProvider<NativeCommandSource> DOWNLOAD_SOURCES =
            (context, builder) ->
                    NativeCommandRegistration.suggest(
                            List.of("pack=overworld", "pack=underworld", "link=", "pack=overworld overwrite=true", "pack=underworld overwrite=true"),
                            builder
                    );
    private static final Predicate<NativeCommandSource> GATE = NativeCommandRegistration.GAMEMASTERS;
    /**
     * SP-4: read-only inspection must work for an unopped player in a no-cheats singleplayer world, where the
     * what/height overlays are the only way to see what Iris generated. Everything that mutates a world,
     * downloads, opens studio or starts a pregen stays on GATE, and so does the world seed: it is the one
     * read-only value a plain player must not be handed, so /iris seed is gated and info/worlds omit the seed
     * field for anyone who fails {@link #isGamemaster(NativeCommandSource)}.
     */
    private static final Predicate<NativeCommandSource> READ_ONLY = NativeCommandRegistration.ALL;

    private ModdedCommandTree() {
    }

    /**
     * Same gate the mutating subtrees use, for output that mixes gated and ungated fields in one command.
     */
    static boolean isGamemaster(NativeCommandSource source) {
        return GATE.test(source);
    }

    static LiteralArgumentBuilder<NativeCommandSource> rootTree() {
        LiteralArgumentBuilder<NativeCommandSource> root = NativeCommandRegistration.literal("iris");

        root.executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> ModdedCommandHelp.send(context.getSource(), "")));
        root.then(helpTree());
        root.then(ModdedLanguageCommands.tree());

        root.then(NativeCommandRegistration.literal("version").requires(READ_ONLY)
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> IrisModdedCommands.version(context.getSource()))));

        root.then(NativeCommandRegistration.literal("info").requires(READ_ONLY)
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> IrisModdedCommands.info(context.getSource(), null)))
                .then(NativeCommandRegistration.argument("dimension", StringArgumentType.greedyString()).suggests(ModdedCommandSuggestions.DIMENSION_NAMES)
                        .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> IrisModdedCommands.info(context.getSource(), StringArgumentType.getString(context, "dimension"))))));

        // ModdedWhatCommands.tree() gates itself at LEVEL_GAMEMASTERS and keeps that gate:
        // "what markers" drives lease-gated 9x9 mantle scans, and the Bukkit twin puts the
        // whole tree behind an op-default permission. requires() would overwrite, not AND.
        root.then(ModdedWhatCommands.tree());

        root.then(teleportTree("teleport"));
        root.then(teleportTree("tp"));

        root.then(NativeCommandRegistration.literal("evacuate").requires(GATE)
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> IrisModdedCommands.evacuate(context.getSource(), null)))
                .then(NativeCommandRegistration.argument("dimension", NativeCommandArguments.dimension()).suggests(ModdedCommandSuggestions.DIMENSION_NAMES)
                        .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> IrisModdedCommands.evacuate(context.getSource(), NativeCommandArguments.getDimension(context, "dimension"))))));

        root.then(NativeCommandRegistration.literal("debug").requires(GATE)
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> IrisModdedCommands.debug(context.getSource()))));

        root.then(NativeCommandRegistration.literal("reload").requires(GATE)
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> IrisModdedCommands.reload(context.getSource()))));
        root.then(NativeCommandRegistration.literal("height").requires(READ_ONLY)
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> IrisModdedCommands.height(context.getSource()))));
        root.then(NativeCommandRegistration.literal("worlds").requires(READ_ONLY)
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> IrisModdedCommands.info(context.getSource(), null))));
        root.then(NativeCommandRegistration.literal("accesslist").requires(GATE)
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> IrisModdedCommands.info(context.getSource(), null))));

        root.then(gotoTree("goto"));
        root.then(gotoTree("find"));

        root.then(NativeCommandRegistration.literal("seed").requires(GATE)
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> IrisModdedCommands.seed(context.getSource()))));

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

        root.then(NativeCommandRegistration.literal("wand").requires(GATE)
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> ModdedObjectCommands.giveWand(context.getSource()))));
        root.then(NativeCommandRegistration.literal("dust").requires(GATE)
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> ModdedObjectCommands.giveDust(context.getSource()))));
        root.then(NativeCommandRegistration.literal("d").requires(GATE)
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> ModdedObjectCommands.giveDust(context.getSource()))));
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

    static Command<NativeCommandSource> localized(Command<NativeCommandSource> command) {
        return context -> {
            try (LanguageAudience.Scope audience = LanguageAudience.open(context.getSource().playerId())) {
                return command.run(context);
            }
        };
    }

    private static LiteralArgumentBuilder<NativeCommandSource> createTree(String name) {
        return NativeCommandRegistration.literal(name).requires(GATE)
                .then(NativeCommandRegistration.argument("name", StringArgumentType.word())
                        .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) ->
                                ModdedWorldCommands.createWorld(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "name"),
                                        "overworld",
                                        1337L)))
                        .then(NativeCommandRegistration.argument("pack", StringArgumentType.string()).suggests(ModdedCommandSuggestions.PACK_NAMES)
                                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> ModdedWorldCommands.createWorld(context.getSource(),
                                        StringArgumentType.getString(context, "name"),
                                        StringArgumentType.getString(context, "pack"),
                                        1337L)))
                                .then(NativeCommandRegistration.argument("seed", LongArgumentType.longArg())
                                        .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> ModdedWorldCommands.createWorld(context.getSource(),
                                                StringArgumentType.getString(context, "name"),
                                                StringArgumentType.getString(context, "pack"),
                                                LongArgumentType.getLong(context, "seed")))))));
    }

    private static LiteralArgumentBuilder<NativeCommandSource> teleportTree(String name) {
        return NativeCommandRegistration.literal(name).requires(GATE)
                .then(NativeCommandRegistration.argument("dimension", NativeCommandArguments.dimension()).suggests(ModdedCommandSuggestions.DIMENSION_NAMES)
                        .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) ->
                                IrisModdedCommands.tp(context.getSource(), NativeCommandArguments.getDimension(context, "dimension"), null)))
                        .then(NativeCommandRegistration.argument("player", NativeCommandArguments.player())
                                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) ->
                                        IrisModdedCommands.tp(context.getSource(), NativeCommandArguments.getDimension(context, "dimension"),
                                                NativeCommandArguments.getPlayer(context, "player"))))));
    }

    private static LiteralArgumentBuilder<NativeCommandSource> helpTree() {
        return NativeCommandRegistration.literal("help").requires(READ_ONLY)
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> ModdedCommandHelp.send(context.getSource(), "")))
                .then(NativeCommandRegistration.argument("section", StringArgumentType.greedyString())
                        .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> ModdedCommandHelp.send(context.getSource(), StringArgumentType.getString(context, "section")))));
    }

    private static LiteralArgumentBuilder<NativeCommandSource> downloadTree(String name) {
        return NativeCommandRegistration.literal(name).requires(GATE)
                .then(NativeCommandRegistration.argument("source", StringArgumentType.greedyString()).suggests(DOWNLOAD_SOURCES)
                        .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> IrisModdedCommands.download(
                                context.getSource(),
                                StringArgumentType.getString(context, "source")
                        ))));
    }

    private static LiteralArgumentBuilder<NativeCommandSource> metricsTree(String name) {
        return NativeCommandRegistration.literal(name).requires(READ_ONLY)
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> IrisModdedCommands.metrics(context.getSource())));
    }

    private static LiteralArgumentBuilder<NativeCommandSource> regenTree(String name) {
        return NativeCommandRegistration.literal(name).requires(GATE)
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> IrisModdedCommands.regen(context.getSource(), 0)))
                .then(NativeCommandRegistration.argument("radius", IntegerArgumentType.integer(0, 64))
                        .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> IrisModdedCommands.regen(context.getSource(), IntegerArgumentType.getInteger(context, "radius")))));
    }

    private static LiteralArgumentBuilder<NativeCommandSource> gotoTree(String name) {
        return NativeCommandRegistration.literal(name).requires(GATE)
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> ModdedCommandHelp.send(context.getSource(), name)))
                .then(NativeCommandRegistration.literal("biome")
                        .then(NativeCommandRegistration.argument("key", StringArgumentType.greedyString()).suggests(ModdedCommandSuggestions.BIOME_KEYS)
                                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> ModdedLocateCommands.gotoBiome(context.getSource(), StringArgumentType.getString(context, "key"))))))
                .then(NativeCommandRegistration.literal("region")
                        .then(NativeCommandRegistration.argument("key", StringArgumentType.greedyString()).suggests(ModdedCommandSuggestions.REGION_KEYS)
                                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> ModdedLocateCommands.gotoRegion(context.getSource(), StringArgumentType.getString(context, "key"))))))
                .then(NativeCommandRegistration.literal("object")
                        .then(NativeCommandRegistration.argument("key", StringArgumentType.greedyString()).suggests(ModdedCommandSuggestions.OBJECT_KEYS)
                                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> ModdedLocateCommands.gotoObject(context.getSource(), StringArgumentType.getString(context, "key"))))))
                .then(NativeCommandRegistration.literal("river")
                        .then(NativeCommandRegistration.argument("type", StringArgumentType.greedyString()).suggests(ModdedCommandSuggestions.HYDROLOGY_TYPES)
                                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> ModdedLocateCommands.gotoRiver(context.getSource(), StringArgumentType.getString(context, "type"))))))
                .then(NativeCommandRegistration.literal("unregistered")
                        .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) ->
                                ModdedUnregisteredStructures.print(context.getSource()))))
                .then(NativeCommandRegistration.literal("structure")
                        .then(NativeCommandRegistration.argument("key", StringArgumentType.greedyString()).suggests(ModdedCommandSuggestions.STRUCTURE_KEYS)
                                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> ModdedLocateCommands.gotoStructure(context.getSource(), StringArgumentType.getString(context, "key"))))))
                .then(NativeCommandRegistration.literal("poi")
                        .then(NativeCommandRegistration.argument("type", StringArgumentType.greedyString()).suggests(ModdedCommandSuggestions.POI_TYPES)
                                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> ModdedLocateCommands.gotoPoi(context.getSource(), StringArgumentType.getString(context, "type"))))));
    }

    private static LiteralArgumentBuilder<NativeCommandSource> pregenTree(String name) {
        RequiredArgumentBuilder<NativeCommandSource, Integer> radius = NativeCommandRegistration.argument("radius", IntegerArgumentType.integer(1, 100000))
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> ModdedPregenCommands.pregenStart(context, false, false, false, false, false)));
        attachPregenCenter(radius, false);
        attachPregenFlags(radius, false, false, false, false, false);
        RequiredArgumentBuilder<NativeCommandSource, ?> dimension = NativeCommandRegistration.argument("dimension", NativeCommandArguments.dimension()).suggests(ModdedCommandSuggestions.DIMENSION_NAMES)
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> ModdedPregenCommands.pregenStart(context, true, false, false, false, false)));
        attachPregenCenter(dimension, true);
        attachPregenFlags(dimension, true, false, false, false, false);
        radius.then(dimension);

        return NativeCommandRegistration.literal(name).requires(GATE)
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> ModdedCommandHelp.send(context.getSource(), name)))
                .then(NativeCommandRegistration.literal("start")
                        .then(radius))
                .then(NativeCommandRegistration.literal("stop")
                        .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> ModdedPregenCommands.pregenStop(context.getSource()))))
                .then(NativeCommandRegistration.literal("x")
                        .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> ModdedPregenCommands.pregenStop(context.getSource()))))
                .then(NativeCommandRegistration.literal("pause")
                        .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> ModdedPregenCommands.pregenPause(context.getSource()))))
                .then(NativeCommandRegistration.literal("resume")
                        .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> ModdedPregenCommands.pregenPause(context.getSource()))))
                .then(NativeCommandRegistration.literal("status")
                        .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> ModdedPregenCommands.pregenStatus(context.getSource()))));
    }

    private static void attachPregenCenter(ArgumentBuilder<NativeCommandSource, ?> node, boolean withDimension) {
        RequiredArgumentBuilder<NativeCommandSource, Integer> z = NativeCommandRegistration.argument("z", IntegerArgumentType.integer())
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> ModdedPregenCommands.pregenStart(context, withDimension, true, false, false, false)));
        attachPregenFlags(z, withDimension, true, false, false, false);
        node.then(NativeCommandRegistration.literal("at")
                .then(NativeCommandRegistration.argument("x", IntegerArgumentType.integer())
                        .then(z)));
    }

    private static void attachPregenFlags(ArgumentBuilder<NativeCommandSource, ?> node, boolean withDimension, boolean withCenter, boolean gui, boolean sync, boolean nocache) {
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

    private static LiteralArgumentBuilder<NativeCommandSource> pregenFlagNode(String name, boolean withDimension, boolean withCenter, boolean gui, boolean sync, boolean nocache) {
        LiteralArgumentBuilder<NativeCommandSource> flag = NativeCommandRegistration.literal(name)
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> ModdedPregenCommands.pregenStart(context, withDimension, withCenter, gui, sync, nocache)));
        attachPregenFlags(flag, withDimension, withCenter, gui, sync, nocache);
        return flag;
    }

    private static LiteralArgumentBuilder<NativeCommandSource> goldenhashTree(String name) {
        LiteralArgumentBuilder<NativeCommandSource> radiusAndThreads = NativeCommandRegistration.literal(name).requires(GATE)
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> IrisModdedCommands.goldenhash(context.getSource(), 8, 8, ModdedGoldenHash.Mode.AUTO)));
        attachModes(radiusAndThreads, (CommandContext<NativeCommandSource> context) -> 8, (CommandContext<NativeCommandSource> context) -> 8);

        com.mojang.brigadier.builder.RequiredArgumentBuilder<NativeCommandSource, Integer> radius = NativeCommandRegistration.argument("radius", IntegerArgumentType.integer(0, 256))
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> IrisModdedCommands.goldenhash(context.getSource(), IntegerArgumentType.getInteger(context, "radius"), 8, ModdedGoldenHash.Mode.AUTO)));
        attachModes(radius, (CommandContext<NativeCommandSource> context) -> IntegerArgumentType.getInteger(context, "radius"), (CommandContext<NativeCommandSource> context) -> 8);

        com.mojang.brigadier.builder.RequiredArgumentBuilder<NativeCommandSource, Integer> threads = NativeCommandRegistration.argument("threads", IntegerArgumentType.integer(1, 64))
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> IrisModdedCommands.goldenhash(context.getSource(), IntegerArgumentType.getInteger(context, "radius"), IntegerArgumentType.getInteger(context, "threads"), ModdedGoldenHash.Mode.AUTO)));
        attachModes(threads, (CommandContext<NativeCommandSource> context) -> IntegerArgumentType.getInteger(context, "radius"), (CommandContext<NativeCommandSource> context) -> IntegerArgumentType.getInteger(context, "threads"));

        radius.then(threads);
        radiusAndThreads.then(radius);
        return radiusAndThreads;
    }

    private interface IntExtractor {
        int extract(CommandContext<NativeCommandSource> context);
    }

    private static void attachModes(com.mojang.brigadier.builder.ArgumentBuilder<NativeCommandSource, ?> node, IntExtractor radius, IntExtractor threads) {
        node.then(NativeCommandRegistration.literal("capture")
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> IrisModdedCommands.goldenhash(context.getSource(), radius.extract(context), threads.extract(context), ModdedGoldenHash.Mode.CAPTURE))));
        node.then(NativeCommandRegistration.literal("verify")
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> IrisModdedCommands.goldenhash(context.getSource(), radius.extract(context), threads.extract(context), ModdedGoldenHash.Mode.VERIFY))));
    }

    private static LiteralArgumentBuilder<NativeCommandSource> editTree() {
        return NativeCommandRegistration.literal("edit").requires(GATE)
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> ModdedCommandHelp.send(context.getSource(), "edit")))
                .then(NativeCommandRegistration.literal("biome")
                        .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> ModdedEditCommands.editBiome(context.getSource(), null)))
                        .then(NativeCommandRegistration.argument("key", StringArgumentType.greedyString()).suggests(ModdedCommandSuggestions.BIOME_KEYS)
                                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> ModdedEditCommands.editBiome(context.getSource(), StringArgumentType.getString(context, "key"))))))
                .then(NativeCommandRegistration.literal("b")
                        .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> ModdedEditCommands.editBiome(context.getSource(), null)))
                        .then(NativeCommandRegistration.argument("key", StringArgumentType.greedyString()).suggests(ModdedCommandSuggestions.BIOME_KEYS)
                                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> ModdedEditCommands.editBiome(context.getSource(), StringArgumentType.getString(context, "key"))))))
                .then(NativeCommandRegistration.literal("region")
                        .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> ModdedEditCommands.editRegion(context.getSource(), null)))
                        .then(NativeCommandRegistration.argument("key", StringArgumentType.greedyString()).suggests(ModdedCommandSuggestions.REGION_KEYS)
                                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> ModdedEditCommands.editRegion(context.getSource(), StringArgumentType.getString(context, "key"))))))
                .then(NativeCommandRegistration.literal("r")
                        .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> ModdedEditCommands.editRegion(context.getSource(), null)))
                        .then(NativeCommandRegistration.argument("key", StringArgumentType.greedyString()).suggests(ModdedCommandSuggestions.REGION_KEYS)
                                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> ModdedEditCommands.editRegion(context.getSource(), StringArgumentType.getString(context, "key"))))))
                .then(NativeCommandRegistration.literal("dimension")
                        .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> ModdedEditCommands.editDimension(context.getSource()))))
                .then(NativeCommandRegistration.literal("d")
                        .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> ModdedEditCommands.editDimension(context.getSource()))));
    }
}
