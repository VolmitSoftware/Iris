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

import java.util.List;

final class Ownership {
    final int schemaVersion;
    final String id;
    final String url;
    final String versionId;
    final String versionNumber;
    final String sha1;
    final String contentHash;
    final List<String> structureKeys;
    final List<String> templateKeys;

    Ownership(
            int schemaVersion,
            String id,
            String url,
            String versionId,
            String versionNumber,
            String sha1,
            String contentHash,
            List<String> structureKeys,
            List<String> templateKeys
    ) {
        this.schemaVersion = schemaVersion;
        this.id = id;
        this.url = url;
        this.versionId = versionId;
        this.versionNumber = versionNumber;
        this.sha1 = sha1;
        this.contentHash = contentHash;
        this.structureKeys = List.copyOf(structureKeys);
        this.templateKeys = List.copyOf(templateKeys);
    }

    Entry toEntry() {
        Entry entry = new Entry();
        entry.id = id;
        entry.url = url;
        entry.versionId = versionId;
        entry.versionNumber = versionNumber;
        entry.sha1 = sha1;
        entry.structureKeys = DatapackManifestStore.normalizeKeys(structureKeys);
        entry.templateKeys = DatapackManifestStore.normalizeKeys(templateKeys);
        return entry;
    }
}
