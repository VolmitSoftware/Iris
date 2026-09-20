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
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.pack.PackDirectoryResolver;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.structure.placement.IrisStructureLocator;
import art.arcane.iris.structure.nativegen.NativeStructureGenerationPolicy;
import art.arcane.iris.structure.placement.StructureReachability;
import art.arcane.iris.generation.hydrology.HydrologyFeatureQuery;
import art.arcane.iris.world.history.GenerationFindCatalog;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.structure.nativegen.IrisNativeStructureDecision;
import art.arcane.iris.structure.nativegen.NativeStructureGenerationStatus;
import art.arcane.iris.modded.IrisModdedChunkGenerator;
import art.arcane.iris.modded.ModdedEngineBootstrap;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandSource;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeCommandRegistration;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeWorldGenerators;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

final class ModdedCommandSuggestions {
    static final SuggestionProvider<NativeCommandSource> BIOME_KEYS = (CommandContext<NativeCommandSource> context, SuggestionsBuilder builder) -> suggestBiomeKeys(context, builder);
    static final SuggestionProvider<NativeCommandSource> REGION_KEYS = (CommandContext<NativeCommandSource> context, SuggestionsBuilder builder) -> suggestRegionKeys(context, builder);
    static final SuggestionProvider<NativeCommandSource> OBJECT_KEYS = (CommandContext<NativeCommandSource> context, SuggestionsBuilder builder) -> suggestObjectKeys(context, builder);
    static final SuggestionProvider<NativeCommandSource> STRUCTURE_KEYS = (CommandContext<NativeCommandSource> context, SuggestionsBuilder builder) -> suggestStructureKeys(context, builder);
    static final SuggestionProvider<NativeCommandSource> POI_TYPES = (CommandContext<NativeCommandSource> context, SuggestionsBuilder builder) -> NativeCommandRegistration.suggest(List.of("buried_treasure"), builder);
    static final SuggestionProvider<NativeCommandSource> HYDROLOGY_TYPES = (CommandContext<NativeCommandSource> context, SuggestionsBuilder builder) -> suggestHydrologyTypes(context, builder);
    static final SuggestionProvider<NativeCommandSource> PACK_NAMES = (CommandContext<NativeCommandSource> context, SuggestionsBuilder builder) -> suggestPackNames(context, builder);
    static final SuggestionProvider<NativeCommandSource> DIMENSION_NAMES = (CommandContext<NativeCommandSource> context, SuggestionsBuilder builder) -> suggestDimensionNames(context, builder);

    private static final int TAB_FAILURE_KEYS_MAX = 256;
    private static final Set<String> REPORTED_TAB_FAILURES = ConcurrentHashMap.newKeySet();
    private static final long PACK_NAME_CACHE_TTL_MS = 3_000L;
    private static volatile Set<String> cachedPackNames;
    private static volatile long cachedPackNamesAt;

    private ModdedCommandSuggestions() {
    }

