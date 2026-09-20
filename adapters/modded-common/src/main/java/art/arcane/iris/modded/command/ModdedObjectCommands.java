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

import art.arcane.volmlib.nativelib.terrain.NativeBlockPoint;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeEditPlayer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeEditWorld;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeTileData;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.modded.ModdedIrisLog;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.studio.tree.TreePlausibilizeBatch;
import art.arcane.iris.studio.tree.TreePlausibilizer;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.structure.object.IrisObjectRotation;
import art.arcane.iris.generation.block.TileData;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedBlockState;
import art.arcane.iris.modded.ModdedTileData;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.math.RNG;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandSource;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandRegistration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.modded.localization.ModdedCommandMessages;
import art.arcane.iris.localization.RuntimeUiMessages;
import art.arcane.volmlib.util.localization.MessageArgument;
public final class ModdedObjectCommands {
    private static final Predicate<NativeCommandSource> GATE = NativeCommandRegistration.GAMEMASTERS;
    private static final long MAX_SAVE_VOLUME = 500000L;
    private static final long MAX_AUTOSELECT_VOLUME = 100000L;
    private static final double TARGET_RANGE = 256.0D;

    private static final SuggestionProvider<NativeCommandSource> OBJECT_KEYS = (CommandContext<NativeCommandSource> context, SuggestionsBuilder builder) -> {
        ModdedCommandFeedback.tab(context.getSource());
        try {
            Engine engine = IrisModdedCommands.engineFor(context.getSource().world());
            if (engine != null) {
                return NativeCommandRegistration.suggest(engine.getData().getObjectLoader().getPossibleKeys(), builder);
            }
        } catch (Throwable e) {
            IrisModdedCommands.warnTabFailure("object keys", context.getSource(), e);
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<NativeCommandSource> ROTATIONS = (CommandContext<NativeCommandSource> context, SuggestionsBuilder builder) ->
            NativeCommandRegistration.suggest(List.of("0", "90", "180", "270"), builder);

    private ModdedObjectCommands() {
    }

    private enum ResizeOp {
        EXPAND,
        CONTRACT,
        SHIFT
    }

    public static LiteralArgumentBuilder<NativeCommandSource> tree(String name) {
        ModdedObjectUndo.init();
        LiteralArgumentBuilder<NativeCommandSource> root = NativeCommandRegistration.literal(name).requires(GATE);

        root.executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> ModdedCommandHelp.send(context.getSource(), name)));

        root.then(NativeCommandRegistration.literal("wand")
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> giveWand(context.getSource()))));
        root.then(NativeCommandRegistration.literal("dust")
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> giveDust(context.getSource()))));
        root.then(NativeCommandRegistration.literal("d")
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> giveDust(context.getSource()))));

        root.then(NativeCommandRegistration.literal("save")
                .then(NativeCommandRegistration.literal("overwrite")
                        .then(NativeCommandRegistration.argument("name", StringArgumentType.greedyString())
                                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> save(context.getSource(), StringArgumentType.getString(context, "name"), true)))))
                .then(NativeCommandRegistration.argument("name", StringArgumentType.greedyString())
                        .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> save(context.getSource(), StringArgumentType.getString(context, "name"), false)))));

        root.then(pasteTree());

        root.then(resizeTree("expand", ResizeOp.EXPAND));
        root.then(resizeTree("contract", ResizeOp.CONTRACT));
        root.then(resizeTree("-", ResizeOp.CONTRACT));
        root.then(resizeTree("shift", ResizeOp.SHIFT));

        root.then(NativeCommandRegistration.literal("xpy")
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> autoSelect(context.getSource(), false))));
        root.then(NativeCommandRegistration.literal("x+y")
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> autoSelect(context.getSource(), false))));
        root.then(NativeCommandRegistration.literal("xay")
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> autoSelect(context.getSource(), true))));
        root.then(NativeCommandRegistration.literal("x&y")
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> autoSelect(context.getSource(), true))));

        root.then(positionTree("position1", true));
        root.then(positionTree("p1", true));
        root.then(positionTree("position2", false));
        root.then(positionTree("p2", false));

        root.then(NativeCommandRegistration.literal("analyze")
                .then(NativeCommandRegistration.argument("key", StringArgumentType.greedyString()).suggests(OBJECT_KEYS)
                        .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> analyze(context.getSource(), StringArgumentType.getString(context, "key"))))));

        root.then(NativeCommandRegistration.literal("shrink")
                .then(NativeCommandRegistration.argument("key", StringArgumentType.greedyString()).suggests(OBJECT_KEYS)
                        .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> shrink(context.getSource(), StringArgumentType.getString(context, "key"))))));

        root.then(NativeCommandRegistration.literal("undo")
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> undo(context.getSource(), 1)))
                .then(NativeCommandRegistration.argument("amount", IntegerArgumentType.integer(1, 32))
                        .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> undo(context.getSource(), IntegerArgumentType.getInteger(context, "amount"))))));
        root.then(NativeCommandRegistration.literal("u")
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> undo(context.getSource(), 1)))
                .then(NativeCommandRegistration.argument("amount", IntegerArgumentType.integer(1, 32))
                        .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> undo(context.getSource(), IntegerArgumentType.getInteger(context, "amount"))))));

        root.then(bukkitOnly("we", "WorldEdit selection import requires the Bukkit plugin with WorldEdit installed."));
        root.then(bukkitOnly("studio", "The object studio world requires the Bukkit studio toolchain; it is not available on modded servers."));
        root.then(bukkitOnly("convert", "Schematic conversion (.schem -> .iob) requires the Bukkit plugin."));
        root.then(NativeCommandRegistration.literal("plausibilize")
                .then(NativeCommandRegistration.argument("args", StringArgumentType.greedyString()).suggests(OBJECT_KEYS)
                        .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> plausibilize(context.getSource(), StringArgumentType.getString(context, "args"))))));

        return root;
    }

    private static LiteralArgumentBuilder<NativeCommandSource> pasteTree() {
        LiteralArgumentBuilder<NativeCommandSource> paste = NativeCommandRegistration.literal("paste");
        paste.then(NativeCommandRegistration.argument("key", StringArgumentType.greedyString()).suggests(OBJECT_KEYS)
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> paste(context.getSource(), StringArgumentType.getString(context, "key"), 0, null))));
        paste.then(NativeCommandRegistration.literal("rotate")
                .then(NativeCommandRegistration.argument("degrees", IntegerArgumentType.integer(-270, 270)).suggests(ROTATIONS)
                        .then(NativeCommandRegistration.argument("key", StringArgumentType.greedyString()).suggests(OBJECT_KEYS)
                                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> paste(context.getSource(), StringArgumentType.getString(context, "key"),
                                        IntegerArgumentType.getInteger(context, "degrees"), null))))));
        paste.then(NativeCommandRegistration.literal("at")
                .then(NativeCommandRegistration.argument("x", IntegerArgumentType.integer())
                        .then(NativeCommandRegistration.argument("y", IntegerArgumentType.integer())
                                .then(NativeCommandRegistration.argument("z", IntegerArgumentType.integer())
                                        .then(NativeCommandRegistration.literal("rotate")
                                                .then(NativeCommandRegistration.argument("degrees", IntegerArgumentType.integer(-270, 270)).suggests(ROTATIONS)
                                                        .then(NativeCommandRegistration.argument("key", StringArgumentType.greedyString()).suggests(OBJECT_KEYS)
                                                                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> paste(context.getSource(), StringArgumentType.getString(context, "key"),
                                                                        IntegerArgumentType.getInteger(context, "degrees"),
                                                                        new NativeBlockPoint(IntegerArgumentType.getInteger(context, "x"), IntegerArgumentType.getInteger(context, "y"), IntegerArgumentType.getInteger(context, "z"))))))))
                                        .then(NativeCommandRegistration.argument("key", StringArgumentType.greedyString()).suggests(OBJECT_KEYS)
                                                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> paste(context.getSource(), StringArgumentType.getString(context, "key"), 0,
                                                        new NativeBlockPoint(IntegerArgumentType.getInteger(context, "x"), IntegerArgumentType.getInteger(context, "y"), IntegerArgumentType.getInteger(context, "z"))))))))));
        return paste;
    }

    private static LiteralArgumentBuilder<NativeCommandSource> resizeTree(String name, ResizeOp op) {
        return NativeCommandRegistration.literal(name)
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> resize(context.getSource(), 1, op)))
                .then(NativeCommandRegistration.argument("amount", IntegerArgumentType.integer(1, 256))
                        .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> resize(context.getSource(), IntegerArgumentType.getInteger(context, "amount"), op))));
    }

    private static LiteralArgumentBuilder<NativeCommandSource> positionTree(String name, boolean first) {
        return NativeCommandRegistration.literal(name)
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> position(context.getSource(), first, false)))
                .then(NativeCommandRegistration.literal("look")
                        .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> position(context.getSource(), first, true))));
    }

    private static LiteralArgumentBuilder<NativeCommandSource> bukkitOnly(String name, String message) {
        return NativeCommandRegistration.literal(name)
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) -> {
                    IrisModdedCommands.fail(context.getSource(), message);
                    return 0;
                }));
    }

    public static int giveWand(NativeCommandSource source) {
        NativeEditPlayer player = source.editingPlayer();
        if (player == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_THIS_COMMAND_CAN_ONLY_BE_USED_BY_PLAYERS_WAND_IS));
            return 0;
        }
        if (!player.give(ModdedWandService.createWand())) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_YOUR_INVENTORY_IS_FULL));
            return 0;
        }
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_POOF_GOOD_LUCK_BUILDING_LEFT_CLICK_CORNER_1_RIGHT_CLICK));
        return 1;
    }

    static int giveDust(NativeCommandSource source) {
        NativeEditPlayer player = source.editingPlayer();
        if (player == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_THIS_COMMAND_CAN_ONLY_BE_USED_BY_PLAYERS_DUST_IS));
            return 0;
        }
        if (!player.give(ModdedWandService.createDust())) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_YOUR_INVENTORY_IS_FULL_2));
            return 0;
        }
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_RIGHT_CLICK_BLOCK_REVEAL_OBJECT_IT_BELONGS));
        return 1;
    }

    private static int save(NativeCommandSource source, String nameRaw, boolean overwrite) {
        NativeEditPlayer player = source.editingPlayer();
        if (player == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_THIS_COMMAND_CAN_ONLY_BE_USED_BY_PLAYERS_SAVING_CAPTURES));
            return 0;
        }
        NativeEditWorld level = player.world();
        Engine engine = IrisModdedCommands.engineFor(level.world());
        if (engine == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_THIS_DIMENSION_IS_NOT_GENERATED_BY_IRIS_OBJECTS_SAVE_INTO));
            return 0;
        }
        if (!ModdedWandService.isHoldingWand(player)) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_HOLD_YOUR_IRIS_WAND_IRIS_WAND));
            return 0;
        }
        ModdedWandService.Selection selection = ModdedWandService.selection(player);
        if (selection == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_NO_AREA_SELECTED_LEFT_RIGHT_CLICK_BLOCKS_WITH_WAND_FIRST));
            return 0;
        }
        String name = nameRaw.trim().replace('\\', '/');
        if (name.isEmpty() || name.contains("..")) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_INVALID_OBJECT_NAME, MessageArgument.untrusted("nameRaw", nameRaw)));
            return 0;
        }
        NativeBlockPoint min = selection.min();
        NativeBlockPoint max = selection.max();
        int w = max.x() - min.x() + 1;
        int h = max.y() - min.y() + 1;
        int d = max.z() - min.z() + 1;
        long volume = (long) w * h * d;
        if (volume > MAX_SAVE_VOLUME) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_SELECTION_TOO_LARGE_BLOCKS_MAX, MessageArgument.untrusted("volume", volume), MessageArgument.untrusted("MAXSAVEVOLUME", MAX_SAVE_VOLUME)));
            return 0;
        }
        File file = new File(engine.getData().getDataFolder(), "objects" + File.separator + name.replace('/', File.separatorChar) + ".iob");
        // Atomic path claim ON the server thread: the async write below turned a plain
        // exists() check into a TOCTOU where two rapid saves interleaved into one file.
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        boolean claimed;
        try {
            claimed = file.createNewFile();
        } catch (IOException e) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_FAILED_SAVE_OBJECT, MessageArgument.untrusted("value", String.valueOf(e.getMessage()))));
            return 0;
        }
        if (!claimed && !overwrite) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_FILE_ALREADY_EXISTS_USE_IRIS_OBJECT_SAVE_OVERWRITE, MessageArgument.untrusted("name", name)));
            return 0;
        }
        int[] tilesSkipped = {0};
        int[] tilesSaved = {0};
        // capture() must stay on the server thread (getBlockState/getBlockEntity are not
        // async-safe), but the disk write of a local, unshared object is not tick work.
        IrisObject object = capture(level, min, max, w, h, d, tilesSkipped, tilesSaved);
        boolean finalClaimed = claimed;
        J.a(() -> {
            try {
                object.write(file);
            } catch (IOException e) {
                ModdedIrisLog.error("Iris object save failed for {}", file.getAbsolutePath(), e);
                if (finalClaimed) {
                    // Never leave a 0-byte claim file permanently blocking non-overwrite saves.
                    file.delete();
                }
                source.execute(() -> IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_FAILED_SAVE_OBJECT, MessageArgument.untrusted("value", String.valueOf(e.getMessage())))));
                return;
            }
            StringBuilder tileNote = new StringBuilder();
            if (tilesSaved[0] > 0) {
                tileNote.append(" (").append(tilesSaved[0]).append(" tile entity state(s) captured");
                if (tilesSkipped[0] > 0) {
                    tileNote.append(", ").append(tilesSkipped[0]).append(" failed");
                }
                tileNote.append(")");
            } else if (tilesSkipped[0] > 0) {
                tileNote.append(" (").append(tilesSkipped[0]).append(" tile state(s) could not be captured)");
            }
            source.execute(() -> IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_SAVED_OBJECTS_IOB_X_X_BLOCK_S, MessageArgument.untrusted("value", engine.getData().getDataFolder().getName()), MessageArgument.untrusted("name", name), MessageArgument.untrusted("w", w), MessageArgument.untrusted("h", h), MessageArgument.untrusted("d", d), MessageArgument.untrusted("value2", object.getBlocks().size()), MessageArgument.untrusted("tileNote", tileNote))));
            ModdedIrisLog.info("Iris object save: {} {}x{}x{} blocks={} tilesSaved={} tilesSkipped={} -> {}", name, w, h, d, object.getBlocks().size(), tilesSaved[0], tilesSkipped[0], file.getAbsolutePath());
        });
        return 1;
    }

    private static IrisObject capture(NativeEditWorld level, NativeBlockPoint min, NativeBlockPoint max, int w, int h, int d, int[] tilesSkipped, int[] tilesSaved) {
        IrisObject object = new IrisObject(w, h, d);
        level.capture(new NativeEditWorld.Bounds(min, max), new ObjectCapture(object, tilesSkipped, tilesSaved));
        return object;
    }

    private static int paste(NativeCommandSource source, String keyRaw, int rotation, NativeBlockPoint at) {
        NativeEditWorld level = new NativeEditWorld(source.world());
        Engine engine = IrisModdedCommands.engineFor(level.world());
        String key = keyRaw.trim();
        IrisObject object = null;
        try {
            object = IrisData.loadAnyObject(key, engine == null ? null : engine.getData());
        } catch (Throwable e) {
            ModdedIrisLog.error("Iris object load failed for {}", key, e);
        }
        if (object == null || object.getBlocks().size() == 0) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_UNKNOWN_EMPTY_OBJECT, MessageArgument.untrusted("key", key)));
            return 0;
        }

        NativeEditPlayer player = source.editingPlayer();
        NativeBlockPoint target = at;
        if (target == null) {
            if (player == null) {
                IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_CONSOLE_MUST_SPECIFY_COORDINATES_IRIS_OBJECT_PASTE_AT_X_Y, MessageArgument.untrusted("key", key)));
                return 0;
            }
            NativeBlockPoint hit = player.pickBlock(TARGET_RANGE);
            if (hit == null) {
                IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_YOU_ARE_NOT_LOOKING_AT_BLOCK_WITHIN_BLOCKS, MessageArgument.untrusted("value", (int) TARGET_RANGE)));
                return 0;
            }
            target = hit.above();
        }

        IrisObjectPlacement placement = new IrisObjectPlacement();
        placement.setRotation(IrisObjectRotation.of(0, rotation, 0));
        ModdedObjectPlacer placer = new ModdedObjectPlacer(level.world(), engine);
        try {
            object = placement.scaleObject(new RNG(), object, engine == null ? null : engine.getDimension());
            object.place(target.x(), target.y() + object.getCenter().getY(), target.z(), placer, placement, new RNG(), object.getLoader());
        } catch (Throwable e) {
            ModdedIrisLog.error("Iris paste failed for {}", key, e);
            ModdedObjectUndo.record(player == null ? ModdedObjectUndo.CONSOLE : player.id(), placer.undoSnapshot());
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_PASTE_FAILED_PARTIAL_CHANGES_RECORDED_UNDO, MessageArgument.untrusted("value", e.getClass().getSimpleName())));
            return 0;
        }
        UUID owner = player == null ? ModdedObjectUndo.CONSOLE : player.id();
        ModdedObjectUndo.record(owner, placer.undoSnapshot());
        String tileNote = tileNote(placer);
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_PLACED_AT_ROT_WRITE_S_NON_AIR, MessageArgument.untrusted("key", key), MessageArgument.untrusted("value", target.x()), MessageArgument.untrusted("value2", target.y()), MessageArgument.untrusted("value3", target.z()), MessageArgument.untrusted("rotation", rotation), MessageArgument.untrusted("value4", placer.writes()), MessageArgument.untrusted("value5", placer.nonAirWrites()), MessageArgument.untrusted("tileNote", tileNote)));
        ModdedIrisLog.info("Iris paste: {} at {},{},{} rot={} writes={} nonAir={} tilesRestored={} tilesSkipped={}",
                key, target.x(), target.y(), target.z(), rotation, placer.writes(), placer.nonAirWrites(), placer.restoredTiles(), placer.skippedTiles());
        return placer.writes() > 0 ? 1 : 0;
    }

    private static int resize(NativeCommandSource source, int amount, ResizeOp op) {
        NativeEditPlayer player = source.editingPlayer();
        if (player == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_THIS_COMMAND_CAN_ONLY_BE_USED_BY_PLAYERS));
            return 0;
        }
        if (!ModdedWandService.isHoldingWand(player)) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_HOLD_YOUR_IRIS_WAND_IRIS_WAND_2));
            return 0;
        }
        ModdedWandService.Selection selection = ModdedWandService.selection(player);
        if (selection == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_NO_AREA_SELECTED));
            return 0;
        }
        NativeEditPlayer.LookDirection direction = player.lookDirection();
        int[] mins = {selection.min().x(), selection.min().y(), selection.min().z()};
        int[] maxs = {selection.max().x(), selection.max().y(), selection.max().z()};
        int axis = direction.axis();
        int step = direction.step();
        switch (op) {
            case EXPAND -> {
                if (step > 0) {
                    maxs[axis] += amount;
                } else {
                    mins[axis] -= amount;
                }
            }
            case CONTRACT -> {
                if (step > 0) {
                    maxs[axis] = Math.max(mins[axis], maxs[axis] - amount);
                } else {
                    mins[axis] = Math.min(maxs[axis], mins[axis] + amount);
                }
            }
            case SHIFT -> {
                mins[axis] += step * amount;
                maxs[axis] += step * amount;
            }
        }
        NativeBlockPoint first = new NativeBlockPoint(mins[0], mins[1], mins[2]);
        NativeBlockPoint second = new NativeBlockPoint(maxs[0], maxs[1], maxs[2]);
        ModdedWandService.setSelection(player, first, second);
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_MESSAGE, MessageArgument.untrusted("value", op.name().toLowerCase(Locale.ROOT)), MessageArgument.untrusted("amount", amount), MessageArgument.untrusted("value2", direction.name()), MessageArgument.untrusted("value3", describe(first, second))));
        return 1;
    }

    private static int position(NativeCommandSource source, boolean first, boolean look) {
        NativeEditPlayer player = source.editingPlayer();
        if (player == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_THIS_COMMAND_CAN_ONLY_BE_USED_BY_PLAYERS_2));
            return 0;
        }
        if (!ModdedWandService.isHoldingWand(player)) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_READY_YOUR_WAND_IRIS_WAND));
            return 0;
        }
        NativeBlockPoint pos;
        if (look) {
            NativeBlockPoint hit = player.pickBlock(TARGET_RANGE);
            if (hit == null) {
                IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_YOU_ARE_NOT_LOOKING_AT_BLOCK));
                return 0;
            }
            pos = hit;
        } else {
            pos = player.blockPosition().below();
        }
        ModdedWandService.Selection selection = ModdedWandService.selection(player);
        NativeBlockPoint other = selection == null ? null : (first ? selection.second() : selection.first());
        NativeBlockPoint fallback = other == null ? pos : other;
        if (first) {
            ModdedWandService.setSelection(player, pos, fallback);
        } else {
            ModdedWandService.setSelection(player, fallback, pos);
        }
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_POSITION_SET, MessageArgument.untrusted("value", (first ? 1 : 2)), MessageArgument.untrusted("value2", pos.x()), MessageArgument.untrusted("value3", pos.y()), MessageArgument.untrusted("value4", pos.z())));
        return 1;
    }

    private static int autoSelect(NativeCommandSource source, boolean down) {
        NativeEditPlayer player = source.editingPlayer();
        if (player == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_THIS_COMMAND_CAN_ONLY_BE_USED_BY_PLAYERS_3));
            return 0;
        }
        if (!ModdedWandService.isHoldingWand(player)) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_HOLD_YOUR_WAND));
            return 0;
        }
        ModdedWandService.Selection selection = ModdedWandService.selection(player);
        if (selection == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_NO_AREA_SELECTED_2));
            return 0;
        }
        NativeEditWorld level = player.world();
        NativeBlockPoint min = selection.min();
        NativeBlockPoint max = selection.max();
        long volume = (long) (max.x() - min.x() + 1) * (max.y() - min.y() + 1) * (max.z() - min.z() + 1);
        if (volume > MAX_AUTOSELECT_VOLUME) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_SELECTION_TOO_LARGE_AUTO_SELECT_BLOCKS_MAX, MessageArgument.untrusted("volume", volume), MessageArgument.untrusted("MAXAUTOSELECTVOLUME", MAX_AUTOSELECT_VOLUME)));
            return 0;
        }
        int levelMinY = level.minY();
        int levelMaxY = levelMinY + level.height() - 1;

        int topMinY = min.y();
        int topMaxY = max.y();
        while (topMaxY < levelMaxY && !boxOnlyAir(level, min.x(), topMinY, min.z(), max.x(), topMaxY, max.z())) {
            topMinY++;
            topMaxY++;
        }
        topMaxY--;

        int bottomY = min.y();
        if (down) {
            int lowMinY = min.y();
            int lowMaxY = max.y();
            while (lowMinY > levelMinY && !boxOnlyAir(level, min.x(), lowMinY, min.z(), max.x(), lowMaxY, max.z())) {
                lowMinY--;
                lowMaxY--;
            }
            bottomY = lowMinY + 1;
        }

        int minX = min.x();
        int maxX = max.x();
        int minZ = min.z();
        int maxZ = max.z();
        while (minX < maxX && boxOnlyAir(level, minX, bottomY, minZ, minX, topMaxY, maxZ)) {
            minX++;
        }
        while (maxX > minX && boxOnlyAir(level, maxX, bottomY, minZ, maxX, topMaxY, maxZ)) {
            maxX--;
        }
        while (minZ < maxZ && boxOnlyAir(level, minX, bottomY, minZ, maxX, topMaxY, minZ)) {
            minZ++;
        }
        while (maxZ > minZ && boxOnlyAir(level, minX, bottomY, maxZ, maxX, topMaxY, maxZ)) {
            maxZ--;
        }

        NativeBlockPoint first = new NativeBlockPoint(minX, bottomY, minZ);
        NativeBlockPoint second = new NativeBlockPoint(maxX, topMaxY, maxZ);
        ModdedWandService.setSelection(player, first, second);
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_AUTO_SELECT_COMPLETE, MessageArgument.untrusted("value", describe(first, second))));
        return 1;
    }

    private static boolean boxOnlyAir(NativeEditWorld level, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return level.boxOnlyAir(new NativeEditWorld.Bounds(new NativeBlockPoint(minX, minY, minZ), new NativeBlockPoint(maxX, maxY, maxZ)));
    }

    private static int analyze(NativeCommandSource source, String keyRaw) {
        NativeEditWorld level = new NativeEditWorld(source.world());
        Engine engine = IrisModdedCommands.engineFor(level.world());
        String key = keyRaw.trim();
        IrisObject object = null;
        try {
            object = IrisData.loadAnyObject(key, engine == null ? null : engine.getData());
        } catch (Throwable e) {
            ModdedIrisLog.error("Iris object load failed for {}", key, e);
        }
        if (object == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_UNKNOWN_OBJECT, MessageArgument.untrusted("key", key)));
            return 0;
        }
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_OBJECT_SIZE, MessageArgument.untrusted("value", object.getW()), MessageArgument.untrusted("value2", object.getH()), MessageArgument.untrusted("value3", object.getD())));
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_BLOCKS_USED, MessageArgument.untrusted("value", object.getBlocks().size())));
        Map<String, Integer> counts = new HashMap<>();
        Iterator<NativeBlockState> values = object.getBlocks().values();
        while (values.hasNext()) {
            NativeBlockState state = values.next();
            counts.merge(state.key(), 1, Integer::sum);
        }
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort(Comparator.comparingInt((Map.Entry<String, Integer> entry) -> entry.getValue()).reversed());
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_BLOCKS_OBJECT));
        int shown = 0;
        for (Map.Entry<String, Integer> entry : sorted) {
            IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_MESSAGE_2, MessageArgument.untrusted("value", entry.getKey()), MessageArgument.untrusted("value2", entry.getValue())));
            shown++;
            if (shown >= 10) {
                int remaining = sorted.size() - shown;
                if (remaining > 0) {
                    IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_OTHER_BLOCK_STATE_S, MessageArgument.untrusted("remaining", remaining)));
                }
                break;
            }
        }
        return 1;
    }

    private static int shrink(NativeCommandSource source, String keyRaw) {
        NativeEditWorld level = new NativeEditWorld(source.world());
        Engine engine = IrisModdedCommands.engineFor(level.world());
        String key = keyRaw.trim();
        IrisObject object = null;
        try {
            object = IrisData.loadAnyObject(key, engine == null ? null : engine.getData());
        } catch (Throwable e) {
            ModdedIrisLog.error("Iris object load failed for {}", key, e);
        }
        if (object == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_UNKNOWN_OBJECT_2, MessageArgument.untrusted("key", key)));
            return 0;
        }
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_CURRENT_OBJECT_SIZE, MessageArgument.untrusted("value", object.getW()), MessageArgument.untrusted("value2", object.getH()), MessageArgument.untrusted("value3", object.getD())));
        object.shrinkwrap();
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_NEW_OBJECT_SIZE, MessageArgument.untrusted("value", object.getW()), MessageArgument.untrusted("value2", object.getH()), MessageArgument.untrusted("value3", object.getD())));
        File file = object.getLoadFile();
        if (file == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_OBJECT_HAS_NO_LOAD_FILE_CANNOT_PERSIST_SHRINK));
            return 0;
        }
        try {
            object.write(file);
        } catch (IOException e) {
            ModdedIrisLog.error("Iris object shrink save failed for {}", file.getAbsolutePath(), e);
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_FAILED_SAVE_OBJECT_2, MessageArgument.untrusted("value", file.getName()), MessageArgument.untrusted("value2", String.valueOf(e.getMessage()))));
            return 0;
        }
        return 1;
    }

    private static int plausibilize(NativeCommandSource source, String raw) {
        Engine engine = IrisModdedCommands.engineFor(source.world());
        IrisData data = engine == null ? null : engine.getData();
        boolean dryRun = false;
        int reach = TreePlausibilizer.DEFAULT_REACH;
        StringBuilder targetBuilder = new StringBuilder();
        for (String token : raw.trim().split("\\s+")) {
            String lower = token.toLowerCase(Locale.ROOT);
            if (lower.startsWith("dryrun=")) {
                dryRun = Boolean.parseBoolean(lower.substring("dryrun=".length()));
                continue;
            }
            if (lower.startsWith("reach=")) {
                try {
                    reach = Integer.parseInt(lower.substring("reach=".length()));
                } catch (NumberFormatException e) {
                    IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_INVALID_REACH, MessageArgument.untrusted("token", token)));
                    return 0;
                }
                continue;
            }
            if (!targetBuilder.isEmpty()) {
                targetBuilder.append(' ');
            }
            targetBuilder.append(token);
        }
        String target = targetBuilder.toString();
        List<TreePlausibilizeBatch.Target> targets = TreePlausibilizeBatch.resolve(target, data);
        if (targets.isEmpty()) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_NO_OBJECTS_MATCHED, MessageArgument.untrusted("target", target)));
            return 0;
        }
        IrisModdedCommands.ok(source, IrisLanguage.plain(
                ModdedCommandMessages.MODDED_OBJECT_COMMANDS_PLAUSIBILIZE_REACH_QUEUED_OBJECT_S,
                MessageArgument.trusted("reach", reach),
                MessageArgument.trusted("value", dryRun ? IrisLanguage.plain(RuntimeUiMessages.TREE_DRY_SUFFIX) : ""),
                MessageArgument.trusted("value2", targets.size())
        ));
        boolean dry = dryRun;
        int reachFinal = reach;
        J.a(() -> TreePlausibilizeBatch.run(targets, dry, reachFinal, data, (TreePlausibilizeBatch.Output output) ->
                source.execute(() -> IrisModdedCommands.ok(source, output.text()))));
        return 1;
    }

    private static int undo(NativeCommandSource source, int amount) {
        NativeEditPlayer player = source.editingPlayer();
        UUID owner = player == null ? ModdedObjectUndo.CONSOLE : player.id();
        int available = ModdedObjectUndo.size(owner);
        if (available == 0) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_NOTHING_UNDO));
            return 0;
        }
        int reverted = ModdedObjectUndo.undo(owner, Math.min(amount, available));
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_REVERTED_PASTE_S, MessageArgument.untrusted("reverted", reverted)));
        return 1;
    }

    private static String describe(NativeBlockPoint first, NativeBlockPoint second) {
        return "(" + first.x() + "," + first.y() + "," + first.z() + ") -> ("
                + second.x() + "," + second.y() + "," + second.z() + ")";
    }

    static String tileNote(ModdedObjectPlacer placer) {
        StringBuilder note = new StringBuilder();
        if (placer.restoredTiles() > 0) {
            note.append(", ").append(placer.restoredTiles()).append(" tile entity state(s) restored");
        }
        if (placer.skippedTiles() > 0) {
            note.append(", ").append(placer.skippedTiles()).append(" tile state(s) skipped");
        }
        return note.toString();
    }

    private record ObjectCapture(IrisObject object, int[] skipped, int[] saved) implements NativeEditWorld.CaptureTarget {
        @Override
        public void block(int x, int y, int z, NativeBlockState state) {
            object.setUnsigned(x, y, z, state);
        }

        @Override
        public void tile(int x, int y, int z, NativeTileData tile) {
            if (tile == null) {
                skipped[0]++;
            } else {
                object.setUnsignedTile(x, y, z, ModdedTileData.wrap(tile));
                saved[0]++;
            }
        }

        @Override
        public void warning(String message) {
            IrisLogging.warn(message);
        }

        @Override
        public void failure(NativeEditWorld.BlockFailure failure) {
            NativeBlockPoint pos = failure.position();
            ModdedIrisLog.error("Iris tile capture failed at {} {} {}", pos.x(), pos.y(), pos.z(), failure.error());
        }
    }
}
