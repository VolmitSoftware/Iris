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
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ModdedModConfigTest {
    @Test
    public void validReplacementsLoadWithoutProblems() throws IOException {
        Path file = config("{\"dimensionReplacements\": {\"aether:the_aether\": {\"type\": \"overworld:sky\"},"
                + " \"minecraft:overworld\": {\"type\": \"overworld\", \"seed\": -42}}}");
        try {
            ModdedModConfig config = ModdedModConfig.read(file);

            assertEquals(Map.of(
                    "aether:the_aether", ModdedDimensionReplacement.parse("aether:the_aether", "overworld:sky", OptionalLong.empty()),
                    "minecraft:overworld", ModdedDimensionReplacement.parse("minecraft:overworld", "overworld", OptionalLong.of(-42L))),
                    config.dimensionReplacements());
            assertEquals(List.of(), config.replacementProblems());
        } finally {
            ModdedReplacementReconcilerTest.deleteTree(file.getParent());
        }
    }

    @Test
    public void unparseableConfigReportsAProblemNamingTheFile() throws IOException {
        Path file = config("{\"dimensionReplacements\": {\"aether:the_aether\": ");
        try {
            ModdedModConfig config = ModdedModConfig.read(file);

            assertEquals(Map.of(), config.dimensionReplacements());
            assertEquals(1, config.replacementProblems().size());
            assertTrue(config.replacementProblems().getFirst(), config.replacementProblems().getFirst().contains(file.toString()));
        } finally {
            ModdedReplacementReconcilerTest.deleteTree(file.getParent());
        }
    }

    @Test
    public void invalidReplacementEntriesAreReportedInsteadOfSilentlyDropped() throws IOException {
        Path file = config("{\"dimensionReplacements\": {\"aether:the_aether\": {\"type\": \"overworld\"},"
                + " \"Broken Key\": {\"type\": \"overworld\"}, \"aether:plain\": \"overworld\"}}");
        try {
            ModdedModConfig config = ModdedModConfig.read(file);

            assertEquals(Map.of("aether:the_aether", ModdedDimensionReplacement.parse("aether:the_aether", "overworld",
                    OptionalLong.empty())), config.dimensionReplacements());
            assertEquals(2, config.replacementProblems().size());
            assertTrue(config.replacementProblems().toString(), config.replacementProblems().get(0).contains("Broken Key"));
            assertTrue(config.replacementProblems().toString(), config.replacementProblems().get(1).contains("aether:plain"));
            assertTrue(config.replacementProblems().getFirst(), config.replacementProblems().getFirst().contains(file.toString()));
        } finally {
            ModdedReplacementReconcilerTest.deleteTree(file.getParent());
        }
    }

    @Test
    public void replacementSectionThatIsNotAnObjectIsReported() throws IOException {
        Path file = config("{\"dimensionReplacements\": [\"aether:the_aether\"]}");
        try {
            ModdedModConfig config = ModdedModConfig.read(file);

            assertEquals(Map.of(), config.dimensionReplacements());
            assertEquals(1, config.replacementProblems().size());
            assertTrue(config.replacementProblems().getFirst(), config.replacementProblems().getFirst().contains("dimensionReplacements"));
        } finally {
            ModdedReplacementReconcilerTest.deleteTree(file.getParent());
        }
    }

    private static Path config(String json) throws IOException {
        Path file = Files.createTempDirectory("iris-modded-config").resolve("modded.json");
        Files.writeString(file, json, StandardCharsets.UTF_8);
        return file;
    }
}
