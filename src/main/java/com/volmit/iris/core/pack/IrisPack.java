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

package com.volmit.iris.core.pack;

import com.volmit.iris.Iris;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.core.loader.ResourceLoader;
import com.volmit.iris.core.service.StudioSVC;
import com.volmit.iris.engine.object.IrisDimension;
import com.volmit.iris.engine.object.IrisWorld;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.exceptions.IrisException;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.json.JSONArray;
import com.volmit.iris.util.json.JSONObject;
import com.volmit.iris.util.plugin.VolmitSender;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import lombok.Data;
import org.bukkit.World;
import org.zeroturnaround.zip.commons.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * Represents an Iris pack that exists
 */
@Data
public class IrisPack {
    private final File folder;
    private final IrisData data;

    /**
     * Create an iris pack backed by a data folder
     * the data folder is assumed to be in the Iris/packs/NAME folder
     *
     * @param name the name
     */
    public IrisPack(String name) {
        this(packsPack(name));
    }

    /**
     * Create an iris pack backed by a data folder
     *
     * @param folder the folder of the pack. Must be a directory
     */
    public IrisPack(File folder) {
        this.folder = folder;

        if (!folder.exists()) {
            throw new RuntimeException("Cannot open Pack " + folder.getPath() + " (directory doesnt exist)");
        }

        if (!folder.isDirectory()) {
            throw new RuntimeException("Cannot open Pack " + folder.getPath() + " (not a directory)");
        }

        this.data = IrisData.get(folder);
    }

    /**
     * Create a new pack from the input url
     *
     * @param sender the sender
     * @param url    the url, or name, or really anything see IrisPackRepository.from(String)
     * @return the iris pack
     * @throws IrisException fails
     */
    public static Future<IrisPack> from(VolmitSender sender, String url) throws IrisException {
        IrisPackRepository repo = IrisPackRepository.from(url);
        if (repo == null) {
            throw new IrisException("Null Repo");
        }

        try {
            return from(sender, repo);
        } catch (MalformedURLException e) {
            throw new IrisException("Malformed URL " + e.getMessage());
        }
    }

    /**
     * Create a pack from a repo
     *
     * @param sender the sender
     * @param repo   the repo
     * @return the pack
     * @throws MalformedURLException shit happens
     */
    public static Future<IrisPack> from(VolmitSender sender, IrisPackRepository repo) throws MalformedURLException {
        CompletableFuture<IrisPack> pack = new CompletableFuture<>();
        repo.install(sender, () -> {
            pack.complete(new IrisPack(repo.getRepo()));
        });
        return pack;
    }

    /**
     * Create a blank pack with a given name
     *
     * @param name the name of the pack
     * @return the pack
     * @throws IrisException if the pack already exists or another error
     */
    public static IrisPack blank(String name) throws IrisException {
        File f = packsPack(name);

        if (f.exists()) {
            throw new IrisException("Already exists");
        }

        File fd = new File(f, "dimensions/" + name + ".json");
        fd.getParentFile().mkdirs();
        try {
            IO.writeAll(fd, "{\n" +
                    "    \"name\": \"" + Form.capitalize(name) + "\",\n" +
                    "    \"version\": 1\n" +
                    "}\n");
        } catch (IOException e) {
            throw new IrisException(e.getMessage(), e);
        }

        IrisPack pack = new IrisPack(f);
        pack.updateWorkspace();
        return pack;
    }

    /**
     * Get a packs pack folder for a name. Such that overworld would resolve as Iris/packs/overworld
     *
     * @param name the name
     * @return the file path
     */
    public static File packsPack(String name) {
        return Iris.instance.getDataFolderNoCreate(StudioSVC.WORKSPACE_NAME, name);
    }

    private static KList<File> collectFiles(File f, String fileExtension) {
        KList<File> l = new KList<>();

        if (f.isDirectory()) {
            for (File i : f.listFiles()) {
                l.addAll(collectFiles(i, fileExtension));
            }
        } else if (f.getName().endsWith("." + fileExtension)) {
            l.add(f);
        }

        return l;
    }

    /**
     * Delete this pack. This invalidates this pack and you should
     * probably no longer use this instance after deleting this pack
     */
    public void delete() {
        IO.delete(folder);
        folder.delete();
    }

    /**
     * Get the name of this pack
     *
     * @return the pack name
     */
    public String getName() {
        return folder.getName();
    }

