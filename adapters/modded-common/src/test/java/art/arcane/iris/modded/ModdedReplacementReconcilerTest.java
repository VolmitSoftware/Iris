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
import art.arcane.iris.world.lifecycle.WorldReplacementEntryGuard;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static art.arcane.iris.modded.ModdedDimensionReplacementsTest.replacement;
import static art.arcane.iris.modded.ModdedDimensionReplacementsTest.seeded;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ModdedReplacementReconcilerTest {
    @Test
    public void stateRoundTripsAppliedAndPendingEntriesAndDefaultsToEmpty() throws IOException {
        Path worldRoot = Files.createTempDirectory("iris-replace-state");
        try {
            assertEquals(ModdedReplacementReconciler.State.EMPTY, ModdedReplacementReconciler.readState(worldRoot));

            ModdedReplacementReconciler.State state = new ModdedReplacementReconciler.State(
                    Map.of("iristest:skylands", seeded("iristest:skylands", "overworld:sky", 5L)),
                    Map.of("irisworldgen:myworld", seeded("irisworldgen:myworld", "underworld", 9L)));
            ModdedReplacementReconciler.writeState(worldRoot, state);

            assertEquals(state, ModdedReplacementReconciler.readState(worldRoot));
            assertTrue(Files.isRegularFile(worldRoot.resolve("data/irisworldgen/dimension-replacements.json")));
        } finally {
            deleteTree(worldRoot);
        }
    }

    @Test
    public void newReplacementQuarantinesOnlyThatLevelsChunkDataAndKeepsPlayerData() throws IOException {
        Path temporary = Files.createTempDirectory("iris-replace-reconcile");
        Path worldRoot = temporary.resolve("world");
        Path recoveryParent = temporary.resolve("config");
        try {
            Path skylands = worldRoot.resolve("dimensions/iristest/skylands");
            for (String folder : List.of("region", "entities", "poi", "mantle-hydrology", "iris", "data")) {
                touch(skylands.resolve(folder).resolve("payload.bin"));
            }
            touch(worldRoot.resolve("dimensions/minecraft/overworld/region/r.0.0.mca"));
            touch(worldRoot.resolve("players/data/player.dat"));

            ModdedReplacementReconciler.Result result = reconcile(worldRoot,
                    Map.of("iristest:skylands", replacement("iristest:skylands", "overworld")), List.of(), recoveryParent);

            assertEquals(Set.of("iristest:skylands"), result.quarantinedLevels());
            assertTrue(result.recovery().getFileName().toString().startsWith("replacement-recovery-"));
            for (String folder : List.of("region", "entities", "poi", "mantle-hydrology", "iris")) {
                assertFalse(folder, Files.exists(skylands.resolve(folder)));
                assertTrue(folder, Files.isRegularFile(result.recovery()
                        .resolve("dimensions/iristest/skylands").resolve(folder).resolve("payload.bin")));
            }
            assertTrue(Files.isRegularFile(skylands.resolve("data/payload.bin")));
            assertTrue(Files.isRegularFile(worldRoot.resolve("dimensions/minecraft/overworld/region/r.0.0.mca")));
            assertTrue(Files.isRegularFile(worldRoot.resolve("players/data/player.dat")));
            assertTrue(WorldReplacementEntryGuard.load(skylands).isEmpty());
            assertEquals(Map.of(), ModdedReplacementReconciler.readState(worldRoot).applied());
        } finally {
            deleteTree(temporary);
        }
    }

    @Test
    public void overworldReplacementGuardsEveryKnownPlayerUntilTheyEnterSafely() throws IOException {
        Path temporary = Files.createTempDirectory("iris-replace-overworld");
        Path worldRoot = temporary.resolve("world");
        try {
            UUID player = UUID.randomUUID();
            Path overworld = worldRoot.resolve("dimensions/minecraft/overworld");
            touch(overworld.resolve("region/r.0.0.mca"));
            touch(worldRoot.resolve("players/data/" + player + ".dat"));

            ModdedReplacementReconciler.Result result = reconcile(worldRoot,
                    Map.of("minecraft:overworld", seeded("minecraft:overworld", "overworld", 123L)), List.of(),
                    temporary.resolve("config"));

            assertEquals(Set.of("minecraft:overworld"), result.quarantinedLevels());
            assertFalse(Files.exists(overworld.resolve("region")));
            Optional<WorldReplacementEntryGuard.Entry> guard = WorldReplacementEntryGuard.load(overworld);
            assertTrue(guard.isPresent());
            assertEquals(Set.of(player), guard.get().pendingPlayers());
        } finally {
            deleteTree(temporary);
        }
    }

    @Test
    public void unchangedReplacementIsANoOpAndRemovalQuarantinesTheIrisChunks() throws IOException {
        Path temporary = Files.createTempDirectory("iris-replace-cycle");
        Path worldRoot = temporary.resolve("world");
        Path recoveryParent = temporary.resolve("config");
        try {
            Path region = worldRoot.resolve("dimensions/iristest/skylands/region/r.0.0.mca");
            writeApplied(worldRoot, seeded("iristest:skylands", "overworld", 5L));
            touch(region);

            ModdedReplacementReconciler.Result unchanged = reconcile(worldRoot,
                    Map.of("iristest:skylands", replacement("iristest:skylands", "overworld")), List.of(), recoveryParent);

            assertEquals(Set.of(), unchanged.quarantinedLevels());
            assertNull(unchanged.recovery());
            assertTrue(Files.isRegularFile(region));

            ModdedReplacementReconciler.Result removed = reconcile(worldRoot, Map.of(), List.of(), recoveryParent);

            assertEquals(Set.of("iristest:skylands"), removed.quarantinedLevels());
            assertFalse(Files.exists(region));
            assertTrue(Files.isRegularFile(removed.recovery().resolve("dimensions/iristest/skylands/region/r.0.0.mca")));
            assertEquals(Map.of(), ModdedReplacementReconciler.readState(worldRoot).applied());
        } finally {
            deleteTree(temporary);
        }
    }

    @Test
    public void newReplacementWithoutChunkDataStaysPendingWithoutARecoveryFolder() throws IOException {
        Path temporary = Files.createTempDirectory("iris-replace-empty");
        Path worldRoot = temporary.resolve("world");
        Path recoveryParent = temporary.resolve("config");
        try {
            Files.createDirectories(worldRoot);

            ModdedReplacementReconciler.Result result = reconcile(worldRoot,
                    Map.of("aether:the_aether", replacement("aether:the_aether", "overworld")), List.of(), recoveryParent);

            assertEquals(Set.of("aether:the_aether"), result.quarantinedLevels());
            assertNull(result.recovery());
            assertFalse(Files.exists(recoveryParent));
            assertEquals(Map.of(), ModdedReplacementReconciler.readState(worldRoot).applied());
        } finally {
            deleteTree(temporary);
        }
    }

    @Test
    public void unreadableConfigWithRecordedReplacementsRefusesWithoutMovingAnything() throws IOException {
        Path temporary = Files.createTempDirectory("iris-replace-unreadable");
        Path worldRoot = temporary.resolve("world");
        Path recoveryParent = temporary.resolve("config");
        try {
            Path region = worldRoot.resolve("dimensions/iristest/skylands/region/r.0.0.mca");
            writeApplied(worldRoot, seeded("iristest:skylands", "overworld", 5L));
            touch(region);
            List<String> problems = List.of("Iris could not read /srv/config/irisworldgen/modded.json: Unterminated object");

            IOException refused = assertThrows(IOException.class, () -> reconcile(
                    worldRoot, Map.of(), problems, recoveryParent));

            assertTrue(refused.getMessage(), refused.getMessage().contains("modded.json"));
            assertTrue(Files.isRegularFile(region));
            assertFalse(Files.exists(recoveryParent));
            assertEquals(Set.of("iristest:skylands"), ModdedReplacementReconciler.readState(worldRoot).applied().keySet());
        } finally {
            deleteTree(temporary);
        }
    }

    @Test
    public void unreadableConfigWithAnEmptyStateFileStillRefuses() throws IOException {
        Path temporary = Files.createTempDirectory("iris-replace-unreadable-empty");
        Path worldRoot = temporary.resolve("world");
        try {
            ModdedReplacementReconciler.writeState(worldRoot, ModdedReplacementReconciler.State.EMPTY);

            assertThrows(IOException.class, () -> reconcile(
                    worldRoot, Map.of(), List.of("invalid replacement entry"), temporary.resolve("config")));
        } finally {
            deleteTree(temporary);
        }
    }

    @Test
    public void unreadableConfigWithoutRecordedStateProceeds() throws IOException {
        Path temporary = Files.createTempDirectory("iris-replace-unreadable-fresh");
        Path worldRoot = temporary.resolve("world");
        try {
            Files.createDirectories(worldRoot);

            ModdedReplacementReconciler.Result result = reconcile(
                    worldRoot, Map.of(), List.of("invalid replacement entry"), temporary.resolve("config"));

            assertEquals(Set.of(), result.quarantinedLevels());
        } finally {
            deleteTree(temporary);
        }
    }

    @Test
    public void changedReplacementKeepsThePreviousStateUntilTheNewPackGenerates() throws IOException {
        Path temporary = Files.createTempDirectory("iris-replace-change");
        Path worldRoot = temporary.resolve("world");
        try {
            Path region = worldRoot.resolve("dimensions/iristest/skylands/region/r.0.0.mca");
            ModdedDimensionReplacement previous = seeded("iristest:skylands", "overworld", 5L);
            writeApplied(worldRoot, previous);
            touch(region);

            ModdedReplacementReconciler.Result result = reconcile(worldRoot,
                    Map.of("iristest:skylands", replacement("iristest:skylands", "skypack")), List.of(),
                    temporary.resolve("config"));

            assertEquals(Set.of("iristest:skylands"), result.quarantinedLevels());
            assertFalse(Files.exists(region));
            assertEquals(Map.of("iristest:skylands", previous), ModdedReplacementReconciler.readState(worldRoot).applied());
        } finally {
            deleteTree(temporary);
        }
    }

    @Test
    public void existingHistoryOfTheConfiguredPackAndSeedIsAdoptedWithoutQuarantine() throws IOException {
        Path temporary = Files.createTempDirectory("iris-replace-history-match");
        Path worldRoot = temporary.resolve("world");
        try {
            Path skylands = worldRoot.resolve("dimensions/iristest/skylands");
            createHistory(temporary, skylands, ModdedWorldgenIds.dimensionTypeRef("skypack", "sky"), "sky", List.of());
            touch(skylands.resolve("region/r.0.0.mca"));

            ModdedReplacementReconciler.Result result = reconcile(worldRoot,
                    Map.of("iristest:skylands", seeded("iristest:skylands", "skypack:sky", 5L)), List.of(),
                    temporary.resolve("config"));

            assertEquals(Set.of("iristest:skylands"), result.adoptedLevels());
            assertNull(result.recovery());
            assertTrue(Files.isRegularFile(skylands.resolve("region/r.0.0.mca")));
            assertEquals(Map.of("iristest:skylands", seeded("iristest:skylands", "skypack:sky", 5L)),
                    ModdedReplacementReconciler.readState(worldRoot).applied());
        } finally {
            deleteTree(temporary);
        }
    }

    @Test
    public void restagingTheAppliedReplacementRegeneratesItAndKeepsItApplied() throws IOException {
        Path temporary = Files.createTempDirectory("iris-replace-refresh");
        Path worldRoot = temporary.resolve("world");
        try {
            Path skylands = worldRoot.resolve("dimensions/iristest/skylands");
            createHistory(temporary, skylands, ModdedWorldgenIds.dimensionTypeRef("skypack", "sky"), "sky", List.of());
            touch(skylands.resolve("region/r.0.0.mca"));
            ModdedDimensionReplacement applied = seeded("iristest:skylands", "skypack:sky", 5L);
            writeApplied(worldRoot, applied);
            ModdedReplacementReconciler.stagePending(worldRoot, applied);
            ModdedReplacementReconciler.stagePending(worldRoot, seeded("iristest:unconfigured", "skypack:sky", 5L));
            touch(worldRoot.resolve("dimensions/iristest/unconfigured/region/r.0.0.mca"));

            ModdedReplacementReconciler.Result result = reconcile(worldRoot,
                    Map.of("iristest:skylands", applied), List.of(), temporary.resolve("config"));

            assertEquals(Set.of("iristest:skylands"), result.quarantinedLevels());
            assertEquals(Set.of(), result.adoptedLevels());
            assertFalse(Files.exists(skylands.resolve("region")));
            assertFalse(Files.exists(skylands.resolve("iris/generation")));
            assertTrue(Files.isRegularFile(worldRoot.resolve("dimensions/iristest/unconfigured/region/r.0.0.mca")));
            assertEquals(ModdedReplacementReconciler.State.EMPTY.pending(),
                    ModdedReplacementReconciler.readState(worldRoot).pending());
            assertEquals(Map.of("iristest:skylands", applied), ModdedReplacementReconciler.readState(worldRoot).applied());
        } finally {
            deleteTree(temporary);
        }
    }

    @Test
    public void historyOfAnotherSeedIsQuarantined() throws IOException {
        Path temporary = Files.createTempDirectory("iris-replace-history-seed");
        Path worldRoot = temporary.resolve("world");
        try {
            Path skylands = worldRoot.resolve("dimensions/iristest/skylands");
            createHistory(temporary, skylands, ModdedWorldgenIds.dimensionTypeRef("skypack", "sky"), "sky", List.of());
            touch(skylands.resolve("region/r.0.0.mca"));

            ModdedReplacementReconciler.Result result = reconcile(worldRoot,
                    Map.of("iristest:skylands", seeded("iristest:skylands", "skypack:sky", 6L)), List.of(),
                    temporary.resolve("config"));

            assertEquals(Set.of(), result.adoptedLevels());
            assertEquals(Set.of("iristest:skylands"), result.quarantinedLevels());
            assertFalse(Files.exists(skylands.resolve("region")));
        } finally {
            deleteTree(temporary);
        }
    }

    @Test
    public void historyOfAnotherPackIsQuarantined() throws IOException {
        Path temporary = Files.createTempDirectory("iris-replace-history-other");
        Path worldRoot = temporary.resolve("world");
        try {
            Path skylands = worldRoot.resolve("dimensions/iristest/skylands");
            createHistory(temporary, skylands, ModdedWorldgenIds.dimensionTypeRef("otherpack", "sky"), "sky", List.of());
            touch(skylands.resolve("region/r.0.0.mca"));

            ModdedReplacementReconciler.Result result = reconcile(worldRoot,
                    Map.of("iristest:skylands", replacement("iristest:skylands", "skypack:sky")), List.of(),
                    temporary.resolve("config"));

            assertEquals(Set.of(), result.adoptedLevels());
            assertFalse(Files.exists(skylands.resolve("region")));
            assertFalse(Files.exists(skylands.resolve("iris")));
            assertEquals(Map.of(), ModdedReplacementReconciler.readState(worldRoot).applied());
        } finally {
            deleteTree(temporary);
        }
    }

    @Test
    public void borrowedHistoryMatchesOnItsRecordedAliasPack() throws IOException {
        Path temporary = Files.createTempDirectory("iris-replace-history-borrowed");
        Path worldRoot = temporary.resolve("world");
        try {
            Path aether = worldRoot.resolve("dimensions/aether/the_aether");
            createHistory(temporary, aether, "aether:the_aether", "sky",
                    List.of(ModdedWorldgenIds.biomeRef("skypack", "sky", "meadow")));
            touch(aether.resolve("region/r.0.0.mca"));

            ModdedReplacementReconciler.Result other = reconcile(worldRoot,
                    Map.of("aether:the_aether", replacement("aether:the_aether", "otherpack:sky")), List.of(),
                    temporary.resolve("config"));

            assertEquals(Set.of(), other.adoptedLevels());
            assertFalse(Files.exists(aether.resolve("region")));

            createHistory(temporary, aether, "aether:the_aether", "sky",
                    List.of(ModdedWorldgenIds.biomeRef("skypack", "sky", "meadow")));
            ModdedReplacementReconciler.Result matching = reconcile(worldRoot,
                    Map.of("aether:the_aether", replacement("aether:the_aether", "skypack:sky")), List.of(),
                    temporary.resolve("config"));

            assertEquals(Set.of("aether:the_aether"), matching.adoptedLevels());
            assertTrue(Files.isDirectory(aether.resolve("iris")));
        } finally {
            deleteTree(temporary);
        }
    }

    @Test
    public void pendingIrisWorldReplacementMovesItsTerrainAndRepointsItsRegistryEntry() throws IOException {
        Path temporary = Files.createTempDirectory("iris-replace-iris-world");
        Path worldRoot = temporary.resolve("world");
        try {
            Path myworld = worldRoot.resolve("dimensions/irisworldgen/myworld");
            touch(myworld.resolve("region/r.0.0.mca"));
            touch(myworld.resolve("iris/generation/manifest.json"));
            ModdedDimensionRegistryStore.write(registryFile(worldRoot), List.of(
                    new ModdedDimensionRegistryStore.PersistentDimension("irisworldgen:myworld", "overworld", "overworld", 1L),
                    new ModdedDimensionRegistryStore.PersistentDimension("irisworldgen:other", "overworld", "overworld", 2L)));
            ModdedReplacementReconciler.stagePending(worldRoot, seeded("irisworldgen:myworld", "underworld", 9L));

            ModdedReplacementReconciler.Result result = reconcile(worldRoot, Map.of(), List.of(), temporary.resolve("config"));

            assertEquals(Set.of("irisworldgen:myworld"), result.replacedWorlds());
            assertFalse(Files.exists(myworld.resolve("region")));
            assertFalse(Files.exists(myworld.resolve("iris")));
            assertTrue(Files.isRegularFile(result.recovery().resolve("dimensions/irisworldgen/myworld/region/r.0.0.mca")));
            assertEquals(List.of(
                    new ModdedDimensionRegistryStore.PersistentDimension("irisworldgen:myworld", "underworld", "underworld", 9L),
                    new ModdedDimensionRegistryStore.PersistentDimension("irisworldgen:other", "overworld", "overworld", 2L)),
                    ModdedDimensionRegistryStore.loadWorldRoot(worldRoot));
            assertEquals(Map.of(), ModdedReplacementReconciler.readState(worldRoot).pending());
        } finally {
            deleteTree(temporary);
        }
    }

    @Test
    public void pendingReplacementOfARemovedIrisWorldIsDroppedWithoutMovingAnything() throws IOException {
        Path temporary = Files.createTempDirectory("iris-replace-iris-world-gone");
        Path worldRoot = temporary.resolve("world");
        try {
            Path region = worldRoot.resolve("dimensions/irisworldgen/myworld/region/r.0.0.mca");
            touch(region);
            ModdedReplacementReconciler.stagePending(worldRoot, seeded("irisworldgen:myworld", "underworld", 9L));

            ModdedReplacementReconciler.Result result = reconcile(worldRoot, Map.of(), List.of(), temporary.resolve("config"));

            assertEquals(Set.of(), result.replacedWorlds());
            assertTrue(Files.isRegularFile(region));
            assertEquals(Map.of(), ModdedReplacementReconciler.readState(worldRoot).pending());
        } finally {
            deleteTree(temporary);
        }
    }

    @Test
    public void pendingIrisWorldReplacementsCanBeCancelledBeforeTheRestart() throws IOException {
        Path worldRoot = Files.createTempDirectory("iris-replace-cancel");
        try {
            ModdedReplacementReconciler.stagePending(worldRoot, seeded("irisworldgen:myworld", "underworld", 9L));

            assertTrue(ModdedReplacementReconciler.cancelPending(worldRoot, "irisworldgen:myworld"));
            assertFalse(ModdedReplacementReconciler.cancelPending(worldRoot, "irisworldgen:myworld"));
            assertEquals(Map.of(), ModdedReplacementReconciler.readState(worldRoot).pending());
        } finally {
            deleteTree(worldRoot);
        }
    }

    @Test
    public void recordAppliedStoresOnlyReplacementsThatGenerateFromTheirPackAndSeed() throws IOException {
        Path temporary = Files.createTempDirectory("iris-replace-record");
        Path worldRoot = temporary.resolve("world");
        try {
            writeApplied(worldRoot, seeded("iristest:kept", "overworld", 5L));

            Map<String, ModdedDimensionReplacement> recorded = ModdedReplacementReconciler.recordApplied(worldRoot,
                    Map.of("iristest:kept", replacement("iristest:kept", "overworld"),
                            "iristest:live", replacement("iristest:live", "skypack"),
                            "iristest:seeded", seeded("iristest:seeded", "skypack", 8L),
                            "iristest:wrongseed", seeded("iristest:wrongseed", "skypack", 8L),
                            "iristest:skipped", replacement("iristest:skipped", "skypack")),
                    (String level, String packRef) -> switch (level) {
                        case "iristest:skipped" -> OptionalLong.empty();
                        case "iristest:wrongseed" -> OptionalLong.of(9L);
                        case "iristest:seeded" -> OptionalLong.of(8L);
                        default -> OptionalLong.of(5L);
                    });

            Map<String, ModdedDimensionReplacement> expected = Map.of(
                    "iristest:kept", seeded("iristest:kept", "overworld", 5L),
                    "iristest:live", seeded("iristest:live", "skypack", 5L),
                    "iristest:seeded", seeded("iristest:seeded", "skypack", 8L));
            assertEquals(expected, recorded);
            assertEquals(expected, ModdedReplacementReconciler.readState(worldRoot).applied());
        } finally {
            deleteTree(temporary);
        }
    }

    @Test
    public void recordAppliedFailsWhenTheStateCannotBeWritten() throws IOException {
        Path temporary = Files.createTempDirectory("iris-replace-record-fail");
        Path worldRoot = temporary.resolve("world");
        try {
            touch(worldRoot.resolve("data/irisworldgen/dimension-replacements.json/blocker"));

            assertThrows(IOException.class, () -> ModdedReplacementReconciler.recordApplied(worldRoot,
                    Map.of("iristest:live", replacement("iristest:live", "skypack")),
                    (String level, String packRef) -> OptionalLong.of(1L)));
        } finally {
            deleteTree(temporary);
        }
    }

    @Test
    public void switchingAnAppliedLevelToAnUninstalledPackRefusesBeforeMovingAnything() throws IOException {
        Path temporary = Files.createTempDirectory("iris-replace-uninstalled-applied");
        Path worldRoot = temporary.resolve("world");
        try {
            Path region = worldRoot.resolve("dimensions/iristest/skylands/region/r.0.0.mca");
            writeApplied(worldRoot, seeded("iristest:skylands", "overworld", 5L));
            touch(region);

            IOException refused = assertThrows(IOException.class, () -> ModdedReplacementReconciler.reconcile(
                    new ModdedReplacementReconciler.Request(worldRoot,
                            Map.of("iristest:skylands", replacement("iristest:skylands", "missing")), List.of(),
                            (ModdedDimensionReplacement replacement) -> false, temporary.resolve("config"))));

            assertTrue(refused.getMessage(), refused.getMessage().contains("missing"));
            assertTrue(Files.isRegularFile(region));
            assertEquals(Set.of("iristest:skylands"), ModdedReplacementReconciler.readState(worldRoot).applied().keySet());
        } finally {
            deleteTree(temporary);
        }
    }

    @Test
    public void newReplacementOfAnUninstalledPackLeavesTheLevelAlone() throws IOException {
        Path temporary = Files.createTempDirectory("iris-replace-uninstalled-new");
        Path worldRoot = temporary.resolve("world");
        try {
            Path region = worldRoot.resolve("dimensions/aether/the_aether/region/r.0.0.mca");
            touch(region);

            ModdedReplacementReconciler.Result result = ModdedReplacementReconciler.reconcile(
                    new ModdedReplacementReconciler.Request(worldRoot,
                            Map.of("aether:the_aether", replacement("aether:the_aether", "missing")), List.of(),
                            (ModdedDimensionReplacement replacement) -> false, temporary.resolve("config")));

            assertEquals(Set.of(), result.quarantinedLevels());
            assertTrue(Files.isRegularFile(region));
            assertEquals(Map.of(), ModdedReplacementReconciler.readState(worldRoot).applied());
        } finally {
            deleteTree(temporary);
        }
    }

    @Test
    public void pendingCoversStagedLevelStemsAndIrisWorlds() throws IOException {
        Path worldRoot = Files.createTempDirectory("iris-replace-pending");
        try {
            writeApplied(worldRoot, seeded("minecraft:overworld", "overworld", 5L));
            ModdedReplacementReconciler.stagePending(worldRoot, seeded("irisworldgen:myworld", "underworld", 9L));
            Map<String, ModdedDimensionReplacement> configured = Map.of(
                    "minecraft:overworld", seeded("minecraft:overworld", "overworld", 5L),
                    "minecraft:the_nether", seeded("minecraft:the_nether", "underworld", 5L));

            assertFalse(ModdedReplacementReconciler.pending(worldRoot, "minecraft:overworld", configured));
            assertTrue(ModdedReplacementReconciler.pending(worldRoot, "minecraft:the_nether", configured));
            assertTrue(ModdedReplacementReconciler.pending(worldRoot, "irisworldgen:myworld", configured));
            assertFalse(ModdedReplacementReconciler.pending(worldRoot, "minecraft:the_end", configured));
        } finally {
            deleteTree(worldRoot);
        }
    }

    private static ModdedReplacementReconciler.Result reconcile(Path worldRoot, Map<String, ModdedDimensionReplacement> configured,
                                                                List<String> problems, Path recoveryParent) throws IOException {
        return ModdedReplacementReconciler.reconcile(new ModdedReplacementReconciler.Request(
                worldRoot, configured, problems, (ModdedDimensionReplacement replacement) -> true, recoveryParent));
    }

    private static void writeApplied(Path worldRoot, ModdedDimensionReplacement applied) throws IOException {
        ModdedReplacementReconciler.writeState(worldRoot, new ModdedReplacementReconciler.State(
                Map.of(applied.levelKey(), applied), Map.of()));
    }

    private static Path registryFile(Path worldRoot) {
        return worldRoot.resolve("iris").resolve("iris-dimensions.json");
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
