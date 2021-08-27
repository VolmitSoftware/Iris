/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
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
import com.volmit.iris.engine.object.common.IrisWorld;
import com.volmit.iris.engine.object.dimensional.IrisDimension;
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

/**
 * Represents an Iris pack that exists
 */
@Data
public class IrisPack {
    private final File folder;
    private final IrisData data;

    /**
     * Create an iris pack backed by a data folder
     * @param folder the folder of the pack. Must be a directory
     */
    public IrisPack(File folder) {
        this.folder = folder;

        if(!folder.exists())
        {
            throw new RuntimeException("Cannot open Pack " + folder.getPath() + " (directory doesnt exist)");
        }

        if(!folder.isDirectory())
        {
            throw new RuntimeException("Cannot open Pack " + folder.getPath() + " (not a directory)");
        }

        this.data = IrisData.get(folder);
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
     * Create a new pack from the input url
     * @param sender the sender
     * @param url the url, or name, or really anything see IrisPackRepository.from(String)
     * @return the iris pack
     * @throws IrisException fails
     */
    public static IrisPack from(VolmitSender sender, String url) throws IrisException {
        IrisPackRepository repo = IrisPackRepository.from(url);
        if(repo == null)
        {
            throw new IrisException("Null Repo");
        }

        try {
            return from(sender, repo);
        } catch (MalformedURLException e) {
            throw new IrisException("Malformed URL " + e.getMessage());
        }
    }

    /**
     * Get the name of this pack
     * @return the pack name
     */
    public String getName()
    {
        return folder.getName();
    }

    /**
     * Get the file path of the workspace file
     * @return the workspace file path
     */
    public File getWorkspaceFile() {
        return new File(getFolder(), getName() + ".code-workspace");
    }

    /**
     * Update the workspace file
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
     * @param world the world to install into (world/iris/pack)
     * @return the main dimension object loaded from the fresh installed directory, NOT this pack's directory
     */
    public IrisDimension install(World world)
    {
        return install(new File(world.getWorldFolder(), "iris/pack"));
    }

    /**
     * Install this pack into a world
     * @param world the world to install into (world/iris/pack)
     * @return the main dimension object loaded from the fresh installed directory, NOT this pack's directory
     */
    public IrisDimension install(IrisWorld world)
    {
        return install(new File(world.worldFolder(), "iris/pack"));
    }

    /**
     * Install this pack into a world
     * @param folder the folder to install this pack into
     * @return the main dimension object loaded from the fresh installed directory, NOT this pack's directory
     */
    public IrisDimension install(File folder)
    {
        IrisDimension dim = getDimension();
        folder.mkdirs();

        try {
            FileUtils.copyDirectory(getFolder(), folder);
        } catch (IOException e) {
            Iris.reportError(e);
        }

        return IrisData.get(folder).getDimensionLoader().load(dim.getLoadKey());
    }

    /**
     * The dimension's assumed loadkey
     * @return getName()
     */
    public String getDimensionKey()
    {
        return getName();
    }

    /**
     * Get the main dimension object
     * @return the dimension (folder name as dim key)
     */
    public IrisDimension getDimension()
    {
        return getData().getDimensionLoader().load(getDimensionKey());
    }

    /**
     * Find all files in this pack with the given extension
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

    /**
     * Create a pack from a repo
     * @param sender the sender
     * @param repo the repo
     * @return the pack
     * @throws MalformedURLException shit happens
     */
    public static IrisPack from(VolmitSender sender, IrisPackRepository repo) throws MalformedURLException {
        repo.install(sender);
        return new IrisPack(Iris.instance.getDataFolder(StudioSVC.WORKSPACE_NAME, repo.getRepo()));
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
}
