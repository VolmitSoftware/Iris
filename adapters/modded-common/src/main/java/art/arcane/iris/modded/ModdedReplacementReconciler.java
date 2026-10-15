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

package art.arcane.iris.modded;

import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.world.history.GenerationEpoch;
import art.arcane.iris.world.history.GenerationHistory;
import art.arcane.iris.world.history.GenerationHistoryPaths;
import art.arcane.iris.world.lifecycle.WorldReplacementEntryGuard;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeWorldGenerators;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeWorldPaths;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;
import art.arcane.volmlib.util.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Predicate;

public final class ModdedReplacementReconciler {
    public static final String OVERWORLD = "minecraft:overworld";
    private static final String STATE_FILE = "data/irisworldgen/dimension-replacements.json";
    private static final String APPLIED = "replacements";
    private static final String PENDING = "pending";
    private static final String IRIS_WORLD_NAMESPACE = "irisworldgen:";

    private ModdedReplacementReconciler() {
    }

    public static void reconcileEarly() {
        try {
            ModdedModConfig config = ModdedModConfig.get();
            Map<String, ModdedDimensionReplacement> configured = ModdedDimensionReplacements.configured();
            Path worldRoot = MainWorldService.configuredWorldRootIfPresent();
            if (worldRoot == null) {
                if (!configured.isEmpty()) {
                    ModdedIrisLog.info("Iris dimension replacements {} apply to a new world; nothing to move aside",
                            configured.keySet());
                }
                return;
            }
            Path recoveryParent = ModdedEngineBootstrap.loader().configDir().resolve("irisworldgen");
            Path packsRoot = IrisPlatforms.get().packsFolderNoCreate().toPath();
            Result result = reconcile(new Request(worldRoot, configured, config.replacementProblems(),
                    (ModdedDimensionReplacement replacement) -> ModdedDimensionReplacements.installed(replacement, packsRoot),
                    recoveryParent, ModdedDimensionReplacements.replaceableLevels(worldRoot, configured)));
            if (!result.adoptedLevels().isEmpty()) {
                ModdedIrisLog.info("Iris kept the existing terrain of {}: its Iris generation history already uses the configured pack and seed; the replacement is recorded once the level generates from it",
                        result.adoptedLevels());
            }
            if (!result.replacedWorlds().isEmpty()) {
                ModdedIrisLog.warn("Iris replaced the Iris worlds {} from their staged packs", result.replacedWorlds());
            }
            if (result.quarantinedLevels().isEmpty() && result.replacedWorlds().isEmpty()) {
                return;
            }
            ModdedIrisLog.warn("Iris dimension replacement changed for {}: {}", movedLevels(result),
                    result.recovery() == null
                            ? "no existing chunk data to move aside"
                            : "moved their chunk, entity, POI, and Iris Mantle data to " + result.recovery()
                            + " so they regenerate (player data kept)");
        } catch (Throwable failure) {
            ModdedIrisLog.error("Iris dimension replacement reconciliation failed", failure);
            throw new IllegalStateException("Iris refused startup after dimension replacement reconciliation failed", failure);
        }
    }

    public static void recordApplied(NativeModdedServer server) {
        Optional<Path> worldRoot = NativeWorldPaths.root(server);
        if (worldRoot.isEmpty()) {
            return;
        }
        try {
            recordApplied(worldRoot.get(), ModdedDimensionReplacements.configured(),
                    (String levelKey) -> liveLevel(server, levelKey));
        } catch (IOException | RuntimeException failure) {
            ModdedIrisLog.error("Iris is stopping the server because it could not record the applied dimension replacements under {}; an unrecorded replacement would be moved aside again on the next start",
                    worldRoot.get(), failure);
            server.halt(false);
        }
    }

    public static void stopUnpublishedEngines(NativeModdedServer server) {
        for (String levelKey : ModdedWorldEngines.evictUnpublished(server,
                ModdedModConfig.get().dimensionReplacements().keySet())) {
            ModdedIrisLog.error("Iris stopped the engine it started for {}: a mod replaced that level with its own chunk generator while the server started",
                    levelKey);
        }
    }

