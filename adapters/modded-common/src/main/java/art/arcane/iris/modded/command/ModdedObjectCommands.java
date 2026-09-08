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
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.tools.TreePlausibilizeBatch;
import art.arcane.iris.core.tools.TreePlausibilizer;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.IrisObject;
import art.arcane.iris.engine.object.IrisObjectPlacement;
import art.arcane.iris.engine.object.IrisObjectRotation;
import art.arcane.iris.engine.object.TileData;
import art.arcane.iris.modded.ModdedBlockState;
import art.arcane.iris.modded.ModdedTileData;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.volmlib.util.math.RNG;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

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

import art.arcane.iris.core.localization.IrisLanguage;
import art.arcane.iris.modded.localization.ModdedCommandMessages;
import art.arcane.iris.core.localization.RuntimeUiMessages;
import art.arcane.volmlib.util.localization.MessageArgument;
public final class ModdedObjectCommands {
    private static final Predicate<CommandSourceStack> GATE = Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);
    private static final long MAX_SAVE_VOLUME = 500000L;
    private static final long MAX_AUTOSELECT_VOLUME = 100000L;
    private static final double TARGET_RANGE = 256.0D;

    private static final SuggestionProvider<CommandSourceStack> OBJECT_KEYS = (CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) -> {
        ModdedCommandFeedback.tab(context.getSource());
        try {
            Engine engine = IrisModdedCommands.engineFor(context.getSource().getLevel());
            if (engine != null) {
                return SharedSuggestionProvider.suggest(engine.getData().getObjectLoader().getPossibleKeys(), builder);
            }
        } catch (Throwable e) {
            IrisModdedCommands.warnTabFailure("object keys", context.getSource(), e);
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> ROTATIONS = (CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) ->
            SharedSuggestionProvider.suggest(List.of("0", "90", "180", "270"), builder);

    private ModdedObjectCommands() {
    }

    private enum ResizeOp {
        EXPAND,
        CONTRACT,
        SHIFT
    }

    public static LiteralArgumentBuilder<CommandSourceStack> tree(String name) {
        ModdedObjectUndo.init();
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name).requires(GATE);

        root.executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> ModdedCommandHelp.send(context.getSource(), name)));

        root.then(Commands.literal("wand")
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> giveWand(context.getSource()))));
        root.then(Commands.literal("dust")
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> giveDust(context.getSource()))));
        root.then(Commands.literal("d")
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> giveDust(context.getSource()))));

        root.then(Commands.literal("save")
                .then(Commands.literal("overwrite")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> save(context.getSource(), StringArgumentType.getString(context, "name"), true)))))
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> save(context.getSource(), StringArgumentType.getString(context, "name"), false)))));

        root.then(pasteTree());

        root.then(resizeTree("expand", ResizeOp.EXPAND));
        root.then(resizeTree("contract", ResizeOp.CONTRACT));
        root.then(resizeTree("-", ResizeOp.CONTRACT));
        root.then(resizeTree("shift", ResizeOp.SHIFT));

        root.then(Commands.literal("xpy")
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> autoSelect(context.getSource(), false))));
        root.then(Commands.literal("x+y")
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> autoSelect(context.getSource(), false))));
        root.then(Commands.literal("xay")
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> autoSelect(context.getSource(), true))));
        root.then(Commands.literal("x&y")
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> autoSelect(context.getSource(), true))));

        root.then(positionTree("position1", true));
        root.then(positionTree("p1", true));
        root.then(positionTree("position2", false));
        root.then(positionTree("p2", false));

        root.then(Commands.literal("analyze")
                .then(Commands.argument("key", StringArgumentType.greedyString()).suggests(OBJECT_KEYS)
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> analyze(context.getSource(), StringArgumentType.getString(context, "key"))))));

        root.then(Commands.literal("shrink")
                .then(Commands.argument("key", StringArgumentType.greedyString()).suggests(OBJECT_KEYS)
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> shrink(context.getSource(), StringArgumentType.getString(context, "key"))))));

        root.then(Commands.literal("undo")
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> undo(context.getSource(), 1)))
                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 32))
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> undo(context.getSource(), IntegerArgumentType.getInteger(context, "amount"))))));
        root.then(Commands.literal("u")
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> undo(context.getSource(), 1)))
                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 32))
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> undo(context.getSource(), IntegerArgumentType.getInteger(context, "amount"))))));

        root.then(bukkitOnly("we", "WorldEdit selection import requires the Bukkit plugin with WorldEdit installed."));
        root.then(bukkitOnly("studio", "The object studio world requires the Bukkit studio toolchain; it is not available on modded servers."));
        root.then(bukkitOnly("convert", "Schematic conversion (.schem -> .iob) requires the Bukkit plugin."));
        root.then(Commands.literal("plausibilize")
                .then(Commands.argument("args", StringArgumentType.greedyString()).suggests(OBJECT_KEYS)
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> plausibilize(context.getSource(), StringArgumentType.getString(context, "args"))))));

        return root;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> pasteTree() {
        LiteralArgumentBuilder<CommandSourceStack> paste = Commands.literal("paste");
        paste.then(Commands.argument("key", StringArgumentType.greedyString()).suggests(OBJECT_KEYS)
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> paste(context.getSource(), StringArgumentType.getString(context, "key"), 0, null))));
        paste.then(Commands.literal("rotate")
                .then(Commands.argument("degrees", IntegerArgumentType.integer(-270, 270)).suggests(ROTATIONS)
                        .then(Commands.argument("key", StringArgumentType.greedyString()).suggests(OBJECT_KEYS)
                                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> paste(context.getSource(), StringArgumentType.getString(context, "key"),
                                        IntegerArgumentType.getInteger(context, "degrees"), null))))));
        paste.then(Commands.literal("at")
                .then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("y", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                        .then(Commands.literal("rotate")
                                                .then(Commands.argument("degrees", IntegerArgumentType.integer(-270, 270)).suggests(ROTATIONS)
                                                        .then(Commands.argument("key", StringArgumentType.greedyString()).suggests(OBJECT_KEYS)
                                                                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> paste(context.getSource(), StringArgumentType.getString(context, "key"),
                                                                        IntegerArgumentType.getInteger(context, "degrees"),
                                                                        new BlockPos(IntegerArgumentType.getInteger(context, "x"), IntegerArgumentType.getInteger(context, "y"), IntegerArgumentType.getInteger(context, "z"))))))))
                                        .then(Commands.argument("key", StringArgumentType.greedyString()).suggests(OBJECT_KEYS)
                                                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> paste(context.getSource(), StringArgumentType.getString(context, "key"), 0,
                                                        new BlockPos(IntegerArgumentType.getInteger(context, "x"), IntegerArgumentType.getInteger(context, "y"), IntegerArgumentType.getInteger(context, "z"))))))))));
        return paste;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> resizeTree(String name, ResizeOp op) {
        return Commands.literal(name)
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> resize(context.getSource(), 1, op)))
                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 256))
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> resize(context.getSource(), IntegerArgumentType.getInteger(context, "amount"), op))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> positionTree(String name, boolean first) {
        return Commands.literal(name)
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> position(context.getSource(), first, false)))
                .then(Commands.literal("look")
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> position(context.getSource(), first, true))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> bukkitOnly(String name, String message) {
        return Commands.literal(name)
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) -> {
                    IrisModdedCommands.fail(context.getSource(), message);
                    return 0;
                }));
    }

    public static int giveWand(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_THIS_COMMAND_CAN_ONLY_BE_USED_BY_PLAYERS_WAND_IS));
            return 0;
        }
        if (!player.getInventory().add(ModdedWandService.createWand())) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_YOUR_INVENTORY_IS_FULL));
            return 0;
        }
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_POOF_GOOD_LUCK_BUILDING_LEFT_CLICK_CORNER_1_RIGHT_CLICK));
        return 1;
    }

    static int giveDust(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_THIS_COMMAND_CAN_ONLY_BE_USED_BY_PLAYERS_DUST_IS));
            return 0;
        }
        if (!player.getInventory().add(ModdedWandService.createDust())) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_YOUR_INVENTORY_IS_FULL_2));
            return 0;
        }
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_RIGHT_CLICK_BLOCK_REVEAL_OBJECT_IT_BELONGS));
        return 1;
    }

    private static int save(CommandSourceStack source, String nameRaw, boolean overwrite) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_THIS_COMMAND_CAN_ONLY_BE_USED_BY_PLAYERS_SAVING_CAPTURES));
            return 0;
        }
        ServerLevel level = player.level();
        Engine engine = IrisModdedCommands.engineFor(level);
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
        BlockPos min = selection.min();
        BlockPos max = selection.max();
        int w = max.getX() - min.getX() + 1;
        int h = max.getY() - min.getY() + 1;
        int d = max.getZ() - min.getZ() + 1;
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
        MinecraftServer server = source.getServer();
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
                server.execute(() -> IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_FAILED_SAVE_OBJECT, MessageArgument.untrusted("value", String.valueOf(e.getMessage())))));
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
            server.execute(() -> IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_SAVED_OBJECTS_IOB_X_X_BLOCK_S, MessageArgument.untrusted("value", engine.getData().getDataFolder().getName()), MessageArgument.untrusted("name", name), MessageArgument.untrusted("w", w), MessageArgument.untrusted("h", h), MessageArgument.untrusted("d", d), MessageArgument.untrusted("value2", object.getBlocks().size()), MessageArgument.untrusted("tileNote", tileNote))));
            ModdedIrisLog.info("Iris object save: {} {}x{}x{} blocks={} tilesSaved={} tilesSkipped={} -> {}", name, w, h, d, object.getBlocks().size(), tilesSaved[0], tilesSkipped[0], file.getAbsolutePath());
        });
        return 1;
    }

    private static IrisObject capture(ServerLevel level, BlockPos min, BlockPos max, int w, int h, int d, int[] tilesSkipped, int[] tilesSaved) {
        IrisObject object = new IrisObject(w, h, d);
        HolderLookup.Provider provider = level.registryAccess();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockState state = level.getBlockState(cursor.set(x, y, z));
                    if (state.is(Blocks.AIR)) {
                        continue;
                    }
                    int ox = x - min.getX();
                    int oy = y - min.getY();
                    int oz = z - min.getZ();
                    object.setUnsigned(ox, oy, oz, ModdedBlockState.of(state, null));
                    if (state.hasBlockEntity()) {
                        TileData tile = captureTile(level, provider, cursor.immutable(), state);
                        if (tile != null) {
                            object.setUnsignedTile(ox, oy, oz, tile);
                            tilesSaved[0]++;
                        } else {
                            tilesSkipped[0]++;
                        }
                    }
                }
            }
        }
        return object;
    }

    private static TileData captureTile(ServerLevel level, HolderLookup.Provider provider, BlockPos pos, BlockState state) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return null;
        }
        try {
            CompoundTag tag = blockEntity.saveWithFullMetadata(provider);
            String snbt = NbtUtils.structureToSnbt(tag);
            String blockKey = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            return ModdedTileData.capture(blockKey, snbt);
        } catch (Throwable e) {
            ModdedIrisLog.error("Iris tile capture failed at {} {} {}", pos.getX(), pos.getY(), pos.getZ(), e);
            return null;
        }
    }

    private static int paste(CommandSourceStack source, String keyRaw, int rotation, BlockPos at) {
        ServerLevel level = source.getLevel();
        Engine engine = IrisModdedCommands.engineFor(level);
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

        ServerPlayer player = source.getPlayer();
        BlockPos target = at;
        if (target == null) {
            if (player == null) {
                IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_CONSOLE_MUST_SPECIFY_COORDINATES_IRIS_OBJECT_PASTE_AT_X_Y, MessageArgument.untrusted("key", key)));
                return 0;
            }
            HitResult hit = player.pick(TARGET_RANGE, 1.0F, false);
            if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult blockHit)) {
                IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_YOU_ARE_NOT_LOOKING_AT_BLOCK_WITHIN_BLOCKS, MessageArgument.untrusted("value", (int) TARGET_RANGE)));
                return 0;
            }
            target = blockHit.getBlockPos().above();
        }

        IrisObjectPlacement placement = new IrisObjectPlacement();
        placement.setRotation(IrisObjectRotation.of(0, rotation, 0));
        ModdedObjectPlacer placer = new ModdedObjectPlacer(level, engine);
        try {
            object = placement.scaleObject(new RNG(), object, engine == null ? null : engine.getDimension());
            object.place(target.getX(), target.getY() + object.getCenter().getY(), target.getZ(), placer, placement, new RNG(), object.getLoader());
        } catch (Throwable e) {
            ModdedIrisLog.error("Iris paste failed for {}", key, e);
            ModdedObjectUndo.record(player == null ? ModdedObjectUndo.CONSOLE : player.getUUID(), level, placer.undoSnapshot());
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_PASTE_FAILED_PARTIAL_CHANGES_RECORDED_UNDO, MessageArgument.untrusted("value", e.getClass().getSimpleName())));
            return 0;
        }
        UUID owner = player == null ? ModdedObjectUndo.CONSOLE : player.getUUID();
        ModdedObjectUndo.record(owner, level, placer.undoSnapshot());
        String tileNote = tileNote(placer);
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_PLACED_AT_ROT_WRITE_S_NON_AIR, MessageArgument.untrusted("key", key), MessageArgument.untrusted("value", target.getX()), MessageArgument.untrusted("value2", target.getY()), MessageArgument.untrusted("value3", target.getZ()), MessageArgument.untrusted("rotation", rotation), MessageArgument.untrusted("value4", placer.writes()), MessageArgument.untrusted("value5", placer.nonAirWrites()), MessageArgument.untrusted("tileNote", tileNote)));
        ModdedIrisLog.info("Iris paste: {} at {},{},{} rot={} writes={} nonAir={} tilesRestored={} tilesSkipped={}",
                key, target.getX(), target.getY(), target.getZ(), rotation, placer.writes(), placer.nonAirWrites(), placer.restoredTiles(), placer.skippedTiles());
        return placer.writes() > 0 ? 1 : 0;
    }

    private static int resize(CommandSourceStack source, int amount, ResizeOp op) {
        ServerPlayer player = source.getPlayer();
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
        Direction direction = Direction.getApproximateNearest(player.getLookAngle());
        int[] mins = {selection.min().getX(), selection.min().getY(), selection.min().getZ()};
        int[] maxs = {selection.max().getX(), selection.max().getY(), selection.max().getZ()};
        int axis = switch (direction.getAxis()) {
            case X -> 0;
            case Y -> 1;
            case Z -> 2;
        };
        int step = direction.getAxisDirection().getStep();
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
        BlockPos first = new BlockPos(mins[0], mins[1], mins[2]);
        BlockPos second = new BlockPos(maxs[0], maxs[1], maxs[2]);
        ModdedWandService.setSelection(player, first, second);
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_MESSAGE, MessageArgument.untrusted("value", op.name().toLowerCase(Locale.ROOT)), MessageArgument.untrusted("amount", amount), MessageArgument.untrusted("value2", direction.getName()), MessageArgument.untrusted("value3", describe(first, second))));
        return 1;
    }

    private static int position(CommandSourceStack source, boolean first, boolean look) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_THIS_COMMAND_CAN_ONLY_BE_USED_BY_PLAYERS_2));
            return 0;
        }
        if (!ModdedWandService.isHoldingWand(player)) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_READY_YOUR_WAND_IRIS_WAND));
            return 0;
        }
        BlockPos pos;
        if (look) {
            HitResult hit = player.pick(TARGET_RANGE, 1.0F, false);
            if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult blockHit)) {
                IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_YOU_ARE_NOT_LOOKING_AT_BLOCK));
                return 0;
            }
            pos = blockHit.getBlockPos();
        } else {
            pos = player.blockPosition().below();
        }
        ModdedWandService.Selection selection = ModdedWandService.selection(player);
        BlockPos other = selection == null ? null : (first ? selection.second() : selection.first());
        BlockPos fallback = other == null ? pos : other;
        if (first) {
            ModdedWandService.setSelection(player, pos, fallback);
        } else {
            ModdedWandService.setSelection(player, fallback, pos);
        }
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_POSITION_SET, MessageArgument.untrusted("value", (first ? 1 : 2)), MessageArgument.untrusted("value2", pos.getX()), MessageArgument.untrusted("value3", pos.getY()), MessageArgument.untrusted("value4", pos.getZ())));
        return 1;
    }

    private static int autoSelect(CommandSourceStack source, boolean down) {
        ServerPlayer player = source.getPlayer();
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
        ServerLevel level = player.level();
        BlockPos min = selection.min();
        BlockPos max = selection.max();
        long volume = (long) (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);
        if (volume > MAX_AUTOSELECT_VOLUME) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_SELECTION_TOO_LARGE_AUTO_SELECT_BLOCKS_MAX, MessageArgument.untrusted("volume", volume), MessageArgument.untrusted("MAXAUTOSELECTVOLUME", MAX_AUTOSELECT_VOLUME)));
            return 0;
        }
        int levelMinY = level.getMinY();
        int levelMaxY = levelMinY + level.getHeight() - 1;

        int topMinY = min.getY();
        int topMaxY = max.getY();
        while (topMaxY < levelMaxY && !boxOnlyAir(level, min.getX(), topMinY, min.getZ(), max.getX(), topMaxY, max.getZ())) {
            topMinY++;
            topMaxY++;
        }
        topMaxY--;

        int bottomY = min.getY();
        if (down) {
            int lowMinY = min.getY();
            int lowMaxY = max.getY();
            while (lowMinY > levelMinY && !boxOnlyAir(level, min.getX(), lowMinY, min.getZ(), max.getX(), lowMaxY, max.getZ())) {
                lowMinY--;
                lowMaxY--;
            }
            bottomY = lowMinY + 1;
        }

        int minX = min.getX();
        int maxX = max.getX();
        int minZ = min.getZ();
        int maxZ = max.getZ();
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

        BlockPos first = new BlockPos(minX, bottomY, minZ);
        BlockPos second = new BlockPos(maxX, topMaxY, maxZ);
        ModdedWandService.setSelection(player, first, second);
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_AUTO_SELECT_COMPLETE, MessageArgument.untrusted("value", describe(first, second))));
        return 1;
    }

    private static boolean boxOnlyAir(ServerLevel level, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (!level.getBlockState(cursor.set(x, y, z)).isAir()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static int analyze(CommandSourceStack source, String keyRaw) {
        ServerLevel level = source.getLevel();
        Engine engine = IrisModdedCommands.engineFor(level);
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
        Iterator<PlatformBlockState> values = object.getBlocks().values();
        while (values.hasNext()) {
            PlatformBlockState state = values.next();
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

    private static int shrink(CommandSourceStack source, String keyRaw) {
        ServerLevel level = source.getLevel();
        Engine engine = IrisModdedCommands.engineFor(level);
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

    private static int plausibilize(CommandSourceStack source, String raw) {
        Engine engine = IrisModdedCommands.engineFor(source.getLevel());
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
        MinecraftServer server = source.getServer();
        J.a(() -> TreePlausibilizeBatch.run(targets, dry, reachFinal, data, (TreePlausibilizeBatch.Output output) ->
                server.execute(() -> IrisModdedCommands.ok(source, output.text()))));
        return 1;
    }

    private static int undo(CommandSourceStack source, int amount) {
        ServerPlayer player = source.getPlayer();
        UUID owner = player == null ? ModdedObjectUndo.CONSOLE : player.getUUID();
        int available = ModdedObjectUndo.size(owner);
        if (available == 0) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_NOTHING_UNDO));
            return 0;
        }
        int reverted = ModdedObjectUndo.undo(owner, Math.min(amount, available));
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.MODDED_OBJECT_COMMANDS_REVERTED_PASTE_S, MessageArgument.untrusted("reverted", reverted)));
        return 1;
    }

    private static String describe(BlockPos first, BlockPos second) {
        return "(" + first.getX() + "," + first.getY() + "," + first.getZ() + ") -> ("
                + second.getX() + "," + second.getY() + "," + second.getZ() + ")";
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
}
