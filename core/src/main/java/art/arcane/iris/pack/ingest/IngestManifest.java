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
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class IngestManifest {
    static final String PATH = "ingest/manifest.json";
    static final int FORMAT = 1;

    private IngestManifest() {
    }

    static void write(IngestPack pack, WorldgenCatalog catalog, String sourceDimension, String dimension,
                      Set<String> namespaces, List<ModIngestItem> items) throws IOException {
        JsonObject manifest = new JsonObject();
        manifest.addProperty("format", FORMAT);
        manifest.addProperty("catalogFingerprint", catalog.contentFingerprint());
        manifest.addProperty("catalogCapturedAt", catalog.capturedAtEpochMillis());
        manifest.addProperty("minecraftVersion", catalog.minecraftVersion());
        manifest.addProperty("platform", catalog.platform());
        manifest.addProperty("sourceDimension", sourceDimension);
        manifest.addProperty("dimension", dimension);
        manifest.add("namespaces", IngestJson.stringArray(namespaces));
        manifest.add("generatedFiles", IngestJson.stringArray(pack.generatedFiles()));
        manifest.add("modifiedFiles", IngestJson.stringArray(pack.modifiedFiles()));
        JsonArray itemArray = new JsonArray(items.size());
        for (ModIngestItem item : items) {
            JsonObject entry = new JsonObject();
            entry.addProperty("namespace", item.namespace());
            entry.addProperty("sourceKey", item.sourceKey());
            entry.addProperty("kind", item.kind());
            entry.addProperty("tier", item.tier().name());
            entry.addProperty("target", item.target());
            entry.addProperty("reason", item.reason());
            itemArray.add(entry);
        }
        manifest.add("items", itemArray);
        pack.write(PATH, manifest);
    }

    static List<String> summary(String dimension, IngestPack pack, Set<String> namespaces, List<ModIngestItem> items) {
        List<String> lines = new ArrayList<>(namespaces.size() + 1);
        lines.add("pack " + dimension + ": " + pack.generatedFiles().size() + " generated files, "
                + pack.modifiedFiles().size() + " modified files");
        for (String namespace : namespaces) {
            Map<ModIngestTier, Integer> counts = new EnumMap<>(ModIngestTier.class);
            for (ModIngestTier tier : ModIngestTier.values()) {
                counts.put(tier, 0);
            }
            for (ModIngestItem item : items) {
                if (item.namespace().equals(namespace)) {
                    counts.merge(item.tier(), 1, Integer::sum);
                }
            }
            lines.add(namespace + ": " + counts.get(ModIngestTier.NATIVE) + " native, "
                    + counts.get(ModIngestTier.ADOPTED) + " adopted, "
                    + counts.get(ModIngestTier.PASSTHROUGH) + " passthrough, "
                    + counts.get(ModIngestTier.SKIPPED) + " skipped");
        }
        return lines;
    }
}