    static synchronized Map<String, ModdedDimensionReplacement> recordApplied(Path worldRoot,
                                                                Map<String, ModdedDimensionReplacement> configured,
                                                                LiveLevels levels) throws IOException {
        State state = readState(worldRoot);
        Map<String, ModdedDimensionReplacement> recorded = new TreeMap<>(state.applied());
        for (ModdedDimensionReplacement replacement : new TreeMap<>(configured).values()) {
            Optional<LiveLevel> live = levels.level(replacement.levelKey());
            if (live.isPresent() && live.get().generates(replacement)) {
                recorded.put(replacement.levelKey(), replacement.withSeed(live.get().seed()));
                continue;
            }
            if (live.isPresent() && !live.get().iris()) {
                recorded.remove(replacement.levelKey());
                ModdedIrisLog.error("Iris replacement of {} is configured but the loaded level uses another chunk generator; a mod creates that level while the server runs, so Iris cannot replace it. Remove its dimensionReplacements entry from modded.json",
                        replacement.levelKey());
                continue;
            }
            ModdedIrisLog.warn("Iris replacement of {} is configured but the level does not generate from {}{}; it stays unapplied",
                    replacement.levelKey(), replacement.packRef(),
                    replacement.seed().isPresent() ? " with seed " + replacement.seed().getAsLong() : "");
        }
        if (!recorded.equals(state.applied())) {
            writeState(worldRoot, new State(recorded, state.pending()));
        }
        return Map.copyOf(recorded);
    }

    static synchronized Result reconcile(Request request) throws IOException {
        Path worldRoot = request.worldRoot();
        requireReadableConfig(worldRoot, request.configProblems());
        State state = readState(worldRoot);
        Map<String, ModdedDimensionReplacement> applied = state.applied();
        Set<String> refreshed = new TreeSet<>();
        boolean irisWorldsPending = false;
        for (String level : state.pending().keySet()) {
            if (level.startsWith(IRIS_WORLD_NAMESPACE)) {
                irisWorldsPending = true;
            } else if (request.configured().containsKey(level)) {
                refreshed.add(level);
            }
        }
        Set<String> changed = new TreeSet<>(ModdedDimensionReplacements.changedLevels(applied, request.configured()));
        changed.addAll(refreshed);
        Map<String, ModdedDimensionReplacement> recorded = new TreeMap<>(applied);
        Set<String> quarantined = new TreeSet<>();
        Set<String> adopted = new TreeSet<>();
        Set<String> unreplaceable = new TreeSet<>();
        for (String level : changed) {
            ModdedDimensionReplacement replacement = request.configured().get(level);
            if (replacement == null) {
                recorded.remove(level);
                quarantined.add(level);
                continue;
            }
            if (!request.levels().replaceable(level)) {
                unreplaceable.add(level);
                ModdedIrisLog.error(ModdedDimensionReplacements.unreplaceableReason(level, request.levels())
                        + " Remove its dimensionReplacements entry from modded.json.");
                continue;
            }
            Path storage = replacement.storageFolder(worldRoot);
            boolean keepHistory = !refreshed.contains(level) && matchingHistorySeed(storage, replacement).isPresent();
            if (keepHistory) {
                adopted.add(level);
            } else if (request.installed().test(replacement)) {
                quarantined.add(level);
            } else {
                refuseOrSkipUninstalled(worldRoot, applied, replacement);
            }
        }
        List<ModdedDimensionRegistryStore.PersistentDimension> registry = irisWorldsPending
                ? ModdedDimensionRegistryStore.loadWorldRoot(worldRoot)
                : List.of();
        Map<String, ModdedDimensionReplacement> replacedWorlds = new TreeMap<>();
        for (ModdedDimensionReplacement pending : state.pending().values()) {
            if (!pending.levelKey().startsWith(IRIS_WORLD_NAMESPACE)) {
                continue;
            }
            if (registered(registry, pending.levelKey())) {
                replacedWorlds.put(pending.levelKey(), pending);
            } else {
                ModdedIrisLog.warn("Iris dropped the staged replacement of {}: the Iris world is no longer registered",
                        pending.levelKey());
            }
        }
        List<Path> sources = new ArrayList<>();
        for (String level : quarantined) {
            addChunkData(sources, ModdedDimensionReplacement.storageFolder(worldRoot, level));
        }
        for (String level : replacedWorlds.keySet()) {
            addChunkData(sources, ModdedDimensionReplacement.storageFolder(worldRoot, level));
        }
        Path recovery = request.recoveryParent().resolve("replacement-recovery-" + UUID.randomUUID());
        List<Path> moved = sources.isEmpty() ? List.of() : ModdedWorldQuarantine.move(worldRoot, sources, recovery);
        if (quarantined.contains(OVERWORLD)) {
            WorldReplacementEntryGuard.stage(worldRoot, ModdedDimensionReplacement.storageFolder(worldRoot, OVERWORLD),
                    UUID.randomUUID());
        }
        if (!replacedWorlds.isEmpty()) {
            ModdedDimensionRegistryStore.writeWorldRoot(worldRoot, repointed(registry, replacedWorlds));
        }
        State reconciled = new State(recorded, Map.of());
        if (!reconciled.equals(state)) {
            writeState(worldRoot, reconciled);
        }
        return new Result(Set.copyOf(quarantined), Set.copyOf(adopted), Set.copyOf(replacedWorlds.keySet()),
                Set.copyOf(unreplaceable), moved.isEmpty() ? null : recovery);
    }

