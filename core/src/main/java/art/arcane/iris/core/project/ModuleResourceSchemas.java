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
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;
import com.volmit.host.schema.ResourceKindSchema;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

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
        List<ResourceKindSchema> kinds;
        try {
            kinds = data.getModules().pack().schema().resources();
        } catch (PackModulesException failedModules) {
            return List.of();
        }
        List<JSONObject> entries = new ArrayList<>();
        for (ResourceKindSchema kind : kinds) {
            entries.add(entry(data, kind, immediate));
        }
        return entries;
    }

    private static JSONObject entry(IrisData data, ResourceKindSchema kind, boolean immediate) {
        File schemaFile = new File(data.getDataFolder(), SCHEMA_FOLDER + kind.folder() + SCHEMA_SUFFIX);
        if (immediate) {
            write(schemaFile, SchemaBuilder.moduleResource(kind, data));
        } else {
            J.attemptAsync(() -> write(schemaFile, SchemaBuilder.moduleResource(kind, data)));
        }
        KList<String> fileMatches = new KList<>();
        for (int depth = 1; depth < FOLDER_MATCH_DEPTH; depth++) {
            fileMatches.add("/" + kind.folder() + Form.repeat("/*", depth) + ".json");
        }
        JSONObject entry = new JSONObject();
        entry.put("fileMatch", new JSONArray(fileMatches.toArray()));
        entry.put("url", "./" + SCHEMA_FOLDER + kind.folder() + SCHEMA_SUFFIX);
        return entry;
    }

    private static void write(File schemaFile, JSONObject schema) {
        try {
            IO.writeAll(schemaFile, schema.toString(4));
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not write schema " + schemaFile.getAbsolutePath(), failure);
        }
    }
}
