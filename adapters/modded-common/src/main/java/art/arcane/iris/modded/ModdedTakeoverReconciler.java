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
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public final class ModdedTakeoverReconciler {
    private static final String STATE_FILE = "data/irisworldgen/dimension-takeovers.json";
    private static final String TAKEOVERS = "takeovers";

    private ModdedTakeoverReconciler() {
    }

    public static void reconcileEarly() {
        try {
            ModdedModConfig config = ModdedModConfig.get();
            Map<String, String> configured = ModdedDimensionTakeovers.configured();
            Path worldRoot = MainWorldService.configuredWorldRootIfPresent();
            if (worldRoot == null) {
                if (!configured.isEmpty()) {
                    ModdedIrisLog.info("Iris dimension takeovers {} apply to a new world; nothing to quarantine",
                            configured.keySet());
                }
                return;
            }
            Path recoveryParent = ModdedEngineBootstrap.loader().configDir().resolve("irisworldgen");
            Path packsRoot = IrisPlatforms.get().packsFolderNoCreate().toPath();
            Result result = reconcile(new Request(worldRoot, configured, config.takeoverProblems(),
                    (ModdedDimensionTakeover takeover) -> ModdedDimensionTakeovers.installed(takeover, packsRoot), recoveryParent));
            if (!result.adoptedLevels().isEmpty()) {
                ModdedIrisLog.info("Iris dimension takeover recorded for {}: their existing Iris generation history already uses the configured pack",
                        result.adoptedLevels());
            }
            if (result.quarantinedLevels().isEmpty()) {
                return;
            }
            ModdedIrisLog.warn("Iris dimension takeover changed for {}: {}", result.quarantinedLevels(),
                    result.recovery() == null
                            ? "no existing chunk data to quarantine"
                            : "moved their chunk, entity, POI, and Iris Mantle data to " + result.recovery()
                            + " so they regenerate (player data kept)");
        } catch (Throwable failure) {
            ModdedIrisLog.error("Iris dimension takeover reconciliation failed", failure);
            throw new IllegalStateException("Iris refused startup after dimension takeover reconciliation failed", failure);
        }
    }

    public static void recordApplied(NativeModdedServer server) {
        Optional<Path> worldRoot = NativeWorldPaths.root(server);
        if (worldRoot.isEmpty()) {
            return;
        }
        try {
            recordApplied(worldRoot.get(), ModdedDimensionTakeovers.configured(),
                    (String levelKey, String packRef) -> generatesFrom(server, levelKey, packRef));
        } catch (IOException | RuntimeException failure) {
            ModdedIrisLog.error("Iris is stopping the server because it could not record the applied dimension takeovers under {}; an unrecorded takeover would be quarantined on the next start",
                    worldRoot.get(), failure);
            server.halt(false);
        }
    }

    static Map<String, String> recordApplied(Path worldRoot, Map<String, String> configured,
                                             BiPredicate<String, String> generating) throws IOException {
        Map<String, String> applied = readApplied(worldRoot);
        Map<String, String> recorded = new TreeMap<>(applied);
        for (Map.Entry<String, String> entry : new TreeMap<>(configured).entrySet()) {
            if (generating.test(entry.getKey(), entry.getValue())) {
                recorded.put(entry.getKey(), entry.getValue());
                continue;
            }
            ModdedIrisLog.warn("Iris dimension takeover of {} is configured but the level does not generate from {}; it stays unapplied",
                    entry.getKey(), entry.getValue());
        }
        if (!recorded.equals(applied)) {
            writeApplied(worldRoot, recorded);
        }
        return Map.copyOf(recorded);
    }

    static Result reconcile(Request request) throws IOException {
        Path worldRoot = request.worldRoot();
        requireReadableConfig(worldRoot, request.configProblems());
        Map<String, String> applied = readApplied(worldRoot);
        Set<String> changed = ModdedDimensionTakeovers.changedLevels(applied, request.configured());
        Map<String, String> state = new TreeMap<>(applied);
        Set<String> quarantined = new TreeSet<>();
        Set<String> adopted = new TreeSet<>();
        for (String level : changed) {
            String packRef = request.configured().get(level);
            if (packRef == null) {
                state.remove(level);
                quarantined.add(level);
                continue;
            }
            ModdedDimensionTakeover takeover = ModdedDimensionTakeover.parse(level, packRef);
            Path storage = takeover.storageFolder(worldRoot);
            if (historyMatches(storage, takeover)) {
                state.put(level, packRef);
                adopted.add(level);
            } else if (request.installed().test(takeover)) {
                quarantined.add(level);
            } else {
                refuseOrSkipUninstalled(worldRoot, applied, takeover);
            }
        }
        List<Path> sources = new ArrayList<>();
        for (String level : quarantined) {
            Path storage = ModdedDimensionTakeover.storageFolder(worldRoot, level);
            for (String folder : ModdedDimensionStorage.chunkDataFolders()) {
                sources.add(storage.resolve(folder));
            }
        }
        Path recovery = request.recoveryParent().resolve("takeover-recovery-" + UUID.randomUUID());
        List<Path> moved = sources.isEmpty() ? List.of() : ModdedWorldQuarantine.move(worldRoot, sources, recovery);
        if (!state.equals(applied)) {
            writeApplied(worldRoot, state);
        }
        return new Result(Set.copyOf(quarantined), Set.copyOf(adopted), moved.isEmpty() ? null : recovery);
    }

    static boolean hasTakeoverRecord(Path worldRoot, String levelKey) throws IOException {
        return readApplied(worldRoot).containsKey(levelKey) || hasGenerationHistory(
                ModdedDimensionTakeover.storageFolder(worldRoot, levelKey));
    }

    private static void refuseOrSkipUninstalled(Path worldRoot, Map<String, String> applied,
                                                ModdedDimensionTakeover takeover) throws IOException {
        if (applied.containsKey(takeover.levelKey()) || hasGenerationHistory(takeover.storageFolder(worldRoot))) {
            throw new IOException("Iris refused to switch the dimension takeover of " + takeover.levelKey() + " to "
                    + takeover.packRef() + ": pack '" + takeover.pack() + "' dimension '" + takeover.packDimension()
                    + "' is not installed, and the level already has an applied takeover or Iris generation history. Install the pack or restore the previous takeover.");
        }
        ModdedIrisLog.error("Iris left {} untouched: its takeover pack '{}' dimension '{}' is not installed",
                takeover.levelKey(), takeover.pack(), takeover.packDimension());
    }

    private static boolean hasGenerationHistory(Path storage) {
        return Files.exists(GenerationHistoryPaths.forDimension(storage).generationRoot(), LinkOption.NOFOLLOW_LINKS);
    }

    static boolean historyMatches(Path storage, ModdedDimensionTakeover takeover) throws IOException {
        if (!hasGenerationHistory(storage)) {
            return false;
        }
        GenerationEpoch epoch = GenerationHistory.inspectPacks(storage).manifest().activeEpoch();
        GenerationEpoch.DimensionContract contract = epoch.dimensionContract();
        if (!contract.dimensionKey().equals(takeover.packDimension())) {
            return false;
        }
        Optional<ModdedWorldgenIds.ScopedDimension> scoped = ModdedWorldgenIds.scopedDimensionType(contract.dimensionTypeKey());
        if (scoped.isPresent()) {
            return scoped.get().equals(new ModdedWorldgenIds.ScopedDimension(takeover.pack(), takeover.packDimension()));
        }
        Set<String> packs = ModdedWorldgenIds.aliasPacks(epoch.registryContract(), contract.dimensionKey());
        return packs.isEmpty() || packs.contains(takeover.pack());
    }

    private static boolean generatesFrom(NativeModdedServer server, String levelKey, String packRef) {
        NativeWorld world = server.world(levelKey);
        if (world == null) {
            return false;
        }
        IrisModdedChunkGenerator generator = NativeWorldGenerators.find(world, IrisModdedChunkGenerator.class);
        return generator != null && packRef.equals(generator.dimensionKey());
    }

    private static void requireReadableConfig(Path worldRoot, List<String> configProblems) throws IOException {
        if (configProblems.isEmpty()) {
            return;
        }
        Path state = worldRoot.resolve(STATE_FILE);
        if (!Files.exists(state)) {
            return;
        }
        throw new IOException("Iris refused to reconcile dimension takeovers because the configured takeovers are unreadable while "
                + state + " records applied takeovers: " + String.join("; ", configProblems)
                + ". Fix the modded config so every takeover entry is valid; Iris never treats an unreadable takeover as removed.");
    }

    static Map<String, String> readApplied(Path worldRoot) throws IOException {
        Path state = worldRoot.resolve(STATE_FILE);
        if (!Files.isRegularFile(state)) {
            return Map.of();
        }
        JSONObject takeovers = new JSONObject(Files.readString(state, StandardCharsets.UTF_8)).optJSONObject(TAKEOVERS);
        if (takeovers == null) {
            return Map.of();
        }
        Map<String, String> applied = new TreeMap<>();
        for (String level : takeovers.keySet()) {
            applied.put(level, takeovers.getString(level));
        }
        return Map.copyOf(applied);
    }

    static void writeApplied(Path worldRoot, Map<String, String> takeovers) throws IOException {
        Path state = worldRoot.resolve(STATE_FILE);
        Files.createDirectories(state.getParent());
        JSONObject entries = new JSONObject();
        for (Map.Entry<String, String> entry : new TreeMap<>(takeovers).entrySet()) {
            entries.put(entry.getKey(), entry.getValue());
        }
        JSONObject json = new JSONObject();
        json.put(TAKEOVERS, entries);
        Path temporary = state.resolveSibling(state.getFileName() + ".tmp-" + UUID.randomUUID());
        Files.writeString(temporary, json.toString(2), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, state, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicUnsupported) {
            Files.move(temporary, state, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    record Request(Path worldRoot, Map<String, String> configured, List<String> configProblems,
                   Predicate<ModdedDimensionTakeover> installed, Path recoveryParent) {
    }

    record Result(Set<String> quarantinedLevels, Set<String> adoptedLevels, Path recovery) {
    }
}
