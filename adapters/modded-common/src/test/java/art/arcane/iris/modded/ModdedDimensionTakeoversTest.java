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

import art.arcane.volmlib.util.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ModdedDimensionTakeoversTest {
    @Test
    public void parsesPackAndPackDimensionReferences() {
        ModdedDimensionTakeover samePack = ModdedDimensionTakeover.parse("iristest:skylands", "skypack");
        ModdedDimensionTakeover scoped = ModdedDimensionTakeover.parse("aether:the_aether", "overworld:floating/sky");

        assertEquals("skypack", samePack.pack());
        assertEquals("skypack", samePack.packDimension());
        assertEquals("skypack", samePack.packRef());
        assertEquals("overworld", scoped.pack());
        assertEquals("floating/sky", scoped.packDimension());
        assertEquals("overworld:floating/sky", scoped.packRef());
        assertEquals("aether", scoped.namespace());
        assertEquals("the_aether", scoped.path());
    }

    @Test
    public void rejectsInvalidLevelKeysAndPackReferences() {
        for (String levelKey : List.of("skylands", "Iristest:skylands", "iristest:", ":skylands", "iristest:sky/../x", "iristest:/sky")) {
            assertThrows(levelKey, IllegalArgumentException.class, () -> ModdedDimensionTakeover.parse(levelKey, "overworld"));
        }
        for (String packRef : List.of("", "  ", "../overworld", "overworld:", "over world", "overworld:a/../b")) {
            assertThrows(packRef, IllegalArgumentException.class, () -> ModdedDimensionTakeover.parse("iristest:skylands", packRef));
        }
    }

    @Test
    public void levelStoragePathFollowsTheDimensionsLayout() {
        Path worldRoot = Path.of("/srv/world");

        assertEquals(worldRoot.resolve("dimensions").resolve("iristest").resolve("skylands"),
                ModdedDimensionTakeover.parse("iristest:skylands", "overworld").storageFolder(worldRoot));
        assertEquals(worldRoot.resolve("dimensions").resolve("aether").resolve("realm").resolve("upper"),
                ModdedDimensionTakeover.parse("aether:realm/upper", "overworld").storageFolder(worldRoot));
    }

    @Test
    public void levelStemUsesTheIrisGeneratorWithThePresetBiomeSourcePlaceholder() {
        ModdedDimensionTakeover takeover = ModdedDimensionTakeover.parse("iristest:skylands", "overworld:sky");

        JSONObject stem = new JSONObject(ModdedDimensionTakeovers.levelStemJson("iristest:skylands", takeover));

        assertEquals("iristest:skylands", stem.getString("type"));
        JSONObject generator = stem.getJSONObject("generator");
        assertEquals("irisworldgen:iris", generator.getString("type"));
        assertEquals("overworld:sky", generator.getString("dimension"));
        assertEquals("minecraft:fixed", generator.getJSONObject("biome_source").getString("type"));
        assertEquals("minecraft:plains", generator.getJSONObject("biome_source").getString("biome"));
    }

    @Test
    public void levelStemIsWrittenAtTheDatapackDimensionPathOfTheLevelKey() throws IOException {
        Path datapack = Files.createTempDirectory("iris-takeover-stem");
        try {
            ModdedDimensionTakeover takeover = ModdedDimensionTakeover.parse("aether:the_aether", "overworld");

            Path written = ModdedDimensionTakeovers.writeLevelStem(datapack, takeover, "aether:the_aether");

            assertEquals(datapack.resolve("data").resolve("aether").resolve("dimension").resolve("the_aether.json"), written);
            assertEquals("aether:the_aether", new JSONObject(Files.readString(written)).getString("type"));
        } finally {
            ModdedTakeoverReconcilerTest.deleteTree(datapack);
        }
    }

    @Test
    public void changedLevelsCoverAddedChangedAndRemovedTakeovers() {
        Map<String, String> applied = Map.of(
                "iristest:kept", "overworld",
                "iristest:changed", "overworld",
                "iristest:removed", "overworld");
        Map<String, String> configured = Map.of(
                "iristest:kept", "overworld",
                "iristest:changed", "overworld:sky",
                "iristest:added", "overworld");

        assertEquals(Set.of("iristest:changed", "iristest:removed", "iristest:added"),
                ModdedDimensionTakeovers.changedLevels(applied, configured));
        assertEquals(Set.of(), ModdedDimensionTakeovers.changedLevels(configured, configured));
    }

    @Test
    public void fingerprintIsOrderIndependentAndTracksEveryEntry() {
        Map<String, String> forward = new LinkedHashMap<>();
        forward.put("iristest:a", "overworld");
        forward.put("iristest:b", "overworld:sky");
        Map<String, String> reversed = new LinkedHashMap<>();
        reversed.put("iristest:b", "overworld:sky");
        reversed.put("iristest:a", "overworld");

        assertEquals(ModdedDimensionTakeovers.fingerprint(forward), ModdedDimensionTakeovers.fingerprint(reversed));
        assertNotEquals(ModdedDimensionTakeovers.fingerprint(forward),
                ModdedDimensionTakeovers.fingerprint(Map.of("iristest:a", "overworld")));
        assertEquals("", ModdedDimensionTakeovers.fingerprint(Map.of()));
        assertTrue(ModdedDimensionTakeovers.fingerprint(forward).contains("iristest:b=overworld:sky"));
    }

    @Test
    public void installedPackTypeIsStagedForTheTakeover() throws IOException {
        Path temporary = Files.createTempDirectory("iris-takeover-stage-installed");
        try {
            Path datapack = temporary.resolve("datapack");
            ModdedDimensionTakeovers.StagedTypes staged = ModdedDimensionTakeovers.StagedTypes.create();
            staged.installed().put("skypack:sky", "irisworldgen:packs/x/dimensions/y/dimension_type");

            ModdedDimensionTakeovers.writeLevelStems(datapack, Map.of("aether:the_aether", "skypack:sky"), staged,
                    temporary.resolve("world"));

            JSONObject stem = new JSONObject(Files.readString(datapack.resolve("data/aether/dimension/the_aether.json")));
            assertEquals("irisworldgen:packs/x/dimensions/y/dimension_type", stem.getString("type"));
            assertEquals("skypack:sky", stem.getJSONObject("generator").getString("dimension"));
        } finally {
            ModdedTakeoverReconcilerTest.deleteTree(temporary);
        }
    }

    @Test
    public void retainedHistoryOfTheLevelStagesTheTakeoverWithoutTheInstalledPack() throws IOException {
        Path temporary = Files.createTempDirectory("iris-takeover-stage-retained");
        try {
            Path datapack = temporary.resolve("datapack");
            Path worldRoot = temporary.resolve("world");
            ModdedDimensionTakeovers.StagedTypes staged = ModdedDimensionTakeovers.StagedTypes.create();
            staged.retained().put(worldRoot.resolve("dimensions/aether/the_aether").toAbsolutePath().normalize(),
                    "aether:the_aether");

            ModdedDimensionTakeovers.writeLevelStems(datapack, Map.of("aether:the_aether", "skypack:sky"), staged, worldRoot);

            JSONObject stem = new JSONObject(Files.readString(datapack.resolve("data/aether/dimension/the_aether.json")));
            assertEquals("aether:the_aether", stem.getString("type"));
        } finally {
            ModdedTakeoverReconcilerTest.deleteTree(temporary);
        }
    }

    @Test
    public void unstageableTakeoverWithoutAnyRecordIsSkipped() throws IOException {
        Path temporary = Files.createTempDirectory("iris-takeover-stage-skip");
        try {
            Path datapack = temporary.resolve("datapack");
            Path worldRoot = temporary.resolve("world");
            Files.createDirectories(worldRoot);

            ModdedDimensionTakeovers.writeLevelStems(datapack, Map.of("aether:the_aether", "skypack:sky"),
                    ModdedDimensionTakeovers.StagedTypes.create(), worldRoot);
            ModdedDimensionTakeovers.writeLevelStems(datapack, Map.of("aether:the_aether", "skypack:sky"),
                    ModdedDimensionTakeovers.StagedTypes.create(), null);

            assertFalse(Files.exists(datapack.resolve("data/aether/dimension/the_aether.json")));
        } finally {
            ModdedTakeoverReconcilerTest.deleteTree(temporary);
        }
    }

    @Test
    public void unstageableTakeoverOfAnAppliedLevelRefusesStaging() throws IOException {
        Path temporary = Files.createTempDirectory("iris-takeover-stage-applied");
        try {
            Path worldRoot = temporary.resolve("world");
            ModdedTakeoverReconciler.writeApplied(worldRoot, Map.of("aether:the_aether", "skypack:sky"));

            IOException refused = assertThrows(IOException.class, () -> ModdedDimensionTakeovers.writeLevelStems(
                    temporary.resolve("datapack"), Map.of("aether:the_aether", "skypack:sky"),
                    ModdedDimensionTakeovers.StagedTypes.create(), worldRoot));

            assertTrue(refused.getMessage(), refused.getMessage().contains("aether:the_aether"));
            assertTrue(refused.getMessage(), refused.getMessage().contains("skypack"));
        } finally {
            ModdedTakeoverReconcilerTest.deleteTree(temporary);
        }
    }

    @Test
    public void unstageableTakeoverOfALevelWithIrisHistoryRefusesStaging() throws IOException {
        Path temporary = Files.createTempDirectory("iris-takeover-stage-history");
        try {
            Path worldRoot = temporary.resolve("world");
            Files.createDirectories(worldRoot.resolve("dimensions/aether/the_aether/iris/generation"));

            assertThrows(IOException.class, () -> ModdedDimensionTakeovers.writeLevelStems(
                    temporary.resolve("datapack"), Map.of("aether:the_aether", "skypack:sky"),
                    ModdedDimensionTakeovers.StagedTypes.create(), worldRoot));
        } finally {
            ModdedTakeoverReconcilerTest.deleteTree(temporary);
        }
    }

    @Test
    public void canonicalTakeoverFollowsTheInstalledPackFolderCase() throws IOException {
        Path packsRoot = Files.createTempDirectory("iris-takeover-canonical");
        try {
            Files.createDirectories(packsRoot.resolve("skypack/dimensions"));
            Files.writeString(packsRoot.resolve("skypack/dimensions/sky.json"), "{}");
            boolean caseInsensitive = Files.isDirectory(packsRoot.resolve("SKYPACK"));

            Map<String, String> canonical = ModdedDimensionTakeovers.canonical(
                    Map.of("aether:the_aether", "SkyPack:Sky", "aether:other", "missing:sky"), packsRoot);

            assertEquals(caseInsensitive ? "skypack:sky" : "SkyPack:Sky", canonical.get("aether:the_aether"));
            assertEquals("missing:sky", canonical.get("aether:other"));
            assertEquals("skypack:sky", ModdedDimensionTakeovers.canonical(
                    Map.of("aether:the_aether", "skypack:sky"), packsRoot).get("aether:the_aether"));
            assertTrue(ModdedDimensionTakeovers.installed(ModdedDimensionTakeover.parse("aether:the_aether", "skypack:sky"), packsRoot));
            assertFalse(ModdedDimensionTakeovers.installed(ModdedDimensionTakeover.parse("aether:the_aether", "skypack:void"), packsRoot));
        } finally {
            ModdedTakeoverReconcilerTest.deleteTree(packsRoot);
        }
    }
}
