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
import com.volmit.iris.engine.object.IrisScript;
import com.volmit.iris.util.data.KCache;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class ScriptResourceLoader extends ResourceLoader<IrisScript> {
    public ScriptResourceLoader(File root, IrisData idm, String folderName, String resourceTypeName) {
        super(root, idm, folderName, resourceTypeName, IrisScript.class);
        loadCache = new KCache<>(this::loadRaw, IrisSettings.get().getPerformance().getScriptLoaderCacheSize());
    }

    public boolean supportsSchemas() {
        return false;
    }

    public long getSize() {
        return loadCache.getSize();
    }

    protected IrisScript loadFile(File j, String name) {
        try {
            PrecisionStopwatch p = PrecisionStopwatch.start();
            IrisScript t = new IrisScript(IO.readAll(j));
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


    public String[] getPossibleKeys() {
        if (possibleKeys != null) {
            return possibleKeys;
        }

        Iris.debug("Building " + resourceTypeName + " Possibility Lists");
        Set<String> keys = new HashSet<>();

        for (File i : getFolders()) {
            if (i.isDirectory()) {
                keys.addAll(getKeysInDirectory(i));
            }
        }

        possibleKeys = keys.toArray(new String[0]);
        return possibleKeys;
    }

    private Set<String> getKeysInDirectory(File directory) {
        Set<String> keys = new HashSet<>();
        for (File file : directory.listFiles()) {
            if (file.isFile() && file.getName().endsWith(".js")) {
                keys.add(file.getName().replaceAll("\\Q.js\\E", ""));
            } else if (file.isDirectory()) {
                keys.addAll(getKeysInDirectory(file));
            }
        }
        return keys;
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
//                if (j.isFile() && j.getName().endsWith(".js")) {
//                    m.add(j.getName().replaceAll("\\Q.js\\E", ""));
//                } else if (j.isDirectory()) {
//                    for (File k : j.listFiles()) {
//                        if (k.isFile() && k.getName().endsWith(".js")) {
//                            m.add(j.getName() + "/" + k.getName().replaceAll("\\Q.js\\E", ""));
//                        } else if (k.isDirectory()) {
//                            for (File l : k.listFiles()) {
//                                if (l.isFile() && l.getName().endsWith(".js")) {
//                                    m.add(j.getName() + "/" + k.getName() + "/" + l.getName().replaceAll("\\Q.js\\E", ""));
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
                if (j.isFile() && j.getName().endsWith(".js") && j.getName().split("\\Q.\\E")[0].equals(name)) {
                    return j;
                }
            }

            File file = new File(i, name + ".js");

            if (file.exists()) {
                return file;
            }
        }

        Iris.warn("Couldn't find " + resourceTypeName + ": " + name);

        return null;
    }

    private IrisScript loadRaw(String name) {
        for (File i : getFolders(name)) {
            for (File j : i.listFiles()) {
                if (j.isFile() && j.getName().endsWith(".js") && j.getName().split("\\Q.\\E")[0].equals(name)) {
                    return loadFile(j, name);
                }
            }

            File file = new File(i, name + ".js");

            if (file.exists()) {
                return loadFile(file, name);
            }
        }

        Iris.warn("Couldn't find " + resourceTypeName + ": " + name);

        return null;
    }

    public IrisScript load(String name, boolean warn) {
        return loadCache.get(name);
    }
}
