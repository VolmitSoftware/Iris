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

import com.google.common.util.concurrent.AtomicDouble;
import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.project.SchemaBuilder;
import com.volmit.iris.core.service.PreservationSVC;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.MeteredCache;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.data.KCache;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.io.CustomOutputStream;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.json.JSONArray;
import com.volmit.iris.util.json.JSONObject;
import com.volmit.iris.util.parallel.BurstExecutor;
import com.volmit.iris.util.parallel.MultiBurst;
import com.volmit.iris.util.scheduling.ChronoLatch;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import lombok.Data;

import java.io.*;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Data
public class ResourceLoader<T extends IrisRegistrant> implements MeteredCache {
    public static final AtomicDouble tlt = new AtomicDouble(0);
    private static final int CACHE_SIZE = 100000;
    protected final AtomicReference<KList<File>> folderCache;
    protected KSet<String> firstAccess;
    protected File root;
    protected String folderName;
    protected String resourceTypeName;
    protected KCache<String, T> loadCache;
    protected Class<? extends T> objectClass;
    protected String cname;
    protected String[] possibleKeys = null;
    protected IrisData manager;
    protected AtomicInteger loads;
    protected ChronoLatch sec;

    public ResourceLoader(File root, IrisData manager, String folderName, String resourceTypeName, Class<? extends T> objectClass) {
        this.manager = manager;
        firstAccess = new KSet<>();
        folderCache = new AtomicReference<>();
        sec = new ChronoLatch(5000);
        loads = new AtomicInteger();
        this.objectClass = objectClass;
        cname = objectClass.getCanonicalName();
        this.resourceTypeName = resourceTypeName;
        this.root = root;
        this.folderName = folderName;
        loadCache = new KCache<>(this::loadRaw, IrisSettings.get().getPerformance().getResourceLoaderCacheSize());
        Iris.debug("Loader<" + C.GREEN + resourceTypeName + C.LIGHT_PURPLE + "> created in " + C.RED + "IDM/" + manager.getId() + C.LIGHT_PURPLE + " on " + C.GRAY + manager.getDataFolder().getPath());
        Iris.service(PreservationSVC.class).registerCache(this);
    }

    public JSONObject buildSchema() {
        Iris.debug("Building Schema " + objectClass.getSimpleName() + " " + root.getPath());
        JSONObject o = new JSONObject();
        KList<String> fm = new KList<>();

        for (int g = 1; g < 8; g++) {
            fm.add("/" + folderName + Form.repeat("/*", g) + ".json");
        }

        o.put("fileMatch", new JSONArray(fm.toArray()));
        o.put("url", "./.iris/schema/" + getFolderName() + "-schema.json");
        File a = new File(getManager().getDataFolder(), ".iris/schema/" + getFolderName() + "-schema.json");
        J.attemptAsync(() -> IO.writeAll(a, new SchemaBuilder(objectClass, manager).construct().toString(4)));

        return o;
    }

    public File findFile(String name) {
        for (File i : getFolders(name)) {
            for (File j : i.listFiles()) {
                if (j.isFile() && j.getName().endsWith(".json") && j.getName().split("\\Q.\\E")[0].equals(name)) {
                    return j;
                }
            }

            File file = new File(i, name + ".json");

            if (file.exists()) {
                return file;
            }
        }

        Iris.warn("Couldn't find " + resourceTypeName + ": " + name);

        return null;
    }

    public void logLoad(File path, T t) {
        loads.getAndIncrement();

        if (loads.get() == 1) {
            sec.flip();
        }

        if (sec.flip()) {
            J.a(() -> {
                Iris.verbose("Loaded " + C.WHITE + loads.get() + " " + resourceTypeName + (loads.get() == 1 ? "" : "s") + C.GRAY + " (" + Form.f(getLoadCache().getSize()) + " " + resourceTypeName + (loadCache.getSize() == 1 ? "" : "s") + " Loaded)");
                loads.set(0);
            });
        }

        Iris.debug("Loader<" + C.GREEN + resourceTypeName + C.LIGHT_PURPLE + "> iload " + C.YELLOW + t.getLoadKey() + C.LIGHT_PURPLE + " in " + C.GRAY + t.getLoadFile().getPath() + C.LIGHT_PURPLE + " TLT: " + C.RED + Form.duration(tlt.get(), 2));
    }

