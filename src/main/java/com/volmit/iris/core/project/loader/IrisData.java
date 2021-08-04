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
import com.volmit.iris.engine.object.biome.IrisBiome;
import com.volmit.iris.engine.object.block.IrisBlockData;
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
import com.volmit.iris.util.math.RNG;
import lombok.Data;

import java.io.File;
import java.util.Objects;
import java.util.function.Function;

@Data
public class IrisData {
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
    private KMap<Class<? extends IrisRegistrant>, ResourceLoader<? extends IrisRegistrant>> loaders = new KMap<>();
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

    private <T extends IrisRegistrant> ResourceLoader<T> registerLoader(Class<T> registrant) {
        try {
            IrisRegistrant rr = registrant.getConstructor().newInstance();
            ResourceLoader<T> r = null;
            if (registrant.equals(IrisObject.class)) {
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
        this.blockLoader = registerLoader(IrisBlockData.class);
        this.expressionLoader = registerLoader(IrisExpression.class);
        this.objectLoader = registerLoader(IrisObject.class);
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

    public static IrisRegion loadAnyRegion(String key) {
        return loadAny(key, (dm) -> dm.getRegionLoader().load(key, false));
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