    public static boolean pending(Path worldRoot, String levelKey, Map<String, ModdedDimensionReplacement> configured)
            throws IOException {
        State state = readState(worldRoot);
        if (state.pending().containsKey(levelKey)) {
            return true;
        }
        ModdedDimensionReplacement replacement = configured.get(levelKey);
        return replacement != null && !ModdedDimensionReplacements.matches(state.applied().get(levelKey), replacement);
    }

    public static Optional<ModdedDimensionReplacement> applied(Path worldRoot, String levelKey) throws IOException {
        return Optional.ofNullable(readState(worldRoot).applied().get(levelKey));
    }

    public static synchronized void stagePending(Path worldRoot, ModdedDimensionReplacement replacement) throws IOException {
        if (replacement.seed().isEmpty()) {
            throw new IllegalArgumentException("A staged replacement needs its effective seed: " + replacement.levelKey());
        }
        State state = readState(worldRoot);
        Map<String, ModdedDimensionReplacement> pending = new TreeMap<>(state.pending());
        pending.put(replacement.levelKey(), replacement);
        writeState(worldRoot, new State(state.applied(), pending));
    }

    public static synchronized boolean cancelPending(Path worldRoot, String levelKey) throws IOException {
        State state = readState(worldRoot);
        Map<String, ModdedDimensionReplacement> pending = new TreeMap<>(state.pending());
        if (pending.remove(levelKey) == null) {
            return false;
        }
        writeState(worldRoot, new State(state.applied(), pending));
        return true;
    }

    static boolean hasReplacementRecord(Path worldRoot, String levelKey) throws IOException {
        return readState(worldRoot).applied().containsKey(levelKey) || hasGenerationHistory(
                ModdedDimensionReplacement.storageFolder(worldRoot, levelKey));
    }

    private static Set<String> movedLevels(Result result) {
        Set<String> levels = new TreeSet<>(result.quarantinedLevels());
        levels.addAll(result.replacedWorlds());
        return levels;
    }

    private static void addChunkData(List<Path> sources, Path storage) {
        for (String folder : ModdedDimensionStorage.chunkDataFolders()) {
            sources.add(storage.resolve(folder));
        }
    }

