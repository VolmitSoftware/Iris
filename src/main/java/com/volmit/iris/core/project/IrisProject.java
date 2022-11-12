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

package com.volmit.iris.core.project;

import com.google.gson.Gson;
import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.core.loader.IrisRegistrant;
import com.volmit.iris.core.loader.ResourceLoader;
import com.volmit.iris.core.tools.IrisToolbelt;
import com.volmit.iris.engine.object.*;
import com.volmit.iris.engine.object.annotations.Snippet;
import com.volmit.iris.engine.platform.PlatformChunkGenerator;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.exceptions.IrisException;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.json.JSONArray;
import com.volmit.iris.util.json.JSONObject;
import com.volmit.iris.util.math.M;
import com.volmit.iris.util.plugin.VolmitSender;
import com.volmit.iris.util.scheduling.ChronoLatch;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.O;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import com.volmit.iris.util.scheduling.jobs.Job;
import com.volmit.iris.util.scheduling.jobs.JobCollection;
import com.volmit.iris.util.scheduling.jobs.ParallelQueueJob;
import lombok.Data;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.zeroturnaround.zip.ZipUtil;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

@SuppressWarnings("ALL")
@Data
public class IrisProject {
    private File path;
    private String name;
    private PlatformChunkGenerator activeProvider;

    public IrisProject(File path) {
        this.path = path;
        this.name = path.getName();
    }

    public static int clean(VolmitSender s, File clean) {
        int c = 0;
        if (clean.isDirectory()) {
            for (File i : clean.listFiles()) {
                c += clean(s, i);
            }
        } else if (clean.getName().endsWith(".json")) {
            try {
                clean(clean);
            } catch (Throwable e) {
                Iris.reportError(e);
                Iris.error("Failed to beautify " + clean.getAbsolutePath() + " You may have errors in your json!");
            }

            c++;
        }

        return c;
    }

    public static void clean(File clean) throws IOException {
        JSONObject obj = new JSONObject(IO.readAll(clean));
        fixBlocks(obj, clean);

        IO.writeAll(clean, obj.toString(4));
    }

    public static void fixBlocks(JSONObject obj, File f) {
        for (String i : obj.keySet()) {
            Object o = obj.get(i);

            if (i.equals("block") && o instanceof String && !o.toString().trim().isEmpty() && !o.toString().contains(":")) {
                obj.put(i, "minecraft:" + o);
                Iris.debug("Updated Block Key: " + o + " to " + obj.getString(i) + " in " + f.getPath());
            }

            if (o instanceof JSONObject) {
                fixBlocks((JSONObject) o, f);
            } else if (o instanceof JSONArray) {
                fixBlocks((JSONArray) o, f);
            }
        }
    }

    public static void fixBlocks(JSONArray obj, File f) {
        for (int i = 0; i < obj.length(); i++) {
            Object o = obj.get(i);

            if (o instanceof JSONObject) {
                fixBlocks((JSONObject) o, f);
            } else if (o instanceof JSONArray) {
                fixBlocks((JSONArray) o, f);
            }
        }
    }

    public boolean isOpen() {
        return activeProvider != null;
    }

