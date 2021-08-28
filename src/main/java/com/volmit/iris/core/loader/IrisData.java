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
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.biome.IrisBiome;
import com.volmit.iris.engine.object.block.IrisBlockData;
import com.volmit.iris.engine.object.carving.IrisCave;
import com.volmit.iris.engine.object.carving.IrisRavine;
import com.volmit.iris.engine.object.common.IrisScript;
import com.volmit.iris.engine.object.dimensional.IrisDimension;
import com.volmit.iris.engine.object.entity.IrisEntity;
import com.volmit.iris.engine.object.jigsaw.IrisJigsawPiece;
import com.volmit.iris.engine.object.jigsaw.IrisJigsawPool;
import com.volmit.iris.engine.object.jigsaw.IrisJigsawStructure;
import com.volmit.iris.engine.object.loot.IrisLootTable;
import com.volmit.iris.engine.object.mods.IrisMod;
import com.volmit.iris.engine.object.noise.IrisExpression;
import com.volmit.iris.engine.object.noise.IrisGenerator;
import com.volmit.iris.engine.object.objects.IrisObject;
import com.volmit.iris.engine.object.regional.IrisRegion;
import com.volmit.iris.engine.object.spawners.IrisSpawner;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.context.IrisContext;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.math.RNG;
import lombok.Data;

import java.io.File;
import java.util.Objects;
import java.util.function.Function;

@Data
public class IrisData {
    private static final KMap<File, IrisData> dataLoaders = new KMap<>();
    private ResourceLoader<IrisBiome> biomeLoader;
    private ResourceLoader<IrisLootTable> lootLoader;
    private ResourceLoader<IrisRegion> regionLoader;
    private ResourceLoader<IrisDimension> dimensionLoader;
    private ResourceLoader<IrisGenerator> generatorLoader;
    private ResourceLoader<IrisJigsawPiece> jigsawPieceLoader;
    private ResourceLoader<IrisJigsawPool> jigsawPoolLoader;
    private ResourceLoader<IrisJigsawStructure> jigsawStructureLoader;
    private ResourceLoader<IrisEntity> entityLoader;
    private ResourceLoader<IrisSpawner> spawnerLoader;
    private ResourceLoader<IrisMod> modLoader;
    private ResourceLoader<IrisBlockData> blockLoader;
    private ResourceLoader<IrisExpression> expressionLoader;
    private ResourceLoader<IrisObject> objectLoader;
    private ResourceLoader<IrisScript> scriptLoader;
    private ResourceLoader<IrisCave> caveLoader;
    private ResourceLoader<IrisRavine> ravineLoader;
    private KMap<Class<? extends IrisRegistrant>, ResourceLoader<? extends IrisRegistrant>> loaders = new KMap<>();
    private boolean closed;
    private final File dataFolder;
    private Engine engine;
    private final int id;

    public static IrisData get(File dataFolder) {
        return dataLoaders.compute(dataFolder, (k, v) -> v == null ? new IrisData(dataFolder) : v);
    }

    private IrisData(File dataFolder) {
        this.engine = null;
        this.dataFolder = dataFolder;
        this.id = RNG.r.imax();
        closed = false;
        hotloaded();
    }

    public static void dereference() {
        dataLoaders.v().forEach(IrisData::cleanupEngine);
    }

    public ResourceLoader<?> getTypedLoaderFor(File f) {
        String[] k = f.getPath().split("\\Q" + File.separator + "\\E");

        for (String i : k) {
            for (ResourceLoader<?> j : loaders.values()) {
                if (j.getFolderName().equals(i)) {
                    return j;
                }
            }
        }

        return null;
    }

    public void cleanupEngine() {
        if (engine != null && engine.isClosed()) {
            engine = null;
            Iris.debug("Dereferenced Data<Engine> " + getId() + " " + getDataFolder());
        }
    }

    public static int cacheSize() {
        int m = 0;
        for (IrisData i : dataLoaders.values()) {
            for (ResourceLoader<?> j : i.getLoaders().values()) {
                m += j.getLoadCache().size();
            }
        }

        return m;
    }