    public void failLoad(File path, Throwable e) {
        J.a(() -> Iris.warn("Couldn't Load " + resourceTypeName + " file: " + path.getPath() + ": " + e.getMessage()));
    }

    private KList<File> matchAllFiles(File root, Predicate<File> f) {
        KList<File> fx = new KList<>();
        matchFiles(root, fx, f);
        return fx;
    }

    private void matchFiles(File at, KList<File> files, Predicate<File> f) {
        if (at.isDirectory()) {
            for (File i : at.listFiles()) {
                matchFiles(i, files, f);
            }
        } else {
            if (f.test(at)) {
                files.add(at);
            }
        }
    }

    public String[] getPossibleKeys() {
        if (possibleKeys != null) {
            return possibleKeys;
        }

        KSet<String> m = new KSet<>();
        KList<File> files = getFolders();

        if (files == null) {
            possibleKeys = new String[0];
            return possibleKeys;
        }

        for (File i : files) {
            for (File j : matchAllFiles(i, (f) -> f.getName().endsWith(".json"))) {
                m.add(i.toURI().relativize(j.toURI()).getPath().replaceAll("\\Q.json\\E", ""));
            }
        }

        KList<String> v = new KList<>(m);
        possibleKeys = v.toArray(new String[0]);
        return possibleKeys;
    }

    public long count() {
        return loadCache.getSize();
    }

    protected T loadFile(File j, String name) {
        try {
            PrecisionStopwatch p = PrecisionStopwatch.start();
            T t = getManager().getGson()
                    .fromJson(preprocess(new JSONObject(IO.readAll(j))).toString(0), objectClass);
            t.setLoadKey(name);
            t.setLoadFile(j);
            t.setLoader(manager);
            getManager().preprocessObject(t);
            logLoad(j, t);
            tlt.addAndGet(p.getMilliseconds());
            return t;
        } catch (Throwable e) {
            Iris.reportError(e);
            failLoad(j, e);
            return null;
        }
    }

    protected JSONObject preprocess(JSONObject j) {
        return j;
    }

    public Stream<T> streamAll(Stream<String> s) {
        return s.map(this::load);
    }

    public KList<T> loadAll(KList<String> s) {
        KList<T> m = new KList<>();

        for (String i : s) {
            T t = load(i);

            if (t != null) {
                m.add(t);
            }
        }

        return m;
    }

    public KList<T> loadAllParallel(KList<String> s) {
        KList<T> m = new KList<>();
        BurstExecutor burst = MultiBurst.burst.burst(s.size());

        for (String i : s) {
            burst.queue(() -> {
                T t = load(i);

                if (t != null) {
                    m.add(t);
                }
            });
        }

        burst.complete();
        return m;
    }

    public KList<T> loadAll(KList<String> s, Consumer<T> postLoad) {
        KList<T> m = new KList<>();

        for (String i : s) {
            T t = load(i);

            if (t != null) {
                m.add(t);
                postLoad.accept(t);
            }
        }

        return m;
    }

    public KList<T> loadAll(String[] s) {
        KList<T> m = new KList<>();

        for (String i : s) {
            T t = load(i);

            if (t != null) {
                m.add(t);
            }
        }

        return m;
    }

    public T load(String name) {
        return load(name, true);
    }

    private T loadRaw(String name) {
        for (File i : getFolders(name)) {
            //noinspection ConstantConditions
            for (File j : i.listFiles()) {
                if (j.isFile() && j.getName().endsWith(".json") && j.getName().split("\\Q.\\E")[0].equals(name)) {
                    return loadFile(j, name);
                }
            }

            File file = new File(i, name + ".json");

            if (file.exists()) {
                return loadFile(file, name);
            }
        }

        return null;
    }

    public T load(String name, boolean warn) {
        if (name == null) {
            return null;
        }

        if (name.trim().isEmpty()) {
            return null;
        }

        firstAccess.add(name);
        return loadCache.get(name);
    }

