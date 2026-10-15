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

import art.arcane.iris.pack.ingest.catalog.CatalogStructure;
import art.arcane.iris.pack.ingest.catalog.WorldgenCatalogIndex;
import art.arcane.iris.structure.nativegen.IrisImportedStructureControl;
import art.arcane.volmlib.util.collection.KList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

final class ModStructureIngester {
    private ModStructureIngester() {
    }

    static List<ModIngestItem> ingest(List<CatalogStructure> structures, Set<String> namespaces, Set<String> hostBiomes, JsonObject dimension) {
        IrisImportedStructureControl control = control(dimension.get("importedStructures"));
        List<CatalogStructure> selected = new ArrayList<>();
        for (CatalogStructure structure : structures) {
            if (namespaces.contains(WorldgenCatalogIndex.namespaceOf(structure.key()))) {
                selected.add(structure);
            }
        }
        selected.sort(Comparator.comparing(CatalogStructure::key));
        List<ModIngestItem> items = new ArrayList<>(selected.size());
        for (CatalogStructure structure : selected) {
            String namespace = WorldgenCatalogIndex.namespaceOf(structure.key());
            if (!control.shouldGenerate(structure.key())) {
                items.add(new ModIngestItem(namespace, structure.key(), "structure", ModIngestTier.SKIPPED, "",
                        "disabled by the dimension's importedStructures"));
                continue;
            }
            if (!structure.biomes().isEmpty() && structure.biomes().stream().noneMatch(hostBiomes::contains)) {
                items.add(new ModIngestItem(namespace, structure.key(), "structure", ModIngestTier.SKIPPED, "",
                        "none of its biomes generate in the target dimension"));
                continue;
            }
            items.add(new ModIngestItem(namespace, structure.key(), "structure", ModIngestTier.ADOPTED, "importedStructures",
                    "generates natively in its biomes"));
        }
        return items;
    }

    private static IrisImportedStructureControl control(JsonElement value) {
        IrisImportedStructureControl control = new IrisImportedStructureControl();
        if (value == null || !value.isJsonObject()) {
            return control;
        }
        JsonObject object = value.getAsJsonObject();
        control.setDisabled(new KList<>(IngestJson.strings(object.get("disabled"))));
        control.setDisabledExact(new KList<>(IngestJson.strings(object.get("disabledExact"))));
        return control;
    }
}