    public void preprocessObject(IrisRegistrant t) {
        try {
            IrisContext ctx = IrisContext.get();
            Engine engine = this.engine;

            if (engine == null && ctx != null && ctx.getEngine() != null) {
                engine = ctx.getEngine();
            }

            if (engine == null && t.getPreprocessors().isNotEmpty()) {
                Iris.error("Failed to preprocess object " + t.getLoadKey() + " because there is no engine context here. (See stack below)");
                try {
                    throw new RuntimeException();
                } catch (Throwable ex) {
                    ex.printStackTrace();
                }
            }

            if (engine != null && t.getPreprocessors().isNotEmpty()) {
                synchronized (this) {
                    engine.getExecution().getAPI().setPreprocessorObject(t);

                    for (String i : t.getPreprocessors()) {
                        engine.getExecution().execute(i);
                        Iris.debug("Loader<" + C.GREEN + t.getTypeName() + C.LIGHT_PURPLE + "> iprocess " + C.YELLOW + t.getLoadKey() + C.LIGHT_PURPLE + " in <rainbow>" + i);
                    }
                }
            }
        } catch (Throwable e) {
            Iris.error("Failed to preprocess object!");
            e.printStackTrace();
        }
    }

    public void close() {
        closed = true;
        dump();
        loaders.clear();
    }

    private static void printData(ResourceLoader<?> rl) {
        Iris.warn("  " + rl.getResourceTypeName() + " @ /" + rl.getFolderName() + ": Cache=" + rl.getLoadCache().size() + " Folders=" + rl.getFolders().size());
    }

    public IrisData copy() {
        return IrisData.get(dataFolder);
    }

    private <T extends IrisRegistrant> ResourceLoader<T> registerLoader(Class<T> registrant) {
        try {
            IrisRegistrant rr = registrant.getConstructor().newInstance();
            ResourceLoader<T> r = null;
            if (registrant.equals(IrisObject.class)) {
                r = (ResourceLoader<T>) new ObjectResourceLoader(dataFolder, this, rr.getFolderName(), rr.getTypeName());
            } else if (registrant.equals(IrisScript.class)) {
                r = (ResourceLoader<T>) new ScriptResourceLoader(dataFolder, this, rr.getFolderName(), rr.getTypeName());
            } else {
                r = new ResourceLoader<T>(dataFolder, this, rr.getFolderName(), rr.getTypeName(), registrant);
            }

            loaders.put(registrant, r);

            return r;
        } catch (Throwable e) {
            e.printStackTrace();
            Iris.error("Failed to create loader! " + registrant.getCanonicalName());
        }

        return null;
    }

    public synchronized void hotloaded() {
        if (closed) {
            return;
        }

        loaders.clear();
        File packs = dataFolder;
        packs.mkdirs();
        this.lootLoader = registerLoader(IrisLootTable.class);
        this.spawnerLoader = registerLoader(IrisSpawner.class);
        this.entityLoader = registerLoader(IrisEntity.class);
        this.regionLoader = registerLoader(IrisRegion.class);
        this.biomeLoader = registerLoader(IrisBiome.class);
        this.modLoader = registerLoader(IrisMod.class);
        this.dimensionLoader = registerLoader(IrisDimension.class);
        this.jigsawPoolLoader = registerLoader(IrisJigsawPool.class);
        this.jigsawStructureLoader = registerLoader(IrisJigsawStructure.class);
        this.jigsawPieceLoader = registerLoader(IrisJigsawPiece.class);
        this.generatorLoader = registerLoader(IrisGenerator.class);
        this.caveLoader = registerLoader(IrisCave.class);
        this.ravineLoader = registerLoader(IrisRavine.class);
        this.blockLoader = registerLoader(IrisBlockData.class);
        this.expressionLoader = registerLoader(IrisExpression.class);
        this.objectLoader = registerLoader(IrisObject.class);
        this.scriptLoader = registerLoader(IrisScript.class);
    }

    public void dump() {
        if (closed) {
            return;
        }

        for (ResourceLoader<?> i : loaders.values()) {
            i.clearCache();
        }
    }

