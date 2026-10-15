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

import art.arcane.iris.world.history.GenerationEpoch;
import art.arcane.iris.world.history.GenerationHistory;
import art.arcane.iris.world.history.GenerationPackFingerprint;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ModdedTakeoverReconcilerTest {
    @Test
    public void appliedStateRoundTripsAndDefaultsToEmpty() throws IOException {
        Path worldRoot = Files.createTempDirectory("iris-takeover-state");
        try {
            assertEquals(Map.of(), ModdedTakeoverReconciler.readApplied(worldRoot));

            ModdedTakeoverReconciler.writeApplied(worldRoot, Map.of("iristest:skylands", "overworld:sky"));

            assertEquals(Map.of("iristest:skylands", "overworld:sky"), ModdedTakeoverReconciler.readApplied(worldRoot));
        } finally {
            deleteTree(worldRoot);
        }
    }

    @Test
    public void newTakeoverQuarantinesOnlyThatLevelsChunkDataAndKeepsPlayerData() throws IOException {
        Path temporary = Files.createTempDirectory("iris-takeover-reconcile");
        Path worldRoot = temporary.resolve("world");
        Path recoveryParent = temporary.resolve("config");
        try {
            Path skylands = worldRoot.resolve("dimensions/iristest/skylands");
            for (String folder : List.of("region", "entities", "poi", "mantle-hydrology", "iris", "data")) {
                touch(skylands.resolve(folder).resolve("payload.bin"));
            }
            touch(worldRoot.resolve("dimensions/minecraft/overworld/region/r.0.0.mca"));
            touch(worldRoot.resolve("players/data/player.dat"));

            ModdedTakeoverReconciler.Result result = ModdedTakeoverReconciler.reconcile(
                    worldRoot, Map.of("iristest:skylands", "overworld"), List.of(), recoveryParent);

            assertEquals(Set.of("iristest:skylands"), result.changedLevels());
            for (String folder : List.of("region", "entities", "poi", "mantle-hydrology", "iris")) {
                assertFalse(folder, Files.exists(skylands.resolve(folder)));
                assertTrue(folder, Files.isRegularFile(result.recovery()
                        .resolve("dimensions/iristest/skylands").resolve(folder).resolve("payload.bin")));
            }
            assertTrue(Files.isRegularFile(skylands.resolve("data/payload.bin")));
            assertTrue(Files.isRegularFile(worldRoot.resolve("dimensions/minecraft/overworld/region/r.0.0.mca")));
            assertTrue(Files.isRegularFile(worldRoot.resolve("players/data/player.dat")));
            assertEquals(Map.of(), ModdedTakeoverReconciler.readApplied(worldRoot));
        } finally {
            deleteTree(temporary);
        }
    }

    @Test
    public void unchangedTakeoverIsANoOpAndRemovalQuarantinesTheIrisChunks() throws IOException {
        Path temporary = Files.createTempDirectory("iris-takeover-cycle");
        Path worldRoot = temporary.resolve("world");
        Path recoveryParent = temporary.resolve("config");
        try {
            Path region = worldRoot.resolve("dimensions/iristest/skylands/region/r.0.0.mca");
            ModdedTakeoverReconciler.writeApplied(worldRoot, Map.of("iristest:skylands", "overworld"));
            touch(region);

            ModdedTakeoverReconciler.Result unchanged = ModdedTakeoverReconciler.reconcile(
                    worldRoot, Map.of("iristest:skylands", "overworld"), List.of(), recoveryParent);

            assertEquals(Set.of(), unchanged.changedLevels());
            assertNull(unchanged.recovery());
            assertTrue(Files.isRegularFile(region));

            ModdedTakeoverReconciler.Result removed = ModdedTakeoverReconciler.reconcile(
                    worldRoot, Map.of(), List.of(), recoveryParent);

            assertEquals(Set.of("iristest:skylands"), removed.changedLevels());
            assertFalse(Files.exists(region));
            assertTrue(Files.isRegularFile(removed.recovery().resolve("dimensions/iristest/skylands/region/r.0.0.mca")));
            assertEquals(Map.of(), ModdedTakeoverReconciler.readApplied(worldRoot));
        } finally {
            deleteTree(temporary);
        }
    }

    @Test
    public void newTakeoverWithoutChunkDataStaysPendingWithoutARecoveryFolder() throws IOException {
        Path temporary = Files.createTempDirectory("iris-takeover-empty");
        Path worldRoot = temporary.resolve("world");
        Path recoveryParent = temporary.resolve("config");
        try {
            Files.createDirectories(worldRoot);

            ModdedTakeoverReconciler.Result result = ModdedTakeoverReconciler.reconcile(
                    worldRoot, Map.of("aether:the_aether", "overworld"), List.of(), recoveryParent);

            assertEquals(Set.of("aether:the_aether"), result.changedLevels());
            assertNull(result.recovery());
            assertFalse(Files.exists(recoveryParent));
            assertEquals(Map.of(), ModdedTakeoverReconciler.readApplied(worldRoot));
        } finally {
            deleteTree(temporary);
        }
    }

    @Test
    public void unreadableConfigWithRecordedTakeoversRefusesWithoutMovingAnything() throws IOException {
        Path temporary = Files.createTempDirectory("iris-takeover-unreadable");
        Path worldRoot = temporary.resolve("world");
        Path recoveryParent = temporary.resolve("config");
        try {
            Path region = worldRoot.resolve("dimensions/iristest/skylands/region/r.0.0.mca");
            ModdedTakeoverReconciler.writeApplied(worldRoot, Map.of("iristest:skylands", "overworld"));
            touch(region);
            List<String> problems = List.of("Iris could not read /srv/config/irisworldgen/modded.json: Unterminated object");

            IOException refused = assertThrows(IOException.class, () -> ModdedTakeoverReconciler.reconcile(
                    worldRoot, Map.of(), problems, recoveryParent));

            assertTrue(refused.getMessage(), refused.getMessage().contains("modded.json"));
            assertTrue(Files.isRegularFile(region));
            assertFalse(Files.exists(recoveryParent));
            assertEquals(Map.of("iristest:skylands", "overworld"), ModdedTakeoverReconciler.readApplied(worldRoot));
        } finally {
            deleteTree(temporary);
        }
    }

    @Test
    public void unreadableConfigWithAnEmptyStateFileStillRefuses() throws IOException {
        Path temporary = Files.createTempDirectory("iris-takeover-unreadable-empty");
        Path worldRoot = temporary.resolve("world");
        try {
            ModdedTakeoverReconciler.writeApplied(worldRoot, Map.of());

            assertThrows(IOException.class, () -> ModdedTakeoverReconciler.reconcile(
                    worldRoot, Map.of(), List.of("invalid takeover entry"), temporary.resolve("config")));
        } finally {
            deleteTree(temporary);
        }
    }

    @Test
    public void unreadableConfigWithoutRecordedStateProceeds() throws IOException {
        Path temporary = Files.createTempDirectory("iris-takeover-unreadable-fresh");
        Path worldRoot = temporary.resolve("world");
        try {
            Files.createDirectories(worldRoot);

            ModdedTakeoverReconciler.Result result = ModdedTakeoverReconciler.reconcile(
                    worldRoot, Map.of(), List.of("invalid takeover entry"), temporary.resolve("config"));

            assertEquals(Set.of(), result.changedLevels());
        } finally {
            deleteTree(temporary);
        }
    }

    @Test
    public void changedTakeoverKeepsThePreviousStateUntilTheNewPackGenerates() throws IOException {
        Path temporary = Files.createTempDirectory("iris-takeover-change");
        Path worldRoot = temporary.resolve("world");
        try {
            Path region = worldRoot.resolve("dimensions/iristest/skylands/region/r.0.0.mca");
            ModdedTakeoverReconciler.writeApplied(worldRoot, Map.of("iristest:skylands", "overworld"));
            touch(region);

            ModdedTakeoverReconciler.Result result = ModdedTakeoverReconciler.reconcile(
                    worldRoot, Map.of("iristest:skylands", "skypack"), List.of(), temporary.resolve("config"));

            assertEquals(Set.of("iristest:skylands"), result.changedLevels());
            assertFalse(Files.exists(region));
            assertEquals(Map.of("iristest:skylands", "overworld"), ModdedTakeoverReconciler.readApplied(worldRoot));
        } finally {
            deleteTree(temporary);
        }
    }

    @Test
    public void existingHistoryOfTheConfiguredPackIsAdoptedWithoutQuarantine() throws IOException {
        Path temporary = Files.createTempDirectory("iris-takeover-history-match");
        Path worldRoot = temporary.resolve("world");
        try {
            Path skylands = worldRoot.resolve("dimensions/iristest/skylands");
            createHistory(temporary, skylands, ModdedWorldgenIds.dimensionTypeRef("skypack", "sky"), "sky", List.of());
            touch(skylands.resolve("region/r.0.0.mca"));

            ModdedTakeoverReconciler.Result result = ModdedTakeoverReconciler.reconcile(
                    worldRoot, Map.of("iristest:skylands", "skypack:sky"), List.of(), temporary.resolve("config"));

            assertEquals(Set.of("iristest:skylands"), result.adoptedLevels());
            assertNull(result.recovery());
            assertTrue(Files.isRegularFile(skylands.resolve("region/r.0.0.mca")));
            assertEquals(Map.of("iristest:skylands", "skypack:sky"), ModdedTakeoverReconciler.readApplied(worldRoot));
        } finally {
            deleteTree(temporary);
        }
    }

    @Test
    public void historyOfAnotherPackIsQuarantined() throws IOException {
        Path temporary = Files.createTempDirectory("iris-takeover-history-other");
        Path worldRoot = temporary.resolve("world");
        try {
            Path skylands = worldRoot.resolve("dimensions/iristest/skylands");
            createHistory(temporary, skylands, ModdedWorldgenIds.dimensionTypeRef("otherpack", "sky"), "sky", List.of());
            touch(skylands.resolve("region/r.0.0.mca"));

            ModdedTakeoverReconciler.Result result = ModdedTakeoverReconciler.reconcile(
                    worldRoot, Map.of("iristest:skylands", "skypack:sky"), List.of(), temporary.resolve("config"));

            assertEquals(Set.of(), result.adoptedLevels());
            assertFalse(Files.exists(skylands.resolve("region")));
            assertFalse(Files.exists(skylands.resolve("iris")));
            assertEquals(Map.of(), ModdedTakeoverReconciler.readApplied(worldRoot));
        } finally {
            deleteTree(temporary);
        }
    }

    @Test
    public void borrowedHistoryMatchesOnItsRecordedAliasPack() throws IOException {
        Path temporary = Files.createTempDirectory("iris-takeover-history-borrowed");
        Path worldRoot = temporary.resolve("world");
        try {
            Path aether = worldRoot.resolve("dimensions/aether/the_aether");
            createHistory(temporary, aether, "aether:the_aether", "sky",
                    List.of(ModdedWorldgenIds.biomeRef("skypack", "sky", "meadow")));
            touch(aether.resolve("region/r.0.0.mca"));

            ModdedTakeoverReconciler.Result other = ModdedTakeoverReconciler.reconcile(
                    worldRoot, Map.of("aether:the_aether", "otherpack:sky"), List.of(), temporary.resolve("config"));

            assertEquals(Set.of(), other.adoptedLevels());
            assertFalse(Files.exists(aether.resolve("region")));

            createHistory(temporary, aether, "aether:the_aether", "sky",
                    List.of(ModdedWorldgenIds.biomeRef("skypack", "sky", "meadow")));
            ModdedTakeoverReconciler.Result matching = ModdedTakeoverReconciler.reconcile(
                    worldRoot, Map.of("aether:the_aether", "skypack:sky"), List.of(), temporary.resolve("config"));

            assertEquals(Set.of("aether:the_aether"), matching.adoptedLevels());
            assertTrue(Files.isDirectory(aether.resolve("iris")));
        } finally {
            deleteTree(temporary);
        }
    }

    @Test
    public void recordAppliedStoresOnlyTakeoversThatGenerateFromTheirPack() throws IOException {
        Path temporary = Files.createTempDirectory("iris-takeover-record");
        Path worldRoot = temporary.resolve("world");
        try {
            ModdedTakeoverReconciler.writeApplied(worldRoot, Map.of("iristest:kept", "overworld"));

            Map<String, String> recorded = ModdedTakeoverReconciler.recordApplied(worldRoot,
                    Map.of("iristest:kept", "overworld", "iristest:live", "skypack", "iristest:skipped", "skypack"),
                    (String level, String packRef) -> !level.equals("iristest:skipped"));

            Map<String, String> expected = Map.of("iristest:kept", "overworld", "iristest:live", "skypack");
            assertEquals(expected, recorded);
            assertEquals(expected, ModdedTakeoverReconciler.readApplied(worldRoot));
        } finally {
            deleteTree(temporary);
        }
    }

    @Test
    public void recordAppliedFailsWhenTheStateCannotBeWritten() throws IOException {
        Path temporary = Files.createTempDirectory("iris-takeover-record-fail");
        Path worldRoot = temporary.resolve("world");
        try {
            touch(worldRoot.resolve("data/irisworldgen/dimension-takeovers.json/blocker"));

            assertThrows(IOException.class, () -> ModdedTakeoverReconciler.recordApplied(worldRoot,
                    Map.of("iristest:live", "skypack"), (String level, String packRef) -> true));
        } finally {
            deleteTree(temporary);
        }
    }

    private static void createHistory(Path temporary, Path dimensionRoot, String dimensionTypeKey, String dimensionKey,
                                      List<String> biomeKeys) throws IOException {
        Path source = Files.createTempDirectory(temporary, "pack");
        touch(source.resolve("dimensions/" + dimensionKey + ".json"));
        GenerationEpoch epoch = ModdedRetainedPackNameTest.epoch(dimensionTypeKey, dimensionKey, biomeKeys);
        Files.createDirectories(dimensionRoot);
        GenerationHistory.create(dimensionRoot, source,
                GenerationPackFingerprint.compute(source, GenerationPackFingerprint.CURRENT_VERSION), 5L,
                epoch.dimensionContract(), epoch.registryContract());
    }

    private static void touch(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "x", StandardCharsets.UTF_8);
    }

    static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
