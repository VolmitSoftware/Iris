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

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeProtocolPlayer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeStructureQueries;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.nativelib.terrain.NativeBlockPoint;
import art.arcane.iris.modded.ModdedIrisLog;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.modded.localization.ModdedCommandMessages;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.runtime.GenerationSessionException;
import art.arcane.iris.generation.runtime.GenerationSessionLease;
import art.arcane.iris.structure.placement.IrisStructureLocator;
import art.arcane.iris.generation.locator.Locator;
import art.arcane.iris.structure.nativegen.NativeStructureGenerationPolicy;
import art.arcane.iris.structure.placement.StructureReachability;
import art.arcane.iris.generation.runtime.WrongEngineBroException;
import art.arcane.iris.world.history.GenerationSemanticQueries;
import art.arcane.iris.world.history.GenerationFindCatalog;
import art.arcane.iris.generation.hydrology.HydrologyFeatureQuery;
import art.arcane.iris.generation.hydrology.runtime.IrisHydrologyRuntime;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.structure.nativegen.IrisNativeStructureDecision;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.structure.nativegen.NativeStructureGenerationStatus;
import art.arcane.iris.generation.context.IrisContext;
import art.arcane.iris.generation.concurrent.MultiBurst;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.math.Position2;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandSource;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

final class ModdedLocateCommands {
    private static final long LOCATE_TIMEOUT_MS = 120000L;
    private static final int NATIVE_STRUCTURE_LOCATE_RADIUS = 100;
    private static final ConcurrentHashMap<UUID, CompletableFuture<Position2>> ACTIVE_LOCATE_REQUESTS = new ConcurrentHashMap<>();

    private ModdedLocateCommands() {
    }