    private static boolean registered(List<ModdedDimensionRegistryStore.PersistentDimension> registry, String levelKey) {
        for (ModdedDimensionRegistryStore.PersistentDimension dimension : registry) {
            if (dimension.id().equals(levelKey)) {
                return true;
            }
        }
        return false;
    }

    private static List<ModdedDimensionRegistryStore.PersistentDimension> repointed(
            List<ModdedDimensionRegistryStore.PersistentDimension> registry,
            Map<String, ModdedDimensionReplacement> replacedWorlds) {
        List<ModdedDimensionRegistryStore.PersistentDimension> updated = new ArrayList<>(registry.size());
        for (ModdedDimensionRegistryStore.PersistentDimension dimension : registry) {
            ModdedDimensionReplacement replacement = replacedWorlds.get(dimension.id());
            updated.add(replacement == null ? dimension : new ModdedDimensionRegistryStore.PersistentDimension(
                    dimension.id(), replacement.pack(), replacement.packDimension(), replacement.seed().getAsLong()));
        }
        return updated;
    }

    private static void refuseOrSkipUninstalled(Path worldRoot, Map<String, ModdedDimensionReplacement> applied,
                                                ModdedDimensionReplacement replacement) throws IOException {
        if (applied.containsKey(replacement.levelKey()) || hasGenerationHistory(replacement.storageFolder(worldRoot))) {
            throw new IOException("Iris refused to switch the replacement of " + replacement.levelKey() + " to "
                    + replacement.packRef() + ": pack '" + replacement.pack() + "' dimension '" + replacement.packDimension()
                    + "' is not installed, and the level already has an applied replacement or Iris generation history. Install the pack or restore the previous replacement.");
        }
        ModdedIrisLog.error("Iris left {} untouched: its replacement pack '{}' dimension '{}' is not installed",
                replacement.levelKey(), replacement.pack(), replacement.packDimension());
    }

    private static boolean hasGenerationHistory(Path storage) {
        return Files.exists(GenerationHistoryPaths.forDimension(storage).generationRoot(), LinkOption.NOFOLLOW_LINKS);
    }

    static OptionalLong matchingHistorySeed(Path storage, ModdedDimensionReplacement replacement) throws IOException {
        if (!hasGenerationHistory(storage)) {
            return OptionalLong.empty();
        }
        GenerationEpoch epoch = GenerationHistory.inspectPacks(storage).manifest().activeEpoch();
        if (replacement.seed().isPresent() && replacement.seed().getAsLong() != epoch.worldSeed()) {
            return OptionalLong.empty();
        }
        GenerationEpoch.DimensionContract contract = epoch.dimensionContract();
        if (!contract.dimensionKey().equals(replacement.packDimension())) {
            return OptionalLong.empty();
        }
        Optional<ModdedWorldgenIds.ScopedDimension> scoped = ModdedWorldgenIds.scopedDimensionType(contract.dimensionTypeKey());
        boolean samePack = scoped.isPresent()
                ? scoped.get().equals(new ModdedWorldgenIds.ScopedDimension(replacement.pack(), replacement.packDimension()))
                : aliasMatches(epoch, contract, replacement);
        return samePack ? OptionalLong.of(epoch.worldSeed()) : OptionalLong.empty();
    }

    private static boolean aliasMatches(GenerationEpoch epoch, GenerationEpoch.DimensionContract contract,
                                        ModdedDimensionReplacement replacement) {
        Set<String> packs = ModdedWorldgenIds.aliasPacks(epoch.registryContract(), contract.dimensionKey());
        return packs.isEmpty() || packs.contains(replacement.pack());
    }

    private static Optional<LiveLevel> liveLevel(NativeModdedServer server, String levelKey) {
        NativeWorld world = server.world(levelKey);
        if (world == null) {
            return Optional.empty();
        }
        IrisModdedChunkGenerator generator = NativeWorldGenerators.find(world, IrisModdedChunkGenerator.class);
        if (generator == null) {
            return Optional.of(new LiveLevel(false, "", 0L));
        }
        return Optional.of(new LiveLevel(true, generator.dimensionKey(), generator.visibleBiomeSeed()));
    }

