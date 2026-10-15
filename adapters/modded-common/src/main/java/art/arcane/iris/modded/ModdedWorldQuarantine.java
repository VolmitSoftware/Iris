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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class ModdedWorldQuarantine {
    private ModdedWorldQuarantine() {
    }

    static List<Path> move(Path worldRoot, List<Path> sources, Path recovery) throws IOException {
        List<Path> moved = new ArrayList<>(sources.size());
        try {
            for (Path source : sources) {
                moveOne(worldRoot, source, recovery, moved);
            }
        } catch (IOException failure) {
            rollback(worldRoot, recovery, moved, failure);
            throw failure;
        }
        if (moved.isEmpty()) {
            Files.deleteIfExists(recovery);
        }
        return List.copyOf(moved);
    }

    private static void moveOne(Path worldRoot, Path source, Path recovery, List<Path> moved) throws IOException {
        if (!Files.exists(source)) {
            return;
        }
        Path relative = worldRoot.relativize(source);
        if (relative.isAbsolute() || relative.startsWith("..")) {
            throw new IOException("Iris refuses to quarantine " + source + " outside the world " + worldRoot);
        }
        Path target = recovery.resolve(relative);
        Files.createDirectories(target.getParent());
        Files.move(source, target);
        moved.add(relative);
    }

    private static void rollback(Path worldRoot, Path recovery, List<Path> moved, IOException failure) {
        for (int index = moved.size() - 1; index >= 0; index--) {
            Path relative = moved.get(index);
            Path rollbackTarget = worldRoot.resolve(relative);
            try {
                Files.createDirectories(rollbackTarget.getParent());
                Files.move(recovery.resolve(relative), rollbackTarget);
            } catch (IOException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }
    }
}
