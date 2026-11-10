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

package art.arcane.iris.modules.jigsaw;

import art.arcane.iris.core.structure.BulkStructureImporter;
import art.arcane.iris.core.structure.StructureImporter;
import com.volmit.resource.ResourceId;
import com.volmit.tooling.DatapackImportReport;
import com.volmit.tooling.DatapackStructureImport;
import com.volmit.tooling.PlatformStructures;
import com.volmit.tooling.Sender;
import com.volmit.tooling.ToolPack;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Imports a datapack's structures and templates as jigsaw structures, replacing what an earlier import of them made. */
final class JigsawDatapackImport implements DatapackStructureImport {
    @Override
    public DatapackImportReport importAll(ToolPack pack, PlatformStructures platform, Sender sender,
                                          Collection<ResourceId> structures, Collection<ResourceId> templates) {
        BulkStructureImporter.Report report = BulkStructureImporter.importManagedDatapackStructures(
                pack, platform, StructureImporter.Mode.OVERWRITE, sender, keysOf(structures), keysOf(templates));
        return new DatapackImportReport(report.total(), report.imported(), report.skipped(), report.failed(),
                importedBundles(report), report.retryRequired());
    }

    private static Set<String> keysOf(Collection<ResourceId> ids) {
        Set<String> keys = new TreeSet<>();
        ids.forEach(id -> keys.add(id.toString()));
        return keys;
    }

    private static Map<String, ResourceId> importedBundles(BulkStructureImporter.Report report) {
        Map<String, ResourceId> bundles = new TreeMap<>();
        report.successfulBundles().forEach((bundle, source) -> bundles.put(bundle, ResourceId.ofPlatformKey(source)));
        return bundles;
    }
}
