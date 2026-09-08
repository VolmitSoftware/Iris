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

package art.arcane.iris.core.datapack;

import java.io.File;
import java.io.IOException;
import java.nio.file.LinkOption;
import java.nio.file.Files;
import java.util.List;

final class DirectoryRemoval {
    private final List<DirectoryMove> moved;
    private int prepared;
    private boolean closed;

    DirectoryRemoval(List<DirectoryMove> moved) {
        this.moved = List.copyOf(moved);
    }

    List<DirectoryMove> moves() {
        return moved;
    }

    void prepare() throws IOException {
        if (closed || prepared > 0) {
            throw new IllegalStateException("Datapack directory removal transaction is not new");
        }
        try {
            for (DirectoryMove directoryMove : moved) {
                DatapackInstallPlanner.verifyDirectoryContainerIdentity(
                        directoryMove.target().getParentFile(),
                        directoryMove.targetRootIdentity(),
                        directoryMove.targetRootFileIdentity(),
                        "datapack removal target root"
                );
                DatapackInstallPlanner.verifyDirectoryContainerIdentity(
                        directoryMove.backup().getParentFile(),
                        directoryMove.scratchRootIdentity(),
                        directoryMove.scratchRootFileIdentity(),
                        "datapack removal scratch root"
                );
                DatapackInstallPlanner.verifyDirectorySnapshot(
                        directoryMove.target(),
                        new File(directoryMove.targetRootIdentity()),
                        directoryMove.originalHash(),
                        directoryMove.originalMarkerHash(),
                        directoryMove.originalIdentity(),
                        "datapack removal target"
                );
                DatapackSupport.moveNew(directoryMove.target().toPath(), directoryMove.backup().toPath());
                prepared++;
                DatapackSupport.forceDirectoryIfSupported(directoryMove.target().getParentFile().toPath());
                DatapackSupport.forceDirectoryIfSupported(directoryMove.backup().getParentFile().toPath());
                DatapackInstallPlanner.verifyDirectorySnapshot(
                        directoryMove.backup(),
                        new File(directoryMove.targetRootIdentity()),
                        directoryMove.originalHash(),
                        directoryMove.originalMarkerHash(),
                        directoryMove.originalIdentity(),
                        "datapack removal backup"
                );
            }
        } catch (IOException removalFailure) {
            try {
                rollback();
            } catch (IOException restoreFailure) {
                removalFailure.addSuppressed(restoreFailure);
            }
            throw removalFailure;
        }
    }

    void rollback() throws IOException {
        if (closed) {
            return;
        }
        IOException failure = null;
        for (int i = prepared - 1; i >= 0; i--) {
            DirectoryMove directoryMove = moved.get(i);
            if (!Files.exists(directoryMove.backup().toPath(), LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            try {
                DatapackInstallPlanner.verifyDirectoryContainerIdentity(
                        directoryMove.target().getParentFile(),
                        directoryMove.targetRootIdentity(),
                        directoryMove.targetRootFileIdentity(),
                        "datapack removal target root"
                );
                DatapackInstallPlanner.verifyDirectoryContainerIdentity(
                        directoryMove.backup().getParentFile(),
                        directoryMove.scratchRootIdentity(),
                        directoryMove.scratchRootFileIdentity(),
                        "datapack removal scratch root"
                );
                DatapackInstallPlanner.verifyDirectorySnapshot(
                        directoryMove.backup(),
                        new File(directoryMove.targetRootIdentity()),
                        directoryMove.originalHash(),
                        directoryMove.originalMarkerHash(),
                        directoryMove.originalIdentity(),
                        "datapack removal backup"
                );
                if (DatapackSupport.pathExists(directoryMove.target().toPath(), "datapack removal target")) {
                    throw new IOException("Datapack removal target was concurrently recreated at "
                            + directoryMove.target().getPath());
                }
                DatapackSupport.moveNew(
                        directoryMove.backup().toPath(),
                        directoryMove.target().toPath()
                );
                DatapackSupport.forceDirectoryIfSupported(directoryMove.target().getParentFile().toPath());
                DatapackSupport.forceDirectoryIfSupported(directoryMove.backup().getParentFile().toPath());
            } catch (IOException restoreFailure) {
                if (failure == null) {
                    failure = restoreFailure;
                } else {
                    failure.addSuppressed(restoreFailure);
                }
            }
        }
        for (int i = 0; i < prepared; i++) {
            DirectoryMove directoryMove = moved.get(i);
            directoryMove.backup().getParentFile().delete();
        }
        closed = true;
        if (failure != null) {
            throw failure;
        }
    }

    void finishCommit() throws IOException {
        if (closed) {
            return;
        }
        IOException failure = null;
        for (int i = 0; i < prepared; i++) {
            DirectoryMove directoryMove = moved.get(i);
            try {
                DatapackInstallPlanner.verifyDirectoryContainerIdentity(
                        directoryMove.backup().getParentFile(),
                        directoryMove.scratchRootIdentity(),
                        directoryMove.scratchRootFileIdentity(),
                        "datapack removal scratch root"
                );
                DatapackCoordinatorResolution.deleteOriginalBackupIfPresent(
                        directoryMove.backup(),
                        new File(directoryMove.targetRootIdentity()),
                        directoryMove.originalHash(),
                        directoryMove.originalMarkerHash(),
                        directoryMove.originalIdentity()
                );
            } catch (IOException cleanupFailure) {
                failure = DatapackSupport.appendIOException(failure, cleanupFailure);
            }
            directoryMove.backup().getParentFile().delete();
        }
        closed = true;
        if (failure != null) {
            throw failure;
        }
    }
}
