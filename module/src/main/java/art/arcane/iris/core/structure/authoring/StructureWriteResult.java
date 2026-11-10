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

package art.arcane.iris.core.structure.authoring;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record StructureWriteResult(
        Status status,
        Action action,
        List<Conflict> conflicts,
        List<String> affectedResources,
        String manifestPath,
        Optional<Throwable> failure
) {
    public StructureWriteResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(conflicts, "conflicts");
        Objects.requireNonNull(affectedResources, "affectedResources");
        Objects.requireNonNull(manifestPath, "manifestPath");
        Objects.requireNonNull(failure, "failure");
        conflicts = List.copyOf(conflicts);
        affectedResources = List.copyOf(affectedResources);
    }

    public boolean successful() {
        return switch (status) {
            case DRY_RUN, ADDED, OVERWRITTEN, UNCHANGED, COMMITTED_CLEANUP_REQUIRED -> true;
            case ADD_ONLY_CONFLICT, OWNERSHIP_CONFLICT, ROLLED_BACK, FAILED -> false;
        };
    }

    public boolean committed() {
        return switch (status) {
            case ADDED, OVERWRITTEN, UNCHANGED, COMMITTED_CLEANUP_REQUIRED -> true;
            case DRY_RUN, ADD_ONLY_CONFLICT, OWNERSHIP_CONFLICT, ROLLED_BACK, FAILED -> false;
        };
    }

    public enum Status {
        DRY_RUN,
        ADDED,
        OVERWRITTEN,
        UNCHANGED,
        ADD_ONLY_CONFLICT,
        OWNERSHIP_CONFLICT,
        ROLLED_BACK,
        FAILED,
        COMMITTED_CLEANUP_REQUIRED
    }

    public enum Action {
        ADD,
        OVERWRITE,
        NONE
    }

    public enum ConflictReason {
        RESOURCE_EXISTS,
        MANIFEST_EXISTS,
        UNOWNED_RESOURCE,
        MODIFIED_RESOURCE,
        MISSING_OWNED_RESOURCE,
        NON_FILE_RESOURCE,
        INVALID_MANIFEST,
        PROVENANCE_MISMATCH,
        STALE_MANIFEST,
        STALE_READ_SET
    }

    public record Conflict(
            String relativePath,
            ConflictReason reason,
            String expectedHash,
            String actualHash,
            String detail
    ) {
        public Conflict {
            Objects.requireNonNull(relativePath, "relativePath");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(expectedHash, "expectedHash");
            Objects.requireNonNull(actualHash, "actualHash");
            Objects.requireNonNull(detail, "detail");
        }

        public static Conflict at(String relativePath, ConflictReason reason) {
            return new Conflict(relativePath, reason, "", "", "");
        }

        public static Conflict modified(String relativePath, String expectedHash, String actualHash) {
            return new Conflict(
                    relativePath,
                    ConflictReason.MODIFIED_RESOURCE,
                    expectedHash,
                    actualHash,
                    "Resource content differs from its ownership manifest"
            );
        }

        public static Conflict invalidManifest(String relativePath, String detail) {
            return new Conflict(relativePath, ConflictReason.INVALID_MANIFEST, "", "", detail);
        }

        public static Conflict staleManifest(String relativePath, String expectedHash, String actualHash) {
            return new Conflict(
                    relativePath,
                    ConflictReason.STALE_MANIFEST,
                    expectedHash,
                    actualHash,
                    "Ownership manifest changed after the graph edit was loaded"
            );
        }

        public static Conflict staleReadSet(
                String relativePath,
                String expectedHash,
                String actualHash,
                String detail
        ) {
            return new Conflict(
                    relativePath,
                    ConflictReason.STALE_READ_SET,
                    expectedHash,
                    actualHash,
                    detail
            );
        }
    }
}
