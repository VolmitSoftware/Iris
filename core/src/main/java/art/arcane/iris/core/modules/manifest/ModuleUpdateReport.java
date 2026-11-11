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

package art.arcane.iris.core.modules.manifest;

import java.util.List;
import java.util.Optional;

/**
 * What bringing one pack's modules up to date did and what it left alone.
 *
 * @param managed        whether the pack has a {@code modules.json}; Iris touches no module of a pack without one
 * @param changes        one entry per module of every shipped jar the manifest asks for
 * @param needNewerIris  the modules the pack was built with in a newer version than this Iris ships
 */
public record ModuleUpdateReport(boolean managed, List<ModuleChange> changes, List<ModuleChange> needNewerIris) {
    public enum Outcome {
        INSTALLED(true, false),
        UPDATED(true, false),
        CURRENT(false, false),
        KEPT_PINNED(false, true),
        KEPT_NEWER_THAN_SHIPPED(false, true),
        KEPT_TERRAIN_CHANGE(false, true),
        KEPT_SHARED_CODE(false, true);

        private final boolean wroteJar;
        private final boolean keptOlderJar;

        Outcome(boolean wroteJar, boolean keptOlderJar) {
            this.wroteJar = wroteJar;
            this.keptOlderJar = keptOlderJar;
        }

        public boolean wroteJar() {
            return wroteJar;
        }

        /** Whether the pack keeps a jar that differs from the one Iris ships. */
        public boolean keptInstalledJar() {
            return keptOlderJar;
        }
    }

    /**
     * @param installedVersion empty when the pack had no jar for the module, or Iris has no record of its version
     * @param builtWithVersion the version {@code modules.json} names, for {@link #needNewerIris}; else empty
     * @param keptWith         for {@link Outcome#KEPT_SHARED_CODE}, the kept module whose code this one uses
     */
    public record ModuleChange(String moduleId, Optional<String> installedVersion, String shippedVersion, Outcome outcome,
                               Optional<String> builtWithVersion, Optional<String> keptWith) {
        public String describe() {
            String installed = installedVersion.orElse("its installed version");
            return switch (outcome) {
                case INSTALLED -> moduleId + " " + shippedVersion + " installed";
                case UPDATED -> describeUpdate();
                case CURRENT -> moduleId + " " + shippedVersion + " is up to date";
                case KEPT_PINNED -> moduleId + " " + shippedVersion + " is available, kept " + installed + " (pinned in "
                        + PackModuleManifest.FILE_NAME + ")";
                case KEPT_NEWER_THAN_SHIPPED -> moduleId + " " + installed + " is newer than the " + shippedVersion
                        + " this Iris ships, kept";
                case KEPT_TERRAIN_CHANGE -> moduleId + " " + shippedVersion + " changes terrain, kept " + installed;
                case KEPT_SHARED_CODE -> moduleId + " " + shippedVersion + " uses code of " + keptWith.orElse("a kept module")
                        + ", which was kept; kept " + installed;
            };
        }

        public String describeNeedForNewerIris() {
            return moduleId + " " + builtWithVersion.orElse("?") + " is what the pack was built with, this Iris ships "
                    + shippedVersion + "; update Iris";
        }

        private String describeUpdate() {
            if (installedVersion.isEmpty()) {
                return moduleId + " updated to " + shippedVersion;
            }
            if (installedVersion.get().equals(shippedVersion)) {
                return moduleId + " " + shippedVersion + " replaced by this Iris's build of it";
            }
            return moduleId + " " + installedVersion.get() + " -> " + shippedVersion;
        }
    }

    public ModuleUpdateReport {
        changes = List.copyOf(changes);
        needNewerIris = List.copyOf(needNewerIris);
    }

    static ModuleUpdateReport unmanaged() {
        return new ModuleUpdateReport(false, List.of(), List.of());
    }

    /** The modules whose jar Iris wrote: installed or updated. */
    public List<ModuleChange> written() {
        return changes.stream().filter(change -> change.outcome().wroteJar()).toList();
    }

    /** The modules that stay behind what Iris ships, or ahead of it. */
    public List<ModuleChange> kept() {
        return changes.stream().filter(change -> change.outcome().keptInstalledJar()).toList();
    }

    public boolean keptTerrainChanges() {
        return changes.stream().anyMatch(change -> change.outcome() == Outcome.KEPT_TERRAIN_CHANGE);
    }
}