    public void loadFirstAccess(Engine engine) throws IOException {
        String id = "DIM" + Math.abs(engine.getSeedManager().getSeed() + engine.getDimension().getVersion() + engine.getDimension().getLoadKey().hashCode());
        File file = Iris.instance.getDataFile("prefetch/" + id + "/" + Math.abs(getFolderName().hashCode()) + ".ipfch");

        if (!file.exists()) {
            return;
        }

        FileInputStream fin = new FileInputStream(file);
        GZIPInputStream gzi = new GZIPInputStream(fin);
        DataInputStream din = new DataInputStream(gzi);
        int m = din.readInt();
        KList<String> s = new KList<>();

        for (int i = 0; i < m; i++) {
            s.add(din.readUTF());
        }

        din.close();
        file.deleteOnExit();
        Iris.info("Loading " + s.size() + " prefetch " + getFolderName());
        loadAllParallel(s);
    }

    public void saveFirstAccess(Engine engine) throws IOException {
        String id = "DIM" + Math.abs(engine.getSeedManager().getSeed() + engine.getDimension().getVersion() + engine.getDimension().getLoadKey().hashCode());
        File file = Iris.instance.getDataFile("prefetch/" + id + "/" + Math.abs(getFolderName().hashCode()) + ".ipfch");
        file.getParentFile().mkdirs();
        FileOutputStream fos = new FileOutputStream(file);
        GZIPOutputStream gzo = new CustomOutputStream(fos, 9);
        DataOutputStream dos = new DataOutputStream(gzo);
        dos.writeInt(firstAccess.size());

        for (String i : firstAccess) {
            dos.writeUTF(i);
        }

        dos.flush();
        dos.close();
    }

    public KList<File> getFolders() {
        synchronized (folderCache) {
            if (folderCache.get() == null) {
                KList<File> fc = new KList<>();

                for (File i : root.listFiles()) {
                    if (i.isDirectory()) {
                        if (i.getName().equals(folderName)) {
                            fc.add(i);
                            break;
                        }
                    }
                }

                folderCache.set(fc);
            }
        }

        return folderCache.get();
    }

    public KList<File> getFolders(String rc) {
        KList<File> folders = getFolders().copy();

        if (rc.contains(":")) {
            for (File i : folders.copy()) {
                if (!rc.startsWith(i.getName() + ":")) {
                    folders.remove(i);
                }
            }
        }

        return folders;
    }

    public void clearCache() {
        possibleKeys = null;
        loadCache.invalidate();
        folderCache.set(null);
    }

    public File fileFor(T b) {
        for (File i : getFolders()) {
            for (File j : i.listFiles()) {
                if (j.isFile() && j.getName().endsWith(".json") && j.getName().split("\\Q.\\E")[0].equals(b.getLoadKey())) {
                    return j;
                }
            }

            File file = new File(i, b.getLoadKey() + ".json");

            if (file.exists()) {
                return file;
            }
        }

        return null;
    }

    public boolean isLoaded(String next) {
        return loadCache.contains(next);
    }

    public void clearList() {
        folderCache.set(null);
        possibleKeys = null;
    }

    public KList<String> getPossibleKeys(String arg) {
        KList<String> f = new KList<>();

        for (String i : getPossibleKeys()) {
            if (i.equalsIgnoreCase(arg) || i.toLowerCase(Locale.ROOT).startsWith(arg.toLowerCase(Locale.ROOT)) || i.toLowerCase(Locale.ROOT).contains(arg.toLowerCase(Locale.ROOT)) || arg.toLowerCase(Locale.ROOT).contains(i.toLowerCase(Locale.ROOT))) {
                f.add(i);
            }
        }

        return f;
    }

    public boolean supportsSchemas() {
        return true;
    }

    public void clean() {

    }

    public long getSize() {
        return loadCache.getSize();
    }

    @Override
    public KCache<?, ?> getRawCache() {
        return loadCache;
    }

    @Override
    public long getMaxSize() {
        return loadCache.getMaxSize();
    }

    @Override
    public boolean isClosed() {
        return getManager().isClosed();
    }

    public long getTotalStorage() {
        return getSize();
    }
}
