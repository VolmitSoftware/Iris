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

package art.arcane.iris.studio.workspace;

import art.arcane.iris.pack.schema.SchemaBuilder;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;
import org.dom4j.Document;
import org.dom4j.Element;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings("ALL")
public class IrisCodeWorkspace {
    private final IrisProject project;

    public IrisCodeWorkspace(IrisProject project) {
        this.project = project;
    }

    public void openVSCode(VolmitSender sender) {
        J.attemptAsync(this::prepareAndOpenVSCode);
    }

    void prepareAndOpenVSCode() {
        boolean updated = updateWorkspace(true);
        File workspace = getCodeWorkspaceFile();
        if (!workspace.isFile()) {
            IrisLogging.warn("Could not create the code workspace for project " + project.getName() + " at " + workspace.getAbsolutePath() + ".");
            return;
        }
        if (!updated) {
            IrisLogging.warn("Could not refresh every schema for " + workspace.getAbsolutePath() + "; the editor will not be opened with stale autocomplete data.");
            return;
        }
        if (!IrisSettings.get().getStudio().isOpenVSCode() || GraphicsEnvironment.isHeadless()) {
            return;
        }

        IrisLogging.msg("Opening VSCode. You may see the output from VSCode.");
        IrisLogging.msg("VSCode output always starts with: '(node:#####) electron'");
        Thread launcherThread = new Thread(() -> {
            try {
                Desktop.getDesktop().open(workspace);
            } catch (Throwable e) {
                IrisLogging.reportError(e);
            }
        }, "Iris-VSCode-Launcher");
        launcherThread.setDaemon(true);
        launcherThread.start();
    }

    public File getCodeWorkspaceFile() {
        return new File(project.getPath(), project.getName() + ".code-workspace");
    }

    public boolean updateWorkspace() {
        return updateWorkspace(false);
    }

    private boolean updateWorkspace(boolean immediateSchemas) {
        project.getPath().mkdirs();
        File ws = getCodeWorkspaceFile();

        // Render before touching the file: a config-generation failure has nothing to do with
        // the existing workspace file, and deleting it before a rethrowing rebuild silently
        // destroyed the author's workspace on every boot.
        String rendered;
        try {
            rendered = createCodeWorkspaceConfig(immediateSchemas).toString(4);
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            IrisLogging.warn("Could not generate the code workspace config for " + ws.getAbsolutePath() + "; leaving the existing workspace file untouched.");
            return false;
        }

        try {
            writeIfChanged(ws, rendered);
            return true;
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            IrisLogging.warn("Project invalid: " + ws.getAbsolutePath() + " Re-creating. You may loose some vs-code workspace settings! But not your actual project!");
            ws.delete();
            try {
                IO.writeAll(ws, rendered);
            } catch (Throwable e1) {
                IrisLogging.reportError(e1);
            }
        }

        return false;
    }

    public JSONObject createCodeWorkspaceConfig() {
        return createCodeWorkspaceConfig(false);
    }

    private static void writeIfChanged(File target, String rendered) throws IOException {
        if (target.isFile() && (rendered + "\n").equals(IO.readAll(target))) {
            return;
        }
        IO.writeAll(target, rendered);
    }

