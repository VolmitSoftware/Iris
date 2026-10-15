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

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

public class ModdedReplaceCommandsTest {
    @Test
    public void installedTypesListEveryPackAndItsOtherDimensionKeys() throws IOException {
        Path packs = Files.createTempDirectory("iris-replace-types");
        try {
            touch(packs.resolve("overworld/dimensions/overworld.json"));
            touch(packs.resolve("skypack/dimensions/sky.json"));
            touch(packs.resolve("skypack/dimensions/floating/upper.json"));
            touch(packs.resolve("empty/readme.txt"));

            assertEquals(List.of("overworld", "skypack", "skypack:floating/upper", "skypack:sky"),
                    ModdedReplaceCommands.installedTypes(packs.toFile()));
        } finally {
            try (Stream<Path> walk = Files.walk(packs)) {
                for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    @Test
    public void noInstalledPacksListNothing() throws IOException {
        Path packs = Files.createTempDirectory("iris-replace-types-empty");
        try {
            assertEquals(List.of(), ModdedReplaceCommands.installedTypes(packs.toFile()));
            assertEquals(List.of(), ModdedReplaceCommands.installedTypes(packs.resolve("missing").toFile()));
        } finally {
            Files.deleteIfExists(packs);
        }
    }

    private static void touch(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{}", StandardCharsets.UTF_8);
    }
}
