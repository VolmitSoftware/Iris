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

package com.volmit.iris.core;

import com.google.gson.Gson;
import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.core.report.Report;
import com.volmit.iris.core.report.ReportType;
import com.volmit.iris.core.tools.IrisWorldCreator;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.framework.IrisAccess;
import com.volmit.iris.engine.object.*;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.format.C;
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
import lombok.Data;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.zeroturnaround.zip.ZipUtil;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

@SuppressWarnings("ALL")
@Data
public class IrisProject {
    private File path;
    private String name;
    private IrisAccess activeProvider;

    public IrisProject(File path) {
        this.path = path;
        this.name = path.getName();
    }

    public KList<Report> scanForErrors() {
        KList<Report> reports = new KList<>();
        IrisDataManager data = new IrisDataManager(path);

        for (int i = 0; i < getActiveProvider().getCompound().getSize(); i++) {
            Engine e = getActiveProvider().getCompound().getEngine(i);
            IrisDimension dim = e.getDimension();
            reports.add(scanForErrors(dim));
        }

        return reports;
    }

    private KList<Report> scanForErrors(IrisDimension dim) {
        KList<Report> reports = new KList<>();

        if (dim.getFocus() != null && !dim.getFocus().isEmpty()) {
            reports.add(Report.builder()
                    .type(ReportType.NOTICE)
                    .title("Focus Mode is Enabled")
                    .message("Make sure to disable this before pushing")
                    .suggestion("Turn off focus mode")
                    .build());
        }

        for (IrisRegion i : dim.getAllRegions(getActiveProvider())) {
            scanForErrors(i);
        }

        return reports;
    }

    private KList<Report> scanForErrors(IrisRegion region) {
        KList<Report> reports = new KList<>();

        if (region.getRarity() > 60) {
            reports.add(Report.builder()
                    .type(ReportType.WARNING)
                    .title("Region " + region.getName() + " has a rarity of " + region.getRarity())
                    .message("The region rarity higher than 60 can cause performance issues")
                    .suggestion("Scale all rarities down by 50% all at once, then repeat until all rarities are below 60")
                    .build());
        }

        for (IrisBiome i : region.getAllBiomes(getActiveProvider())) {
            reports.add(scanForErrors(i));
        }

        return reports;
    }

    private KList<Report> scanForErrors(IrisBiome biome) {
        KList<Report> reports = new KList<>();

        for (IrisObjectPlacement i : biome.getObjects()) {
            reports.add(scanForErrors(biome, i));
        }

        for (IrisBiomePaletteLayer i : biome.getLayers()) {
            reports.add(scanForErrors(biome, i));
        }

        for (IrisBiomePaletteLayer i : biome.getSeaLayers()) {
            reports.add(scanForErrorsSeaLayers(biome, i));
        }

        return reports;
    }

    private KList<Report> scanForErrors(IrisBiome biome, IrisObjectPlacement i) {

        return new KList<>();
    }

    private KList<Report> scanForErrors(IrisBiome biome, IrisBiomePaletteLayer i) {

        return new KList<>();
    }

    private KList<Report> scanForErrorsSeaLayers(IrisBiome biome, IrisBiomePaletteLayer i) {

        return new KList<>();
    }

    public boolean isOpen() {
        return activeProvider != null;
    }

    public KList<File> collectFiles(File f, String json) {
        KList<File> l = new KList<>();

        if (f.isDirectory()) {
            for (File i : f.listFiles()) {
                l.addAll(collectFiles(i, json));
            }
        } else if (f.getName().endsWith("." + json)) {
            l.add(f);
        }

        return l;
    }

    public KList<File> collectFiles(String json) {
        return collectFiles(path, json);
    }

    public void open(VolmitSender sender) {
        open(sender, () ->
        {
        });
    }

