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

package com.volmit.iris.core.loader;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.object.IrisObject;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.scheduling.ChronoLatch;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

public class ObjectResourceLoader extends ResourceLoader<IrisObject> {
    private final ChronoLatch useFlip = new ChronoLatch(2222);
    private final KMap<String, Long> useCache = new KMap<>();
    private final ChronoLatch cl;
    private final AtomicInteger unload;

    public ObjectResourceLoader(File root, IrisData idm, String folderName, String resourceTypeName) {
        super(root, idm, folderName, resourceTypeName, IrisObject.class);
        cl = new ChronoLatch(30000);
        unload = new AtomicInteger(0);
    }

    public boolean supportsSchemas() {
        return false;
    }

    public int getSize() {
        return loadCache.size();
    }

    public int getTotalStorage() {
        int m = 0;

        for (IrisObject i : loadCache.values()) {
            m += i.getBlocks().size();
        }

        return m;
    }

    public void clean() {
        if (useFlip.flip()) {
            unloadLast(30000);
        }
    }

    public void unloadLast(long age) {
        String v = getOldest();

        if (v == null) {
            return;
        }

        if (M.ms() - useCache.get(v) > age) {
            unload(v);
        }
    }

    private String getOldest() {
        long min = M.ms();
        String v = null;

        for (String i : useCache.k()) {
            long t = useCache.get(i);
            if (t < min) {
                min = t;
                v = i;
            }
        }

        return v;
    }

    private void unload(String v) {
        lock.lock();
        useCache.remove(v);
        loadCache.remove(v);
        lock.unlock();
        unload.getAndIncrement();

        if (unload.get() == 1) {
            cl.flip();
        }

        if (cl.flip()) {
            J.a(() -> {
                Iris.verbose("Unloaded " + C.WHITE + unload.get() + " " + resourceTypeName + (unload.get() == 1 ? "" : "s") + C.GRAY + " to optimize memory usage." + " (" + Form.f(getLoadCache().size()) + " " + resourceTypeName + (loadCache.size() == 1 ? "" : "s") + " Loaded)");
                unload.set(0);
            });
        }
    }

    public IrisObject loadFile(File j, String key, String name) {
        lock.lock();
        try {
            PrecisionStopwatch p = PrecisionStopwatch.start();
            IrisObject t = new IrisObject(0, 0, 0);
            t.read(j);
            loadCache.put(key, t);
            t.setLoadKey(name);
            t.setLoader(manager);
            t.setLoadFile(j);
            logLoad(j, t);
            lock.unlock();
            tlt.addAndGet(p.getMilliseconds());
            return t;
        } catch (Throwable e) {
            Iris.reportError(e);
            lock.unlock();
            Iris.warn("Couldn't read " + resourceTypeName + " file: " + j.getPath() + ": " + e.getMessage());
            return null;
        }
    }

    public String[] getPossibleKeys() {
        if (possibleKeys != null) {
            return possibleKeys;
        }

        Iris.debug("Building " + resourceTypeName + " Possibility Lists");
        KSet<String> m = new KSet<>();

        for (File i : getFolders()) {
            for (File j : i.listFiles()) {
                if (j.isFile() && j.getName().endsWith(".iob")) {
                    m.add(j.getName().replaceAll("\\Q.iob\\E", ""));
                } else if (j.isDirectory()) {
                    for (File k : j.listFiles()) {
                        if (k.isFile() && k.getName().endsWith(".iob")) {
                            m.add(j.getName() + "/" + k.getName().replaceAll("\\Q.iob\\E", ""));
                        } else if (k.isDirectory()) {
                            for (File l : k.listFiles()) {
                                if (l.isFile() && l.getName().endsWith(".iob")) {
                                    m.add(j.getName() + "/" + k.getName() + "/" + l.getName().replaceAll("\\Q.iob\\E", ""));
                                }
                            }
                        }
                    }
                }
            }
        }

        KList<String> v = new KList<>(m);
        possibleKeys = v.toArray(new String[0]);
        return possibleKeys;
    }

    public File findFile(String name) {
        lock.lock();
        for (File i : getFolders(name)) {
            for (File j : i.listFiles()) {
                if (j.isFile() && j.getName().endsWith(".iob") && j.getName().split("\\Q.\\E")[0].equals(name)) {
                    lock.unlock();
                    return j;
                }
            }

            File file = new File(i, name + ".iob");

            if (file.exists()) {
                lock.unlock();
                return file;
            }
        }

        Iris.warn("Couldn't find " + resourceTypeName + ": " + name);

        lock.unlock();
        return null;
    }

    public IrisObject load(String name) {
        return load(name, true);
    }

    public IrisObject load(String name, boolean warn) {
        String key = name + "-" + objectClass.getCanonicalName();

        if (loadCache.containsKey(key)) {
            IrisObject t = loadCache.get(key);
            useCache.put(key, M.ms());
            return t;
        }

        lock.lock();
        for (File i : getFolders(name)) {
            for (File j : i.listFiles()) {
                if (j.isFile() && j.getName().endsWith(".iob") && j.getName().split("\\Q.\\E")[0].equals(name)) {
                    useCache.put(key, M.ms());
                    lock.unlock();
                    return loadFile(j, key, name);
                }
            }

            File file = new File(i, name + ".iob");

            if (file.exists()) {
                useCache.put(key, M.ms());
                lock.unlock();
                return loadFile(file, key, name);
            }
        }

        Iris.warn("Couldn't find " + resourceTypeName + ": " + name);

        lock.unlock();
        return null;
    }
}
