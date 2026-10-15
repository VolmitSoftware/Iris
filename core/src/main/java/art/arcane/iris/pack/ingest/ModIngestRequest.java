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


package art.arcane.iris.pack.ingest;

import art.arcane.iris.pack.ingest.catalog.WorldgenCatalog;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

public record ModIngestRequest(Path sourcePack, Path targetPack, String dimensionKey, WorldgenCatalog catalog, Set<String> namespaces,
                               Set<String> excludedRegions, boolean replaceTarget) {
    public ModIngestRequest {
        Objects.requireNonNull(sourcePack, "sourcePack");
        Objects.requireNonNull(targetPack, "targetPack");
        Objects.requireNonNull(dimensionKey, "dimensionKey");
        Objects.requireNonNull(catalog, "catalog");
        namespaces = Set.copyOf(Objects.requireNonNull(namespaces, "namespaces"));
        excludedRegions = Set.copyOf(Objects.requireNonNull(excludedRegions, "excludedRegions"));
    }
}