    public void open(VolmitSender sender, Runnable onDone) {
        if (isOpen()) {
            close();
        }

        IrisDimension d = IrisDataManager.loadAnyDimension(getName());
        if (d == null) {
            sender.sendMessage("Can't find dimension: " + getName());
            return;
        } else if (sender.isPlayer()) {
            sender.player().setGameMode(GameMode.SPECTATOR);
        }

        J.attemptAsync(() ->
        {
            try {
                if (d.getLoader() == null) {
                    sender.sendMessage("Could not get dimension loader");
                    return;
                }
                File f = d.getLoader().getDataFolder();
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

                if (!foundWork) {
                    File ff = new File(d.getLoader().getDataFolder(), d.getLoadKey() + ".code-workspace");
                    Iris.warn("Project missing code-workspace: " + ff.getAbsolutePath() + " Re-creating code workspace.");

                    try {
                        IO.writeAll(ff, createCodeWorkspaceConfig());
                    } catch (IOException e1) {
                        Iris.reportError(e1);
                        e1.printStackTrace();
                    }
                    updateWorkspace();
                }
            } catch (Throwable e) {
                Iris.reportError(e);
                e.printStackTrace();
            }
        });

        String wfp = "iris/" + UUID.randomUUID();

        WorldCreator c = new IrisWorldCreator().dimension(getName())
                .seed(1337)
                .name(wfp)
                .studioMode()
                .create();

        IrisAccess gx = ((IrisAccess) c.generator());
        O<Boolean> done = new O<>();
        done.set(false);
        activeProvider = gx;

        J.a(() ->
        {
            double last = 0;
            int req = 400;

            while (gx.getGenerated() < req) {
                assert gx != null;
                double v = (double) gx.getGenerated() / (double) req;

                if (sender.isPlayer()) {
                    sender.player().spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(C.WHITE + "Generating " + Form.pc(v) + ((C.GRAY + " (" + (req - gx.getGenerated()) + " Left)"))));
                    J.sleep(50);
                } else {
                    sender.sendMessage(C.WHITE + "Generating " + Form.pc(v) + ((C.GRAY + " (" + (req - gx.getGenerated()) + " Left)")));
                    J.sleep(1000);
                }

                if (gx.isFailing()) {

                    sender.sendMessage("Generation Failed!");
                    break;
                }
            }
            if (sender.isPlayer()) {
                sender.player().spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(C.WHITE + "Generation Complete"));
            }
        });

        //@builder
        World world = INMS.get().createWorld(c);
        if (IrisSettings.get().getStudio().isDisableTimeAndWeather()) {
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setTime(6000);
        }
        Iris.linkMultiverseCore.removeFromConfig(world);

        done.set(true);

        if (sender.isPlayer()) {
            assert world != null;
            sender.player().teleport(world.getSpawnLocation());
        } else {
            sender.sendMessage(C.WHITE + "Generating Complete!");
        }

        Bukkit.getScheduler().scheduleSyncDelayedTask(Iris.instance, () ->
        {
            if (sender.isPlayer()) {
                sender.player().setGameMode(GameMode.SPECTATOR);
            }

            onDone.run();
        }, 0);
    }

    public void close() {
        activeProvider.close();
        File folder = activeProvider.getTarget().getWorld().worldFolder();
        Iris.linkMultiverseCore.removeFromConfig(activeProvider.getTarget().getWorld().name());
        Bukkit.unloadWorld(activeProvider.getTarget().getWorld().name(), false);
        J.attemptAsync(() -> IO.delete(folder));
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
            Iris.info("Building Workspace: " + ws.getPath());
            JSONObject j = createCodeWorkspaceConfig();
            IO.writeAll(ws, j.toString(4));
            p.end();
            Iris.info("Building Workspace: " + ws.getPath() + " took " + Form.duration(p.getMilliseconds(), 2));
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
        settings.put("json.maxItemsComputed", 50000);
        JSONArray schemas = new JSONArray();
        IrisDataManager dm = new IrisDataManager(getPath());
        schemas.put(getSchemaEntry(IrisDimension.class, dm, "/dimensions/*.json", "/dimensions/*/*.json", "/dimensions/*/*/*.json"));
        schemas.put(getSchemaEntry(IrisEntity.class, dm, "/entities/*.json", "/entities/*/*.json", "/entities/*/*/*.json"));
        schemas.put(getSchemaEntry(IrisBiome.class, dm, "/biomes/*.json", "/biomes/*/*.json", "/biomes/*/*/*.json"));
        schemas.put(getSchemaEntry(IrisRegion.class, dm, "/regions/*.json", "/regions/*/*.json", "/regions/*/*/*.json"));
        schemas.put(getSchemaEntry(IrisGenerator.class, dm, "/generators/*.json", "/generators/*/*.json", "/generators/*/*/*.json"));
        schemas.put(getSchemaEntry(IrisJigsawPiece.class, dm, "/jigsaw-pieces/*.json", "/jigsaw-pieces/*/*.json", "/jigsaw-pieces/*/*/*.json"));
        schemas.put(getSchemaEntry(IrisJigsawPool.class, dm, "/jigsaw-pools/*.json", "/jigsaw-pools/*/*.json", "/jigsaw-pools/*/*/*.json"));
        schemas.put(getSchemaEntry(IrisJigsawStructure.class, dm, "/jigsaw-structures/*.json", "/jigsaw-structures/*/*/*.json", "/jigsaw-structures/*/*.json"));
        schemas.put(getSchemaEntry(IrisBlockData.class, dm, "/blocks/*.json", "/blocks/*/*.json", "/blocks/*/*/*.json"));
        schemas.put(getSchemaEntry(IrisLootTable.class, dm, "/loot/*.json", "/loot/*/*.json", "/loot/*/*/*.json"));
        settings.put("json.schemas", schemas);
        ws.put("settings", settings);

        return ws;
    }

    public JSONObject getSchemaEntry(Class<?> i, IrisDataManager dat, String... fileMatch) {
        Iris.verbose("Processing Folder " + i.getSimpleName() + " " + fileMatch[0]);
        JSONObject o = new JSONObject();
        o.put("fileMatch", new JSONArray(fileMatch));
        o.put("schema", new SchemaBuilder(i, dat).compute());

        return o;
    }

    public File compilePackage(VolmitSender sender, boolean obfuscate, boolean minify) {
        String dimm = getName();
        IrisDataManager dm = new IrisDataManager(path);
        IrisDimension dimension = dm.getDimensionLoader().load(dimm);
        File folder = new File(Iris.instance.getDataFolder(), "exports/" + dimension.getLoadKey());
        folder.mkdirs();
        Iris.info("Packaging Dimension " + dimension.getName() + " " + (obfuscate ? "(Obfuscated)" : ""));
        KSet<IrisRegion> regions = new KSet<>();
        KSet<IrisBiome> biomes = new KSet<>();
        KSet<IrisEntity> entities = new KSet<>();
        KSet<IrisGenerator> generators = new KSet<>();
        KSet<IrisLootTable> loot = new KSet<>();
        KSet<IrisBlockData> blocks = new KSet<>();

        for (String i : dm.getDimensionLoader().getPossibleKeys()) {
            blocks.add(dm.getBlockLoader().load(i));
        }

        //TODO: EXPORT JIGSAW PIECES FROM STRUCTURES
        dimension.getRegions().forEach((i) -> regions.add(dm.getRegionLoader().load(i)));
        dimension.getLoot().getTables().forEach((i) -> loot.add(dm.getLootLoader().load(i)));
        regions.forEach((i) -> biomes.addAll(i.getAllBiomes(null)));
        biomes.forEach((i) -> i.getGenerators().forEach((j) -> generators.add(j.getCachedGenerator(null))));
        regions.forEach((r) -> r.getLoot().getTables().forEach((i) -> loot.add(dm.getLootLoader().load(i))));
        biomes.forEach((r) -> r.getLoot().getTables().forEach((i) -> loot.add(dm.getLootLoader().load(i))));
        biomes.forEach((r) -> r.getEntitySpawnOverrides().forEach((sp) -> entities.add(dm.getEntityLoader().load(sp.getEntity()))));
        regions.forEach((r) -> r.getEntitySpawnOverrides().forEach((sp) -> entities.add(dm.getEntityLoader().load(sp.getEntity()))));
        dimension.getEntitySpawnOverrides().forEach((sp) -> entities.add(dm.getEntityLoader().load(sp.getEntity())));
        biomes.forEach((r) -> r.getEntityInitialSpawns().forEach((sp) -> entities.add(dm.getEntityLoader().load(sp.getEntity()))));
        regions.forEach((r) -> r.getEntityInitialSpawns().forEach((sp) -> entities.add(dm.getEntityLoader().load(sp.getEntity()))));
        dimension.getEntityInitialSpawns().forEach((sp) -> entities.add(dm.getEntityLoader().load(sp.getEntity())));
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

        for (IrisBiomeMutation i : dimension.getMutations()) {
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

        dimension.getMutations().forEach((i) -> i.getObjects().forEach((j) -> j.getPlace().forEach((k) ->
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
}
