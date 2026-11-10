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

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record StructureRecoveryResult(
        int restoredPreparedTransactions,
        int cleanedCommittedTransactions,
        int cleanedOrphanTransactions,
        List<Failure> failures
) {
    public StructureRecoveryResult {
        if (restoredPreparedTransactions < 0
                || cleanedCommittedTransactions < 0
                || cleanedOrphanTransactions < 0) {
            throw new IllegalArgumentException("Structure recovery counts cannot be negative");
        }
        Objects.requireNonNull(failures, "failures");
        failures = List.copyOf(failures);
    }

    public boolean successful() {
        return failures.isEmpty();
    }

    public int recoveredTransactions() {
        return restoredPreparedTransactions + cleanedCommittedTransactions + cleanedOrphanTransactions;
    }

    public record Failure(Path transactionRoot, Throwable cause) {
        public Failure {
            Objects.requireNonNull(transactionRoot, "transactionRoot");
            Objects.requireNonNull(cause, "cause");
            transactionRoot = transactionRoot.toAbsolutePath().normalize();
        }
    }
}
