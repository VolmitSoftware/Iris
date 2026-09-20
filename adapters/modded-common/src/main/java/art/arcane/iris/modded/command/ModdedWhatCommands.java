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
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeWorldInspection;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeEditPlayer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeEditWorld;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.nativelib.terrain.NativeBlockPoint;
import art.arcane.iris.modded.ModdedEngineBootstrap;
import art.arcane.iris.modded.ModdedScheduler;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.iris.generation.context.IrisContext;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.volmlib.util.matter.MatterMarker;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandSource;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandRegistration;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandText;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

public final class ModdedWhatCommands {
    private static final Predicate<NativeCommandSource> GATE =
            NativeCommandRegistration.GAMEMASTERS;
    private static final SuggestionProvider<NativeCommandSource> MARKER_TYPES =
            (CommandContext<NativeCommandSource> context, SuggestionsBuilder builder) ->
                    NativeCommandRegistration.suggest(
                            List.of("cave_floor", "cave_ceiling", "object"), builder);
    private static final NativeEditPlayer.Dust MARKER_DUST = new NativeEditPlayer.Dust(0x5A8CFF, 1.2F);
    private static final NativeEditPlayer.ParticleSpread MARKER_SPREAD = new NativeEditPlayer.ParticleSpread(3, 0.2D, 0.2D, 0.2D, 0.0D);
    private static final int MAX_MARKERS = 8_192;
    private static final int MARKER_BATCH_SIZE = 128;
    private static final ConcurrentHashMap<UUID, MarkerRun> ACTIVE_MARKER_RUNS = new ConcurrentHashMap<>();

    private ModdedWhatCommands() {
    }

