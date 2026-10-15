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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ModdedModConfigTest {
    @Test
    public void validTakeoversLoadWithoutProblems() throws IOException {
        Path file = config("{\"dimensionTakeovers\": {\"aether:the_aether\": \"overworld:sky\"}}");
        try {
            ModdedModConfig config = ModdedModConfig.read(file);

            assertEquals(Map.of("aether:the_aether", "overworld:sky"), config.dimensionTakeovers());
            assertEquals(List.of(), config.takeoverProblems());
        } finally {
            ModdedTakeoverReconcilerTest.deleteTree(file.getParent());
        }
    }

    @Test
    public void unparseableConfigReportsAProblemNamingTheFile() throws IOException {
        Path file = config("{\"dimensionTakeovers\": {\"aether:the_aether\": ");
        try {
            ModdedModConfig config = ModdedModConfig.read(file);

            assertEquals(Map.of(), config.dimensionTakeovers());
            assertEquals(1, config.takeoverProblems().size());
            assertTrue(config.takeoverProblems().getFirst(), config.takeoverProblems().getFirst().contains(file.toString()));
        } finally {
            ModdedTakeoverReconcilerTest.deleteTree(file.getParent());
        }
    }

    @Test
    public void invalidTakeoverEntryIsReportedInsteadOfSilentlyDropped() throws IOException {
        Path file = config("{\"dimensionTakeovers\": {\"aether:the_aether\": \"overworld\", \"Broken Key\": \"overworld\"}}");
        try {
            ModdedModConfig config = ModdedModConfig.read(file);

            assertEquals(Map.of("aether:the_aether", "overworld"), config.dimensionTakeovers());
            assertEquals(1, config.takeoverProblems().size());
            assertTrue(config.takeoverProblems().getFirst(), config.takeoverProblems().getFirst().contains("Broken Key"));
            assertTrue(config.takeoverProblems().getFirst(), config.takeoverProblems().getFirst().contains(file.toString()));
        } finally {
            ModdedTakeoverReconcilerTest.deleteTree(file.getParent());
        }
    }

    @Test
    public void takeoverSectionThatIsNotAnObjectIsReported() throws IOException {
        Path file = config("{\"dimensionTakeovers\": [\"aether:the_aether\"]}");
        try {
            ModdedModConfig config = ModdedModConfig.read(file);

            assertEquals(Map.of(), config.dimensionTakeovers());
            assertEquals(1, config.takeoverProblems().size());
            assertTrue(config.takeoverProblems().getFirst(), config.takeoverProblems().getFirst().contains("dimensionTakeovers"));
        } finally {
            ModdedTakeoverReconcilerTest.deleteTree(file.getParent());
        }
    }

    private static Path config(String json) throws IOException {
        Path file = Files.createTempDirectory("iris-modded-config").resolve("modded.json");
        Files.writeString(file, json, StandardCharsets.UTF_8);
        return file;
    }
}
