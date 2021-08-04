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

package com.volmit.iris.core.project.loader;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.biome.LoaderBiome;
import com.volmit.iris.engine.object.block.LoaderBlockData;
import com.volmit.iris.engine.object.dimensional.LoaderDimension;
import com.volmit.iris.engine.object.entity.LoaderEntity;
import com.volmit.iris.engine.object.jigsaw.LoaderJigsawPiece;
import com.volmit.iris.engine.object.jigsaw.LoaderJigsawPool;
import com.volmit.iris.engine.object.jigsaw.LoaderJigsawStructure;
import com.volmit.iris.engine.object.loot.LoaderLootTable;
import com.volmit.iris.engine.object.mods.LoaderMod;
import com.volmit.iris.engine.object.noise.LoaderExpression;
import com.volmit.iris.engine.object.noise.LoaderGenerator;
import com.volmit.iris.engine.object.objects.LoaderObject;
import com.volmit.iris.engine.object.regional.LoaderRegion;
import com.volmit.iris.engine.object.spawners.LoaderSpawner;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.math.RNG;
import lombok.Data;

import java.io.File;
import java.util.Objects;
import java.util.function.Function;

@Data
public class IrisData {
    private ResourceLoader<LoaderBiome> biomeLoader;
    private ResourceLoader<LoaderLootTable> lootLoader;
    private ResourceLoader<LoaderRegion> regionLoader;
    private ResourceLoader<LoaderDimension> dimensionLoader;
    private ResourceLoader<LoaderGenerator> generatorLoader;
    private ResourceLoader<LoaderJigsawPiece> jigsawPieceLoader;
    private ResourceLoader<LoaderJigsawPool> jigsawPoolLoader;
    private ResourceLoader<LoaderJigsawStructure> jigsawStructureLoader;
    private ResourceLoader<LoaderEntity> entityLoader;
    private ResourceLoader<LoaderSpawner> spawnerLoader;
    private ResourceLoader<LoaderMod> modLoader;
    private ResourceLoader<LoaderBlockData> blockLoader;
    private ResourceLoader<LoaderExpression> expressionLoader;
    private ResourceLoader<LoaderObject> objectLoader;
    private KMap<Class<? extends LoaderRegistrant>, ResourceLoader<? extends LoaderRegistrant>> loaders = new KMap<>();
    private boolean closed;
    private final File dataFolder;
    private Engine engine;
    private final int id;

    public IrisData(File dataFolder) {
        this(dataFolder, false);
    }

    public IrisData(File dataFolder, boolean oneshot) {
        this.engine = null;
        this.dataFolder = dataFolder;
        this.id = RNG.r.imax();
        closed = false;
        hotloaded();
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
        return new IrisData(dataFolder);
    }

    private <T extends LoaderRegistrant> ResourceLoader<T> registerLoader(Class<T> registrant) {
        try {
            LoaderRegistrant rr = registrant.getConstructor().newInstance();
            ResourceLoader<T> r = null;
            if (registrant.equals(LoaderObject.class)) {
                r = (ResourceLoader<T>) new ObjectResourceLoader(dataFolder, this, rr.getFolderName(), rr.getTypeName());
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
        this.lootLoader = registerLoader(LoaderLootTable.class);
        this.spawnerLoader = registerLoader(LoaderSpawner.class);
        this.entityLoader = registerLoader(LoaderEntity.class);
        this.regionLoader = registerLoader(LoaderRegion.class);
        this.biomeLoader = registerLoader(LoaderBiome.class);
        this.modLoader = registerLoader(LoaderMod.class);
        this.dimensionLoader = registerLoader(LoaderDimension.class);
        this.jigsawPoolLoader = registerLoader(LoaderJigsawPool.class);
        this.jigsawStructureLoader = registerLoader(LoaderJigsawStructure.class);
        this.jigsawPieceLoader = registerLoader(LoaderJigsawPiece.class);
        this.generatorLoader = registerLoader(LoaderGenerator.class);
        this.blockLoader = registerLoader(LoaderBlockData.class);
        this.expressionLoader = registerLoader(LoaderExpression.class);
        this.objectLoader = registerLoader(LoaderObject.class);
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

    public static LoaderObject loadAnyObject(String key) {
        return loadAny(key, (dm) -> dm.getObjectLoader().load(key, false));
    }

    public static LoaderBiome loadAnyBiome(String key) {
        return loadAny(key, (dm) -> dm.getBiomeLoader().load(key, false));
    }

    public static LoaderExpression loadAnyExpression(String key) {
        return loadAny(key, (dm) -> dm.getExpressionLoader().load(key, false));
    }

    public static LoaderMod loadAnyMod(String key) {
        return loadAny(key, (dm) -> dm.getModLoader().load(key, false));
    }

    public static LoaderJigsawPiece loadAnyJigsawPiece(String key) {
        return loadAny(key, (dm) -> dm.getJigsawPieceLoader().load(key, false));
    }

    public static LoaderJigsawPool loadAnyJigsawPool(String key) {
        return loadAny(key, (dm) -> dm.getJigsawPoolLoader().load(key, false));
    }

    public static LoaderEntity loadAnyEntity(String key) {
        return loadAny(key, (dm) -> dm.getEntityLoader().load(key, false));
    }

    public static LoaderLootTable loadAnyLootTable(String key) {
        return loadAny(key, (dm) -> dm.getLootLoader().load(key, false));
    }

    public static LoaderBlockData loadAnyBlock(String key) {
        return loadAny(key, (dm) -> dm.getBlockLoader().load(key, false));
    }

    public static LoaderSpawner loadAnySpaner(String key) {
        return loadAny(key, (dm) -> dm.getSpawnerLoader().load(key, false));
    }

    public static LoaderRegion loadAnyRegion(String key) {
        return loadAny(key, (dm) -> dm.getRegionLoader().load(key, false));
    }

    public static LoaderDimension loadAnyDimension(String key) {
        return loadAny(key, (dm) -> dm.getDimensionLoader().load(key, false));
    }

    public static LoaderJigsawStructure loadAnyJigsawStructure(String key) {
        return loadAny(key, (dm) -> dm.getJigsawStructureLoader().load(key, false));
    }

    public static LoaderGenerator loadAnyGenerator(String key) {
        return loadAny(key, (dm) -> dm.getGeneratorLoader().load(key, false));
    }

    public static <T extends LoaderRegistrant> T loadAny(String key, Function<IrisData, T> v) {
        try {
            for (File i : Objects.requireNonNull(Iris.instance.getDataFolder("packs").listFiles())) {
                if (i.isDirectory()) {
                    IrisData dm = new IrisData(i, true);
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
}