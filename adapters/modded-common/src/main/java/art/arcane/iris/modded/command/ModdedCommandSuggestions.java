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
import art.arcane.iris.modded.ModdedServerLevels;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.Structure;

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
    static final SuggestionProvider<CommandSourceStack> BIOME_KEYS = (CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) -> suggestBiomeKeys(context, builder);
    static final SuggestionProvider<CommandSourceStack> REGION_KEYS = (CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) -> suggestRegionKeys(context, builder);
    static final SuggestionProvider<CommandSourceStack> OBJECT_KEYS = (CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) -> suggestObjectKeys(context, builder);
    static final SuggestionProvider<CommandSourceStack> STRUCTURE_KEYS = (CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) -> suggestStructureKeys(context, builder);
    static final SuggestionProvider<CommandSourceStack> POI_TYPES = (CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) -> SharedSuggestionProvider.suggest(List.of("buried_treasure"), builder);
    static final SuggestionProvider<CommandSourceStack> HYDROLOGY_TYPES = (CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) -> suggestHydrologyTypes(context, builder);
    static final SuggestionProvider<CommandSourceStack> PACK_NAMES = (CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) -> suggestPackNames(context, builder);
    static final SuggestionProvider<CommandSourceStack> DIMENSION_NAMES = (CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) -> suggestDimensionNames(context, builder);

    private static final int TAB_FAILURE_KEYS_MAX = 256;
    private static final Set<String> REPORTED_TAB_FAILURES = ConcurrentHashMap.newKeySet();
    private static final long PACK_NAME_CACHE_TTL_MS = 3_000L;
    private static volatile Set<String> cachedPackNames;
    private static volatile long cachedPackNamesAt;

    private ModdedCommandSuggestions() {
    }

    private static CompletableFuture<Suggestions> suggestBiomeKeys(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        ModdedCommandFeedback.tab(context.getSource());
        try {
            Engine engine = IrisModdedCommands.engineFor(context.getSource().getLevel());
            if (engine != null) {
                return SharedSuggestionProvider.suggest(reachableBiomeKeys(engine), builder);
            }
        } catch (Throwable e) {
            warnTabFailure("biome keys", context.getSource(), e);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestHydrologyTypes(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder
    ) {
        ModdedCommandFeedback.tab(context.getSource());
        try {
            Engine engine = IrisModdedCommands.engineFor(context.getSource().getLevel());
            if (engine != null && engine.getComplex().getHydrologyRuntime() != null) {
                return SharedSuggestionProvider.suggest(
                        engine.getComplex().getHydrologyRuntime().featureQueryKeys(), builder);
            }
        } catch (Throwable error) {
            warnTabFailure("hydrology types", context.getSource(), error);
        }
        return SharedSuggestionProvider.suggest(
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

    private static CompletableFuture<Suggestions> suggestRegionKeys(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        ModdedCommandFeedback.tab(context.getSource());
        try {
            Engine engine = IrisModdedCommands.engineFor(context.getSource().getLevel());
            if (engine != null) {
                return SharedSuggestionProvider.suggest(GenerationFindCatalog.regions(engine).stream()
                        .map(region -> region.getLoadKey()), builder);
            }
        } catch (Throwable e) {
            warnTabFailure("region keys", context.getSource(), e);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestObjectKeys(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        ModdedCommandFeedback.tab(context.getSource());
        try {
            Engine engine = IrisModdedCommands.engineFor(context.getSource().getLevel());
            if (engine != null) {
                return SharedSuggestionProvider.suggest(GenerationFindCatalog.objectKeys(engine), builder);
            }
        } catch (Throwable e) {
            warnTabFailure("object keys", context.getSource(), e);
        }
        return builder.buildFuture();
    }

    static CompletableFuture<Suggestions> suggestStructureKeys(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        CommandSourceStack source = context.getSource();
        ModdedCommandFeedback.tab(source);
        try {
            ServerLevel level = source.getLevel();
            Engine engine = IrisModdedCommands.engineFor(level);
            if (engine == null) {
                return builder.buildFuture();
            }
            boolean nativeGenerationEnabled =
                    source.getServer().getWorldGenSettings().options().generateStructures();
            Collection<String> irisKeys = IrisStructureLocator.locatableEditableKeys(engine);
            Set<String> reachableNativeKeys = StructureReachability.reachableKeys(engine);
            Registry<Structure> registry = source.getServer().registryAccess().lookupOrThrow(Registries.STRUCTURE);
            List<String> nativeKeys = new ArrayList<>(registry.keySet().size());
            Set<String> registeredKeys = new HashSet<>(registry.keySet().size());
            for (Identifier identifier : registry.keySet()) {
                String key = identifier.toString();
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
            return SharedSuggestionProvider.suggest(
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

    static void warnTabFailure(String suggestion, CommandSourceStack source, Throwable error) {
        String origin = tabOrigin(source);
        if (!REPORTED_TAB_FAILURES.add(suggestion + '|' + origin + '|' + error.getClass().getName())) {
            return;
        }
        if (REPORTED_TAB_FAILURES.size() > TAB_FAILURE_KEYS_MAX) {
            REPORTED_TAB_FAILURES.clear();
        }
        ModdedIrisLog.warn("Iris tab-complete for {} in {} failed; suggestions will be empty", suggestion, origin, error);
    }

    private static String tabOrigin(CommandSourceStack source) {
        if (source == null) {
            return "<no source>";
        }
        try {
            return source.getLevel().dimension().identifier().toString();
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

    private static CompletableFuture<Suggestions> suggestPackNames(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        ModdedCommandFeedback.tab(context.getSource());
        // Suggestion packets arrive per keystroke; a short-lived snapshot keeps the directory
        // walk off the hot path without ever serving stale names for more than a few seconds.
        long now = System.currentTimeMillis();
        Set<String> cached = cachedPackNames;
        if (cached != null && now - cachedPackNamesAt < PACK_NAME_CACHE_TTL_MS) {
            return SharedSuggestionProvider.suggest(cached, builder);
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
        return SharedSuggestionProvider.suggest(names, builder);
    }

    private static CompletableFuture<Suggestions> suggestDimensionNames(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        ModdedCommandFeedback.tab(context.getSource());
        List<String> names = new ArrayList<>();
        for (ServerLevel level : ModdedServerLevels.levels(context.getSource().getServer())) {
            if (level.getChunkSource().getGenerator() instanceof IrisModdedChunkGenerator) {
                names.add(level.dimension().identifier().toString());
            }
        }
        return SharedSuggestionProvider.suggest(names, builder);
    }
}