    public static LiteralArgumentBuilder<NativeCommandSource> tree() {
        return NativeCommandRegistration.literal("what").requires(GATE)
                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) ->
                        inspectHere(context.getSource())))
                .then(NativeCommandRegistration.literal("here")
                        .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) ->
                                inspectHere(context.getSource()))))
                .then(NativeCommandRegistration.literal("biome")
                        .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) ->
                                inspectBiome(context.getSource()))))
                .then(NativeCommandRegistration.literal("region")
                        .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) ->
                                inspectRegion(context.getSource()))))
                .then(NativeCommandRegistration.literal("block")
                        .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) ->
                                inspectBlock(context.getSource()))))
                .then(NativeCommandRegistration.literal("hand")
                        .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) ->
                                inspectHand(context.getSource()))))
                .then(NativeCommandRegistration.literal("markers")
                        .then(NativeCommandRegistration.argument("marker", StringArgumentType.greedyString())
                                .suggests(MARKER_TYPES)
                                .executes(ModdedCommandTree.localized((CommandContext<NativeCommandSource> context) ->
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

    private static int inspectHere(NativeCommandSource source) {
        NativeEditPlayer player = source.editingPlayer();
        if (player == null) {
            return playerRequired(source, ModdedCommandMessages.IRIS_MODDED_COMMANDS_THIS_COMMAND_CAN_ONLY_BE_USED_BY_PLAYERS_2);
        }
        NativeWorld level = source.world();
        Engine engine = IrisModdedCommands.engineFor(level);
        if (engine == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(
                    ModdedCommandMessages.IRIS_MODDED_COMMANDS_THIS_DIMENSION_IS_NOT_GENERATED_BY_IRIS_6));
            return 0;
        }
        NativeBlockPoint pos = player.blockPosition();
        int result = inspectBiome(source);
        result &= inspectRegion(source);
        int relativeY = pos.y() - engine.getMinHeight();
        try {
            IrisBiome cave = engine.getCaveOrMantleBiome(pos.x(), relativeY, pos.z());
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
        int surfaceY = new NativeWorldInspection(level).surfaceY(pos.x(), pos.z());
        NativeBlockState surface = level.getBlock(pos.x(), surfaceY, pos.z());
        IrisModdedCommands.ok(source, IrisLanguage.plain(
                ModdedCommandMessages.IRIS_MODDED_COMMANDS_SURFACE_BLOCK_Y,
                MessageArgument.untrusted("value", surface.materialKey()),
                MessageArgument.trusted("value2", surfaceY)));
        sendPosition(source, pos);
        return result;
    }

    private static int inspectBiome(NativeCommandSource source) {
        NativeEditPlayer player = source.editingPlayer();
        if (player == null) {
            return playerRequired(source, ModdedCommandMessages.IRIS_MODDED_COMMANDS_THIS_COMMAND_CAN_ONLY_BE_USED_BY_PLAYERS_2);
        }
        NativeWorld level = source.world();
        NativeBlockPoint pos = player.blockPosition();
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
                    pos.x(), pos.y() - engine.getMinHeight(), pos.z());
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
                        source.world().name(), error);
            }
            IrisModdedCommands.fail(source, error.getMessage());
            return 0;
        } catch (Throwable error) {
            logLookupFailure(source, "biome", error,
                    ModdedCommandMessages.IRIS_MODDED_COMMANDS_BIOME_LOOKUP_FAILED_2);
            return 0;
        }
    }

    private static int inspectRegion(NativeCommandSource source) {
        NativeEditPlayer player = source.editingPlayer();
        if (player == null) {
            return playerRequired(source, ModdedCommandMessages.IRIS_MODDED_COMMANDS_THIS_COMMAND_CAN_ONLY_BE_USED_BY_PLAYERS_2);
        }
        Engine engine = IrisModdedCommands.engineFor(source.world());
        if (engine == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(
                    ModdedCommandMessages.IRIS_MODDED_COMMANDS_THIS_DIMENSION_IS_NOT_GENERATED_BY_IRIS_6));
            return 0;
        }
        NativeBlockPoint pos = player.blockPosition();
        int centerX = (pos.x() & ~15) + 8;
        int centerZ = (pos.z() & ~15) + 8;
        try {
            IrisRegion region = engine.getRegion(
                    centerX, pos.y() - engine.getMinHeight(), centerZ);
            IrisModdedCommands.ok(source, IrisLanguage.plain(
                    RuntimeUiMessages.WHAT_IRIS_REGION,
                    MessageArgument.untrusted("region", region.getLoadKey()),
                    MessageArgument.untrusted("name", region.getName())));
            return 1;
        } catch (SavedBiomeUnavailableException error) {
            if (error.getCause() != null) {
                ModdedIrisLog.error("Iris saved biome lookup failed in {}",
                        source.world().name(), error);
            }
            IrisModdedCommands.fail(source, error.getMessage());
            return 0;
        } catch (Throwable error) {
            logLookupFailure(source, "region", error,
                    ModdedCommandMessages.IRIS_MODDED_COMMANDS_REGION_LOOKUP_FAILED_2);
            return 0;
        }
    }

    private static int inspectHand(NativeCommandSource source) {
        NativeEditPlayer player = source.editingPlayer();
        if (player == null) {
            return playerRequired(source, ModdedCommandMessages.IRIS_MODDED_COMMANDS_THIS_COMMAND_CAN_ONLY_BE_USED_BY_PLAYERS_IT_INSPECTS);
        }
        NativeWorldInspection.HeldItem stack = NativeWorldInspection.heldItem(source.player());
        if (stack == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(
                    ModdedCommandMessages.IRIS_MODDED_COMMANDS_YOUR_MAIN_HAND_IS_EMPTY));
            return 0;
        }
        IrisModdedCommands.ok(source, IrisLanguage.plain(
                RuntimeUiMessages.WHAT_MATERIAL,
                MessageArgument.untrusted("material", stack.key())));
        if (stack.blockState() != null) {
            IrisModdedCommands.ok(source, IrisLanguage.plain(
                    RuntimeUiMessages.WHAT_FULL_STATE,
                    MessageArgument.untrusted(
                            "state", stack.blockState())));
        }
        IrisModdedCommands.ok(source, IrisLanguage.plain(
                RuntimeUiMessages.WHAT_ITEM_COUNT,
                MessageArgument.trusted("count", stack.count())));
        return 1;
    }

    private static int inspectBlock(NativeCommandSource source) {
        NativeEditPlayer player = source.editingPlayer();
        if (player == null) {
            return playerRequired(source, ModdedCommandMessages.IRIS_MODDED_COMMANDS_THIS_COMMAND_CAN_ONLY_BE_USED_BY_PLAYERS_IT_INSPECTS_2);
        }
        NativeBlockPoint pos = player.pickBlock(128.0D);
        if (pos == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(
                    ModdedCommandMessages.IRIS_MODDED_COMMANDS_LOOK_AT_BLOCK_NOT_SKY));
            return 0;
        }
        NativeWorld level = source.world();
        NativeBlockState platform = level.getBlock(pos.x(), pos.y(), pos.z());
        IrisModdedCommands.ok(source, IrisLanguage.plain(
                RuntimeUiMessages.WHAT_MATERIAL,
                MessageArgument.untrusted("material", platform.materialKey())));
        IrisModdedCommands.ok(source, IrisLanguage.plain(
                RuntimeUiMessages.WHAT_FULL_STATE,
                MessageArgument.untrusted("state", platform.key())));
        sendPosition(source, pos);
        sendProperties(source, platform);
        sendObject(source, level, pos);
        sendBlockEntity(source, level, pos);
        return 1;
    }

    private static void sendPosition(NativeCommandSource source, NativeBlockPoint pos) {
        IrisModdedCommands.ok(source, IrisLanguage.plain(
                RuntimeUiMessages.WHAT_POSITION,
                MessageArgument.trusted("x", pos.x()),
                MessageArgument.trusted("y", pos.y()),
                MessageArgument.trusted("z", pos.z()),
                MessageArgument.trusted("chunkX", pos.x() >> 4),
                MessageArgument.trusted("chunkZ", pos.z() >> 4)));
    }

    private static void sendProperties(NativeCommandSource source, NativeBlockState platform) {
        List<String> flags = propertyNames(platform);
        if (flags.isEmpty()) {
            IrisModdedCommands.ok(source, IrisLanguage.plain(IrisMessages.MODDED_PROPERTIES_NONE));
            return;
        }
        IrisModdedCommands.ok(source, IrisLanguage.plain(
                IrisMessages.MODDED_PROPERTIES,
                MessageArgument.untrusted("properties", String.join(", ", flags))));
    }

    static List<String> propertyNames(NativeBlockState platform) {
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

    private static void sendObject(NativeCommandSource source, NativeWorld level, NativeBlockPoint pos) {
        Engine engine = IrisModdedCommands.engineFor(level);
        if (engine == null) {
            return;
        }
        try {
            String object = engine.getObjectPlacementKey(
                    pos.x(), pos.y() - engine.getMinHeight(), pos.z());
            if (object != null) {
                IrisModdedCommands.ok(source, IrisLanguage.plain(
                        RuntimeUiMessages.WHAT_OBJECT,
                        MessageArgument.untrusted("object", object)));
            }
        } catch (Throwable error) {
            ModdedIrisLog.error("Iris object lookup failed for /iris what block at {}, {}, {}",
                    pos.x(), pos.y(), pos.z(), error);
        }
    }

    private static void sendBlockEntity(NativeCommandSource source,
                                        NativeWorld level, NativeBlockPoint pos) {
        NativeWorldInspection.BlockEntityDetails details = new NativeWorldInspection(level).blockEntity(pos);
        if (details == null) {
            return;
        }
        IrisModdedCommands.ok(source, IrisLanguage.plain(
                RuntimeUiMessages.WHAT_BLOCK_ENTITY, MessageArgument.untrusted("type", details.type())));
        if (details.lootTable() != null) {
            IrisModdedCommands.ok(source, IrisLanguage.plain(
                    RuntimeUiMessages.WHAT_LOOT_TABLE, MessageArgument.untrusted("loot", details.lootTable())));
        }
        if (details.spawnedEntity() != null) {
            IrisModdedCommands.ok(source, IrisLanguage.plain(
                    RuntimeUiMessages.WHAT_SPAWNER_ENTITY, MessageArgument.untrusted("entity", details.spawnedEntity())));
        }
    }

    private static int inspectMarkers(NativeCommandSource source, String markerRaw) {
        NativeEditPlayer player = source.editingPlayer();
        if (player == null) {
            return playerRequired(source, ModdedCommandMessages.IRIS_MODDED_COMMANDS_THIS_COMMAND_CAN_ONLY_BE_USED_BY_PLAYERS_MARKERS_RENDER);
        }
        NativeWorld level = source.world();
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
        NativeBlockPoint origin = player.blockPosition();
        MarkerRun run = new MarkerRun(
                player.id(), player, level, engine, marker,
                origin.x() >> 4, origin.z() >> 4, new AtomicBoolean());
        MarkerRun previous = ACTIVE_MARKER_RUNS.put(player.id(), run);
        if (previous != null) {
            previous.cancelled().set(true);
        }
        IrisModdedCommands.ok(source, IrisLanguage.plain(
                ModdedCommandMessages.IRIS_MODDED_COMMANDS_SCANNING_MARKERS_AROUND_YOU,
                MessageArgument.untrusted("marker", marker)));
        scheduler.async(() -> scanMarkers(source, scheduler, run));
        return 1;
    }

    private static void scanMarkers(NativeCommandSource source,
                                    ModdedScheduler scheduler, MarkerRun run) {
        List<NativeBlockPoint> hits = new ArrayList<>();
        MatterMarker marker = new MatterMarker(run.marker());
        try (GenerationSessionLease lease = run.engine().acquireGenerationLease("modded_what_markers");
             IrisContext.Scope ignored = IrisContext.open(run.engine(), lease.sessionId(), null)) {
            for (int chunkX = run.chunkX() - 4; chunkX <= run.chunkX() + 4; chunkX++) {
                for (int chunkZ = run.chunkZ() - 4; chunkZ <= run.chunkZ() + 4; chunkZ++) {
                    if (run.cancelled().get()) {
                        return;
                    }
                    for (IrisPosition position : run.engine().getMantle().findMarkers(chunkX, chunkZ, marker)) {
                        hits.add(new NativeBlockPoint(position.getX(), position.getY(), position.getZ()));
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

    private static void renderMarkerBatch(NativeCommandSource source,
                                          ModdedScheduler scheduler, MarkerRun run,
                                          List<NativeBlockPoint> hits, int from) {
        if (!active(run)) {
            // Drop the registry entry on abort too, or the run record pins the player, level
            // and engine until server stop. No-op if a newer run already replaced it.
            ACTIVE_MARKER_RUNS.remove(run.playerId(), run);
            return;
        }
        int to = Math.min(hits.size(), from + MARKER_BATCH_SIZE);
        for (int index = from; index < to; index++) {
            NativeBlockPoint hit = hits.get(index);
            run.player().dust(MARKER_DUST, hit.x() + 0.5D, hit.y() + 1.0D, hit.z() + 0.5D, MARKER_SPREAD);
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
                && run.player().activeIn(new NativeEditWorld(run.level()))
                && !run.engine().isClosing()
                && !run.engine().isClosed();
    }

    private static void markerFailure(NativeCommandSource source,
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

    private static NativeBiome nativeBiome(NativeWorld level, NativeBlockPoint pos) {
        NativeEditWorld.BiomeIdentity identity = new NativeEditWorld(level).biome(pos);
        String key = identity.key() == null ? IrisLanguage.plain(RuntimeUiMessages.STATUS_UNREGISTERED) : identity.key();
        return new NativeBiome(key, identity.id());
    }

    private static int playerRequired(NativeCommandSource source,
                                      TextKey message) {
        IrisModdedCommands.fail(source, IrisLanguage.plain(message));
        return 0;
    }

    private static void logLookupFailure(NativeCommandSource source,
                                         String operation, Throwable error,
                                         TextKey message) {
        ModdedIrisLog.error("Iris /what {} lookup failed in {}", operation,
                source.world().name(), error);
        IrisModdedCommands.fail(source, IrisLanguage.plain(
                message,
                MessageArgument.untrusted("value", error.getClass().getSimpleName())));
    }

    private record NativeBiome(String key, int id) {
    }

    private record MarkerRun(
            UUID playerId,
            NativeEditPlayer player,
            NativeWorld level,
            Engine engine,
            String marker,
            int chunkX,
            int chunkZ,
            AtomicBoolean cancelled
    ) {
    }
}
