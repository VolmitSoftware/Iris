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

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.structure.authoring.StructureTransactionWriter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

final class EditableImportRemoval {
    private final List<PreparedEditableImport> prepared;
    private boolean closed;

    EditableImportRemoval(List<PreparedEditableImport> prepared) {
        this.prepared = List.copyOf(prepared);
    }

    List<StructureTransactionWriter.PreparedRemovalToken> recoveryTokens() {
        List<StructureTransactionWriter.PreparedRemovalToken> tokens = new ArrayList<>();
        for (PreparedEditableImport editableImport : prepared) {
            editableImport.removal().recoveryToken().ifPresent(tokens::add);
        }
        return List.copyOf(tokens);
    }

    void claimRecoveryOwners(Path transactionRoot, CoordinatorJournal journal) throws IOException {
        int ownerIndex = 0;
        for (PreparedEditableImport editableImport : prepared) {
            Optional<StructureTransactionWriter.PreparedRemovalToken> token =
                    editableImport.removal().recoveryToken();
            if (token.isEmpty()) {
                continue;
            }
            if (ownerIndex >= journal.editables.size()) {
                throw new IOException("Missing datapack coordinator claim for editable structure removal");
            }
            CoordinatorEditable editable = journal.editables.get(ownerIndex++);
            StructureTransactionWriter.PreparedRemovalToken recoveryToken = token.get();
            if (!Objects.equals(recoveryToken.packRoot().toString(), editable.packRoot)
                    || !Objects.equals(recoveryToken.transactionId().toString(), editable.transactionId)) {
                throw new IOException("Datapack coordinator editable claim order changed during preparation");
            }
            editableImport.removal().claimRecoveryOwner(
                    new StructureTransactionWriter.RecoveryOwner(
                            transactionRoot,
                            UUID.fromString(journal.transactionId),
                            UUID.fromString(editable.claimId)
                    )
            );
        }
        if (ownerIndex != journal.editables.size()) {
            throw new IOException("Unexpected datapack coordinator editable recovery claim");
        }
    }

    void markCommitted() throws IOException {
        if (closed) {
            throw new IllegalStateException("Editable import removal transaction is closed");
        }
        for (PreparedEditableImport editableImport : prepared) {
            editableImport.removal().markCommitted();
        }
    }

    void finishCommit() throws IOException {
        if (closed) {
            return;
        }
        IOException failure = null;
        for (PreparedEditableImport editableImport : prepared) {
            try {
                editableImport.removal().finishCommit();
            } catch (IOException | RuntimeException cleanupFailure) {
                IOException transactionFailure = cleanupFailure instanceof IOException ioFailure
                        ? ioFailure
                        : new IOException("Failed finalizing editable import removal", cleanupFailure);
                if (failure == null) {
                    failure = transactionFailure;
                } else {
                    failure.addSuppressed(transactionFailure);
                }
            }
            if (editableImport.removal().changed()) {
                try {
                    IrisData.getLoaded(editableImport.dataFolder())
                            .ifPresent(IrisData::invalidateStructureResources);
                } catch (RuntimeException invalidationFailure) {
                    IOException transactionFailure = new IOException(
                            "Failed invalidating editable import structure resources",
                            invalidationFailure
                    );
                    if (failure == null) {
                        failure = transactionFailure;
                    } else {
                        failure.addSuppressed(transactionFailure);
                    }
                }
            }
        }
        closed = true;
        if (failure != null) {
            throw failure;
        }
    }

    void rollback() throws IOException {
        if (closed) {
            return;
        }
        IOException failure = null;
        for (int i = prepared.size() - 1; i >= 0; i--) {
            try {
                prepared.get(i).removal().rollback();
            } catch (IOException rollbackFailure) {
                if (failure == null) {
                    failure = rollbackFailure;
                } else {
                    failure.addSuppressed(rollbackFailure);
                }
            }
        }
        closed = true;
        if (failure != null) {
            throw failure;
        }
    }

    void leaveForRecovery() throws IOException {
        if (closed) {
            return;
        }
        IOException failure = null;
        for (PreparedEditableImport editableImport : prepared) {
            try {
                editableImport.removal().leaveForRecovery();
            } catch (IOException releaseFailure) {
                if (failure == null) {
                    failure = releaseFailure;
                } else {
                    failure.addSuppressed(releaseFailure);
                }
            }
        }
        closed = true;
        if (failure != null) {
            throw failure;
        }
    }
}
