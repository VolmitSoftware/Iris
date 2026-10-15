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
                    worldRoot, Map.of("iristest:skylands", "overworld"), recoveryParent);

            assertEquals(Set.of("iristest:skylands"), result.changedLevels());
            for (String folder : List.of("region", "entities", "poi", "mantle-hydrology", "iris")) {
                assertFalse(folder, Files.exists(skylands.resolve(folder)));
                assertTrue(folder, Files.isRegularFile(result.recovery()
                        .resolve("dimensions/iristest/skylands").resolve(folder).resolve("payload.bin")));
            }
            assertTrue(Files.isRegularFile(skylands.resolve("data/payload.bin")));
            assertTrue(Files.isRegularFile(worldRoot.resolve("dimensions/minecraft/overworld/region/r.0.0.mca")));
            assertTrue(Files.isRegularFile(worldRoot.resolve("players/data/player.dat")));
            assertEquals(Map.of("iristest:skylands", "overworld"), ModdedTakeoverReconciler.readApplied(worldRoot));
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
                    worldRoot, Map.of("iristest:skylands", "overworld"), recoveryParent);

            assertEquals(Set.of(), unchanged.changedLevels());
            assertNull(unchanged.recovery());
            assertTrue(Files.isRegularFile(region));

            ModdedTakeoverReconciler.Result removed = ModdedTakeoverReconciler.reconcile(
                    worldRoot, Map.of(), recoveryParent);

            assertEquals(Set.of("iristest:skylands"), removed.changedLevels());
            assertFalse(Files.exists(region));
            assertTrue(Files.isRegularFile(removed.recovery().resolve("dimensions/iristest/skylands/region/r.0.0.mca")));
            assertEquals(Map.of(), ModdedTakeoverReconciler.readApplied(worldRoot));
        } finally {
            deleteTree(temporary);
        }
    }

    @Test
    public void changeWithoutChunkDataRecordsStateWithoutARecoveryFolder() throws IOException {
        Path temporary = Files.createTempDirectory("iris-takeover-empty");
        Path worldRoot = temporary.resolve("world");
        Path recoveryParent = temporary.resolve("config");
        try {
            Files.createDirectories(worldRoot);

            ModdedTakeoverReconciler.Result result = ModdedTakeoverReconciler.reconcile(
                    worldRoot, Map.of("aether:the_aether", "overworld"), recoveryParent);

            assertEquals(Set.of("aether:the_aether"), result.changedLevels());
            assertNull(result.recovery());
            assertFalse(Files.exists(recoveryParent));
            assertEquals(Map.of("aether:the_aether", "overworld"), ModdedTakeoverReconciler.readApplied(worldRoot));
        } finally {
            deleteTree(temporary);
        }
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
