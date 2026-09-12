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

package art.arcane.iris.modded;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.pack.schema.SchemaBuilder;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;

import java.io.File;
import java.io.IOException;

public final class ModdedWorkspaceGenerator {
    private ModdedWorkspaceGenerator() {
    }

    public static File writeWorkspace(IrisData data, File folder) throws IOException {
        return writeWorkspace(data, folder, false);
    }

    public static File writeWorkspace(IrisData data, File folder, boolean immediateSchemas) throws IOException {
        JSONObject workspaceConfig = buildWorkspace(data, immediateSchemas);
        File workspace = new File(folder, folder.getName() + ".code-workspace");
        IO.writeAll(workspace, workspaceConfig.toString(4));
        return workspace;
    }

    public static JSONObject buildWorkspace(IrisData data) {
        return buildWorkspace(data, false);
    }

    private static JSONObject buildWorkspace(IrisData data, boolean immediateSchemas) {
        JSONObject ws = new JSONObject();
        JSONArray folders = new JSONArray();
        JSONObject folder = new JSONObject();
        folder.put("path", ".");
        folders.put(folder);
        ws.put("folders", folders);
        JSONObject settings = new JSONObject();
        settings.put("workbench.colorTheme", "Monokai");
        settings.put("workbench.preferredDarkColorTheme", "Solarized Dark");
        settings.put("workbench.tips.enabled", false);
        settings.put("workbench.tree.indent", 24);
        settings.put("files.autoSave", "onFocusChange");
        JSONObject json = new JSONObject();
        json.put("editor.autoIndent", "brackets");
        json.put("editor.acceptSuggestionOnEnter", "smart");
        json.put("editor.cursorSmoothCaretAnimation", true);
        json.put("editor.dragAndDrop", false);
        json.put("files.trimTrailingWhitespace", true);
        json.put("diffEditor.ignoreTrimWhitespace", true);
        json.put("files.trimFinalNewlines", true);
        json.put("editor.suggest.showKeywords", false);
        json.put("editor.suggest.showSnippets", false);
        json.put("editor.suggest.showWords", false);
        JSONObject quick = new JSONObject();
        quick.put("strings", true);
        json.put("editor.quickSuggestions", quick);
        json.put("editor.suggest.insertMode", "replace");
        settings.put("[json]", json);
        settings.put("json.maxItemsComputed", 30000);
        settings.put("json.schemas", buildSchemas(data, immediateSchemas));
        ws.put("settings", settings);
        return ws;
    }

    private static JSONArray buildSchemas(IrisData data, boolean immediateSchemas) {
        JSONArray schemas = new JSONArray();

        for (ResourceLoader<?> loader : data.getLoaders().v()) {
            if (loader.supportsSchemas()) {
                schemas.put(immediateSchemas ? loader.buildSchemaImmediately() : loader.buildSchema());
            }
        }

        for (Class<?> snippetClass : data.resolveSnippets()) {
            try {
                String snipType = snippetClass.getDeclaredAnnotation(Snippet.class).value();
                JSONObject entry = new JSONObject();
                KList<String> fileMatch = new KList<>();

                for (int depth = 1; depth < 8; depth++) {
                    fileMatch.add("/snippet/" + snipType + Form.repeat("/*", depth) + ".json");
                }

                entry.put("fileMatch", new JSONArray(fileMatch.toArray()));
                entry.put("url", "./.iris/schema/snippet/" + snipType + "-schema.json");
                schemas.put(entry);
                File schemaFile = new File(data.getDataFolder(), ".iris/schema/snippet/" + snipType + "-schema.json");
                if (immediateSchemas) {
                    IO.writeAll(schemaFile, new SchemaBuilder(snippetClass, data).construct().toString(4));
                } else {
                    J.attemptAsync(() -> {
                        try {
                            IO.writeAll(schemaFile, new SchemaBuilder(snippetClass, data).construct().toString(4));
                        } catch (Throwable e) {
                            IrisLogging.reportError(e);
                        }
                    });
                }
            } catch (Throwable e) {
                if (immediateSchemas) {
                    throw new IllegalStateException("Could not write snippet schema for " + snippetClass.getName(), e);
                }
                IrisLogging.reportError(e);
            }
        }

        return schemas;
    }
}