    /**
     * Get the file path of the workspace file
     *
     * @return the workspace file path
     */
    public File getWorkspaceFile() {
        return new File(getFolder(), getName() + ".code-workspace");
    }

    /**
     * Update the workspace file
     *
     * @return true if it was updated
     */
    public boolean updateWorkspace() {
        getFolder().mkdirs();
        File ws = getWorkspaceFile();

        try {
            PrecisionStopwatch p = PrecisionStopwatch.start();
            Iris.debug("Building Workspace: " + ws.getPath());
            JSONObject j = generateWorkspaceConfig();
            IO.writeAll(ws, j.toString(4));
            p.end();
            Iris.debug("Building Workspace: " + ws.getPath() + " took " + Form.duration(p.getMilliseconds(), 2));
            return true;
        } catch (Throwable e) {
            Iris.reportError(e);
            Iris.warn("Pack invalid: " + ws.getAbsolutePath() + " Re-creating. You may loose some vs-code workspace settings! But not your actual project!");
            ws.delete();
            try {
                IO.writeAll(ws, generateWorkspaceConfig());
            } catch (IOException e1) {
                Iris.reportError(e1);
                e1.printStackTrace();
            }
        }

        return false;
    }

    /**
     * Install this pack into a world
     *
     * @param world the world to install into (world/iris/pack)
     * @return the installed pack
     */
    public IrisPack install(World world) throws IrisException {
        return install(new File(world.getWorldFolder(), "iris/pack"));
    }

    /**
     * Install this pack into a world
     *
     * @param world the world to install into (world/iris/pack)
     * @return the installed pack
     */
    public IrisPack install(IrisWorld world) throws IrisException {
        return install(new File(world.worldFolder(), "iris/pack"));
    }

    /**
     * Install this pack into a world
     *
     * @param folder the folder to install this pack into
     * @return the installed pack
     */
    public IrisPack install(File folder) throws IrisException {
        if (folder.exists()) {
            throw new IrisException("Cannot install new pack because the folder " + folder.getName() + " already exists!");
        }

        folder.mkdirs();

        try {
            FileUtils.copyDirectory(getFolder(), folder);
        } catch (IOException e) {
            Iris.reportError(e);
        }

        return new IrisPack(folder);
    }

    /**
     * Create a new pack using this pack as a template. The new pack will be renamed & have a renamed dimension
     * to match it.
     *
     * @param newName the new pack name
     * @return the new IrisPack
     */
    public IrisPack install(String newName) throws IrisException {
        File newPack = packsPack(newName);

        if (newPack.exists()) {
            throw new IrisException("Cannot install new pack because the folder " + newName + " already exists!");
        }

        try {
            FileUtils.copyDirectory(getFolder(), newPack);
        } catch (IOException e) {
            Iris.reportError(e);
        }

        IrisData data = IrisData.get(newPack);
        IrisDimension dim = data.getDimensionLoader().load(getDimensionKey());
        data.dump();
        File from = dim.getLoadFile();
        File to = new File(from.getParentFile(), newName + ".json");
        try {
            FileUtils.moveFile(from, to);
            new File(newPack, getWorkspaceFile().getName()).delete();
        } catch (Throwable e) {
            throw new IrisException(e);
        }

        IrisPack pack = new IrisPack(newPack);
        pack.updateWorkspace();

        return pack;
    }

    /**
     * The dimension's assumed loadkey
     *
     * @return getName()
     */
    public String getDimensionKey() {
        return getName();
    }

    /**
     * Get the main dimension object
     *
     * @return the dimension (folder name as dim key)
     */
    public IrisDimension getDimension() {
        return getData().getDimensionLoader().load(getDimensionKey());
    }

    /**
     * Find all files in this pack with the given extension
     *
     * @param fileExtension the extension
     * @return the list of files
     */
    public KList<File> collectFiles(String fileExtension) {
        return collectFiles(getFolder(), fileExtension);
    }

    private JSONObject generateWorkspaceConfig() {
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
        IrisData dm = IrisData.get(getFolder());

        for (ResourceLoader<?> r : dm.getLoaders().v()) {
            if (r.supportsSchemas()) {
                schemas.put(r.buildSchema());
            }
        }

        settings.put("json.schemas", schemas);
        ws.put("settings", settings);

        return ws;
    }
}
