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
import art.arcane.iris.structure.StructureIndexService;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.structure.placement.PlacedStructurePiece;
import art.arcane.iris.structure.placement.StructureAssembler;
import art.arcane.iris.structure.graph.StructureAssemblyResult;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.iris.structure.placement.IrisStructure;
import art.arcane.iris.structure.object.ObjectPlaceMode;
import art.arcane.volmlib.util.math.RNG;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandSource;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandRegistration;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeEditPlayer;

import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.modded.localization.ModdedCommandMessages;
import art.arcane.volmlib.util.localization.MessageArgument;
public final class ModdedStructureCommands {
    private static final Predicate<NativeCommandSource> GATE = NativeCommandRegistration.GAMEMASTERS;

    private static final SuggestionProvider<NativeCommandSource> IRIS_STRUCTURE_KEYS = (CommandContext<NativeCommandSource> context, SuggestionsBuilder builder) -> suggestIrisStructureKeys(context, builder);
    private static final SuggestionProvider<NativeCommandSource> ALL_STRUCTURE_KEYS = (CommandContext<NativeCommandSource> context, SuggestionsBuilder builder) -> IrisModdedCommands.suggestStructureKeys(context, builder);

    private ModdedStructureCommands() {
    }

    public static LiteralArgumentBuilder<NativeCommandSource> tree(String name) {
        LiteralArgumentBuilder<NativeCommandSource> root = NativeCommandRegistration.literal(name).requires(GATE);

        root.executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> ModdedCommandHelp.send(context.getSource(), name)));

        root.then(NativeCommandRegistration.literal("list")
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> list(context.getSource()))));
        root.then(NativeCommandRegistration.literal("ls")
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> list(context.getSource()))));

        root.then(NativeCommandRegistration.literal("info")
                .then(NativeCommandRegistration.argument("key", StringArgumentType.greedyString()).suggests(IRIS_STRUCTURE_KEYS)
                        .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> info(context.getSource(), StringArgumentType.getString(context, "key"))))));

        root.then(NativeCommandRegistration.literal("place")
                .then(NativeCommandRegistration.argument("key", StringArgumentType.greedyString()).suggests(IRIS_STRUCTURE_KEYS)
                        .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> place(context.getSource(), StringArgumentType.getString(context, "key"))))));
        root.then(NativeCommandRegistration.literal("p")
                .then(NativeCommandRegistration.argument("key", StringArgumentType.greedyString()).suggests(IRIS_STRUCTURE_KEYS)
                        .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> place(context.getSource(), StringArgumentType.getString(context, "key"))))));

        root.then(message("import", "Structure import rebuilds vanilla & datapack structures as editable Iris resources through Bukkit/NMS template managers; run /iris structure import on a Bukkit server against this pack, then copy the pack folder over."));
        root.then(message("import-all", "Structure import rebuilds vanilla & datapack structures as editable Iris resources through Bukkit/NMS template managers; run /iris structure import on a Bukkit server against this pack, then copy the pack folder over."));
        root.then(message("reimport", "Structure import rebuilds vanilla & datapack structures as editable Iris resources through Bukkit/NMS template managers; run /iris structure import on a Bukkit server against this pack, then copy the pack folder over."));
        root.then(message("imp", "Structure import rebuilds vanilla & datapack structures as editable Iris resources through Bukkit/NMS template managers; run /iris structure import on a Bukkit server against this pack, then copy the pack folder over."));
        root.then(message("all", "Structure import rebuilds vanilla & datapack structures as editable Iris resources through Bukkit/NMS template managers; run /iris structure import on a Bukkit server against this pack, then copy the pack folder over."));
        root.then(message("capture", "Structure capture generates each structure in a throwaway Bukkit scratch world to read its blocks; it requires the Bukkit plugin (v26 NMS binding)."));
        root.then(message("cap", "Structure capture generates each structure in a throwaway Bukkit scratch world to read its blocks; it requires the Bukkit plugin (v26 NMS binding)."));
        root.then(verifyTree("verify"));
        root.then(verifyTree("locateall"));

        return root;
    }

    private static LiteralArgumentBuilder<NativeCommandSource> verifyTree(String name) {
        return NativeCommandRegistration.literal(name)
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> IrisModdedCommands.verifyStructures(context.getSource(), null)))
                .then(NativeCommandRegistration.argument("key", StringArgumentType.greedyString()).suggests(ALL_STRUCTURE_KEYS)
                        .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> IrisModdedCommands.verifyStructures(
                                context.getSource(), StringArgumentType.getString(context, "key")))));
    }

    private static LiteralArgumentBuilder<NativeCommandSource> message(String name, String text) {
        return NativeCommandRegistration.literal(name)
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> {
                    IrisModdedCommands.fail(context.getSource(), text);
                    return 0;
                }))
                .then(NativeCommandRegistration.argument("args", StringArgumentType.greedyString())
                        .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> {
                            IrisModdedCommands.fail(context.getSource(), text);
                            return 0;
                        })));
    }

    private static IrisData dataFor(NativeCommandSource source) {
        Engine engine = IrisModdedCommands.engineFor(source.world());
        if (engine == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STRUCTURE_COMMANDS_THIS_DIMENSION_IS_NOT_GENERATED_BY_IRIS_RUN_THIS_FROM));
            return null;
        }
        return engine.getData();
    }

    private static int list(NativeCommandSource source) {
        IrisData data = dataFor(source);
        if (data == null) {
            return 0;
        }
        File file = StructureIndexService.write(data);
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STRUCTURE_COMMANDS_WROTE_STRUCTURE_INDEX, MessageArgument.untrusted("value", file.getPath())));
        return 1;
    }

    private static int info(NativeCommandSource source, String keyRaw) {
        IrisData data = dataFor(source);
        if (data == null) {
            return 0;
        }
        String key = keyRaw.trim();
        IrisStructure structure = data.load(IrisStructure.class, key, false);
        if (structure == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STRUCTURE_COMMANDS_NO_IRIS_STRUCTURE_THIS_PACK, MessageArgument.untrusted("key", key)));
            return 0;
        }
        StructureAssembler assembler = StructureAssembler.forData(
                data, structure, new IrisPosition(0, 64, 0));
        StructureAssemblyResult assembly = assembler.assemble(new RNG(1234));
        List<PlacedStructurePiece> pieces = assembly.pieces();
        if (!assembly.hasOutput()) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STRUCTURE_COMMANDS_STRUCTURE_ASSEMBLED_0_PIECES_CHECK_STARTPOOL, MessageArgument.untrusted("key", key), MessageArgument.untrusted("value", structure.getStartPool())));
            return 0;
        }
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (PlacedStructurePiece piece : pieces) {
            minX = Math.min(minX, piece.getMinX());
            minZ = Math.min(minZ, piece.getMinZ());
            maxX = Math.max(maxX, piece.getMaxX());
            maxZ = Math.max(maxZ, piece.getMaxZ());
        }
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STRUCTURE_COMMANDS_STRUCTURE_PIECES_FOOTPRINT_X_BLOCKS_SAMPLE_SEED_1234, MessageArgument.untrusted("key", key), MessageArgument.untrusted("value", pieces.size()), MessageArgument.untrusted("value2", (maxX - minX + 1)), MessageArgument.untrusted("value3", (maxZ - minZ + 1))));
        return 1;
    }

    private static int place(NativeCommandSource source, String keyRaw) {
        NativeEditPlayer player = source.editingPlayer();
        if (player == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STRUCTURE_COMMANDS_THIS_COMMAND_CAN_ONLY_BE_USED_BY_PLAYERS_STRUCTURE_IS));
            return 0;
        }
        NativeWorld level = source.world();
        Engine engine = IrisModdedCommands.engineFor(level);
        IrisData data = dataFor(source);
        if (data == null) {
            return 0;
        }
        String key = keyRaw.trim();
        IrisStructure structure = data.load(IrisStructure.class, key, false);
        if (structure == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STRUCTURE_COMMANDS_NO_IRIS_STRUCTURE_THIS_PACK_2, MessageArgument.untrusted("key", key)));
            return 0;
        }
        int originX = player.blockPosition().x();
        int originY = player.blockPosition().y();
        int originZ = player.blockPosition().z();
        StructureAssembler assembler = StructureAssembler.forData(
                data, structure, new IrisPosition(originX, originY, originZ));
        RNG rng = new RNG((long) originX * 341873128712L + originZ);
        StructureAssemblyResult assembly = assembler.assemble(rng);
        List<PlacedStructurePiece> pieces = assembly.pieces();
        if (!assembly.hasOutput()) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STRUCTURE_COMMANDS_STRUCTURE_ASSEMBLED_0_PIECES, MessageArgument.untrusted("key", key)));
            return 0;
        }
        ModdedObjectPlacer placer = new ModdedObjectPlacer(level, engine);
        UUID owner = player.id();
        try {
            for (PlacedStructurePiece piece : pieces) {
                IrisObjectPlacement config = new IrisObjectPlacement();
                config.setMode(ObjectPlaceMode.STRUCTURE_PIECE);
                config.setRotation(piece.getRotation());
                config.getPlace().add(piece.getObject().getLoadKey());
                if (!structure.getEdit().isEmpty()) {
                    config.setEdit(structure.getEdit());
                }
                piece.getObject().place(piece.getX(), piece.getY(), piece.getZ(), placer, config, rng, null, null, data);
            }
        } catch (Throwable e) {
            ModdedIrisLog.error("Iris structure place failed for {}", key, e);
            ModdedObjectUndo.record(owner, placer.undoSnapshot());
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STRUCTURE_COMMANDS_PLACE_FAILED_PARTIAL_CHANGES_RECORDED_UNDO, MessageArgument.untrusted("value", e.getClass().getSimpleName())));
            return 0;
        }
        ModdedObjectUndo.record(owner, placer.undoSnapshot());
        String tileNote = ModdedObjectCommands.tileNote(placer);
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_STRUCTURE_COMMANDS_PLACED_PIECES_WRITE_S_AT_YOUR_LOCATION_IRIS_OBJECT_UNDO, MessageArgument.untrusted("key", key), MessageArgument.untrusted("value", pieces.size()), MessageArgument.untrusted("value2", placer.writes()), MessageArgument.untrusted("tileNote", tileNote)));
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestIrisStructureKeys(CommandContext<NativeCommandSource> context, SuggestionsBuilder builder) {
        ModdedCommandFeedback.tab(context.getSource());
        try {
            Engine engine = IrisModdedCommands.engineFor(context.getSource().world());
            if (engine != null && engine.getData().getStructureLoader() != null) {
                return NativeCommandRegistration.suggest(engine.getData().getStructureLoader().getPossibleKeys(), builder);
            }
        } catch (Throwable ignored) {
        }
        return builder.buildFuture();
    }
}