    public void clearLists() {
        if (closed) {
            return;
        }

        for (ResourceLoader<?> i : loaders.values()) {
            i.clearList();
        }
    }

    public static IrisObject loadAnyObject(String key) {
        return loadAny(key, (dm) -> dm.getObjectLoader().load(key, false));
    }

    public static IrisBiome loadAnyBiome(String key) {
        return loadAny(key, (dm) -> dm.getBiomeLoader().load(key, false));
    }

    public static IrisExpression loadAnyExpression(String key) {
        return loadAny(key, (dm) -> dm.getExpressionLoader().load(key, false));
    }

    public static IrisMod loadAnyMod(String key) {
        return loadAny(key, (dm) -> dm.getModLoader().load(key, false));
    }

    public static IrisJigsawPiece loadAnyJigsawPiece(String key) {
        return loadAny(key, (dm) -> dm.getJigsawPieceLoader().load(key, false));
    }

    public static IrisJigsawPool loadAnyJigsawPool(String key) {
        return loadAny(key, (dm) -> dm.getJigsawPoolLoader().load(key, false));
    }

    public static IrisEntity loadAnyEntity(String key) {
        return loadAny(key, (dm) -> dm.getEntityLoader().load(key, false));
    }

    public static IrisLootTable loadAnyLootTable(String key) {
        return loadAny(key, (dm) -> dm.getLootLoader().load(key, false));
    }

    public static IrisBlockData loadAnyBlock(String key) {
        return loadAny(key, (dm) -> dm.getBlockLoader().load(key, false));
    }

    public static IrisSpawner loadAnySpaner(String key) {
        return loadAny(key, (dm) -> dm.getSpawnerLoader().load(key, false));
    }

    public static IrisScript loadAnyScript(String key) {
        return loadAny(key, (dm) -> dm.getScriptLoader().load(key, false));
    }

    public static IrisRavine loadAnyRavine(String key) {
        return loadAny(key, (dm) -> dm.getRavineLoader().load(key, false));
    }

    public static IrisRegion loadAnyRegion(String key) {
        return loadAny(key, (dm) -> dm.getRegionLoader().load(key, false));
    }

    public static IrisCave loadAnyCave(String key) {
        return loadAny(key, (dm) -> dm.getCaveLoader().load(key, false));
    }

    public static IrisDimension loadAnyDimension(String key) {
        return loadAny(key, (dm) -> dm.getDimensionLoader().load(key, false));
    }

    public static IrisJigsawStructure loadAnyJigsawStructure(String key) {
        return loadAny(key, (dm) -> dm.getJigsawStructureLoader().load(key, false));
    }

    public static IrisGenerator loadAnyGenerator(String key) {
        return loadAny(key, (dm) -> dm.getGeneratorLoader().load(key, false));
    }

    public static <T extends IrisRegistrant> T loadAny(String key, Function<IrisData, T> v) {
        try {
            for (File i : Objects.requireNonNull(Iris.instance.getDataFolder("packs").listFiles())) {
                if (i.isDirectory()) {
                    IrisData dm = get(i);
                    T t = v.apply(dm);

                    if (t != null) {
                        return t;
                    }
                }
            }
        } catch (Throwable e) {
            Iris.reportError(e);
            e.printStackTrace();
        }

        return null;
    }

    public String toLoadKey(File f) {
        if (f.getPath().startsWith(getDataFolder().getPath())) {
            String[] full = f.getPath().split("\\Q" + File.separator + "\\E");
            String[] df = getDataFolder().getPath().split("\\Q" + File.separator + "\\E");
            String g = "";
            boolean m = true;
            for (int i = 0; i < full.length; i++) {
                if (i >= df.length) {
                    if (m) {
                        m = false;
                        continue;
                    }

                    g += "/" + full[i];
                }
            }

            String ff = g.substring(1).split("\\Q.\\E")[0];
            return ff;
        } else {
            Iris.error("Forign file from loader " + f.getPath() + " (loader realm: " + getDataFolder().getPath() + ")");
        }

        Iris.error("Failed to load " + f.getPath() + " (loader realm: " + getDataFolder().getPath() + ")");

        return null;
    }
}