    private JSONObject createCodeWorkspaceConfig(boolean immediateSchemas) {
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
        JSONObject jc = new JSONObject();
        jc.put("editor.autoIndent", "brackets");
        jc.put("editor.acceptSuggestionOnEnter", "smart");
        jc.put("editor.cursorSmoothCaretAnimation", true);
        jc.put("editor.dragAndDrop", false);
        jc.put("files.trimTrailingWhitespace", true);
        jc.put("diffEditor.ignoreTrimWhitespace", true);
        jc.put("files.trimFinalNewlines", true);
        jc.put("editor.suggest.showKeywords", false);
        jc.put("editor.suggest.showSnippets", false);
        jc.put("editor.suggest.showWords", false);
        JSONObject st = new JSONObject();
        st.put("strings", true);
        jc.put("editor.quickSuggestions", st);
        jc.put("editor.suggest.insertMode", "replace");
        settings.put("[json]", jc);
        settings.put("json.maxItemsComputed", 30000);
        JSONArray schemas = new JSONArray();
        List<JSONObject> schemaEntries = new ArrayList<>();
        IrisData dm = IrisData.get(project.getPath());
        for (ResourceLoader<?> resourceLoader : dm.getLoaders().v()) {
            if (resourceLoader.supportsSchemas()) {
                schemaEntries.add(immediateSchemas
                        ? resourceLoader.buildSchemaImmediately()
                        : resourceLoader.buildSchema());
            }
        }

        for (Class<?> snippetClass : sortedSnippets(dm.resolveSnippets())) {
            try {
                String snipType = snippetClass.getDeclaredAnnotation(Snippet.class).value();
                JSONObject schemaEntry = new JSONObject();
                KList<String> fileMatches = new KList<>();

                for (int depth = 1; depth < 8; depth++) {
                    fileMatches.add("/snippet/" + snipType + Form.repeat("/*", depth) + ".json");
                }

                schemaEntry.put("fileMatch", new JSONArray(fileMatches.toArray()));
                schemaEntry.put("url", "./.iris/schema/snippet/" + snipType + "-schema.json");
                schemaEntries.add(schemaEntry);
                File schemaFile = new File(dm.getDataFolder(), ".iris/schema/snippet/" + snipType + "-schema.json");
                if (immediateSchemas) {
                    IO.writeAll(schemaFile, new SchemaBuilder(snippetClass, dm).construct().toString(4));
                } else {
                    IrisData snippetData = dm;
                    J.attemptAsync(() -> {
                        try {
                            IO.writeAll(schemaFile, new SchemaBuilder(snippetClass, snippetData).construct().toString(4));
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

        schemaEntries.sort(Comparator.comparing(entry -> entry.getString("url")));
        for (JSONObject entry : schemaEntries) {
            schemas.put(entry);
        }

        settings.put("json.schemas", schemas);
        ws.put("settings", settings);

        try {
            updateIntelliJSchemaMappings(schemas);
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            IrisLogging.warn("Could not update IntelliJ schema mappings for " + project.getPath().getAbsolutePath() + "; VSCode workspace generation will continue.");
        }

        return ws;
    }

    private void updateIntelliJSchemaMappings(JSONArray schemas) {
        File schemasFile = new File(project.getPath(), ".idea" + File.separator + "jsonSchemas.xml");
        Document doc = IO.read(schemasFile);
        Element mappings = (Element) doc.selectSingleNode("//component[@name='JsonSchemaMappingsProjectConfiguration']");
        if (mappings == null) {
            mappings = doc.getRootElement()
                    .addElement("component")
                    .addAttribute("name", "JsonSchemaMappingsProjectConfiguration");
        }

        Element state = (Element) mappings.selectSingleNode("state");
        if (state == null) state = mappings.addElement("state");

        Element map = (Element) state.selectSingleNode("map");
        if (map == null) map = state.addElement("map");
        KMap<String, String> schemaMap = new KMap<>();
        schemas.forEach(element -> {
            if (!(element instanceof JSONObject obj))
                return;

            String url = obj.getString("url");
            String dir = obj.getJSONArray("fileMatch").getString(0);
            schemaMap.put(url, dir.substring(1, dir.indexOf("/*")));
        });

        map.selectNodes("entry/value/SchemaInfo/option[@name='relativePathToSchema']")
                .stream()
                .map(node -> node.valueOf("@value"))
                .forEach(schemaMap::remove);

        Element ideaSchemas = map;
        schemaMap.forEach((url, dir) -> {
            String generatedName = UUID.randomUUID().toString();

            Element info = ideaSchemas.addElement("entry")
                    .addAttribute("key", generatedName)
                    .addElement("value")
                    .addElement("SchemaInfo");
            info.addElement("option")
                    .addAttribute("name", "generatedName")
                    .addAttribute("value", generatedName);
            info.addElement("option")
                    .addAttribute("name", "name")
                    .addAttribute("value", dir);
            info.addElement("option")
                    .addAttribute("name", "relativePathToSchema")
                    .addAttribute("value", url);


            Element item = info.addElement("option")
                    .addAttribute("name", "patterns")
                    .addElement("list")
                    .addElement("Item");
            item.addElement("option")
                    .addAttribute("name", "directory")
                    .addAttribute("value", "true");
            item.addElement("option")
                    .addAttribute("name", "path")
                    .addAttribute("value", dir);
            item.addElement("option")
                    .addAttribute("name", "mappingKind")
                    .addAttribute("value", "Directory");
        });
        if (!schemaMap.isEmpty()) {
            IO.write(schemasFile, doc);
        }
    }

    private static List<Class<?>> sortedSnippets(Set<Class<?>> snippets) {
        List<Class<?>> sorted = new ArrayList<>(snippets);
        sorted.sort(Comparator.comparing(Class::getName));
        return sorted;
    }
}
