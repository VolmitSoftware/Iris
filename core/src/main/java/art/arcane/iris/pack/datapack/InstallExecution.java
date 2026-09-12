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

import art.arcane.iris.pack.datapack.DatapackIngestService.Entry;

import java.io.File;
import java.util.List;

final class InstallExecution {
    private final InstallResult result;
    private final DatapackCoordinator coordinator;
    private final File stagedDir;
    private final Entry entry;
    private final String stagedHash;
    private final boolean verifyStagedSource;
    private final List<InstallPlan> publishedPlans;
    private final List<InstallPlan> unchangedPlans;
    private boolean verified;

    InstallExecution(
            InstallResult result,
            DatapackCoordinator coordinator,
            File stagedDir,
            Entry entry,
            String stagedHash,
            boolean verifyStagedSource,
            List<InstallPlan> publishedPlans,
            List<InstallPlan> unchangedPlans
    ) {
        this.result = result;
        this.coordinator = coordinator;
        this.stagedDir = stagedDir;
        this.entry = DatapackManifestStore.copyEntry(entry);
        this.stagedHash = stagedHash;
        this.verifyStagedSource = verifyStagedSource;
        this.publishedPlans = List.copyOf(publishedPlans);
        this.unchangedPlans = List.copyOf(unchangedPlans);
    }

    InstallResult result() {
        return result;
    }

    DatapackCoordinator coordinator() {
        return coordinator;
    }

    File stagedDir() {
        return stagedDir;
    }

    Entry entry() {
        return entry;
    }

    String stagedHash() {
        return stagedHash;
    }

    boolean verifyStagedSource() {
        return verifyStagedSource;
    }

    List<InstallPlan> publishedPlans() {
        return publishedPlans;
    }

    List<InstallPlan> unchangedPlans() {
        return unchangedPlans;
    }

    boolean verified() {
        return verified;
    }

    void markVerified() {
        verified = true;
    }
}