    static int gotoBiome(NativeCommandSource source, String key) {
        NativeProtocolPlayer player = source.player();
        if (player == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_THIS_COMMAND_CAN_ONLY_BE_USED_BY_PLAYERS_3));
            return 0;
        }
        NativeWorld level = source.world();
        Engine engine = IrisModdedCommands.engineFor(level);
        if (engine == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_THIS_DIMENSION_IS_NOT_GENERATED_BY_IRIS_8));
            return 0;
        }
        IrisBiome biome = GenerationFindCatalog.biome(engine, key);
        if (biome == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_UNKNOWN_BIOME_2, MessageArgument.untrusted("key", key)));
            return 0;
        }
        locate(source, level, engine, player, Locator.surfaceBiome(biome.getLoadKey()), "biome " + biome.getLoadKey());
        return 1;
    }

    static int gotoRegion(NativeCommandSource source, String key) {
        NativeProtocolPlayer player = source.player();
        if (player == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_THIS_COMMAND_CAN_ONLY_BE_USED_BY_PLAYERS_4));
            return 0;
        }
        NativeWorld level = source.world();
        Engine engine = IrisModdedCommands.engineFor(level);
        if (engine == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_THIS_DIMENSION_IS_NOT_GENERATED_BY_IRIS_9));
            return 0;
        }
        IrisRegion region = GenerationFindCatalog.region(engine, key);
        if (region == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_UNKNOWN_REGION_2, MessageArgument.untrusted("key", key)));
            return 0;
        }
        locate(source, level, engine, player, Locator.region(region.getLoadKey()), "region " + region.getLoadKey());
        return 1;
    }

    static int gotoObject(NativeCommandSource source, String keyRaw) {
        NativeWorld level = source.world();
        Engine engine = IrisModdedCommands.engineFor(level);
        if (engine == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_THIS_DIMENSION_IS_NOT_GENERATED_BY_IRIS_10));
            return 0;
        }
        String key = keyRaw.trim();
        if (!GenerationFindCatalog.hasObjectPlacement(engine, key)) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_IS_NOT_CONFIGURED_ANY_REGION_BIOME_OBJECT_PLACEMENTS_OBJECT_KEYS, MessageArgument.untrusted("key", key), MessageArgument.untrusted("value", engine.getData().getObjectLoader().getPossibleKeys().length)));
            return 0;
        }
        NativeProtocolPlayer player = source.player();
        if (player == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_THIS_COMMAND_CAN_ONLY_BE_USED_BY_PLAYERS_OBJECT_KEY, MessageArgument.untrusted("key", key), MessageArgument.untrusted("value", engine.getData().getObjectLoader().getPossibleKeys().length)));
            return 0;
        }
        locate(source, level, engine, player, Locator.object(key), "object " + key);
        return 1;
    }

    static int gotoRiver(NativeCommandSource source, String type) {
        NativeProtocolPlayer player = source.player();
        if (player == null) {
            IrisModdedCommands.fail(source, "This command can only be used by a player.");
            return 0;
        }
        NativeWorld level = source.world();
        Engine engine = IrisModdedCommands.engineFor(level);
        if (engine == null) {
            IrisModdedCommands.fail(source, "This dimension is not generated by Iris.");
            return 0;
        }
        IrisHydrologyRuntime runtime = engine.getComplex().getHydrologyRuntime();
        HydrologyFeatureQuery query;
        try {
            query = HydrologyFeatureQuery.parse(type);
        } catch (IllegalArgumentException error) {
            IrisModdedCommands.fail(source, error.getMessage());
            return 0;
        }
        int originX = player.blockX();
        int originZ = player.blockZ();
        int requestedDistance = runtime == null
                ? 8192
                : Math.min(8192, runtime.settings().routing().tileSize() * 15);
        int maximumDistance = runtime == null
                ? requestedDistance
                : runtime.maximumFeatureSearchDistance(originX, originZ, requestedDistance);
        NativeModdedServer server = source.server();
        IrisModdedCommands.ok(source, "Searching accepted hydrology plans for " + type + "...");
        MultiBurst.burst.completeValueAsync(() -> GenerationSemanticQueries.nearestRiver(
                        engine, query, originX, originZ, maximumDistance,
                        (int visited) -> server.execute(() -> IrisModdedCommands.ok(source,
                                "Searched " + visited + " hydrology tiles for " + type + "..."))).orElse(null))
                .whenComplete((GenerationSemanticQueries.RiverResult feature, Throwable error) -> server.execute(() -> {
                    if (error != null) {
                        ModdedIrisLog.error("Hydrology locate failed for {}", type, error);
                        IrisModdedCommands.fail(source,
                                "Could not locate " + type + " hydrology: " + error.getClass().getSimpleName());
                        return;
                    }
                    if (feature == null) {
                        IrisModdedCommands.fail(source,
                                "No accepted " + type + " hydrology feature found within "
                                        + maximumDistance + " blocks.");
                        return;
                    }
                    teleportToStructure(
                            source,
                            level,
                            player,
                            feature.x(),
                            feature.y() + 2,
                            feature.z(),
                            type + " hydrology feature"
                    );
                }));
        return 1;
    }

    static int gotoStructure(NativeCommandSource source, String keyRaw) {
        NativeWorld level = source.world();
        Engine engine = IrisModdedCommands.engineFor(level);
        if (engine == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_THIS_DIMENSION_IS_NOT_GENERATED_BY_IRIS_11));
            return 0;
        }
        String key = keyRaw.trim();
        if (key.isEmpty()) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_NAME_IRIS_NATIVE_STRUCTURE_LOCATE));
            return 0;
        }
        NativeProtocolPlayer player = source.player();
        if (player == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_THIS_COMMAND_CAN_ONLY_BE_USED_BY_PLAYERS_5));
            return 0;
        }
        Optional<NativeStructureTarget> resolved = resolveNativeStructure(source, level, engine, key);
        if (resolved.isEmpty()) {
            if ((!IrisStructureLocator.hasNativePlacement(engine, key)
                    && IrisStructureLocator.hasLocatableEditablePlacement(engine, key))
                    || GenerationFindCatalog.hasRetainedStructurePlacement(engine, key)) {
                locateIrisStructure(source, level, engine, player, key);
                return 1;
            }
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_UNKNOWN_STRUCTURE_USE_TAB_COMPLETION_CHOOSE_IRIS_PLACEMENT_REGISTERED_NATIVE, MessageArgument.untrusted("key", key)));
            return 0;
        }
        NativeStructureTarget target = resolved.get();
        IrisNativeStructureDecision decision = NativeStructureGenerationPolicy.resolve(engine, target.key(), false);
        boolean nativeGenerationEnabled =
                source.server().generateStructures();
        boolean nativePlacement = IrisStructureLocator.hasNativePlacement(engine, target.key());
        boolean locatableNativePlacement = nativePlacement
                && IrisStructureLocator.hasLocatableNativePlacement(engine, target.key());
        boolean locatableEditableReplacement = !nativePlacement
                && decision.status() == NativeStructureGenerationStatus.REPLACED_BY_IRIS
                && IrisStructureLocator.hasLocatableEditablePlacement(engine, target.key());
        boolean reachable = StructureReachability.isReachable(engine, target.key());
        if (!ModdedCommandSuggestions.isEligibleRegisteredStructure(
                decision, nativePlacement, locatableNativePlacement, locatableEditableReplacement,
                reachable, nativeGenerationEnabled)) {
            if (GenerationFindCatalog.hasRetainedStructurePlacement(engine, key)) {
                locateIrisStructure(source, level, engine, player, key);
                return 1;
            }
            IrisModdedCommands.fail(source, registeredStructureUnavailableMessage(
                    target.key(), target.availability(), decision,
                    nativePlacement, locatableNativePlacement,
                    nativeGenerationEnabled, reachable));
            return 0;
        }
        if (locatableEditableReplacement) {
            locateIrisStructure(source, level, engine, player, target.key());
            return 1;
        }
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_SEARCHING_NATIVE_STRUCTURE_WITHIN_CHUNKS, MessageArgument.untrusted("value", target.key()), MessageArgument.untrusted("NATIVESTRUCTURELOCATERADIUS", NATIVE_STRUCTURE_LOCATE_RADIUS)));
        runNativeStructureLocate(source, level, player, target);
        return 1;
    }

    static String registeredStructureUnavailableMessage(
            String key, NativeStructureAvailability nativeAvailability,
            IrisNativeStructureDecision decision,
            boolean nativePlacement, boolean locatableNativePlacement,
            boolean nativeGenerationEnabled, boolean reachable) {
        if (!decision.generate()
                && decision.status() != NativeStructureGenerationStatus.REPLACED_BY_IRIS) {
            return NativeStructureGenerationPolicy.generationStatusMessage(key, decision.status());
        }
        if (decision.status() == NativeStructureGenerationStatus.REPLACED_BY_IRIS) {
            if (nativePlacement && !nativeGenerationEnabled) {
                return withInactiveNativePlacement(
                        nativeUnavailableMessage(key, NativeStructureAvailability.WORLD_DISABLED),
                        nativePlacement, locatableNativePlacement);
            }
            return "Iris replacement " + key
                    + " is configured, but every matching placement has non-positive density or a Y band "
                    + "outside this world's height range.";
        }
        if (!nativeGenerationEnabled) {
            return withInactiveNativePlacement(
                    nativeUnavailableMessage(key, NativeStructureAvailability.WORLD_DISABLED),
                    nativePlacement, locatableNativePlacement);
        }
        NativeStructureAvailability availability = nativeAvailability;
        if (availability == NativeStructureAvailability.AVAILABLE && !reachable) {
            availability = NativeStructureAvailability.NO_PLACEMENT;
        }
        return withInactiveNativePlacement(
                nativeUnavailableMessage(key, availability), nativePlacement, locatableNativePlacement);
    }

    private static String withInactiveNativePlacement(
            String reason, boolean nativePlacement, boolean locatableNativePlacement) {
        if (!nativePlacement || locatableNativePlacement) {
            return reason;
        }
        return reason + " A matching Iris nativeStructures placement is also configured, but has non-positive "
                + "density or a Y band outside this world's height range.";
    }

    private static void locateIrisStructure(NativeCommandSource source, NativeWorld level, Engine engine,
                                            NativeProtocolPlayer player, String key) {
        NativeModdedServer server = source.server();
        int blockX = player.blockX();
        int blockZ = player.blockZ();
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_SEARCHING_IRIS_PLACED_STRUCTURE, MessageArgument.untrusted("key", key)));
        Thread thread = new Thread(() -> {
            try {
                IrisStructureLocator.LocateResult result =
                        GenerationSemanticQueries.nearestStructure(engine, key, blockX, blockZ, 1024);
                if (result.status() == IrisStructureLocator.LocateStatus.SEARCH_LIMIT_REACHED) {
                    server.execute(() -> IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_UNABLE_LOCATE_IRIS_PLACED_STRUCTURE_DENSITY_SEARCH_SAFETY_LIMIT_WAS, MessageArgument.untrusted("key", key))));
                    return;
                }
                if (!result.found()) {
                    server.execute(() -> IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_COULD_NOT_FIND_IRIS_PLACED_STRUCTURE_WITHIN_1024_CHUNKS, MessageArgument.untrusted("key", key))));
                    return;
                }
                int targetX = result.originX();
                int targetY = result.baseY() + 2;
                int targetZ = result.originZ();
                server.execute(() -> teleportToStructure(source, level, player, targetX, targetY, targetZ,
                        "Iris-placed structure " + key));
            } catch (Throwable e) {
                ModdedIrisLog.error("Iris structure locate failed for {}", key, e);
                server.execute(() -> IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_SEARCH_FAILED, MessageArgument.untrusted("value", e.getClass().getSimpleName()))));
            }
        }, "Iris Structure Locator");
        thread.setDaemon(true);
        thread.start();
    }

    private static void runNativeStructureLocate(NativeCommandSource source, NativeWorld level,
                                                 NativeProtocolPlayer player, NativeStructureTarget target) {
        NativeModdedServer server = source.server();
        Runnable locateTask = () -> locateNativeStructure(source, level, player, target);
        if (server.isServerThread()) {
            locateTask.run();
            return;
        }
        server.execute(locateTask);
    }

    private static void locateNativeStructure(NativeCommandSource source, NativeWorld level,
                                              NativeProtocolPlayer player, NativeStructureTarget target) {
        try {
            NativeStructureQueries queries = new NativeStructureQueries(level);
            NativeBlockPoint found = queries.locate(target.holder(),
                    new NativeBlockPoint(player.blockX(), player.blockY(), player.blockZ()), NATIVE_STRUCTURE_LOCATE_RADIUS);
            if (found == null) {
                IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_COULD_NOT_FIND_NATIVE_STRUCTURE_WITHIN_CHUNKS, MessageArgument.untrusted("value", target.key()), MessageArgument.untrusted("NATIVESTRUCTURELOCATERADIUS", NATIVE_STRUCTURE_LOCATE_RADIUS)));
                return;
            }
            int targetX = found.x();
            int targetZ = found.z();
            int surfaceY = queries.surfaceY(targetX, targetZ) + 1;
            int targetY = Math.max(level.minHeight() + 1, Math.min(level.maxHeight() - 1, surfaceY));
            teleportToStructure(source, level, player, targetX, targetY, targetZ,
                    "native structure " + target.key());
        } catch (Throwable e) {
            ModdedIrisLog.error("Native structure locate failed for {}", target.key(), e);
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_SEARCH_NATIVE_STRUCTURE_FAILED, MessageArgument.untrusted("value", target.key()), MessageArgument.untrusted("value2", e.getClass().getSimpleName())));
        }
    }

    private static void teleportToStructure(NativeCommandSource source, NativeWorld level, NativeProtocolPlayer player,
                                            int targetX, int targetY, int targetZ, String label) {
        if (!player.connected()) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_PLAYER_DISCONNECTED_BEFORE_STRUCTURE_SEARCH_COMPLETED));
            return;
        }
        if (!player.inWorld(level)) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_YOU_CHANGED_DIMENSIONS_BEFORE_STRUCTURE_SEARCH_COMPLETED_RUN_COMMAND_AGAIN));
            return;
        }
        new NativeStructureQueries(level).loadChunk(targetX >> 4, targetZ >> 4);
        int clampedY = Math.max(level.minHeight() + 1, Math.min(level.maxHeight() - 1, targetY));
        boolean teleported = player.teleport(level, targetX + 0.5D, clampedY, targetZ + 0.5D);
        if (!teleported) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_FOUND_AT_BUT_TELEPORTATION_FAILED, MessageArgument.untrusted("label", label), MessageArgument.untrusted("targetX", targetX), MessageArgument.untrusted("clampedY", clampedY), MessageArgument.untrusted("targetZ", targetZ)));
            return;
        }
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_TELEPORTED_AT, MessageArgument.untrusted("label", label), MessageArgument.untrusted("targetX", targetX), MessageArgument.untrusted("clampedY", clampedY), MessageArgument.untrusted("targetZ", targetZ)));
    }

    static int verifyStructures(NativeCommandSource source, String keyRaw) {
        NativeWorld level = source.world();
        Engine engine = IrisModdedCommands.engineFor(level);
        if (engine == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_THIS_DIMENSION_IS_NOT_GENERATED_BY_IRIS_12));
            return 0;
        }
        String key = keyRaw == null ? "" : keyRaw.trim();
        if (!key.isEmpty()) {
            return verifyStructure(source, level, engine, key);
        }
        NativeStructureQueries registry = new NativeStructureQueries(level);
        int available = 0;
        int disabled = 0;
        int suppressed = 0;
        int unreachableBiomes = 0;
        int unsupported = 0;
        for (String identifier : registry.keys()) {
            Optional<NativeStructureQueries.Reference> holder = registry.resolve(identifier);
            if (holder.isEmpty()) {
                continue;
            }
            NativeStructureAvailability availability = nativeAvailability(source, level, engine,
                    identifier, holder.get());
            switch (availability) {
                case AVAILABLE -> available++;
                case WORLD_DISABLED, FILTERED -> disabled++;
                case IRIS_SUPPRESSED -> suppressed++;
                case EMPTY_BIOME_FILTER, BIOME_UNREACHABLE -> unreachableBiomes++;
                case NO_PLACEMENT -> unsupported++;
            }
        }
        int irisPlaced = IrisStructureLocator.placedKeys(engine).size();
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_STRUCTURE_REACHABILITY_NATIVE_GENERATION_ELIGIBLE_IRIS_PLACED_NATIVE_DISABLED_NATIVE, MessageArgument.untrusted("available", available), MessageArgument.untrusted("irisPlaced", irisPlaced), MessageArgument.untrusted("disabled", disabled), MessageArgument.untrusted("suppressed", suppressed), MessageArgument.untrusted("unreachableBiomes", unreachableBiomes), MessageArgument.untrusted("unsupported", unsupported)));
        return 1;
    }

    private static int verifyStructure(NativeCommandSource source, NativeWorld level, Engine engine, String key) {
        Optional<NativeStructureTarget> target = resolveNativeStructure(source, level, engine, key);
        if (target.isEmpty()) {
            if (IrisStructureLocator.isPlaced(engine, key)) {
                IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_STRUCTURE_IS_IRIS_PLACED_LOCATABLE_WITH_IRIS_GOTO_STRUCTURE, MessageArgument.untrusted("key", key), MessageArgument.untrusted("key2", key)));
                return 1;
            }
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_UNKNOWN_STRUCTURE_IT_IS_NEITHER_IRIS_PLACED_NOR_REGISTERED_BY, MessageArgument.untrusted("key", key)));
            return 0;
        }
        NativeStructureTarget resolved = target.get();
        if (resolved.availability() == NativeStructureAvailability.IRIS_SUPPRESSED) {
            IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_STRUCTURE_IS_EXPLICITLY_REPLACED_BY_IRIS_PLACEMENT_LOCATABLE_WITH_IRIS, MessageArgument.untrusted("value", resolved.key()), MessageArgument.untrusted("value2", resolved.key())));
            return 1;
        }
        if (resolved.availability() != NativeStructureAvailability.AVAILABLE) {
            IrisModdedCommands.fail(source, nativeUnavailableMessage(resolved.key(), resolved.availability()));
            return 0;
        }
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_NATIVE_STRUCTURE_IS_ENABLED_SUPPORTED_BY_THIS_DIMENSION_S_GENERATOR, MessageArgument.untrusted("value", resolved.key()), MessageArgument.untrusted("value2", resolved.key())));
        return 1;
    }

    private static Optional<NativeStructureTarget> resolveNativeStructure(NativeCommandSource source,
                                                                           NativeWorld level,
                                                                           Engine engine,
                                                                           String keyRaw) {
        NativeStructureQueries registry = new NativeStructureQueries(level);
        Optional<NativeStructureQueries.Reference> holder = registry.resolve(keyRaw);
        if (holder.isEmpty()) {
            return Optional.empty();
        }
        String key = holder.get().key();
        NativeStructureAvailability availability = nativeAvailability(source, level, engine, key, holder.get());
        return Optional.of(new NativeStructureTarget(key, holder.get(), availability));
    }

    static NativeStructureAvailability nativeAvailability(NativeCommandSource source, NativeWorld level,
                                                           Engine engine, String key,
                                                           NativeStructureQueries.Reference holder) {
        boolean worldEnabled = source.server().generateStructures();
        IrisNativeStructureDecision decision = NativeStructureGenerationPolicy.resolve(engine, key, false);
        boolean selected = decision.status() != NativeStructureGenerationStatus.DISABLED_BY_PACK;
        boolean suppressed = decision.status() == NativeStructureGenerationStatus.REPLACED_BY_IRIS;
        boolean biomeFilterEmpty = holder.emptyBiomeFilter();
        NativeStructureQueries queries = new NativeStructureQueries(level);
        boolean biomeReachable = queries.biomeReachable(holder);
        boolean hasPlacement = false;
        if (worldEnabled && selected && !suppressed && !biomeFilterEmpty && biomeReachable) {
            hasPlacement = queries.hasPlacement(holder);
        }
        return classifyNativeAvailability(
                worldEnabled, selected, suppressed, biomeFilterEmpty, biomeReachable, hasPlacement);
    }

    static NativeStructureAvailability classifyNativeAvailability(boolean worldEnabled, boolean selected,
                                                                   boolean suppressed, boolean biomeFilterEmpty,
                                                                   boolean biomeReachable,
                                                                   boolean hasPlacement) {
        if (!worldEnabled) {
            return NativeStructureAvailability.WORLD_DISABLED;
        }
        if (!selected) {
            return NativeStructureAvailability.FILTERED;
        }
        if (suppressed) {
            return NativeStructureAvailability.IRIS_SUPPRESSED;
        }
        if (biomeFilterEmpty) {
            return NativeStructureAvailability.EMPTY_BIOME_FILTER;
        }
        if (!biomeReachable) {
            return NativeStructureAvailability.BIOME_UNREACHABLE;
        }
        if (!hasPlacement) {
            return NativeStructureAvailability.NO_PLACEMENT;
        }
        return NativeStructureAvailability.AVAILABLE;
    }

    static String nativeUnavailableMessage(String key, NativeStructureAvailability availability) {
        return switch (availability) {
            case WORLD_DISABLED -> "Native structure generation is disabled for this world, so " + key + " cannot generate or be located.";
            case FILTERED -> NativeStructureGenerationPolicy.generationStatusMessage(
                    key, NativeStructureGenerationStatus.DISABLED_BY_PACK);
            case IRIS_SUPPRESSED -> NativeStructureGenerationPolicy.generationStatusMessage(
                    key, NativeStructureGenerationStatus.REPLACED_BY_IRIS);
            case EMPTY_BIOME_FILTER -> "Native structure " + key
                    + " has a biome tag or filter that resolves to zero registered biomes.";
            case BIOME_UNREACHABLE -> "Native structure " + key + " cannot generate because none of its required biomes are produced by this Iris pack.";
            case NO_PLACEMENT -> "Native structure " + key
                    + " is registered and biome-compatible, but has no active positive-weight, "
                    + "positive-frequency structure-set placement in this dimension's generator state.";
            case AVAILABLE -> "Native structure " + key + " is available.";
        };
    }

    static int gotoPoi(NativeCommandSource source, String typeRaw) {
        NativeWorld level = source.world();
        Engine engine = IrisModdedCommands.engineFor(level);
        if (engine == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_THIS_DIMENSION_IS_NOT_GENERATED_BY_IRIS_13));
            return 0;
        }
        String type = typeRaw.trim();
        NativeProtocolPlayer player = source.player();
        if (player == null) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_THIS_COMMAND_CAN_ONLY_BE_USED_BY_PLAYERS_POI_TYPE, MessageArgument.untrusted("type", type)));
            return 0;
        }
        locate(source, level, engine, player, Locator.poi(type), "POI " + type);
        return 1;
    }

    private static void locate(NativeCommandSource source, NativeWorld level, Engine engine, NativeProtocolPlayer player, Locator<?> locator, String label) {
        NativeModdedServer server = source.server();
        int chunkX = player.blockX() >> 4;
        int chunkZ = player.blockZ() >> 4;
        IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_SEARCHING, MessageArgument.untrusted("label", label)));
        CompletableFuture<Position2> search;
        try {
            search = locator.find(engine, new Position2(chunkX, chunkZ), LOCATE_TIMEOUT_MS, (Integer checks) -> {
            });
        } catch (WrongEngineBroException e) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_ENGINE_THIS_WORLD_HAS_BEEN_CLOSED_REJOIN_DIMENSION_TRY_AGAIN));
            return;
        }
        UUID playerId = player.id();
        CompletableFuture<Position2> previous = ACTIVE_LOCATE_REQUESTS.put(playerId, search);
        if (previous != null && previous != search) {
            previous.cancel(true);
        }
        search.whenComplete((Position2 at, Throwable error) -> completeLocate(
                source, level, engine, player, label, server, playerId, search, at, error));
    }

    private static void completeLocate(NativeCommandSource source, NativeWorld level, Engine engine,
                                       NativeProtocolPlayer player, String label, NativeModdedServer server, UUID playerId,
                                       CompletableFuture<Position2> search, Position2 at, Throwable error) {
        if (ACTIVE_LOCATE_REQUESTS.get(playerId) != search) {
            return;
        }
        Throwable failure = unwrapCompletionFailure(error);
        if (failure instanceof CancellationException) {
            ACTIVE_LOCATE_REQUESTS.remove(playerId, search);
            return;
        }
        if (failure != null) {
            ModdedIrisLog.error("Iris locate failed for {}", label, failure);
            server.execute(() -> {
                if (ACTIVE_LOCATE_REQUESTS.remove(playerId, search)) {
                    IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_SEARCH_FAILED_2, MessageArgument.untrusted("failure", failure)));
                }
            });
            return;
        }
        if (at == null) {
            server.execute(() -> {
                if (ACTIVE_LOCATE_REQUESTS.remove(playerId, search)) {
                    IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_COULD_NOT_FIND_WITHIN_SEARCH_TIMEOUT, MessageArgument.untrusted("label", label)));
                }
            });
            return;
        }
        server.execute(() -> {
            if (ACTIVE_LOCATE_REQUESTS.remove(playerId, search)) {
                teleportToLocateResult(source, level, engine, player, label, at);
            }
        });
    }

    private static void teleportToLocateResult(NativeCommandSource source, NativeWorld level, Engine engine,
                                                NativeProtocolPlayer player, String label, Position2 at) {
        // Same liveness guards the structure completion path has: the search can take up to
        // two minutes, and the captured NativeProtocolPlayer may be gone or elsewhere by then.
        if (!player.connected()) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_PLAYER_DISCONNECTED_BEFORE_STRUCTURE_SEARCH_COMPLETED));
            return;
        }
        if (!player.inWorld(level)) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_YOU_CHANGED_DIMENSIONS_BEFORE_STRUCTURE_SEARCH_COMPLETED_RUN_COMMAND_AGAIN));
            return;
        }
        int blockX = (at.getX() << 4) + 8;
        int blockZ = (at.getZ() << 4) + 8;
        try (GenerationSessionLease lease = engine.acquireGenerationLease("modded_locator_teleport");
            IrisContext.Scope ignored = IrisContext.open(engine, lease.sessionId(), null)) {
            int blockY = engine.getMinHeight() + engine.getHeight(blockX, blockZ, false) + 2;
            boolean teleported = player.teleport(level, blockX + 0.5D, blockY, blockZ + 0.5D);
            if (!teleported) {
                IrisModdedCommands.fail(source, IrisLanguage.plain(
                        ModdedCommandMessages.IRIS_MODDED_COMMANDS_FOUND_AT_BUT_TELEPORTATION_FAILED,
                        MessageArgument.untrusted("label", label),
                        MessageArgument.trusted("targetX", blockX),
                        MessageArgument.trusted("clampedY", blockY),
                        MessageArgument.trusted("targetZ", blockZ)));
                return;
            }
            IrisModdedCommands.ok(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_TELEPORTED_AT_2, MessageArgument.untrusted("label", label), MessageArgument.untrusted("blockX", blockX), MessageArgument.untrusted("blockY", blockY), MessageArgument.untrusted("blockZ", blockZ)));
        } catch (GenerationSessionException e) {
            IrisModdedCommands.fail(source, IrisLanguage.plain(ModdedCommandMessages.IRIS_MODDED_COMMANDS_ENGINE_CHANGED_WHILE_LOCATING_TRY_AGAIN, MessageArgument.untrusted("label", label)));
        }
    }

    private static Throwable unwrapCompletionFailure(Throwable error) {
        Throwable failure = error;
        while ((failure instanceof CompletionException || failure instanceof ExecutionException)
                && failure.getCause() != null) {
            failure = failure.getCause();
        }
        return failure;
    }

    enum NativeStructureAvailability {
        AVAILABLE,
        WORLD_DISABLED,
        FILTERED,
        IRIS_SUPPRESSED,
        EMPTY_BIOME_FILTER,
        BIOME_UNREACHABLE,
        NO_PLACEMENT
    }

    private record NativeStructureTarget(String key, NativeStructureQueries.Reference holder,
                                         NativeStructureAvailability availability) {
    }
}
