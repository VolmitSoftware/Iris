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

package art.arcane.iris.pack.loading;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.structure.object.matter.IrisMatterObject;
import art.arcane.volmlib.util.collection.KSet;
import art.arcane.volmlib.util.data.KCache;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;

public class MatterObjectResourceLoader extends ResourceLoader<IrisMatterObject> {
    public MatterObjectResourceLoader(File root, IrisData idm, String folderName, String resourceTypeName, Options options) {
        super(root, idm, folderName, resourceTypeName, IrisMatterObject.class, options);
        loadCache = new KCache<>(this::loadRaw, IrisSettings.get().getPerformance().getObjectLoaderCacheSize());
    }

    public boolean supportsSchemas() {
        return false;
    }

    public long getSize() {
        return loadCache.getSize();
    }

    public long getTotalStorage() {
        return getSize();
    }

    protected IrisMatterObject loadFile(File j, String name) {
        try {
            PrecisionStopwatch p = PrecisionStopwatch.start();
            IrisMatterObject t = IrisMatterObject.from(j, manager);
            t.setLoadKey(name);
            t.setLoader(manager);
            t.setLoadFile(j);
            logLoad(j, t);
            tlt.addAndGet(p.getMilliseconds());
            return t;
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            IrisLogging.warn("Couldn't read " + resourceTypeName + " file: " + j.getPath() + ": " + e.getMessage());
            return null;
        }
    }

    private void findMatFiles(File dir, String prefix, KSet<String> m, HashSet<String> visitedDirectories) {
        if (dir == null || !dir.exists()) {
            return;
        }

        if (dir.isDirectory()) {
            String canonicalDirectory = toCanonicalPath(dir);
            if (canonicalDirectory != null && !visitedDirectories.add(canonicalDirectory)) {
                return;
            }
        }

        File[] listedFiles = dir.listFiles();
        if (listedFiles == null) {
            return;
        }

        for (File file : listedFiles) {
            if (file.isFile() && file.getName().endsWith(".mat")) {
                m.add(prefix + file.getName().replace(".mat", ""));
            } else if (file.isDirectory()) {
                findMatFiles(file, prefix + file.getName() + "/", m, visitedDirectories);
            }
        }
    }

    public String[] getPossibleKeys() {
        if (possibleKeys != null) {
            return possibleKeys;
        }

        IrisLogging.debug("Building " + resourceTypeName + " Possibility Lists");
        KSet<String> m = new KSet<>();
        HashSet<String> visitedDirectories = new HashSet<>();

        for (File folder : getFolders()) {
            findMatFiles(folder, "", m, visitedDirectories);
        }

        possibleKeys = m.toArray(new String[0]);
        return possibleKeys;
    }

    private String toCanonicalPath(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException ignored) {
            return null;
        }
    }

    public File findFile(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        if (name.equals("null")) {
            IrisLogging.warn("Refusing " + resourceTypeName + " lookup for literal string \"null\" (called by " + callerHint() + ")");
            return null;
        }

        File file = resolveFile(name, ".mat");

        if (file != null) {
            return file;
        }

        IrisLogging.warn("Couldn't find " + resourceTypeName + ": " + name + " (called by " + callerHint() + ")");

        return null;
    }

    public IrisMatterObject load(String name) {
        return load(name, true);
    }

    private IrisMatterObject loadRaw(String name) {
        File file = resolveFile(name, ".mat");

        if (file != null) {
            return loadFile(file, name);
        }

        IrisLogging.warn("Couldn't find " + resourceTypeName + ": " + name + " (called by " + callerHint() + ")");

        return null;
    }

    public IrisMatterObject load(String name, boolean warn) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        if (name.equals("null") && warn) {
            IrisLogging.warn("Refusing " + resourceTypeName + " load for literal string \"null\" (called by " + callerHint() + ")");
            return null;
        }
        return loadCache.get(name);
    }
}
