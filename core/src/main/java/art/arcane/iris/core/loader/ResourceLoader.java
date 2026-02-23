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

package art.arcane.iris.core.loader;

import com.google.common.util.concurrent.AtomicDouble;
import art.arcane.iris.Iris;
import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.project.SchemaBuilder;
import art.arcane.iris.core.service.PreservationSVC;
import art.arcane.iris.engine.data.cache.AtomicCache;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.MeteredCache;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KSet;
import art.arcane.volmlib.util.data.KCache;
import art.arcane.iris.util.common.format.C;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.io.CustomOutputStream;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;
import art.arcane.iris.util.common.parallel.BurstExecutor;
import art.arcane.iris.util.common.parallel.MultiBurst;
import art.arcane.volmlib.util.scheduling.ChronoLatch;
import art.arcane.iris.util.common.scheduling.J;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.io.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Data
@EqualsAndHashCode(exclude = "manager")
@ToString(exclude = "manager")
public class ResourceLoader<T extends IrisRegistrant> implements MeteredCache {
    public static final AtomicDouble tlt = new AtomicDouble(0);
    private static final int CACHE_SIZE = 100000;
    private static final ExecutorService schemaBuildExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Iris-Schema-Builder");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private static final Set<String> schemaBuildQueue = ConcurrentHashMap.newKeySet();
    protected final AtomicCache<KList<File>> folderCache;
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
        folderCache = new AtomicCache<>();
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
        String schemaPath = a.getAbsolutePath();
        if (!a.exists() && schemaBuildQueue.add(schemaPath)) {
            schemaBuildExecutor.execute(() -> {
                try {
                    IO.writeAll(a, new SchemaBuilder(objectClass, manager).construct().toString(4));
                } catch (Throwable e) {
                    Iris.reportError(e);
                } finally {
                    schemaBuildQueue.remove(schemaPath);
                }
            });
        }

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
        KList<File> files = new KList<>();
        HashSet<String> visitedDirectories = new HashSet<>();
        matchFiles(root, files, f, visitedDirectories);
        return files;
    }

    private void matchFiles(File at, KList<File> files, Predicate<File> f, HashSet<String> visitedDirectories) {
        if (at == null || !at.exists()) {
            return;
        }

        if (at.isDirectory()) {
            String canonicalPath = toCanonicalPath(at);
            if (canonicalPath != null && !visitedDirectories.add(canonicalPath)) {
                return;
            }

            File[] listedFiles = at.listFiles();
            if (listedFiles == null) {
                return;
            }

            for (File listedFile : listedFiles) {
                matchFiles(listedFile, files, f, visitedDirectories);
            }
            return;
        }

        if (f.test(at)) {
            files.add(at);
        }
    }

    private String toCanonicalPath(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException ignored) {
            return null;
        }
    }

    public String[] getPossibleKeys() {
        if (possibleKeys != null) {
            return possibleKeys;
        }

        KList<File> files = getFolders();

        if (files == null) {
            possibleKeys = new String[0];
            return possibleKeys;
        }

        HashSet<String> m = new HashSet<>();
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

    public Stream<T> streamAll() {
        return streamAll(Arrays.stream(getPossibleKeys()));
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
        BurstExecutor burst = MultiBurst.ioBurst.burst(s.size());

        for (String i : s) {
            burst.queue(() -> {
                T t = load(i);
                if (t == null)
                    return;

                synchronized (m) {
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

        var set = firstAccess;
        if (set != null) firstAccess.add(name);
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
        Iris.info("Loading " + s.size() + " prefetch " + getFolderName());
        firstAccess = null;
        loadAllParallel(s);
    }

    public void saveFirstAccess(Engine engine) throws IOException {
        if (firstAccess == null) return;
        String id = "DIM" + Math.abs(engine.getSeedManager().getSeed() + engine.getDimension().getVersion() + engine.getDimension().getLoadKey().hashCode());
        File file = Iris.instance.getDataFile("prefetch/" + id + "/" + Math.abs(getFolderName().hashCode()) + ".ipfch");
        file.getParentFile().mkdirs();
        FileOutputStream fos = new FileOutputStream(file);
        GZIPOutputStream gzo = new CustomOutputStream(fos, 9);
        DataOutputStream dos = new DataOutputStream(gzo);
        var set = firstAccess;
        firstAccess = null;
        dos.writeInt(set.size());

        for (String i : set) {
            dos.writeUTF(i);
        }

        dos.flush();
        dos.close();
    }

    public KList<File> getFolders() {
        return folderCache.aquire(() -> {
            KList<File> fc = new KList<>();

            File[] files = root.listFiles();
            if (files == null) {
                throw new IllegalStateException("Failed to list files in " + root);
            }

            for (File i : files) {
                if (i.isDirectory()) {
                    if (i.getName().equals(folderName)) {
                        fc.add(i);
                        break;
                    }
                }
            }
            return fc;
        });
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
        folderCache.reset();
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
        folderCache.reset();
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
