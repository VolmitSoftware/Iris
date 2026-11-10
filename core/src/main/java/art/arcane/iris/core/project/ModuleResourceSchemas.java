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

package art.arcane.iris.core.project;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.PackModulesException;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;
import com.volmit.host.schema.ResourceKindSchema;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The studio schemas of a pack's module resource kinds, written the way Iris's loaders write theirs: one schema file
 * per kind, {@code .iris/schema/<folder>-schema.json}, and a workspace entry matching every JSON file below the
 * kind's folder. Every workspace Iris writes adds these next to its loaders' entries.
 */
public final class ModuleResourceSchemas {
    private static final String SCHEMA_FOLDER = ".iris/schema/";
    private static final String SCHEMA_SUFFIX = "-schema.json";
    private static final int FOLDER_MATCH_DEPTH = 8;

    private ModuleResourceSchemas() {
    }

    /**
     * The workspace entries of {@code data}'s module resource kinds, writing each kind's schema file now when
     * {@code immediate}, or in the background. A pack whose modules fail to load gets none; its load reported why.
     */
    public static List<JSONObject> entries(IrisData data, boolean immediate) {
        return entries(data, immediate, new ArrayList<>());
    }

    /**
     * As {@link #entries(IrisData, boolean)}, adding the write of each schema file to {@code schemaWrites}.
     */
    public static List<JSONObject> entries(IrisData data, boolean immediate, List<CompletableFuture<String>> schemaWrites) {
        List<ResourceKindSchema> kinds;
        try {
            kinds = data.getModules().pack().schema().resources();
        } catch (PackModulesException failedModules) {
            return List.of();
        }
        List<JSONObject> entries = new ArrayList<>();
        for (ResourceKindSchema kind : kinds) {
            entries.add(entry(data, kind, immediate, schemaWrites));
        }
        return entries;
    }

    private static JSONObject entry(IrisData data, ResourceKindSchema kind, boolean immediate, List<CompletableFuture<String>> schemaWrites) {
        File schemaFile = new File(data.getDataFolder(), SCHEMA_FOLDER + kind.folder() + SCHEMA_SUFFIX);
        schemaWrites.add(SchemaFiles.write(schemaFile, () -> SchemaBuilder.moduleResource(kind, data).toString(4), immediate));
        KList<String> fileMatches = new KList<>();
        for (int depth = 1; depth < FOLDER_MATCH_DEPTH; depth++) {
            fileMatches.add("/" + kind.folder() + Form.repeat("/*", depth) + ".json");
        }
        JSONObject entry = new JSONObject();
        entry.put("fileMatch", new JSONArray(fileMatches.toArray()));
        entry.put("url", "./" + SCHEMA_FOLDER + kind.folder() + SCHEMA_SUFFIX);
        return entry;
    }
}
