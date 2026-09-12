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

package art.arcane.iris.studio.workspace;

import com.google.gson.Gson;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.localization.BukkitRuntimeMessages;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.pack.ImageMapPackageClosure;
import art.arcane.iris.pack.PackValidationResult;
import art.arcane.iris.pack.PackValidator;
import art.arcane.iris.pack.StructurePackageClosure;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.block.IrisBlockData;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.world.entity.IrisEntity;
import art.arcane.iris.generation.noise.IrisExpression;
import art.arcane.iris.generation.noise.IrisGenerator;
import art.arcane.iris.world.loot.IrisLootTable;
import art.arcane.iris.pack.PackExportClosure;
import art.arcane.iris.world.entity.IrisMarker;
import art.arcane.iris.structure.object.IrisObjectPlacement;
import art.arcane.iris.structure.object.IrisStaticObject;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.world.entity.IrisSpawner;
import art.arcane.iris.structure.placement.IrisStructurePlacement;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.collection.KSet;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.json.JSONObject;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.scheduling.ChronoLatch;
import art.arcane.volmlib.util.scheduling.O;
import org.zeroturnaround.zip.ZipUtil;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
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
        KList<IrisDimension> dimensions = new KList<>();
        for (String dimensionKey : PackExportClosure.collectDimensionKeys(dimension).stream().sorted().toList()) {
            IrisDimension exportedDimension = dm.getDimensionLoader().load(dimensionKey);
            if (exportedDimension != null) {
                dimensions.add(exportedDimension);
            }
        }
        KSet<IrisRegion> regions = new KSet<>();
        KSet<IrisBiome> biomes = new KSet<>();
        KSet<IrisEntity> entities = new KSet<>();
        KSet<IrisSpawner> spawners = new KSet<>();
        KSet<IrisGenerator> generators = new KSet<>();
        KSet<IrisExpression> expressions = new KSet<>();
        KSet<IrisLootTable> loot = new KSet<>();
        KSet<IrisBlockData> blocks = new KSet<>();

        // KSet is ConcurrentHashMap-backed: both add(null) and remove(null) throw NPE, so a
        // failed loader lookup must be filtered at the add site, never stripped afterwards.
        for (String i : dm.getBlockLoader().getPossibleKeys()) {
            addIfPresent(blocks, dm.getBlockLoader().load(i));
        }
        for (String key : dm.getExpressionLoader().getPossibleKeys()) {
            addIfPresent(expressions, dm.getExpressionLoader().load(key));
        }

        for (IrisDimension exportedDimension : dimensions) {
            exportedDimension.getAllRegions(() -> dm).forEach((region) -> addIfPresent(regions, region));
            exportedDimension.getLoot().getTables().forEach((i) -> addIfPresent(loot, dm.getLootLoader().load(i)));
            exportedDimension.getReachableBiomes(() -> dm).forEach((biome) -> addIfPresent(biomes, biome));
            exportedDimension.getEntitySpawners().forEach((sp) ->
                    addIfPresent(spawners, dm.getSpawnerLoader().load(sp)));
        }
        regions.forEach((i) -> biomes.addAll(i.getAllBiomes(() -> dm)));
        regions.forEach((r) -> r.getLoot().getTables().forEach((i) -> addIfPresent(loot, dm.getLootLoader().load(i))));
        regions.forEach((r) -> r.getEntitySpawners().forEach((sp) -> addIfPresent(spawners, dm.getSpawnerLoader().load(sp))));
        biomes.forEach((i) -> i.getGenerators().forEach((j) -> addIfPresent(generators, j.getCachedGenerator(() -> dm))));
        biomes.forEach((r) -> r.getLoot().getTables().forEach((i) -> addIfPresent(loot, dm.getLootLoader().load(i))));
        biomes.forEach((r) -> r.getEntitySpawners().forEach((sp) -> addIfPresent(spawners, dm.getSpawnerLoader().load(sp))));
        KList<IrisObjectPlacement> allPlacements = new KList<>();
        regions.forEach((r) -> allPlacements.addAll(r.getObjects()));
        biomes.forEach((i) -> allPlacements.addAll(i.getObjects()));
        for (IrisDimension exportedDimension : dimensions) {
            for (IrisStaticObject placement : exportedDimension.getStaticObjects()) {
                allPlacements.add(placement.toPlacement());
            }
        }
        KSet<IrisMarker> markers = new KSet<>();
        for (String markerKey : PackExportClosure.collectMarkerKeys(allPlacements)) {
            IrisMarker marker = dm.getMarkerLoader().load(markerKey);
            if (marker == null) {
                continue;
            }
            markers.add(marker);
            marker.getSpawners().forEach((sp) -> addIfPresent(spawners, dm.getSpawnerLoader().load(sp)));
        }
        collectSpawnerEntityKeys(spawners).forEach((i) -> addIfPresent(entities, dm.getEntityLoader().load(i)));
        entities.forEach((e) -> e.getLoot().getTables().forEach((i) -> addIfPresent(loot, dm.getLootLoader().load(i))));
        Set<String> structureKeys = new LinkedHashSet<>();
        for (IrisDimension exportedDimension : dimensions) {
            collectStructureKeys(structureKeys, exportedDimension.getStructures());
        }
        regions.forEach((region) -> collectStructureKeys(structureKeys, region.getStructures()));
        biomes.forEach((biome) -> collectStructureKeys(structureKeys, biome.getStructures()));
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

        for (IrisDimension exportedDimension : dimensions) {
            for (IrisStaticObject placement : exportedDimension.getStaticObjects()) {
                placement.setObject(renameObjects.get(placement.getObject()));
            }
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
            for (IrisDimension exportedDimension : dimensions.stream()
                    .sorted(Comparator.comparing(IrisDimension::getLoadKey)).toList()) {
                a = new JSONObject(new Gson().toJson(exportedDimension)).toString(minify ? 0 : 4);
                IO.writeAll(new File(folder, "dimensions/" + exportedDimension.getLoadKey() + ".json"), a);
                b.append(IO.hash(a));
            }

            for (IrisGenerator i : generators) {
                a = new JSONObject(new Gson().toJson(i)).toString(minify ? 0 : 4);
                IO.writeAll(new File(folder, "generators/" + i.getLoadKey() + ".json"), a);
                b.append(IO.hash(a));
            }

            for (IrisExpression i : expressions.stream()
                    .sorted(Comparator.comparing(IrisExpression::getLoadKey)).toList()) {
                a = new JSONObject(new Gson().toJson(i)).toString(minify ? 0 : 4);
                IO.writeAll(new File(folder, "expressions/" + i.getLoadKey() + ".json"), a);
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

            // Sorted so package.json.hash stays stable across runs.
            for (IrisSpawner i : spawners.stream().sorted(Comparator.comparing(IrisSpawner::getLoadKey)).toList()) {
                a = new JSONObject(new Gson().toJson(i)).toString(minify ? 0 : 4);
                IO.writeAll(new File(folder, "spawners/" + i.getLoadKey() + ".json"), a);
                b.append(IO.hash(a));
            }

            for (IrisMarker i : markers.stream().sorted(Comparator.comparing(IrisMarker::getLoadKey)).toList()) {
                a = new JSONObject(new Gson().toJson(i)).toString(minify ? 0 : 4);
                IO.writeAll(new File(folder, "markers/" + i.getLoadKey() + ".json"), a);
                b.append(IO.hash(a));
            }

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

    static KSet<String> collectSpawnerEntityKeys(KSet<IrisSpawner> spawners) {
        KSet<String> entityKeys = new KSet<>();
        for (IrisSpawner spawner : spawners) {
            spawner.getSpawns().forEach((spawn) -> entityKeys.add(spawn.getEntity()));
            spawner.getInitialSpawns().forEach((spawn) -> entityKeys.add(spawn.getEntity()));
        }
        return entityKeys;
    }

    private static void collectStructureKeys(Set<String> keys, KList<IrisStructurePlacement> placements) {
        if (placements == null) {
            return;
        }
        for (IrisStructurePlacement placement : placements) {
            if (placement == null || placement.getStructures() == null) {
                continue;
            }
            for (String structureKey : placement.getStructures()) {
                keys.add(structureKey);
            }
        }
    }
}
