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

package art.arcane.iris.core.project;

import art.arcane.iris.engine.object.placement.ObjectPlacements;
import art.arcane.iris.engine.object.placement.IrisObjectPlacement;
import com.google.gson.Gson;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.core.localization.BukkitRuntimeMessages;
import art.arcane.iris.core.localization.IrisLanguage;
import art.arcane.iris.core.pack.ImageMapPackageClosure;
import art.arcane.iris.core.pack.ModuleResourceClosure;
import art.arcane.iris.core.pack.PackValidationResult;
import art.arcane.iris.core.pack.PackValidator;
import art.arcane.iris.core.pack.StructurePackageClosure;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.material.IrisBlockData;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.terrain.IrisGenerator;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import art.arcane.iris.engine.object.structure.IrisStructure;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.util.common.plugin.VolmitSender;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.collection.KSet;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.json.JSONObject;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.scheduling.ChronoLatch;
import art.arcane.volmlib.util.scheduling.O;
import com.volmit.config.Level;
import org.zeroturnaround.zip.ZipUtil;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("ALL")
public class IrisPackageCompiler {
    private final IrisProject project;

    public IrisPackageCompiler(IrisProject project) {
        this.project = project;
    }

    public File compilePackage(VolmitSender sender, boolean obfuscate, boolean minify) {
        PackValidationResult validation = PackValidator.validateForPackaging(project.getPath());
        if (!validation.isLoadable()) {
            IllegalStateException error = new IllegalStateException("Pack validation failed before packaging: "
                    + String.join("; ", validation.getBlockingErrors()));
            IrisLogging.reportError("Package validation failed for '" + project.getName() + "'.", error);
            sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.IRIS_PROJECT_FAILED));
            return null;
        }
        // Detached loader on purpose: obfuscation rewrites the placement 'place' lists on the
        // loaded resources, which must never leak into the shared cached pack state — a second
        // export against a mutated cache produced archives with an empty objects/ directory.
        IrisData dm = IrisData.openRuntime(project.getPath());
        try {
            return compilePackage(dm, sender, obfuscate, minify);
        } finally {
            dm.close();
        }
    }

    private static <T> void addIfPresent(KSet<T> set, T value) {
        if (value != null) {
            set.add(value);
        }
    }

    private File compilePackage(IrisData dm, VolmitSender sender, boolean obfuscate, boolean minify) {
        String dimm = project.getName();
        IrisDimension dimension = dm.getDimensionLoader().load(dimm);
        File folder = new File(IrisPlatforms.get().dataFolder(), "exports/" + dimension.getLoadKey());
        IO.delete(folder);
        if (folder.exists()) {
            throw new IllegalStateException("Failed to clear structure package staging folder " + folder.getAbsolutePath());
        }
        if (!folder.mkdirs() && !folder.isDirectory()) {
            throw new IllegalStateException("Failed to create structure package staging folder " + folder.getAbsolutePath());
        }
        IrisLogging.info("Packaging Dimension " + dimension.getName() + " " + (obfuscate ? "(Obfuscated)" : ""));
        KSet<IrisRegion> regions = new KSet<>();
        KSet<IrisBiome> biomes = new KSet<>();
        KSet<IrisGenerator> generators = new KSet<>();
        ModuleResourceClosure moduleResources = new ModuleResourceClosure(dm).exportingElsewhere(IrisStructure.class);
        KSet<IrisBlockData> blocks = new KSet<>();

        // KSet is ConcurrentHashMap-backed: both add(null) and remove(null) throw NPE, so a
        // failed loader lookup must be filtered at the add site, never stripped afterwards.
        for (String i : dm.getBlockLoader().getPossibleKeys()) {
            addIfPresent(blocks, dm.getBlockLoader().load(i));
        }

        dimension.getAllRegions(() -> dm).forEach((region) -> addIfPresent(regions, region));
        moduleResources.followSettingsOf(dimension);
        regions.forEach((i) -> biomes.addAll(i.getAllBiomes(() -> dm)));
        dimension.getReachableBiomes(() -> dm).forEach((biome) -> addIfPresent(biomes, biome));
        regions.forEach(moduleResources::followSettingsOf);
        biomes.forEach((i) -> i.getGenerators().forEach((j) -> addIfPresent(generators, j.getCachedGenerator(() -> dm))));
        biomes.forEach(moduleResources::followSettingsOf);
        KList<IrisObjectPlacement> allPlacements = new KList<>();
        regions.forEach((r) -> allPlacements.addAll(ObjectPlacements.all(r)));
        biomes.forEach((i) -> allPlacements.addAll(ObjectPlacements.all(i)));
        moduleResources.follow(allPlacements);
        List<String> structureKeys = moduleResources.keysOf(IrisStructure.class);
        KMap<String, String> renameObjects = new KMap<>();
        String a;
        StringBuilder b = new StringBuilder();
        StringBuilder c = new StringBuilder();
        sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.IRIS_PROJECT_SERIALIZING_OBJECTS));

        for (IrisObjectPlacement j : allPlacements) {
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

        KMap<String, KList<String>> lookupObjects = renameObjects.flip();
        StringBuilder gb = new StringBuilder();
        ChronoLatch cl = new ChronoLatch(1000);
        O<Integer> ggg = new O<>();
        ggg.set(0);
        O<Integer> missingObjects = new O<>();
        missingObjects.set(0);
        allPlacements.forEach((j) -> j.getPlace().forEach((k) ->
        {
            try {
                KList<String> sources = lookupObjects.get(k);
                File f = sources == null || sources.isEmpty() ? null : dm.getObjectLoader().findFile(sources.get(0));
                if (f == null) {
                    missingObjects.set(missingObjects.get() + 1);
                    IrisLogging.error("Missing object for placement key " + k);
                    return;
                }
                IO.copyFile(f, new File(folder, "objects/" + k + ".iob"));
                gb.append(IO.hash(f));
                ggg.set(ggg.get() + 1);

                if (cl.flip()) {
                    int g = ggg.get();
                    ggg.set(0);
                    sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.IRIS_PROJECT_WROTE_ANOTHER_OBJECTS, MessageArgument.untrusted("g", String.valueOf(g))));
                }
            } catch (Throwable e) {
                missingObjects.set(missingObjects.get() + 1);
                IrisLogging.reportError(e);
            }
        }));
        if (missingObjects.get() > 0) {
            // A package missing objects must fail loudly, not ship as a clean "compiled".
            throw new IllegalStateException(missingObjects.get()
                    + " object(s) could not be exported for pack '" + dimm + "'; package compile aborted.");
        }

        b.append(IO.hash(gb.toString()));
        c.append(IO.hash(b.toString()));
        b = new StringBuilder();

        IrisLogging.info("Writing Dimensional Scaffold");

        try {
            StructurePackageClosure structureClosure = StructurePackageClosure.collect(project.getPath(), structureKeys);
            if (!structureClosure.isValid()) {
                throw new IOException("Structure package closure is invalid: " + String.join("; ", structureClosure.errors()));
            }
            b.append(structureClosure.writeTo(folder, minify));
            b.append(ImageMapPackageClosure.writeAll(dm, folder, minify));
            a = withModuleSettings(dimension, Level.DIMENSION, dm).toString(minify ? 0 : 4);
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
                a = withModuleSettings(i, Level.REGION, dm).toString(minify ? 0 : 4);
                IO.writeAll(new File(folder, "regions/" + i.getLoadKey() + ".json"), a);
                b.append(IO.hash(a));
            }

            for (IrisBlockData i : blocks) {
                a = new JSONObject(new Gson().toJson(i)).toString(minify ? 0 : 4);
                IO.writeAll(new File(folder, "blocks/" + i.getLoadKey() + ".json"), a);
                b.append(IO.hash(a));
            }

            for (IrisBiome i : biomes) {
                a = withModuleSettings(i, Level.BIOME, dm).toString(minify ? 0 : 4);
                IO.writeAll(new File(folder, "biomes/" + i.getLoadKey() + ".json"), a);
                b.append(IO.hash(a));
            }

            b.append(moduleResources.writeTo(folder, minify));

            c.append(IO.hash(b.toString()));
            String finalHash = IO.hash(c.toString());
            JSONObject meta = new JSONObject();
            meta.put("hash", finalHash);
            meta.put("time", M.ms());
            meta.put("version", dimension.getVersion());
            IO.writeAll(new File(folder, "package.json"), meta.toString(minify ? 0 : 4));
            File p = new File(IrisPlatforms.get().dataFolder(), "exports/" + dimension.getLoadKey() + ".iris");
            IrisLogging.info("Compressing Package");
            ZipUtil.pack(folder, p, 9);
            IO.delete(folder);

            sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.IRIS_PROJECT_PACKAGE_COMPILED));
            return p;
        } catch (Throwable e) {
            IrisLogging.reportError(e);
        }
        sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.IRIS_PROJECT_FAILED));
        return null;
    }

    /** The JSON of a dimension, region or biome with the settings its modules declare, which the resource itself does not hold. */
    private static JSONObject withModuleSettings(IrisRegistrant resource, Level target, IrisData data) {
        JSONObject json = new JSONObject(new Gson().toJson(resource));
        JSONObject settings = data.getModules().settings().encode(target, resource.getModuleSettings());
        settings.keySet().forEach(key -> json.put(key, settings.get(key)));
        return json;
    }
}
