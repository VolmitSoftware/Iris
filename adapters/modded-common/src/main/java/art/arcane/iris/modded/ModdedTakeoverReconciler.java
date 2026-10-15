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

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeWorldPaths;
import art.arcane.volmlib.util.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

public final class ModdedTakeoverReconciler {
    private static final String STATE_FILE = "data/irisworldgen/dimension-takeovers.json";
    private static final String TAKEOVERS = "takeovers";

    private ModdedTakeoverReconciler() {
    }

    public static void reconcileEarly() {
        try {
            Map<String, String> configured = ModdedModConfig.get().dimensionTakeovers();
            Path worldRoot = MainWorldService.configuredWorldRootIfPresent();
            if (worldRoot == null) {
                if (!configured.isEmpty()) {
                    ModdedIrisLog.info("Iris dimension takeovers {} apply to a new world; nothing to quarantine",
                            configured.keySet());
                }
                return;
            }
            Path recoveryParent = ModdedEngineBootstrap.loader().configDir().resolve("irisworldgen");
            Result result = reconcile(worldRoot, configured, recoveryParent);
            if (result.changedLevels().isEmpty()) {
                return;
            }
            ModdedIrisLog.warn("Iris dimension takeover changed for {}: {}", result.changedLevels(),
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
        Map<String, String> configured = ModdedModConfig.get().dimensionTakeovers();
        try {
            if (readApplied(worldRoot.get()).equals(configured)) {
                return;
            }
            writeApplied(worldRoot.get(), configured);
        } catch (IOException | RuntimeException failure) {
            ModdedIrisLog.error("Iris could not record the applied dimension takeovers under {}", worldRoot.get(), failure);
        }
    }

    static Result reconcile(Path worldRoot, Map<String, String> configured, Path recoveryParent) throws IOException {
        Map<String, String> applied = readApplied(worldRoot);
        Set<String> changed = ModdedDimensionTakeovers.changedLevels(applied, configured);
        if (changed.isEmpty()) {
            return new Result(changed, null);
        }
        List<Path> sources = new ArrayList<>();
        for (String level : changed) {
            Path storage = ModdedDimensionTakeover.storageFolder(worldRoot, level);
            for (String folder : ModdedDimensionStorage.chunkDataFolders()) {
                sources.add(storage.resolve(folder));
            }
        }
        Path recovery = recoveryParent.resolve("takeover-recovery-" + UUID.randomUUID());
        List<Path> moved = ModdedWorldQuarantine.move(worldRoot, sources, recovery);
        writeApplied(worldRoot, configured);
        return new Result(changed, moved.isEmpty() ? null : recovery);
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

    record Result(Set<String> changedLevels, Path recovery) {
    }
}