    public KList<File> collectFiles(File f, String fileExtension) {
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

    public KList<File> collectFiles(String json) {
        return collectFiles(path, json);
    }

    public void open(VolmitSender sender) throws IrisException {
        open(sender, 1337, (w) ->
        {
        });
    }

    public void openVSCode(VolmitSender sender) {

        IrisDimension d = IrisData.loadAnyDimension(getName());
        J.attemptAsync(() ->
        {
            try {
                if (d.getLoader() == null) {
                    sender.sendMessage("Could not get dimension loader");
                    return;
                }
                File f = d.getLoader().getDataFolder();

                if (!doOpenVSCode(f)) {
                    File ff = new File(d.getLoader().getDataFolder(), d.getLoadKey() + ".code-workspace");
                    Iris.warn("Project missing code-workspace: " + ff.getAbsolutePath() + " Re-creating code workspace.");

                    try {
                        IO.writeAll(ff, createCodeWorkspaceConfig());
                    } catch (IOException e1) {
                        Iris.reportError(e1);
                        e1.printStackTrace();
                    }
                    updateWorkspace();
                    if (!doOpenVSCode(f)) {
                        Iris.warn("Tried creating code workspace but failed a second time. Your project is likely corrupt.");
                    }
                }
            } catch (Throwable e) {
                Iris.reportError(e);
                e.printStackTrace();
            }
        });
    }

    private boolean doOpenVSCode(File f) throws IOException {
        boolean foundWork = false;
        for (File i : Objects.requireNonNull(f.listFiles())) {
            if (i.getName().endsWith(".code-workspace")) {
                foundWork = true;
                J.a(() ->
                {
                    updateWorkspace();
                });

                if (IrisSettings.get().getStudio().isOpenVSCode()) {
                    if (!GraphicsEnvironment.isHeadless()) {
                        Iris.msg("Opening VSCode. You may see the output from VSCode.");
                        Iris.msg("VSCode output always starts with: '(node:#####) electron'");
                        Desktop.getDesktop().open(i);
                    }
                }

                break;
            }
        }
        return foundWork;
    }

    public void open(VolmitSender sender, long seed, Consumer<World> onDone) throws IrisException {
        if (isOpen()) {
            close();
        }

        boolean hasError = false;

        if (hasError) {
            return;
        }

        IrisDimension d = IrisData.loadAnyDimension(getName());
        if (d == null) {
            sender.sendMessage("Can't find dimension: " + getName());
            return;
        } else if (sender.isPlayer()) {
            sender.player().setGameMode(GameMode.SPECTATOR);
        }

        openVSCode(sender);


        J.a(() -> {
            try {
                activeProvider = (PlatformChunkGenerator) IrisToolbelt.createWorld()
                        .seed(seed)
                        .sender(sender)
                        .studio(true)
                        .name("iris/" + UUID.randomUUID())
                        .dimension(d.getLoadKey())
                        .create().getGenerator();
                onDone.accept(activeProvider.getTarget().getWorld().realWorld());
            } catch (IrisException e) {
                e.printStackTrace();
            }
        });
    }

    public void close() {
        Iris.debug("Closing Active Provider");
        IrisToolbelt.evacuate(activeProvider.getTarget().getWorld().realWorld());
        activeProvider.close();
        File folder = activeProvider.getTarget().getWorld().worldFolder();
        Iris.linkMultiverseCore.removeFromConfig(activeProvider.getTarget().getWorld().name());
        Bukkit.unloadWorld(activeProvider.getTarget().getWorld().name(), false);
        J.attemptAsync(() -> IO.delete(folder));
        Iris.debug("Closed Active Provider " + activeProvider.getTarget().getWorld().name());
        activeProvider = null;
    }

    public File getCodeWorkspaceFile() {
        return new File(path, getName() + ".code-workspace");
    }

    public boolean updateWorkspace() {
        getPath().mkdirs();
        File ws = getCodeWorkspaceFile();

        try {
            PrecisionStopwatch p = PrecisionStopwatch.start();
            JSONObject j = createCodeWorkspaceConfig();
            IO.writeAll(ws, j.toString(4));
            p.end();
            return true;
        } catch (Throwable e) {
            Iris.reportError(e);
            Iris.warn("Project invalid: " + ws.getAbsolutePath() + " Re-creating. You may loose some vs-code workspace settings! But not your actual project!");
            ws.delete();
            try {
                IO.writeAll(ws, createCodeWorkspaceConfig());
            } catch (IOException e1) {
                Iris.reportError(e1);
                e1.printStackTrace();
            }
        }

        return false;
    }

    public JSONObject createCodeWorkspaceConfig() {
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
        IrisData dm = IrisData.get(getPath());

        for (ResourceLoader<?> r : dm.getLoaders().v()) {
            if (r.supportsSchemas()) {
                schemas.put(r.buildSchema());
            }
        }

        for (Class<?> i : Iris.getClasses("com.volmit.iris.engine.object.", Snippet.class)) {
            try {
                String snipType = i.getDeclaredAnnotation(Snippet.class).value();
                JSONObject o = new JSONObject();
                KList<String> fm = new KList<>();

                for (int g = 1; g < 8; g++) {
                    fm.add("/snippet/" + snipType + Form.repeat("/*", g) + ".json");
                }

                o.put("fileMatch", new JSONArray(fm.toArray()));
                o.put("url", "./.iris/schema/snippet/" + snipType + "-schema.json");
                schemas.put(o);
                File a = new File(dm.getDataFolder(), ".iris/schema/snippet/" + snipType + "-schema.json");
                J.attemptAsync(() -> {
                    try {
                        IO.writeAll(a, new SchemaBuilder(i, dm).construct().toString(4));
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                });
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        settings.put("json.schemas", schemas);
        ws.put("settings", settings);

        return ws;
    }

    public File compilePackage(VolmitSender sender, boolean obfuscate, boolean minify) {
        String dimm = getName();
        IrisData dm = IrisData.get(path);
        IrisDimension dimension = dm.getDimensionLoader().load(dimm);
        File folder = new File(Iris.instance.getDataFolder(), "exports/" + dimension.getLoadKey());
        folder.mkdirs();
        Iris.info("Packaging Dimension " + dimension.getName() + " " + (obfuscate ? "(Obfuscated)" : ""));
        KSet<IrisRegion> regions = new KSet<>();
        KSet<IrisBiome> biomes = new KSet<>();
        KSet<IrisEntity> entities = new KSet<>();
        KSet<IrisSpawner> spawners = new KSet<>();
        KSet<IrisGenerator> generators = new KSet<>();
        KSet<IrisLootTable> loot = new KSet<>();
        KSet<IrisBlockData> blocks = new KSet<>();

        for (String i : dm.getDimensionLoader().getPossibleKeys()) {
            blocks.add(dm.getBlockLoader().load(i));
        }

        dimension.getRegions().forEach((i) -> regions.add(dm.getRegionLoader().load(i)));
        dimension.getLoot().getTables().forEach((i) -> loot.add(dm.getLootLoader().load(i)));
        regions.forEach((i) -> biomes.addAll(i.getAllBiomes(null)));
        regions.forEach((r) -> r.getLoot().getTables().forEach((i) -> loot.add(dm.getLootLoader().load(i))));
        regions.forEach((r) -> r.getEntitySpawners().forEach((sp) -> spawners.add(dm.getSpawnerLoader().load(sp))));
        dimension.getEntitySpawners().forEach((sp) -> spawners.add(dm.getSpawnerLoader().load(sp)));
        biomes.forEach((i) -> i.getGenerators().forEach((j) -> generators.add(j.getCachedGenerator(null))));
        biomes.forEach((r) -> r.getLoot().getTables().forEach((i) -> loot.add(dm.getLootLoader().load(i))));
        biomes.forEach((r) -> r.getEntitySpawners().forEach((sp) -> spawners.add(dm.getSpawnerLoader().load(sp))));
        spawners.forEach((i) -> i.getSpawns().forEach((j) -> entities.add(dm.getEntityLoader().load(j.getEntity()))));
        KMap<String, String> renameObjects = new KMap<>();
        String a;
        StringBuilder b = new StringBuilder();
        StringBuilder c = new StringBuilder();
        sender.sendMessage("Serializing Objects");

        for (IrisBiome i : biomes) {
            for (IrisObjectPlacement j : i.getObjects()) {
                b.append(j.hashCode());
                KList<String> newNames = new KList<>();

                for (String k : j.getPlace()) {
                    if (renameObjects.containsKey(k)) {
                        newNames.add(renameObjects.get(k));
                        continue;
                    }

                    String name = !obfuscate ? k : UUID.randomUUID().toString().replaceAll("-", "");
                    b.append(name);
                    newNames.add(name);
                    renameObjects.put(k, name);
                }

                j.setPlace(newNames);
            }
        }

        KMap<String, KList<String>> lookupObjects = renameObjects.flip();
        StringBuilder gb = new StringBuilder();
        ChronoLatch cl = new ChronoLatch(1000);
        O<Integer> ggg = new O<>();
        ggg.set(0);
        biomes.forEach((i) -> i.getObjects().forEach((j) -> j.getPlace().forEach((k) ->
        {
            try {
                File f = dm.getObjectLoader().findFile(lookupObjects.get(k).get(0));
                IO.copyFile(f, new File(folder, "objects/" + k + ".iob"));
                gb.append(IO.hash(f));
                ggg.set(ggg.get() + 1);

                if (cl.flip()) {
                    int g = ggg.get();
                    ggg.set(0);
                    sender.sendMessage("Wrote another " + g + " Objects");
                }
            } catch (Throwable e) {
                Iris.reportError(e);
            }
        })));

        b.append(IO.hash(gb.toString()));
        c.append(IO.hash(b.toString()));
        b = new StringBuilder();

        Iris.info("Writing Dimensional Scaffold");

        try {
            a = new JSONObject(new Gson().toJson(dimension)).toString(minify ? 0 : 4);
            IO.writeAll(new File(folder, "dimensions/" + dimension.getLoadKey() + ".json"), a);
            b.append(IO.hash(a));

            for (IrisGenerator i : generators) {
                a = new JSONObject(new Gson().toJson(i)).toString(minify ? 0 : 4);
                IO.writeAll(new File(folder, "generators/" + i.getLoadKey() + ".json"), a);
                b.append(IO.hash(a));
            }

            c.append(IO.hash(b.toString()));
            b = new StringBuilder();

            for (IrisRegion i : regions) {
                a = new JSONObject(new Gson().toJson(i)).toString(minify ? 0 : 4);
                IO.writeAll(new File(folder, "regions/" + i.getLoadKey() + ".json"), a);
                b.append(IO.hash(a));
            }

            for (IrisBlockData i : blocks) {
                a = new JSONObject(new Gson().toJson(i)).toString(minify ? 0 : 4);
                IO.writeAll(new File(folder, "blocks/" + i.getLoadKey() + ".json"), a);
                b.append(IO.hash(a));
            }

            for (IrisBiome i : biomes) {
                a = new JSONObject(new Gson().toJson(i)).toString(minify ? 0 : 4);
                IO.writeAll(new File(folder, "biomes/" + i.getLoadKey() + ".json"), a);
                b.append(IO.hash(a));
            }

            for (IrisEntity i : entities) {
                a = new JSONObject(new Gson().toJson(i)).toString(minify ? 0 : 4);
                IO.writeAll(new File(folder, "entities/" + i.getLoadKey() + ".json"), a);
                b.append(IO.hash(a));
            }

            for (IrisLootTable i : loot) {
                a = new JSONObject(new Gson().toJson(i)).toString(minify ? 0 : 4);
                IO.writeAll(new File(folder, "loot/" + i.getLoadKey() + ".json"), a);
                b.append(IO.hash(a));
            }

            c.append(IO.hash(b.toString()));
            String finalHash = IO.hash(c.toString());
            JSONObject meta = new JSONObject();
            meta.put("hash", finalHash);
            meta.put("time", M.ms());
            meta.put("version", dimension.getVersion());
            IO.writeAll(new File(folder, "package.json"), meta.toString(minify ? 0 : 4));
            File p = new File(Iris.instance.getDataFolder(), "exports/" + dimension.getLoadKey() + ".iris");
            Iris.info("Compressing Package");
            ZipUtil.pack(folder, p, 9);
            IO.delete(folder);

            sender.sendMessage("Package Compiled!");
            return p;
        } catch (Throwable e) {
            Iris.reportError(e);
            e.printStackTrace();
        }
        sender.sendMessage("Failed!");
        return null;
    }

    public void compile(VolmitSender sender) {
        IrisData data = IrisData.get(getPath());
        KList<Job> jobs = new KList<>();
        KList<File> files = new KList<>();
        KList<File> objects = new KList<>();
        files(getPath(), files);
        filesObjects(getPath(), objects);

        jobs.add(new ParallelQueueJob<File>() {
            @Override
            public void execute(File f) {
                try {
                    IrisObject o = new IrisObject(0, 0, 0);
                    o.read(f);

                    if (o.getBlocks().isEmpty()) {
                        sender.sendMessageRaw("<hover:show_text:'Error:\n" +
                                "<yellow>" + f.getPath() +
                                "'><red>- IOB " + f.getName() + " has 0 blocks!");
                    }

                    if (o.getW() == 0 || o.getH() == 0 || o.getD() == 0) {
                        sender.sendMessageRaw("<hover:show_text:'Error:\n" +
                                "<yellow>" + f.getPath() + "\n<red>The width height or depth has a zero in it (bad format)" +
                                "'><red>- IOB " + f.getName() + " is not 3D!");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public String getName() {
                return "IOB";
            }
        }.queue(objects));

        jobs.add(new ParallelQueueJob<File>() {
            @Override
            public void execute(File f) {
                try {
                    JSONObject p = new JSONObject(IO.readAll(f));
                    fixBlocks(p);
                    scanForErrors(data, f, p, sender);
                    IO.writeAll(f, p.toString(4));

                } catch (Throwable e) {
                    sender.sendMessageRaw("<hover:show_text:'Error:\n" +
                            "<yellow>" + f.getPath() +
                            "\n<red>" + e.getMessage() +
                            "'><red>- JSON Error " + f.getName());
                }
            }

            @Override
            public String getName() {
                return "JSON";
            }
        }.queue(files));

        new JobCollection("Compile", jobs).execute(sender);
    }

    private void scanForErrors(IrisData data, File f, JSONObject p, VolmitSender sender) {
        String key = data.toLoadKey(f);
        ResourceLoader<?> loader = data.getTypedLoaderFor(f);

        if (loader == null) {
            sender.sendMessageBasic("Can't find loader for " + f.getPath());
            return;
        }

        IrisRegistrant load = loader.load(key);
        compare(load.getClass(), p, sender, new KList<>());
        load.scanForErrors(p, sender);
    }

    public void compare(Class<?> c, JSONObject j, VolmitSender sender, KList<String> path) {
        try {
            Object o = c.getClass().getConstructor().newInstance();
        } catch (Throwable e) {

        }
    }

    public void files(File clean, KList<File> files) {
        if (clean.isDirectory()) {
            for (File i : clean.listFiles()) {
                files(i, files);
            }
        } else if (clean.getName().endsWith(".json")) {
            try {
                files.add(clean);
            } catch (Throwable e) {
                Iris.reportError(e);
            }
        }
    }

    public void filesObjects(File clean, KList<File> files) {
        if (clean.isDirectory()) {
            for (File i : clean.listFiles()) {
                filesObjects(i, files);
            }
        } else if (clean.getName().endsWith(".iob")) {
            try {
                files.add(clean);
            } catch (Throwable e) {
                Iris.reportError(e);
            }
        }
    }

    private void fixBlocks(JSONObject obj) {
        for (String i : obj.keySet()) {
            Object o = obj.get(i);

            if (i.equals("block") && o instanceof String && !o.toString().trim().isEmpty() && !o.toString().contains(":")) {
                obj.put(i, "minecraft:" + o);
            }

            if (o instanceof JSONObject) {
                fixBlocks((JSONObject) o);
            } else if (o instanceof JSONArray) {
                fixBlocks((JSONArray) o);
            }
        }
    }

    private void fixBlocks(JSONArray obj) {
        for (int i = 0; i < obj.length(); i++) {
            Object o = obj.get(i);

            if (o instanceof JSONObject) {
                fixBlocks((JSONObject) o);
            } else if (o instanceof JSONArray) {
                fixBlocks((JSONArray) o);
            }
        }
    }
}