    private static void requireReadableConfig(Path worldRoot, List<String> configProblems) throws IOException {
        if (configProblems.isEmpty()) {
            return;
        }
        Path state = worldRoot.resolve(STATE_FILE);
        if (!Files.exists(state)) {
            return;
        }
        throw new IOException("Iris refused to reconcile dimension replacements because the configured replacements are unreadable while "
                + state + " records applied replacements: " + String.join("; ", configProblems)
                + ". Fix the modded config so every replacement entry is valid; Iris never treats an unreadable replacement as removed.");
    }

    static State readState(Path worldRoot) throws IOException {
        Path state = worldRoot.resolve(STATE_FILE);
        if (!Files.isRegularFile(state)) {
            return State.EMPTY;
        }
        JSONObject json = new JSONObject(Files.readString(state, StandardCharsets.UTF_8));
        return new State(readSection(json, APPLIED, state), readSection(json, PENDING, state));
    }

    static void writeState(Path worldRoot, State replacements) throws IOException {
        Path state = worldRoot.resolve(STATE_FILE);
        Files.createDirectories(state.getParent());
        JSONObject json = new JSONObject();
        json.put(APPLIED, section(replacements.applied()));
        json.put(PENDING, section(replacements.pending()));
        Path temporary = state.resolveSibling(state.getFileName() + ".tmp-" + UUID.randomUUID());
        Files.writeString(temporary, json.toString(2), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, state, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicUnsupported) {
            Files.move(temporary, state, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Map<String, ModdedDimensionReplacement> readSection(JSONObject json, String name, Path state)
            throws IOException {
        JSONObject section = json.optJSONObject(name);
        if (section == null) {
            return Map.of();
        }
        Map<String, ModdedDimensionReplacement> replacements = new TreeMap<>();
        for (String level : section.keySet()) {
            try {
                replacements.put(level, ModdedDimensionReplacement.fromJson(level, section.opt(level)));
            } catch (IllegalArgumentException invalid) {
                throw new IOException("Iris could not read replacement '" + level + "' in " + state + ": "
                        + invalid.getMessage(), invalid);
            }
        }
        return Map.copyOf(replacements);
    }

    private static JSONObject section(Map<String, ModdedDimensionReplacement> replacements) {
        JSONObject section = new JSONObject();
        for (Map.Entry<String, ModdedDimensionReplacement> entry : new TreeMap<>(replacements).entrySet()) {
            section.put(entry.getKey(), entry.getValue().toJson());
        }
        return section;
    }

    interface LiveLevels {
        Optional<LiveLevel> level(String levelKey);
    }

    record LiveLevel(boolean iris, String packRef, long seed) {
        LiveLevel {
            Objects.requireNonNull(packRef, "packRef");
        }

        boolean generates(ModdedDimensionReplacement replacement) {
            return iris && packRef.equals(replacement.packRef())
                    && (replacement.seed().isEmpty() || replacement.seed().getAsLong() == seed);
        }
    }

    record State(Map<String, ModdedDimensionReplacement> applied, Map<String, ModdedDimensionReplacement> pending) {
        static final State EMPTY = new State(Map.of(), Map.of());

        State {
            applied = Map.copyOf(applied);
            pending = Map.copyOf(pending);
        }
    }

    record Request(Path worldRoot, Map<String, ModdedDimensionReplacement> configured, List<String> configProblems,
                   Predicate<ModdedDimensionReplacement> installed, Path recoveryParent,
                   ModdedDimensionReplacements.ReplaceableLevels levels) {
    }

    record Result(Set<String> quarantinedLevels, Set<String> adoptedLevels, Set<String> replacedWorlds,
                  Set<String> unreplaceableLevels, Path recovery) {
    }
}
