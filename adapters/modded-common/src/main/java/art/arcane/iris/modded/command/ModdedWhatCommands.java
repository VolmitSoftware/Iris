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
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.localization.IrisMessages;
import art.arcane.iris.modded.localization.ModdedCommandMessages;
import art.arcane.iris.localization.RuntimeUiMessages;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.world.history.SavedBiomeUnavailableException;
import art.arcane.iris.generation.runtime.GenerationSessionException;
import art.arcane.iris.generation.runtime.GenerationSessionLease;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.pack.value.IrisPosition;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.modded.ModdedBlockState;
import art.arcane.iris.modded.ModdedEngineBootstrap;
import art.arcane.iris.modded.ModdedScheduler;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.context.IrisContext;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.matter.MatterMarker;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

public final class ModdedWhatCommands {
    private static final Predicate<CommandSourceStack> GATE =
            Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);
    private static final SuggestionProvider<CommandSourceStack> MARKER_TYPES =
            (CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) ->
                    SharedSuggestionProvider.suggest(
                            List.of("cave_floor", "cave_ceiling", "object"), builder);
    private static final DustParticleOptions MARKER_DUST = new DustParticleOptions(0x5A8CFF, 1.2F);
    private static final int MAX_MARKERS = 8_192;
    private static final int MARKER_BATCH_SIZE = 128;
    private static final ConcurrentHashMap<UUID, MarkerRun> ACTIVE_MARKER_RUNS = new ConcurrentHashMap<>();

    private ModdedWhatCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("what").requires(GATE)
                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) ->
                        inspectHere(context.getSource())))
                .then(Commands.literal("here")
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) ->
                                inspectHere(context.getSource()))))
                .then(Commands.literal("biome")
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) ->
                                inspectBiome(context.getSource()))))
                .then(Commands.literal("region")
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) ->
                                inspectRegion(context.getSource()))))
                .then(Commands.literal("block")
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) ->
                                inspectBlock(context.getSource()))))
                .then(Commands.literal("hand")
                        .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) ->
                                inspectHand(context.getSource()))))
                .then(Commands.literal("markers")
                        .then(Commands.argument("marker", StringArgumentType.greedyString())
                                .suggests(MARKER_TYPES)
                                .executes(ModdedCommandTree.localized((CommandContext<CommandSourceStack> context) ->
                                        inspectMarkers(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "marker"))))));
    }

    static void clear() {
        for (MarkerRun run : ACTIVE_MARKER_RUNS.values()) {
            run.cancelled().set(true);
        }
        ACTIVE_MARKER_RUNS.clear();
    }

    private static int inspectHere(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return playerRequired(source, ModdedCommandMessages.IRIS_MODDED_COMMANDS_THIS_COMMAND_CAN_ONLY_BE_USED_BY_PLAYERS_2);
        }
        ServerLevel level = source.getLevel();
        Engine engine = IrisModdedCommands.engineFor(level);
        if (engine == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(
                    ModdedCommandMessages.IRIS_MODDED_COMMANDS_THIS_DIMENSION_IS_NOT_GENERATED_BY_IRIS_6));
            return 0;
        }
        BlockPos pos = player.blockPosition();
        int result = inspectBiome(source);
        result &= inspectRegion(source);
        int relativeY = pos.getY() - engine.getMinHeight();
        try {
            IrisBiome cave = engine.getCaveOrMantleBiome(pos.getX(), relativeY, pos.getZ());
            IrisModdedCommands.ok(source, IrisLanguage.plain(
                    ModdedCommandMessages.IRIS_MODDED_COMMANDS_CAVE_BIOME,
                    MessageArgument.untrusted("value", cave == null
                            ? IrisLanguage.plain(RuntimeUiMessages.STATUS_NONE)
                            : cave.getLoadKey())));
        } catch (Throwable error) {
            logLookupFailure(source, "cave biome", error,
                    ModdedCommandMessages.IRIS_MODDED_COMMANDS_CAVE_BIOME_LOOKUP_FAILED);
            result = 0;
        }
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()) - 1;
        BlockState surface = level.getBlockState(new BlockPos(pos.getX(), surfaceY, pos.getZ()));
        IrisModdedCommands.ok(source, IrisLanguage.plain(
                ModdedCommandMessages.IRIS_MODDED_COMMANDS_SURFACE_BLOCK_Y,
                MessageArgument.untrusted("value", BuiltInRegistries.BLOCK.getKey(surface.getBlock())),
                MessageArgument.trusted("value2", surfaceY)));
        sendPosition(source, pos);
        return result;
    }

    private static int inspectBiome(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return playerRequired(source, ModdedCommandMessages.IRIS_MODDED_COMMANDS_THIS_COMMAND_CAN_ONLY_BE_USED_BY_PLAYERS_2);
        }
        ServerLevel level = source.getLevel();
        BlockPos pos = player.blockPosition();
        NativeBiome nativeBiome = nativeBiome(level, pos);
        Engine engine = IrisModdedCommands.engineFor(level);
        if (engine == null) {
            IrisModdedCommands.ok(source, IrisLanguage.plain(
                    RuntimeUiMessages.WHAT_NON_IRIS_BIOME,
                    MessageArgument.untrusted("biome", nativeBiome.key()),
                    MessageArgument.trusted("id", nativeBiome.id())));
            return 1;
        }
        try {
            IrisBiome biome = engine.getBiome(
                    pos.getX(), pos.getY() - engine.getMinHeight(), pos.getZ());
            IrisModdedCommands.ok(source, IrisLanguage.plain(
                    RuntimeUiMessages.WHAT_IRIS_BIOME,
                    MessageArgument.untrusted("biome", biome.getLoadKey()),
                    MessageArgument.untrusted("name", biome.getName())));
            IrisModdedCommands.ok(source, IrisLanguage.plain(
                    RuntimeUiMessages.WHAT_DERIVATIVE_BIOME,
                    MessageArgument.untrusted("biome", biome.getDerivativeKey())));
            IrisModdedCommands.ok(source, IrisLanguage.plain(
                    RuntimeUiMessages.WHAT_NATIVE_BIOME,
                    MessageArgument.untrusted("biome", nativeBiome.key()),
                    MessageArgument.trusted("id", nativeBiome.id())));
            return 1;
        } catch (SavedBiomeUnavailableException error) {
            if (error.getCause() != null) {
                ModdedIrisLog.error("Iris saved biome lookup failed in {}",
                        source.getLevel().dimension().identifier(), error);
            }
            IrisModdedCommands.fail(source, error.getMessage());
            return 0;
        } catch (Throwable error) {
            logLookupFailure(source, "biome", error,
                    ModdedCommandMessages.IRIS_MODDED_COMMANDS_BIOME_LOOKUP_FAILED_2);
            return 0;
        }
    }

    private static int inspectRegion(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return playerRequired(source, ModdedCommandMessages.IRIS_MODDED_COMMANDS_THIS_COMMAND_CAN_ONLY_BE_USED_BY_PLAYERS_2);
        }
        Engine engine = IrisModdedCommands.engineFor(source.getLevel());
        if (engine == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(
                    ModdedCommandMessages.IRIS_MODDED_COMMANDS_THIS_DIMENSION_IS_NOT_GENERATED_BY_IRIS_6));
            return 0;
        }
        BlockPos pos = player.blockPosition();
        int centerX = (pos.getX() & ~15) + 8;
        int centerZ = (pos.getZ() & ~15) + 8;
        try {
            IrisRegion region = engine.getRegion(
                    centerX, pos.getY() - engine.getMinHeight(), centerZ);
            IrisModdedCommands.ok(source, IrisLanguage.plain(
                    RuntimeUiMessages.WHAT_IRIS_REGION,
                    MessageArgument.untrusted("region", region.getLoadKey()),
                    MessageArgument.untrusted("name", region.getName())));
            return 1;
        } catch (SavedBiomeUnavailableException error) {
            if (error.getCause() != null) {
                ModdedIrisLog.error("Iris saved biome lookup failed in {}",
                        source.getLevel().dimension().identifier(), error);
            }
            IrisModdedCommands.fail(source, error.getMessage());
            return 0;
        } catch (Throwable error) {
            logLookupFailure(source, "region", error,
                    ModdedCommandMessages.IRIS_MODDED_COMMANDS_REGION_LOOKUP_FAILED_2);
            return 0;
        }
    }

    private static int inspectHand(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return playerRequired(source, ModdedCommandMessages.IRIS_MODDED_COMMANDS_THIS_COMMAND_CAN_ONLY_BE_USED_BY_PLAYERS_IT_INSPECTS);
        }
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(
                    ModdedCommandMessages.IRIS_MODDED_COMMANDS_YOUR_MAIN_HAND_IS_EMPTY));
            return 0;
        }
        IrisModdedCommands.ok(source, IrisLanguage.plain(
                RuntimeUiMessages.WHAT_MATERIAL,
                MessageArgument.untrusted("material", BuiltInRegistries.ITEM.getKey(stack.getItem()))));
        if (stack.getItem() instanceof BlockItem blockItem) {
            IrisModdedCommands.ok(source, IrisLanguage.plain(
                    RuntimeUiMessages.WHAT_FULL_STATE,
                    MessageArgument.untrusted(
                            "state", ModdedBlockState.serialize(blockItem.getBlock().defaultBlockState()))));
        }
        IrisModdedCommands.ok(source, IrisLanguage.plain(
                RuntimeUiMessages.WHAT_ITEM_COUNT,
                MessageArgument.trusted("count", stack.getCount())));
        return 1;
    }

    private static int inspectBlock(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return playerRequired(source, ModdedCommandMessages.IRIS_MODDED_COMMANDS_THIS_COMMAND_CAN_ONLY_BE_USED_BY_PLAYERS_IT_INSPECTS_2);
        }
        HitResult hit = player.pick(128.0D, 1.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(
                    ModdedCommandMessages.IRIS_MODDED_COMMANDS_LOOK_AT_BLOCK_NOT_SKY));
            return 0;
        }
        ServerLevel level = source.getLevel();
        BlockPos pos = blockHit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        PlatformBlockState platform = ModdedBlockState.of(state, null);
        IrisModdedCommands.ok(source, IrisLanguage.plain(
                RuntimeUiMessages.WHAT_MATERIAL,
                MessageArgument.untrusted("material", BuiltInRegistries.BLOCK.getKey(state.getBlock()))));
        IrisModdedCommands.ok(source, IrisLanguage.plain(
                RuntimeUiMessages.WHAT_FULL_STATE,
                MessageArgument.untrusted("state", ModdedBlockState.serialize(state))));
        sendPosition(source, pos);
        sendProperties(source, platform);
        sendObject(source, level, pos);
        sendBlockEntity(source, level, pos);
        return 1;
    }

    private static void sendPosition(CommandSourceStack source, BlockPos pos) {
        IrisModdedCommands.ok(source, IrisLanguage.plain(
                RuntimeUiMessages.WHAT_POSITION,
                MessageArgument.trusted("x", pos.getX()),
                MessageArgument.trusted("y", pos.getY()),
                MessageArgument.trusted("z", pos.getZ()),
                MessageArgument.trusted("chunkX", pos.getX() >> 4),
                MessageArgument.trusted("chunkZ", pos.getZ() >> 4)));
    }

    private static void sendProperties(CommandSourceStack source, PlatformBlockState platform) {
        List<String> flags = propertyNames(platform);
        if (flags.isEmpty()) {
            IrisModdedCommands.ok(source, IrisLanguage.plain(IrisMessages.MODDED_PROPERTIES_NONE));
            return;
        }
        IrisModdedCommands.ok(source, IrisLanguage.plain(
                IrisMessages.MODDED_PROPERTIES,
                MessageArgument.untrusted("properties", String.join(", ", flags))));
    }

    static List<String> propertyNames(PlatformBlockState platform) {
        List<String> flags = new ArrayList<>();
        addFlag(flags, platform.isSolid(), RuntimeUiMessages.WHAT_FLAG_SOLID);
        addFlag(flags, platform.isFluid(), RuntimeUiMessages.WHAT_FLAG_FLUID);
        addFlag(flags, platform.isWater(), RuntimeUiMessages.WHAT_FLAG_WATER);
        addFlag(flags, platform.isWaterLogged(), RuntimeUiMessages.WHAT_FLAG_WATERLOGGED);
        addFlag(flags, platform.isStorage(), RuntimeUiMessages.WHAT_FLAG_STORAGE);
        addFlag(flags, platform.isLit(), RuntimeUiMessages.WHAT_FLAG_LIT);
        addFlag(flags, platform.isFoliage(), RuntimeUiMessages.WHAT_FLAG_FOLIAGE);
        addFlag(flags, platform.isFoliagePlantable(), RuntimeUiMessages.WHAT_FLAG_PLANTABLE_FOLIAGE);
        addFlag(flags, platform.isDecorant(), RuntimeUiMessages.WHAT_FLAG_DECORANT);
        addFlag(flags, platform.isOre(), RuntimeUiMessages.WHAT_FLAG_ORE);
        addFlag(flags, platform.hasTileEntity(), RuntimeUiMessages.WHAT_FLAG_BLOCK_ENTITY);
        return List.copyOf(flags);
    }

    private static void addFlag(List<String> flags, boolean enabled,
                                TextKey message) {
        if (enabled) {
            flags.add(IrisLanguage.plain(message));
        }
    }

    private static void sendObject(CommandSourceStack source, ServerLevel level, BlockPos pos) {
        Engine engine = IrisModdedCommands.engineFor(level);
        if (engine == null) {
            return;
        }
        try {
            String object = engine.getObjectPlacementKey(
                    pos.getX(), pos.getY() - engine.getMinHeight(), pos.getZ());
            if (object != null) {
                IrisModdedCommands.ok(source, IrisLanguage.plain(
                        RuntimeUiMessages.WHAT_OBJECT,
                        MessageArgument.untrusted("object", object)));
            }
        } catch (Throwable error) {
            ModdedIrisLog.error("Iris object lookup failed for /iris what block at {}, {}, {}",
                    pos.getX(), pos.getY(), pos.getZ(), error);
        }
    }

    private static void sendBlockEntity(CommandSourceStack source,
                                        ServerLevel level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return;
        }
        IrisModdedCommands.ok(source, IrisLanguage.plain(
                RuntimeUiMessages.WHAT_BLOCK_ENTITY,
                MessageArgument.untrusted(
                        "type", BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()))));
        if (blockEntity instanceof RandomizableContainer container) {
            ResourceKey<LootTable> lootTable = container.getLootTable();
            if (lootTable != null) {
                IrisModdedCommands.ok(source, IrisLanguage.plain(
                        RuntimeUiMessages.WHAT_LOOT_TABLE,
                        MessageArgument.untrusted("loot", lootTable.identifier())));
            }
        }
        if (blockEntity instanceof SpawnerBlockEntity) {
            CompoundTag tag = blockEntity.saveWithoutMetadata(level.registryAccess());
            CompoundTag spawnData = tag.getCompound("SpawnData").orElse(null);
            CompoundTag entity = spawnData == null ? null : spawnData.getCompound("entity").orElse(null);
            String entityId = entity == null ? "" : entity.getStringOr("id", "");
            if (!entityId.isBlank()) {
                IrisModdedCommands.ok(source, IrisLanguage.plain(
                        RuntimeUiMessages.WHAT_SPAWNER_ENTITY,
                        MessageArgument.untrusted("entity", entityId)));
            }
        }
    }

    private static int inspectMarkers(CommandSourceStack source, String markerRaw) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return playerRequired(source, ModdedCommandMessages.IRIS_MODDED_COMMANDS_THIS_COMMAND_CAN_ONLY_BE_USED_BY_PLAYERS_MARKERS_RENDER);
        }
        ServerLevel level = source.getLevel();
        Engine engine = IrisModdedCommands.engineFor(level);
        if (engine == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(
                    ModdedCommandMessages.IRIS_MODDED_COMMANDS_THIS_DIMENSION_IS_NOT_GENERATED_BY_IRIS_7));
            return 0;
        }
        ModdedScheduler scheduler = ModdedEngineBootstrap.schedulerOrNull();
        if (scheduler == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(
                    ModdedCommandMessages.IRIS_MODDED_COMMANDS_MARKER_SCAN_FAILED,
                    MessageArgument.untrusted("value", "scheduler unavailable")));
            return 0;
        }
        String marker = markerRaw.trim();
        BlockPos origin = player.blockPosition();
        MarkerRun run = new MarkerRun(
                player.getUUID(), player, level, engine, marker,
                origin.getX() >> 4, origin.getZ() >> 4, new AtomicBoolean());
        MarkerRun previous = ACTIVE_MARKER_RUNS.put(player.getUUID(), run);
        if (previous != null) {
            previous.cancelled().set(true);
        }
        IrisModdedCommands.ok(source, IrisLanguage.plain(
                ModdedCommandMessages.IRIS_MODDED_COMMANDS_SCANNING_MARKERS_AROUND_YOU,
                MessageArgument.untrusted("marker", marker)));
        scheduler.async(() -> scanMarkers(source, scheduler, run));
        return 1;
    }

    private static void scanMarkers(CommandSourceStack source,
                                    ModdedScheduler scheduler, MarkerRun run) {
        List<BlockPos> hits = new ArrayList<>();
        MatterMarker marker = new MatterMarker(run.marker());
        try (GenerationSessionLease lease = run.engine().acquireGenerationLease("modded_what_markers");
             IrisContext.Scope ignored = IrisContext.open(run.engine(), lease.sessionId(), null)) {
            for (int chunkX = run.chunkX() - 4; chunkX <= run.chunkX() + 4; chunkX++) {
                for (int chunkZ = run.chunkZ() - 4; chunkZ <= run.chunkZ() + 4; chunkZ++) {
                    if (run.cancelled().get()) {
                        return;
                    }
                    for (IrisPosition position : run.engine().getMantle().findMarkers(chunkX, chunkZ, marker)) {
                        hits.add(new BlockPos(position.getX(), position.getY(), position.getZ()));
                        if (hits.size() >= MAX_MARKERS) {
                            break;
                        }
                    }
                    if (hits.size() >= MAX_MARKERS) {
                        break;
                    }
                }
                if (hits.size() >= MAX_MARKERS) {
                    break;
                }
            }
            scheduler.global(() -> renderMarkerBatch(source, scheduler, run, hits, 0));
        } catch (GenerationSessionException error) {
            markerFailure(source, scheduler, run, error);
        } catch (Throwable error) {
            markerFailure(source, scheduler, run, error);
        }
    }

    private static void renderMarkerBatch(CommandSourceStack source,
                                          ModdedScheduler scheduler, MarkerRun run,
                                          List<BlockPos> hits, int from) {
        if (!active(run)) {
            // Drop the registry entry on abort too, or the run record pins the player, level
            // and engine until server stop. No-op if a newer run already replaced it.
            ACTIVE_MARKER_RUNS.remove(run.playerId(), run);
            return;
        }
        int to = Math.min(hits.size(), from + MARKER_BATCH_SIZE);
        for (int index = from; index < to; index++) {
            BlockPos hit = hits.get(index);
            run.level().sendParticles(run.player(), MARKER_DUST, true, true,
                    hit.getX() + 0.5D, hit.getY() + 1.0D, hit.getZ() + 0.5D,
                    3, 0.2D, 0.2D, 0.2D, 0.0D);
        }
        if (to < hits.size()) {
            scheduler.laterGlobal(() -> renderMarkerBatch(source, scheduler, run, hits, to), 1);
            return;
        }
        ACTIVE_MARKER_RUNS.remove(run.playerId(), run);
        IrisModdedCommands.ok(source, IrisLanguage.plain(
                ModdedCommandMessages.IRIS_MODDED_COMMANDS_FOUND_NEARBY_MARKER_S,
                MessageArgument.trusted("value", hits.size()),
                MessageArgument.untrusted("marker", run.marker())));
    }

    private static boolean active(MarkerRun run) {
        return !run.cancelled().get()
                && ACTIVE_MARKER_RUNS.get(run.playerId()) == run
                && !run.player().hasDisconnected()
                && !run.player().isRemoved()
                && run.player().level() == run.level()
                && !run.engine().isClosing()
                && !run.engine().isClosed();
    }

    private static void markerFailure(CommandSourceStack source,
                                      ModdedScheduler scheduler, MarkerRun run, Throwable error) {
        ModdedIrisLog.error("Iris marker scan failed for {}", run.marker(), error);
        scheduler.global(() -> {
            if (ACTIVE_MARKER_RUNS.remove(run.playerId(), run)) {
                IrisModdedCommands.fail(source, IrisLanguage.plain(
                        ModdedCommandMessages.IRIS_MODDED_COMMANDS_MARKER_SCAN_FAILED,
                        MessageArgument.untrusted("value", error.getClass().getSimpleName())));
            }
        });
    }

    private static NativeBiome nativeBiome(ServerLevel level, BlockPos pos) {
        Holder<Biome> holder = level.getBiome(pos);
        String key = holder.unwrapKey()
                .map((ResourceKey<Biome> resourceKey) -> resourceKey.identifier().toString())
                .orElse(IrisLanguage.plain(RuntimeUiMessages.STATUS_UNREGISTERED));
        Registry<Biome> registry = level.registryAccess().lookupOrThrow(Registries.BIOME);
        return new NativeBiome(key, registry.getId(holder.value()));
    }

    private static int playerRequired(CommandSourceStack source,
                                      TextKey message) {
        IrisModdedCommands.fail(source, IrisLanguage.plain(message));
        return 0;
    }

    private static void logLookupFailure(CommandSourceStack source,
                                         String operation, Throwable error,
                                         TextKey message) {
        ModdedIrisLog.error("Iris /what {} lookup failed in {}", operation,
                source.getLevel().dimension().identifier(), error);
        IrisModdedCommands.fail(source, IrisLanguage.plain(
                message,
                MessageArgument.untrusted("value", error.getClass().getSimpleName())));
    }

    private record NativeBiome(String key, int id) {
    }

    private record MarkerRun(
            UUID playerId,
            ServerPlayer player,
            ServerLevel level,
            Engine engine,
            String marker,
            int chunkX,
            int chunkZ,
            AtomicBoolean cancelled
    ) {
    }
}