    private static CompletableFuture<Suggestions> suggestBiomeKeys(CommandContext<NativeCommandSource> context, SuggestionsBuilder builder) {
        ModdedCommandFeedback.tab(context.getSource());
        try {
            Engine engine = IrisModdedCommands.engineFor(context.getSource().world());
            if (engine != null) {
                return NativeCommandRegistration.suggest(reachableBiomeKeys(engine), builder);
            }
        } catch (Throwable e) {
            warnTabFailure("biome keys", context.getSource(), e);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestHydrologyTypes(
            CommandContext<NativeCommandSource> context,
            SuggestionsBuilder builder
    ) {
        ModdedCommandFeedback.tab(context.getSource());
        try {
            Engine engine = IrisModdedCommands.engineFor(context.getSource().world());
            if (engine != null && engine.getComplex().getHydrologyRuntime() != null) {
                return NativeCommandRegistration.suggest(
                        engine.getComplex().getHydrologyRuntime().featureQueryKeys(), builder);
            }
        } catch (Throwable error) {
            warnTabFailure("hydrology types", context.getSource(), error);
        }
        return NativeCommandRegistration.suggest(
                HydrologyFeatureQuery.suggestions(List.of()), builder);
    }

    static Set<String> reachableBiomeKeys(Engine engine) {
        return reachableBiomeKeys(GenerationFindCatalog.biomes(engine));
    }

    static Set<String> reachableBiomeKeys(Iterable<IrisBiome> biomes) {
        Set<String> keys = new TreeSet<>();
        for (IrisBiome biome : biomes) {
            if (biome != null && biome.getLoadKey() != null && !biome.getLoadKey().isBlank()) {
                keys.add(biome.getLoadKey());
            }
        }
        return keys;
    }

    static boolean isReachableBiome(Engine engine, String biomeKey) {
        return GenerationFindCatalog.biome(engine, biomeKey) != null;
    }

    static boolean isReachableBiome(Iterable<IrisBiome> biomes, String biomeKey) {
        return biomeKey != null && reachableBiomeKeys(biomes).contains(biomeKey.trim());
    }

    private static CompletableFuture<Suggestions> suggestRegionKeys(CommandContext<NativeCommandSource> context, SuggestionsBuilder builder) {
        ModdedCommandFeedback.tab(context.getSource());
        try {
            Engine engine = IrisModdedCommands.engineFor(context.getSource().world());
            if (engine != null) {
                return NativeCommandRegistration.suggest(GenerationFindCatalog.regions(engine).stream()
                        .map(region -> region.getLoadKey()), builder);
            }
        } catch (Throwable e) {
            warnTabFailure("region keys", context.getSource(), e);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestObjectKeys(CommandContext<NativeCommandSource> context, SuggestionsBuilder builder) {
        ModdedCommandFeedback.tab(context.getSource());
        try {
            Engine engine = IrisModdedCommands.engineFor(context.getSource().world());
            if (engine != null) {
                return NativeCommandRegistration.suggest(GenerationFindCatalog.objectKeys(engine), builder);
            }
        } catch (Throwable e) {
            warnTabFailure("object keys", context.getSource(), e);
        }
        return builder.buildFuture();
    }

    static CompletableFuture<Suggestions> suggestStructureKeys(CommandContext<NativeCommandSource> context, SuggestionsBuilder builder) {
        NativeCommandSource source = context.getSource();
        ModdedCommandFeedback.tab(source);
        try {
            NativeWorld level = source.world();
            Engine engine = IrisModdedCommands.engineFor(level);
            if (engine == null) {
                return builder.buildFuture();
            }
            boolean nativeGenerationEnabled =
                    source.server().generateStructures();
            Collection<String> irisKeys = IrisStructureLocator.locatableEditableKeys(engine);
            Set<String> reachableNativeKeys = StructureReachability.reachableKeys(engine);
            List<String> registered = source.server().structureKeys();
            List<String> nativeKeys = new ArrayList<>(registered.size());
            Set<String> registeredKeys = new HashSet<>(registered.size());
            for (String key : registered) {
                registeredKeys.add(normalizeKey(key));
                IrisNativeStructureDecision decision = NativeStructureGenerationPolicy.resolve(engine, key, false);
                boolean nativePlacement = IrisStructureLocator.hasNativePlacement(engine, key);
                boolean locatableNativePlacement = nativePlacement
                        && IrisStructureLocator.hasLocatableNativePlacement(engine, key);
                boolean locatableEditableReplacement = !nativePlacement
                        && decision.status() == NativeStructureGenerationStatus.REPLACED_BY_IRIS
                        && IrisStructureLocator.hasLocatableEditablePlacement(engine, key);
                if (isEligibleRegisteredStructure(decision, nativePlacement,
                        locatableNativePlacement, locatableEditableReplacement,
                        reachableNativeKeys.contains(normalizeKey(key)), nativeGenerationEnabled)) {
                    nativeKeys.add(key);
                }
            }
            Collection<String> unregisteredIrisKeys = eligibleUnregisteredEditableKeys(
                    irisKeys, registeredKeys,
                    (String candidate) -> IrisStructureLocator.hasNativePlacement(engine, candidate));
            nativeKeys.addAll(GenerationFindCatalog.retainedStructureKeys(engine));
            return NativeCommandRegistration.suggest(
                    combineStructureKeys(unregisteredIrisKeys, nativeKeys), builder);
        } catch (Throwable e) {
            warnTabFailure("structure keys", source, e);
        }
        return builder.buildFuture();
    }

    static boolean isEligibleRegisteredStructure(IrisNativeStructureDecision decision,
                                                 boolean nativePlacement,
                                                 boolean locatableNativePlacement,
                                                 boolean locatableEditableReplacement,
                                                 boolean reachable,
                                                 boolean nativeGenerationEnabled) {
        if (decision.status() == NativeStructureGenerationStatus.REPLACED_BY_IRIS) {
            return locatableEditableReplacement
                    || nativeGenerationEnabled && nativePlacement && locatableNativePlacement;
        }
        return nativeGenerationEnabled && decision.generate()
                && (reachable || nativePlacement && locatableNativePlacement);
    }

    static void warnTabFailure(String suggestion, NativeCommandSource source, Throwable error) {
        String origin = tabOrigin(source);
        if (!REPORTED_TAB_FAILURES.add(suggestion + '|' + origin + '|' + error.getClass().getName())) {
            return;
        }
        if (REPORTED_TAB_FAILURES.size() > TAB_FAILURE_KEYS_MAX) {
            REPORTED_TAB_FAILURES.clear();
        }
        ModdedIrisLog.warn("Iris tab-complete for {} in {} failed; suggestions will be empty", suggestion, origin, error);
    }

    private static String tabOrigin(NativeCommandSource source) {
        if (source == null) {
            return "<no source>";
        }
        try {
            return source.world().name();
        } catch (Throwable originFailure) {
            return "<no level>";
        }
    }

    static List<String> combineStructureKeys(Collection<String> irisKeys, Collection<String> nativeKeys) {
        Map<String, String> combined = new TreeMap<>();
        addStructureKeys(combined, irisKeys);
        addStructureKeys(combined, nativeKeys);
        return List.copyOf(combined.values());
    }

    static List<String> eligibleUnregisteredEditableKeys(
            Collection<String> irisKeys, Set<String> normalizedRegisteredKeys,
            Predicate<String> nativePlacement) {
        List<String> filtered = new ArrayList<>(irisKeys.size());
        for (String key : irisKeys) {
            String normalizedKey = normalizeKey(key);
            if (!normalizedKey.isEmpty()
                    && !normalizedRegisteredKeys.contains(normalizedKey)
                    && !nativePlacement.test(key)) {
                filtered.add(key.trim());
            }
        }
        return List.copyOf(filtered);
    }

    private static void addStructureKeys(Map<String, String> combined, Collection<String> keys) {
        for (String key : keys) {
            String normalizedKey = normalizeKey(key);
            if (!normalizedKey.isEmpty()) {
                combined.putIfAbsent(normalizedKey, key.trim());
            }
        }
    }

    static String normalizeKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    }

    private static CompletableFuture<Suggestions> suggestPackNames(CommandContext<NativeCommandSource> context, SuggestionsBuilder builder) {
        ModdedCommandFeedback.tab(context.getSource());
        // Suggestion packets arrive per keystroke; a short-lived snapshot keeps the directory
        // walk off the hot path without ever serving stale names for more than a few seconds.
        long now = System.currentTimeMillis();
        Set<String> cached = cachedPackNames;
        if (cached != null && now - cachedPackNamesAt < PACK_NAME_CACHE_TTL_MS) {
            return NativeCommandRegistration.suggest(cached, builder);
        }
        Set<String> names = new TreeSet<>();
        names.add("overworld");
        try {
            File packs = IrisPlatforms.get().packsFolderNoCreate();
            for (File child : PackDirectoryResolver.listVisiblePackDirectories(packs)) {
                String packName = child.getName();
                names.add(packName);
                File dimensions = new File(child, "dimensions");
                File[] dimensionFiles = dimensions.listFiles(
                        (File directory, String name) -> name.endsWith(".json"));
                if (dimensionFiles == null) {
                    continue;
                }
                for (File dimensionFile : dimensionFiles) {
                    String fileName = dimensionFile.getName();
                    names.add(packName + ":" + fileName.substring(0, fileName.length() - 5));
                }
            }
        } catch (Throwable e) {
            warnTabFailure("pack names", context.getSource(), e);
        }
        cachedPackNames = names;
        cachedPackNamesAt = now;
        return NativeCommandRegistration.suggest(names, builder);
    }

    private static CompletableFuture<Suggestions> suggestDimensionNames(CommandContext<NativeCommandSource> context, SuggestionsBuilder builder) {
        ModdedCommandFeedback.tab(context.getSource());
        List<String> names = new ArrayList<>();
        for (NativeWorld level : context.getSource().server().worlds()) {
            if (NativeWorldGenerators.find(level, IrisModdedChunkGenerator.class) != null) {
                names.add(level.name());
            }
        }
        return NativeCommandRegistration.suggest(names, builder);
    }
}
