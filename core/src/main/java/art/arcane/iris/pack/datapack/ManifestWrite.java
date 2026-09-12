/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.pack.datapack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

final class ManifestWrite implements AutoCloseable {
    private final Path staged;
    private final Path target;
    private final Path rollback;
    private boolean published;

    ManifestWrite(Path staged, Path target, Path rollback) {
        this.staged = staged;
        this.target = target;
        this.rollback = rollback;
    }

    void publish() throws IOException {
        if (published) {
            throw new IllegalStateException("Datapack manifest write was already published");
        }
        try {
            DatapackSupport.move(staged, target);
            published = true;
        } catch (IOException publishFailure) {
            try {
                restoreOriginal();
            } catch (IOException restoreFailure) {
                publishFailure.addSuppressed(restoreFailure);
            }
            throw publishFailure;
        }
        DatapackSupport.forceDirectoryIfSupported(Objects.requireNonNull(target.getParent(), "manifest parent"));
    }

    boolean published() {
        return published;
    }

    void discard() throws IOException {
        IOException failure = null;
        try {
            Files.deleteIfExists(staged);
        } catch (IOException cleanupFailure) {
            failure = cleanupFailure;
        }
        if (rollback != null) {
            try {
                Files.deleteIfExists(rollback);
            } catch (IOException cleanupFailure) {
                if (failure == null) {
                    failure = cleanupFailure;
                } else {
                    failure.addSuppressed(cleanupFailure);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public void close() throws IOException {
        discard();
    }

    private void restoreOriginal() throws IOException {
        if (rollback == null) {
            Files.deleteIfExists(target);
        } else {
            DatapackSupport.move(rollback, target);
        }
        DatapackSupport.forceDirectoryIfSupported(Objects.requireNonNull(target.getParent(), "manifest parent"));
    }
}
