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

import art.arcane.iris.generation.runtime.IrisEngineMantle;
import art.arcane.iris.world.history.GenerationHistoryPaths;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class ModdedDimensionStorage {
    private static final List<String> CHUNK_DATA_FOLDERS = List.of(
            "region",
            "entities",
            "poi",
            IrisEngineMantle.STORAGE_FOLDER_NAME,
            GenerationHistoryPaths.IRIS_DIRECTORY_NAME
    );

    private ModdedDimensionStorage() {
    }

    public static File storageFolder(MinecraftServer server, ResourceKey<Level> dimension) {
        return DimensionType.getStorageFolder(dimension, server.getWorldPath(LevelResource.ROOT)).toFile();
    }

    public static void wipe(MinecraftServer server, ResourceKey<Level> dimension) {
        File storageFolder = storageFolder(server, dimension);
        try {
            for (String folder : CHUNK_DATA_FOLDERS) {
                deleteRecursively(new File(storageFolder, folder).toPath());
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Iris failed to completely wipe dimension storage at "
                            + storageFolder.getAbsolutePath(), e);
        }
        ModdedIrisLog.info("Iris wiped dimension storage at {}", storageFolder.getAbsolutePath());
    }

    private static void deleteRecursively(Path root) throws IOException {
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
