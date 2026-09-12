/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.command;

import art.arcane.iris.Iris;
import art.arcane.iris.localization.BukkitCommandMessages;
import art.arcane.iris.localization.BukkitCommandMessagesExtended;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.pack.datapack.DatapackIngestService;
import art.arcane.iris.studio.object.ObjectStudioSaveService;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.structure.placement.IrisStructureLocator;
import art.arcane.iris.structure.nativegen.NativeStructureGenerationPolicy;
import art.arcane.iris.structure.placement.StructureReachability;
import art.arcane.iris.world.history.GenerationSemanticQueries;
import art.arcane.iris.generation.hydrology.HydrologyFeatureQuery;
import art.arcane.iris.generation.hydrology.runtime.IrisHydrologyRuntime;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.structure.nativegen.IrisNativeStructureDecision;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.structure.nativegen.NativeStructureGenerationStatus;
import art.arcane.iris.platform.generation.EngineBukkitOps;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformStructureHooks;
import art.arcane.iris.command.specialhandlers.HydrologyTypeHandler;
import art.arcane.iris.world.history.GenerationFindCatalog;
import art.arcane.iris.command.specialhandlers.LocatableObjectHandler;
import art.arcane.iris.command.specialhandlers.ReachableBiomeHandler;
import art.arcane.iris.command.specialhandlers.ReachableRegionHandler;
import art.arcane.iris.command.specialhandlers.StructureHandler;
import art.arcane.iris.localization.C;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.DirectorOrigin;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.volmlib.util.localization.MessageArgument;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.generator.structure.Structure;
import org.bukkit.util.StructureSearchResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Director(name = "find", origin = DirectorOrigin.PLAYER, description = "Iris Find commands", descriptionKey = "iris.director.commandfind.director.iris_find_commands", aliases = "goto")
public class CommandFind implements DirectorExecutor {
    @Director(description = "Find a biome", descriptionKey = "iris.director.commandfind.director.find_biome")
    public void biome(
            @Param(description = "The biome to look for", descriptionKey = "iris.director.commandfind.param.biome_look", customHandler = ReachableBiomeHandler.class)
            IrisBiome biome,
            @Param(description = "Should you be teleported", descriptionKey = "iris.director.commandfind.param.should_you_be_teleported", defaultValue = "true")
            boolean teleport
    ) {
        Engine e = engine();

        if (e == null) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_FIND_NOT_IRIS_WORLD));
            return;
        }

        EngineBukkitOps.gotoBiome(e, biome, player(), teleport);
    }

    @Director(description = "Find a region", descriptionKey = "iris.director.commandfind.director.find_region")
    public void region(
            @Param(description = "The region to look for", descriptionKey = "iris.director.commandfind.param.region_look", customHandler = ReachableRegionHandler.class)
            IrisRegion region,
            @Param(description = "Should you be teleported", descriptionKey = "iris.director.commandfind.param.should_you_be_teleported_2", defaultValue = "true")
            boolean teleport
    ) {
        Engine e = engine();

        if (e == null) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_FIND_NOT_IRIS_WORLD_2));
            return;
        }

        EngineBukkitOps.gotoRegion(e, region, player(), teleport);
    }

    @Director(description = "Find a point of interest.", descriptionKey = "iris.director.commandfind.director.find_point_interest")
    public void poi(
            @Param(description = "The type of PoI to look for.", descriptionKey = "iris.director.commandfind.param.type_poi_look")
            String type,
            @Param(description = "Should you be teleported", descriptionKey = "iris.director.commandfind.param.should_you_be_teleported_3", defaultValue = "true")
            boolean teleport
    ) {
        Engine e = engine();
        if (e == null) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_FIND_NOT_IRIS_WORLD_3));
            return;
        }

        EngineBukkitOps.gotoPOI(e, type, player(), teleport);
    }

    @Director(description = "Find an accepted hydrology feature")
    public void river(
            @Param(description = "Feature type: surface, waterfall, underground, grotto, mouth, or a deep-fluid id", customHandler = HydrologyTypeHandler.class)
            String type,
            @Param(description = "Should you be teleported", defaultValue = "true")
            boolean teleport
    ) {
        Engine activeEngine = engine();
        VolmitSender commandSender = sender();
        Player target = player();
        if (activeEngine == null || commandSender == null || target == null) {
            if (commandSender != null) {
                commandSender.sendMessage(C.RED + "Run this command from an Iris world.");
            }
            return;
        }
        IrisHydrologyRuntime runtime = activeEngine.getComplex().getHydrologyRuntime();
        HydrologyFeatureQuery query;
        try {
            query = HydrologyFeatureQuery.parse(type);
        } catch (IllegalArgumentException error) {
            commandSender.sendMessage(C.RED + error.getMessage());
            return;
        }
        Location origin = target.getLocation();
        int requestedDistance = runtime == null
                ? 8192
                : Math.min(8192, runtime.settings().routing().tileSize() * 15);
        int maximumDistance = runtime == null
                ? requestedDistance
                : runtime.maximumFeatureSearchDistance(
                        origin.getBlockX(), origin.getBlockZ(), requestedDistance);
        commandSender.sendMessage(C.GRAY + "Searching accepted hydrology plans for " + type + "...");
        J.a(() -> {
            try {
                GenerationSemanticQueries.RiverResult feature = GenerationSemanticQueries.nearestRiver(
                        activeEngine,
                        query,
                        origin.getBlockX(),
                        origin.getBlockZ(),
                        maximumDistance,
                        (int visited) -> sendStructureMessage(target, commandSender,
                                C.GRAY + "Searched " + visited + " hydrology tiles for " + type + "...")
                ).orElse(null);
                if (feature == null) {
                    sendStructureMessage(target, commandSender,
                            C.YELLOW + "No accepted " + type + " hydrology feature found within "
                                    + maximumDistance + " blocks.");
                    return;
                }
                String label = type + " hydrology feature";
                if (!teleport) {
                    sendStructureMessage(target, commandSender,
                            C.GREEN + "Found " + label + " at " + feature.x() + ", " + feature.y() + ", " + feature.z() + ".");
                    return;
                }
                Location destination = new Location(
                        target.getWorld(), feature.x(), feature.y(), feature.z());
                prepareStructureTeleport(
                        target, target.getWorld(), commandSender, label, destination, true);
            } catch (Throwable error) {
                sendStructureMessage(target, commandSender,
                        C.RED + "Could not locate " + type + " hydrology: " + error.getClass().getSimpleName());
                Iris.reportError("Could not locate accepted hydrology feature '" + type + "'.", error);
            }
        });
    }

    @Director(description = "Find a structure (a vanilla key like minecraft:village_plains or minecraft:stronghold, or an imported iris structure key)", descriptionKey = "iris.director.commandfind.director.find_structure_vanilla_key_like_minecraft_village_plains_minecraft_stronghold_imported_iris", sync = true)
    public void structure(
            @Param(description = "The structure to look for (e.g. minecraft:village_plains, minecraft:stronghold, minecraft_ancient_city)", descriptionKey = "iris.director.commandfind.param.structure_look_e_g_minecraft_village_plains_minecraft_stronghold_minecraft_ancient_city", customHandler = StructureHandler.class)
            String structure
    ) {
        VolmitSender commandSender = sender();
        if (commandSender == null) {
            Iris.reportError("Structure lookup started without a command sender context.", new IllegalStateException("Missing command sender context"));
            return;
        }

        Engine e = engine();

        if (e == null) {
            commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessages.COMMAND_FIND_NOT_IRIS_WORLD));
            return;
        }

        Player target = player();
        if (target == null) {
            commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessages.COMMAND_FIND_RUN_THIS_GAME_TELEPORT_STRUCTURE));
            return;
        }

        String structureKey = structure == null ? "" : structure.trim();
        Structure nativeStructure = resolveNativeStructure(structureKey);
        boolean registered = nativeStructure != null;
        IrisNativeStructureDecision decision = registered
                ? NativeStructureGenerationPolicy.resolve(e, structureKey, false)
                : null;
        boolean nativePlacement = IrisStructureLocator.hasNativePlacement(e, structureKey);
        boolean locatableNativePlacement = IrisStructureLocator.hasLocatableNativePlacement(e, structureKey);
        boolean locatableEditablePlacement = IrisStructureLocator.hasLocatableEditablePlacement(e, structureKey);
        World targetWorld = target.getWorld();
        boolean nativeGenerationEnabled = targetWorld.canGenerateStructures();
        boolean requiresReachability = registered && decision.generate()
                && decision.status() != NativeStructureGenerationStatus.REPLACED_BY_IRIS
                && nativeGenerationEnabled;
        boolean reachable = !requiresReachability || StructureReachability.isReachable(e, structureKey);
        StructureLookupRoute route = selectStructureLookupRoute(
                registered, decision, nativePlacement, locatableNativePlacement,
                locatableEditablePlacement, nativeGenerationEnabled, reachable);

        if (route != StructureLookupRoute.NATIVE && route != StructureLookupRoute.IRIS
                && GenerationFindCatalog.hasRetainedStructurePlacement(e, structureKey)) {
            locateIrisStructure(e, structureKey, commandSender);
            return;
        }

        if (route == StructureLookupRoute.UNKNOWN) {
            commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessages.COMMAND_FIND_UNKNOWN_STRUCTURE, MessageArgument.untrusted("structureKey", structureKey)));
            return;
        }
        if (route == StructureLookupRoute.POLICY_DISABLED) {
            commandSender.sendMessage(C.RED + NativeStructureGenerationPolicy.generationStatusMessage(
                    structureKey, decision.status()));
            return;
        }
        if (route == StructureLookupRoute.NO_ACTIVE_PLACEMENT) {
            commandSender.sendMessage(C.YELLOW + structureKey
                    + " has no active placement in this world.");
            return;
        }
        if (route == StructureLookupRoute.WORLD_DISABLED) {
            commandSender.sendMessage(C.YELLOW + structureKey
                    + " cannot generate because native structure generation is disabled for this world.");
            return;
        }
        if (route == StructureLookupRoute.UNREACHABLE) {
            KList<String> miss = StructureReachability.missingBiomeKeys(e, structureKey);
            commandSender.sendMessage(C.YELLOW + structureKey
                    + " cannot generate in this world (its required biomes are not produced by this pack"
                    + (miss.isEmpty() ? "" : ": needs " + String.join("/", miss)) + ").");
            return;
        }
        if (route == StructureLookupRoute.IRIS) {
            locateIrisStructure(e, structureKey, commandSender);
            return;
        }

        Location origin = target.getLocation();
        commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessages.COMMAND_FIND_LOCATING, MessageArgument.untrusted("structureKey", structureKey)));
        J.s(() -> {
            try {
                StructureSearchResult result = targetWorld.locateNearestStructure(
                        origin, nativeStructure, 100, false);
                if (result == null || result.getLocation() == null) {
                    sendStructureMessage(target, commandSender,
                            C.YELLOW + "No " + structureKey + " found within range of you.");
                    return;
                }
                prepareStructureTeleport(
                        target, targetWorld, commandSender, structureKey, result.getLocation(), false);
            } catch (Throwable t) {
                sendStructureMessage(target, commandSender,
                        C.RED + "Could not locate " + structureKey + ": " + t.getClass().getSimpleName());
                Iris.reportError("Could not locate structure '" + structureKey + "'.", t);
            }
        });
    }

    @Director(description = "Print every structure excluded from /iris goto and its rejection reason to the server console", sync = true)
    public void unregistered() {
        VolmitSender commandSender = sender();
        if (commandSender == null) {
            Iris.reportError("Structure exclusion report started without a command sender context.",
                    new IllegalStateException("Missing command sender context"));
            return;
        }
        Engine activeEngine = engine();
        if (activeEngine == null) {
            commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessages.COMMAND_FIND_NOT_IRIS_WORLD));
            return;
        }
        Player target = player();
        if (target == null) {
            commandSender.sendMessage(C.RED + "Run this command from the Iris world to inspect its structures.");
            return;
        }

        StructureExclusionReport snapshot;
        try {
            snapshot = collectStructureExclusions(
                    activeEngine, target.getWorld().canGenerateStructures());
        } catch (Throwable error) {
            commandSender.sendMessage(C.RED + "Could not build the structure exclusion report; see the server console.");
            Iris.reportError("Could not snapshot /iris goto unregistered report for world '"
                    + target.getWorld().getName() + "'.", error);
            return;
        }

        String worldName = target.getWorld().getName();
        J.a(() -> {
            try {
                StructureExclusionReport report = includeManagedDatapackStructures(
                        snapshot, DatapackIngestService.installed());
                printStructureExclusionReport(worldName, report);
                sendStructureMessage(target, commandSender, C.GREEN + "Printed " + report.entries().size()
                        + " non-generating structure candidate(s) and their reasons to the server console. "
                        + "This was an eligibility check; no chunks were searched.");
            } catch (Throwable error) {
                sendStructureMessage(target, commandSender,
                        C.RED + "Could not finish the structure exclusion report; see the server console.");
                Iris.reportError("Could not finish /iris goto unregistered report for world '"
                        + worldName + "'.", error);
            }
        });
    }

    private static StructureExclusionReport collectStructureExclusions(
            Engine engine, boolean nativeGenerationEnabled) {
        PlatformStructureHooks structureHooks = IrisPlatforms.get().structureHooks();
        Map<String, String> registeredKeys = distinctStructureKeys(structureHooks.structureKeys());
        Set<String> reachableKeys = StructureReachability.reachableKeys(engine);
        Set<String> possibleBiomeKeys = normalizedStructureKeys(
                structureHooks.possibleBiomeKeys(engine.getWorld().platformWorld()));
        List<StructureExclusion> exclusions = new ArrayList<>();
        int registeredExcluded = 0;

        for (Map.Entry<String, String> entry : registeredKeys.entrySet()) {
            String normalizedKey = entry.getKey();
            String key = entry.getValue();
            IrisNativeStructureDecision decision = NativeStructureGenerationPolicy.resolve(engine, key, false);
            boolean nativePlacement = IrisStructureLocator.hasNativePlacement(engine, key);
            boolean locatableNativePlacement = IrisStructureLocator.hasLocatableNativePlacement(engine, key);
            boolean locatableEditablePlacement = IrisStructureLocator.hasLocatableEditablePlacement(engine, key);
            StructureLookupRoute route = selectStructureLookupRoute(
                    true, decision, nativePlacement, locatableNativePlacement,
                    locatableEditablePlacement, nativeGenerationEnabled,
                    reachableKeys.contains(normalizedKey));
            if (route == StructureLookupRoute.IRIS || route == StructureLookupRoute.NATIVE) {
                continue;
            }
            List<String> requiredBiomes = needsNativeReachabilityReason(route, decision)
                    ? structureHooks.structureBiomeKeys(key)
                    : List.of();
            exclusions.add(new StructureExclusion(
                    StructureExclusionKind.EXCLUDED, key, null,
                    describeStructureExclusion(
                            route, key, decision.status(), nativePlacement,
                            requiredBiomes, possibleBiomeKeys)));
            registeredExcluded++;
        }

        int configuredUnregistered = 0;
        Set<String> configuredUnregisteredKeys = new LinkedHashSet<>();
        for (String configuredKey : IrisStructureLocator.placedKeys(engine)) {
            String normalizedKey = normalizeStructureKey(configuredKey);
            if (normalizedKey.isEmpty()
                    || registeredKeys.containsKey(normalizedKey)
                    || !IrisStructureLocator.hasNativePlacement(engine, configuredKey)
                    || !configuredUnregisteredKeys.add(normalizedKey)) {
                continue;
            }
            exclusions.add(new StructureExclusion(
                    StructureExclusionKind.UNREGISTERED, configuredKey, null,
                    describeConfiguredUnregisteredNative(
                            IrisStructureLocator.hasLocatableNativePlacement(engine, configuredKey))));
            configuredUnregistered++;
        }

        int editableUnplaced = 0;
        Set<String> unplacedEditableKeys = new LinkedHashSet<>();
        Set<String> locatableEditableKeys = normalizedStructureKeys(
                IrisStructureLocator.locatableEditableKeys(engine));
        for (String editableKey : engine.getData().getStructureLoader().getPossibleKeys()) {
            String normalizedKey = normalizeStructureKey(editableKey);
            if (!isUnplacedEditableCandidate(
                    normalizedKey, registeredKeys.keySet(), locatableEditableKeys,
                    IrisStructureLocator.hasNativePlacement(engine, editableKey))
                    || !unplacedEditableKeys.add(normalizedKey)) {
                continue;
            }
            boolean configuredPlacement = IrisStructureLocator.hasEditablePlacement(engine, editableKey);
            String reason = configuredPlacement
                    ? "editable Iris structure has a configured placement, but no matching placement is active "
                    + "because its density is not positive or its Y band does not intersect this world"
                    : "editable Iris structure exists in this pack, but no structure placement references it";
            exclusions.add(new StructureExclusion(
                    StructureExclusionKind.UNPLACED, editableKey, null, reason));
            editableUnplaced++;
        }

        Set<String> configuredImportUrls = normalizedImportUrls(
                DatapackIngestService.configuredImports(engine.getDimension()));
        return new StructureExclusionReport(
                List.copyOf(exclusions), Set.copyOf(registeredKeys.keySet()),
                configuredImportUrls, registeredExcluded, configuredUnregistered,
                editableUnplaced, 0);
    }

    static boolean isUnplacedEditableCandidate(
            String normalizedKey, Set<String> registeredKeys,
            Set<String> locatableEditableKeys, boolean nativePlacement) {
        return normalizedKey != null
                && !normalizedKey.isEmpty()
                && !registeredKeys.contains(normalizedKey)
                && !locatableEditableKeys.contains(normalizedKey)
                && !nativePlacement;
    }

    private static StructureExclusionReport includeManagedDatapackStructures(
            StructureExclusionReport snapshot, List<DatapackIngestService.Entry> installedEntries) {
        Map<String, ManagedDatapackStructure> unregisteredStructures = new LinkedHashMap<>();
        for (DatapackIngestService.Entry entry : installedEntries) {
            String importUrl = normalizeImportUrl(entry.url);
            if (importUrl.isEmpty() || !snapshot.configuredImportUrls().contains(importUrl)) {
                continue;
            }
            String sourceId = entry.id == null || entry.id.isBlank() ? "unknown" : entry.id.trim();
            List<String> structureKeys = entry.structureKeys == null ? List.of() : entry.structureKeys;
            for (String key : structureKeys) {
                String normalizedKey = normalizeStructureKey(key);
                if (normalizedKey.isEmpty() || snapshot.registeredKeys().contains(normalizedKey)) {
                    continue;
                }
                ManagedDatapackStructure managed = unregisteredStructures.computeIfAbsent(
                        normalizedKey, ignored -> new ManagedDatapackStructure(key.trim()));
                managed.sourceIds().add(sourceId);
            }
        }

        List<StructureExclusion> entries = new ArrayList<>();
        int configuredUnregistered = snapshot.configuredUnregistered();
        for (StructureExclusion exclusion : snapshot.entries()) {
            boolean replacedByManagedSource = exclusion.kind() == StructureExclusionKind.UNREGISTERED
                    && unregisteredStructures.containsKey(normalizeStructureKey(exclusion.key()));
            if (replacedByManagedSource) {
                configuredUnregistered--;
                continue;
            }
            entries.add(exclusion);
        }
        for (ManagedDatapackStructure managed : unregisteredStructures.values()) {
            entries.add(new StructureExclusion(
                    StructureExclusionKind.UNREGISTERED, managed.key(),
                    String.join(",", managed.sourceIds()),
                    "declared by an Iris-managed datapack but absent from the live registry; "
                            + "restart, enablement, or datapack validation may be required"));
        }
        sortStructureExclusions(entries);
        return new StructureExclusionReport(
                List.copyOf(entries), snapshot.registeredKeys(), snapshot.configuredImportUrls(),
                snapshot.registeredExcluded(), Math.max(0, configuredUnregistered),
                snapshot.editableUnplaced(), unregisteredStructures.size());
    }

    private static void printStructureExclusionReport(
            String worldName, StructureExclusionReport report) {
        Iris.info("Iris goto unregistered report for world '%s': %d non-generating structure candidate(s).",
                worldName, report.entries().size());
        for (StructureExclusion exclusion : report.entries()) {
            String source = exclusion.source() == null
                    ? ""
                    : " (source " + exclusion.source() + ")";
            Iris.info("[%s] %s%s: %s",
                    exclusion.kind().label(), exclusion.key(), source, exclusion.reason());
        }
        Iris.info("Iris goto unregistered summary: %d registered key(s) excluded, "
                        + "%d managed datapack key(s) unregistered, %d configured native key(s) unregistered, "
                        + "%d editable Iris structure(s) unplaced.",
                report.registeredExcluded(), report.managedUnregistered(),
                report.configuredUnregistered(), report.editableUnplaced());
        Iris.info("Eligibility report only; no chunks were searched and existing generated starts were not scanned.");
    }

    private static boolean needsNativeReachabilityReason(
            StructureLookupRoute route, IrisNativeStructureDecision decision) {
        return route == StructureLookupRoute.UNREACHABLE
                || route == StructureLookupRoute.NO_ACTIVE_PLACEMENT && decision.generate();
    }

    static String describeStructureExclusion(
            StructureLookupRoute route, String key, NativeStructureGenerationStatus status,
            boolean nativePlacement, List<String> requiredBiomes, Set<String> possibleBiomeKeys) {
        return switch (route) {
            case UNKNOWN -> "the key is not registered by the active server/datapack";
            case POLICY_DISABLED -> NativeStructureGenerationPolicy.generationStatusMessage(key, status);
            case WORLD_DISABLED -> "native structure generation is disabled for this world";
            case UNREACHABLE -> nativeReachabilityReason(requiredBiomes, possibleBiomeKeys);
            case NO_ACTIVE_PLACEMENT -> {
                String placementType = nativePlacement
                        ? "configured nativeStructures placement"
                        : "configured Iris replacement";
                String reason = placementType + " is inactive: no matching placement has positive density "
                        + "when density-based and a Y band intersecting this world";
                if (status == NativeStructureGenerationStatus.GENERATE_NATIVE) {
                    reason += "; the native route is also inactive because "
                            + nativeReachabilityReason(requiredBiomes, possibleBiomeKeys);
                }
                yield reason;
            }
            case IRIS, NATIVE -> throw new IllegalArgumentException(
                    "Active structure route cannot be described as excluded: " + route);
        };
    }

    static String nativeReachabilityReason(
            List<String> requiredBiomes, Set<String> possibleBiomeKeys) {
        if (requiredBiomes == null || requiredBiomes.isEmpty()) {
            return "its resolved biome filter is empty";
        }
        Set<String> possible = possibleBiomeKeys == null ? Set.of() : possibleBiomeKeys;
        for (String requiredBiome : requiredBiomes) {
            if (possible.contains(normalizeStructureKey(requiredBiome))) {
                return "no active positive-weight, positive-frequency structure-set entry includes it in this world";
            }
        }
        return "this pack does not produce any of its required biome(s): "
                + String.join("/", requiredBiomes);
    }

    static String describeConfiguredUnregisteredNative(boolean locatablePlacement) {
        String reason = "configured in nativeStructures, but the key is not registered by the active "
                + "server/datapack, so Minecraft cannot create its native structure start";
        return locatablePlacement
                ? reason
                : reason + "; its Iris placement is also inactive because no matching placement has positive "
                + "density when density-based and a Y band intersecting this world";
    }

    private static Map<String, String> distinctStructureKeys(List<String> keys) {
        Map<String, String> distinctKeys = new LinkedHashMap<>();
        for (String key : keys) {
            String normalizedKey = normalizeStructureKey(key);
            if (!normalizedKey.isEmpty()) {
                distinctKeys.putIfAbsent(normalizedKey, key.trim());
            }
        }
        return distinctKeys;
    }

    private static Set<String> normalizedStructureKeys(Iterable<String> keys) {
        Set<String> normalizedKeys = new LinkedHashSet<>();
        if (keys == null) {
            return normalizedKeys;
        }
        for (String key : keys) {
            String normalizedKey = normalizeStructureKey(key);
            if (!normalizedKey.isEmpty()) {
                normalizedKeys.add(normalizedKey);
            }
        }
        return normalizedKeys;
    }

    private static Set<String> normalizedImportUrls(Iterable<String> urls) {
        Set<String> normalizedUrls = new LinkedHashSet<>();
        if (urls == null) {
            return normalizedUrls;
        }
        for (String url : urls) {
            String normalizedUrl = normalizeImportUrl(url);
            if (!normalizedUrl.isEmpty()) {
                normalizedUrls.add(normalizedUrl);
            }
        }
        return Set.copyOf(normalizedUrls);
    }

    private static String normalizeImportUrl(String url) {
        return url == null ? "" : url.trim();
    }

    private static void sortStructureExclusions(List<StructureExclusion> exclusions) {
        exclusions.sort((left, right) -> {
            int kindComparison = left.kind().compareTo(right.kind());
            return kindComparison == 0
                    ? left.key().compareToIgnoreCase(right.key())
                    : kindComparison;
        });
    }

    private static String normalizeStructureKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    static StructureLookupRoute selectStructureLookupRoute(
            boolean registered, IrisNativeStructureDecision decision, boolean nativePlacement,
            boolean locatableNativePlacement, boolean locatableEditablePlacement,
            boolean nativeGenerationEnabled, boolean reachable) {
        if (!registered) {
            return !nativePlacement && locatableEditablePlacement
                    ? StructureLookupRoute.IRIS
                    : StructureLookupRoute.UNKNOWN;
        }
        if (decision == null) {
            throw new IllegalArgumentException("Registered structure lookup requires a generation decision");
        }
        if (!decision.generate()
                && decision.status() != NativeStructureGenerationStatus.REPLACED_BY_IRIS) {
            return StructureLookupRoute.POLICY_DISABLED;
        }
        if (decision.status() == NativeStructureGenerationStatus.REPLACED_BY_IRIS) {
            if (!nativePlacement) {
                return locatableEditablePlacement
                        ? StructureLookupRoute.IRIS
                        : StructureLookupRoute.NO_ACTIVE_PLACEMENT;
            }
            if (!nativeGenerationEnabled) {
                return StructureLookupRoute.WORLD_DISABLED;
            }
            return locatableNativePlacement
                    ? StructureLookupRoute.NATIVE
                    : StructureLookupRoute.NO_ACTIVE_PLACEMENT;
        }
        if (!nativeGenerationEnabled) {
            return StructureLookupRoute.WORLD_DISABLED;
        }
        if (nativePlacement && locatableNativePlacement) {
            return StructureLookupRoute.NATIVE;
        }
        if (reachable) {
            return StructureLookupRoute.NATIVE;
        }
        return nativePlacement
                ? StructureLookupRoute.NO_ACTIVE_PLACEMENT
                : StructureLookupRoute.UNREACHABLE;
    }

    private static Structure resolveNativeStructure(String structureKey) {
        Registry<Structure> structureRegistry = Bukkit.getRegistry(Structure.class);
        if (structureRegistry == null) {
            return null;
        }
        for (Structure candidate : structureRegistry) {
            NamespacedKey key = structureRegistry.getKey(candidate);
            if (key != null && key.toString().equalsIgnoreCase(structureKey)) {
                return candidate;
            }
        }
        return null;
    }

    private void locateIrisStructure(Engine engine, String structure, VolmitSender commandSender) {
        Player target = player();
        if (target == null) {
            commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessages.COMMAND_FIND_RUN_THIS_GAME_TELEPORT_STRUCTURE_2));
            return;
        }
        World targetWorld = target.getWorld();
        Location origin = target.getLocation();
        int blockX = origin.getBlockX();
        int blockZ = origin.getBlockZ();
        commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessages.COMMAND_FIND_LOCATING_2, MessageArgument.untrusted("structure", structure)));
        J.a(() -> {
            try {
                IrisStructureLocator.LocateResult result =
                        GenerationSemanticQueries.nearestStructure(engine, structure, blockX, blockZ, 1024);
                if (result.status() == IrisStructureLocator.LocateStatus.SEARCH_LIMIT_REACHED) {
                    sendStructureMessage(target, commandSender,
                            C.YELLOW + "Unable to locate " + structure
                                    + ": the density search safety limit was reached before the full 1024-chunk radius was searched.");
                    return;
                }
                if (!result.found()) {
                    sendStructureMessage(target, commandSender,
                            C.YELLOW + "No " + structure + " found within 1024 chunks of you.");
                    return;
                }
                Location destination = new Location(
                        targetWorld, result.originX(), result.baseY(), result.originZ());
                prepareStructureTeleport(target, targetWorld, commandSender, structure, destination, true);
            } catch (Throwable t) {
                sendStructureMessage(target, commandSender,
                        C.RED + "Could not locate " + structure + ": " + t.getClass().getSimpleName());
                Iris.reportError("Could not locate Iris-placed structure '" + structure + "'.", t);
            }
        });
    }

    @Director(description = "Find an object", descriptionKey = "iris.director.commandfind.director.find_object")
    public void object(
            @Param(description = "The object to look for", descriptionKey = "iris.director.commandfind.param.object_look", customHandler = LocatableObjectHandler.class)
            String object,
            @Param(description = "Should you be teleported", descriptionKey = "iris.director.commandfind.param.should_you_be_teleported_4", defaultValue = "true")
            boolean teleport
    ) {
        Engine e = engine();

        if (e == null) {
            sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_FIND_NOT_IRIS_WORLD_4));
            return;
        }

        Player studioPlayer = player();
        if (studioPlayer != null) {
            try {
                if (ObjectStudioSaveService.get().teleportTo(studioPlayer, object)) {
                    sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_FIND_OBJECT_STUDIO_TELEPORTING, MessageArgument.untrusted("object", object)));
                    return;
                }
            } catch (Throwable t) {
                Iris.reportError(t);
            }
        }

        if (GenerationFindCatalog.hasObjectPlacement(e, object)) {
            EngineBukkitOps.gotoObject(e, object, player(), teleport);
            return;
        }

        sender().sendMessage(IrisLanguage.text(BukkitCommandMessagesExtended.COMMAND_FIND_IS_NOT_CONFIGURED_ANY_REGION_BIOME_OBJECT_PLACEMENTS, MessageArgument.untrusted("object", object)));
    }

    private void prepareStructureTeleport(Player target, World world, VolmitSender commandSender, String structure,
                                          Location at, boolean useLocatedY) {
        int chunkX = at.getBlockX() >> 4;
        int chunkZ = at.getBlockZ() >> 4;
        BukkitPlatform.chunkAtAsync(world, chunkX, chunkZ, true).whenComplete((chunk, error) -> {
            if (error != null) {
                sendStructureMessage(target, commandSender, C.RED + "Could not load the destination for " + structure + ".");
                Iris.reportError("Could not load structure destination '" + structure + "'.", error);
                return;
            }
            boolean scheduled = J.runRegion(world, chunkX, chunkZ,
                    () -> teleportToStructure(target, world, commandSender, structure, at, useLocatedY));
            if (!scheduled) {
                sendStructureMessage(target, commandSender, C.RED + "Could not schedule the destination lookup for " + structure + ".");
            }
        });
    }

    private void teleportToStructure(Player target, World world, VolmitSender commandSender, String structure,
                                     Location at, boolean useLocatedY) {
        try {
            int y = useLocatedY
                    ? Math.max(world.getMinHeight() + 1, Math.min(world.getMaxHeight() - 1, at.getBlockY() + 2))
                    : world.getHighestBlockYAt(at.getBlockX(), at.getBlockZ()) + 2;
            Location destination = new Location(world, at.getBlockX() + 0.5, y, at.getBlockZ() + 0.5);
            J.runEntity(target, () -> {
                BukkitPlatform.teleportAsync(target, destination);
                commandSender.sendMessage(IrisLanguage.text(BukkitCommandMessages.COMMAND_FIND_TELEPORTED, MessageArgument.untrusted("structure", structure), MessageArgument.untrusted("value", at.getBlockX()), MessageArgument.untrusted("y", y), MessageArgument.untrusted("value2", at.getBlockZ())));
            });
        } catch (Throwable t) {
            sendStructureMessage(target, commandSender, C.RED + "Could not prepare the destination for " + structure + ".");
            Iris.reportError("Could not prepare structure destination '" + structure + "'.", t);
        }
    }

    private void sendStructureMessage(Player target, VolmitSender commandSender, String message) {
        J.runEntity(target, () -> commandSender.sendMessage(message));
    }

    enum StructureLookupRoute {
        UNKNOWN,
        POLICY_DISABLED,
        NO_ACTIVE_PLACEMENT,
        WORLD_DISABLED,
        UNREACHABLE,
        IRIS,
        NATIVE
    }

    private enum StructureExclusionKind {
        UNREGISTERED("unregistered"),
        EXCLUDED("excluded"),
        UNPLACED("unplaced");

        private final String label;

        StructureExclusionKind(String label) {
            this.label = label;
        }

        private String label() {
            return label;
        }
    }

    private record StructureExclusion(
            StructureExclusionKind kind, String key, String source, String reason) {
    }

    private record StructureExclusionReport(
            List<StructureExclusion> entries, Set<String> registeredKeys,
            Set<String> configuredImportUrls, int registeredExcluded,
            int configuredUnregistered, int editableUnplaced, int managedUnregistered) {
    }

    private record ManagedDatapackStructure(String key, Set<String> sourceIds) {
        private ManagedDatapackStructure(String key) {
            this(key, new LinkedHashSet<>());
        }
    }
}
