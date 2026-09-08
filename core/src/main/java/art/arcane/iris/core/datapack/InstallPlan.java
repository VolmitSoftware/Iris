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

record InstallPlan(
        File target,
        File pending,
        File backup,
        File pendingRoot,
        boolean hadTarget,
        boolean publishRequired,
        boolean contentChanged,
        String originalHash,
        String desiredHash,
        String originalMarkerHash,
        String desiredMarkerHash,
        String originalIdentity,
        String desiredIdentity,
        String targetRootIdentity,
        String scratchRootIdentity,
        String targetRootFileIdentity,
        String scratchRootFileIdentity,
        String id,
        String url,
        VerifiedStagingInstall legacyWorldAuthorization
) {
}
