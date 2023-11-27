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
import com.volmit.iris.engine.object.IrisImage;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.data.KCache;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Set;

public class ImageResourceLoader extends ResourceLoader<IrisImage> {
    public ImageResourceLoader(File root, IrisData idm, String folderName, String resourceTypeName) {
        super(root, idm, folderName, resourceTypeName, IrisImage.class);
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

    protected IrisImage loadFile(File j, String name) {
        try {
            PrecisionStopwatch p = PrecisionStopwatch.start();
            BufferedImage bu = ImageIO.read(j);
            IrisImage img = new IrisImage(bu);
            img.setLoadFile(j);
            img.setLoader(manager);
            img.setLoadKey(name);
            logLoad(j, img);
            tlt.addAndGet(p.getMilliseconds());
            return img;
        } catch (Throwable e) {
            Iris.reportError(e);
            Iris.warn("Couldn't read " + resourceTypeName + " file: " + j.getPath() + ": " + e.getMessage());
            return null;
        }
    }

    void getPNGFiles(File directory, Set<String> m) {
        for (File file : directory.listFiles()) {
            if (file.isFile() && file.getName().endsWith(".png")) {
                m.add(file.getName().replaceAll("\\Q.png\\E", ""));
            } else if (file.isDirectory()) {
                getPNGFiles(file, m);
            }
        }
    }


    public String[] getPossibleKeys() {
        if (possibleKeys != null) {
            return possibleKeys;
        }

        Iris.debug("Building " + resourceTypeName + " Possibility Lists");
        KSet<String> m = new KSet<>();


        for (File i : getFolders()) {
            getPNGFiles(i, m);
        }

//        for (File i : getFolders()) {
//            for (File j : i.listFiles()) {
//                if (j.isFile() && j.getName().endsWith(".png")) {
//                    m.add(j.getName().replaceAll("\\Q.png\\E", ""));
//                } else if (j.isDirectory()) {
//                    for (File k : j.listFiles()) {
//                        if (k.isFile() && k.getName().endsWith(".png")) {
//                            m.add(j.getName() + "/" + k.getName().replaceAll("\\Q.png\\E", ""));
//                        } else if (k.isDirectory()) {
//                            for (File l : k.listFiles()) {
//                                if (l.isFile() && l.getName().endsWith(".png")) {
//                                    m.add(j.getName() + "/" + k.getName() + "/" + l.getName().replaceAll("\\Q.png\\E", ""));
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//        }

        KList<String> v = new KList<>(m);
        possibleKeys = v.toArray(new String[0]);
        return possibleKeys;
    }

    public File findFile(String name) {
        for (File i : getFolders(name)) {
            for (File j : i.listFiles()) {
                if (j.isFile() && j.getName().endsWith(".png") && j.getName().split("\\Q.\\E")[0].equals(name)) {
                    return j;
                }
            }

            File file = new File(i, name + ".png");

            if (file.exists()) {
                return file;
            }
        }

        Iris.warn("Couldn't find " + resourceTypeName + ": " + name);

        return null;
    }

    public IrisImage load(String name) {
        return load(name, true);
    }

    private IrisImage loadRaw(String name) {
        for (File i : getFolders(name)) {
            for (File j : i.listFiles()) {
                if (j.isFile() && j.getName().endsWith(".png") && j.getName().split("\\Q.\\E")[0].equals(name)) {
                    return loadFile(j, name);
                }
            }

            File file = new File(i, name + ".png");

            if (file.exists()) {
                return loadFile(file, name);
            }
        }

        Iris.warn("Couldn't find " + resourceTypeName + ": " + name);

        return null;
    }

    public IrisImage load(String name, boolean warn) {
        return loadCache.get(name);
    }
}
