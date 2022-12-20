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

package com.volmit.iris.core.loader;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.engine.object.matter.IrisMatterObject;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.data.KCache;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;

import java.io.File;

public class MatterObjectResourceLoader extends ResourceLoader<IrisMatterObject> {
    private String[] possibleKeys;

    public MatterObjectResourceLoader(File root, IrisData idm, String folderName, String resourceTypeName) {
        super(root, idm, folderName, resourceTypeName, IrisMatterObject.class);
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
            IrisMatterObject t = IrisMatterObject.from(j);
            t.setLoadKey(name);
            t.setLoader(manager);
            t.setLoadFile(j);
            logLoad(j, t);
            tlt.addAndGet(p.getMilliseconds());
            return t;
        } catch (Throwable e) {
            Iris.reportError(e);
            Iris.warn("Couldn't read " + resourceTypeName + " file: " + j.getPath() + ": " + e.getMessage());
            return null;
        }
    }

    private void findMatFiles(File dir, KSet<String> m) {
        for (File file : dir.listFiles()) {
            if (file.isFile() && file.getName().endsWith(".mat")) {
                m.add(file.getName().replaceAll("\\Q.mat\\E", ""));
            } else if (file.isDirectory()) {
                findMatFiles(file, m);
            }
        }
    }

    public String[] getPossibleKeys() {
        if (possibleKeys != null) {
            return possibleKeys;
        }

        Iris.debug("Building " + resourceTypeName + " Possibility Lists");
        KSet<String> m = new KSet<>();

        for (File folder : getFolders()) {
            findMatFiles(folder, m);
        }

        KList<String> v = new KList<>(m);
        possibleKeys = v.toArray(new String[0]);
        return possibleKeys;
    }


//    public String[] getPossibleKeys() {
//        if (possibleKeys != null) {
//            return possibleKeys;
//        }
//
//        Iris.debug("Building " + resourceTypeName + " Possibility Lists");
//        KSet<String> m = new KSet<>();
//
//        for (File i : getFolders()) {
//            for (File j : i.listFiles()) {
//                if (j.isFile() && j.getName().endsWith(".mat")) {
//                    m.add(j.getName().replaceAll("\\Q.mat\\E", ""));
//                } else if (j.isDirectory()) {
//                    for (File k : j.listFiles()) {
//                        if (k.isFile() && k.getName().endsWith(".mat")) {
//                            m.add(j.getName() + "/" + k.getName().replaceAll("\\Q.mat\\E", ""));
//                        } else if (k.isDirectory()) {
//                            for (File l : k.listFiles()) {
//                                if (l.isFile() && l.getName().endsWith(".mat")) {
//                                    m.add(j.getName() + "/" + k.getName() + "/" + l.getName().replaceAll("\\Q.mat\\E", ""));
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//        }
//
//        KList<String> v = new KList<>(m);
//        possibleKeys = v.toArray(new String[0]);
//        return possibleKeys;
//    }

    public File findFile(String name) {
        for (File i : getFolders(name)) {
            for (File j : i.listFiles()) {
                if (j.isFile() && j.getName().endsWith(".mat") && j.getName().split("\\Q.\\E")[0].equals(name)) {
                    return j;
                }
            }

            File file = new File(i, name + ".mat");

            if (file.exists()) {
                return file;
            }
        }

        Iris.warn("Couldn't find " + resourceTypeName + ": " + name);

        return null;
    }

    public IrisMatterObject load(String name) {
        return load(name, true);
    }

    private IrisMatterObject loadRaw(String name) {
        for (File i : getFolders(name)) {
            for (File j : i.listFiles()) {
                if (j.isFile() && j.getName().endsWith(".mat") && j.getName().split("\\Q.\\E")[0].equals(name)) {
                    return loadFile(j, name);
                }
            }

            File file = new File(i, name + ".mat");

            if (file.exists()) {
                return loadFile(file, name);
            }
        }

        Iris.warn("Couldn't find " + resourceTypeName + ": " + name);

        return null;
    }

    public IrisMatterObject load(String name, boolean warn) {
        return loadCache.get(name);
    }
}
