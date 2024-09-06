/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
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

package com.volmit.iris.core.nms.v1X;

import com.google.common.base.Preconditions;
import com.volmit.iris.Iris;
import com.volmit.iris.core.nms.INMSBinding;
import com.volmit.iris.core.nms.container.BiomeColor;
import com.volmit.iris.core.nms.container.BlockPos;
import com.volmit.iris.core.nms.container.IPackRepository;
import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.engine.object.IrisBiomeCustom;
import com.volmit.iris.engine.object.IrisDimension;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.mantle.Mantle;
import com.volmit.iris.util.math.Vector3d;
import com.volmit.iris.util.nbt.mca.palette.MCABiomeContainer;
import com.volmit.iris.util.nbt.mca.palette.MCAPaletteAccess;
import com.volmit.iris.util.nbt.tag.CompoundTag;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.matcher.ElementMatchers;
import org.bukkit.*;
import org.bukkit.WorldCreator;
import org.bukkit.block.Biome;
import org.bukkit.entity.Dolphin;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.structure.Structure;
import org.bukkit.inventory.ItemStack;

import java.awt.Color;
import java.io.File;

public class NMSBinding1X implements INMSBinding {
    private static final boolean supportsCustomHeight = testCustomHeight();

    @SuppressWarnings("ConstantConditions")
    private static boolean testCustomHeight() {
        try {
            if (World.class.getDeclaredMethod("getMaxHeight") != null && World.class.getDeclaredMethod("getMinHeight") != null)
                ;
            {
                return true;
            }
        } catch (Throwable ignored) {

        }

        return false;
    }

    @Override
    public boolean hasTile(Material material) {
        return false;
    }

    @Override
    public boolean hasTile(Location l) {
        return false;
    }

    @Override
    public KMap<String, Object> serializeTile(Location location) {
        return null;
    }

    @Override
    public void deserializeTile(KMap<String, Object> s, Location newPosition) {

    }


    @Override
    public void injectBiomesFromMantle(Chunk e, Mantle mantle) {

    }

    @Override
    public ItemStack applyCustomNbt(ItemStack itemStack, KMap<String, Object> customNbt) throws IllegalArgumentException {
        return itemStack;
    }

    @Override
    public void inject(long seed, Engine engine, World world) throws NoSuchFieldException, IllegalAccessException {

    }

    public Vector3d getBoundingbox() {
        return null;
    }

    @Override
    public Entity spawnEntity(Location location, EntityType type, CreatureSpawnEvent.SpawnReason reason) {
        return location.getWorld().spawnEntity(location, type);
    }

    @Override
    public boolean registerDimension(String name, IrisDimension dimension) {
        return false;
    }

    @Override
    public boolean registerBiome(String dimensionId, IrisBiomeCustom biome, boolean replace) {
        return false;
    }

    @Override
    public boolean dumpRegistry(File... folders) {
        return false;
    }

    @Override
    public Color getBiomeColor(Location location, BiomeColor type) {
        return Color.GREEN;
    }

    @Override
    public KList<String> getStructureKeys() {
        var list = Registry.STRUCTURE.stream()
                .map(Structure::getKey)
                .map(NamespacedKey::toString)
                .toList();
        return new KList<>(list);
    }

    @Override
    public void reconnect(Player player) {

    }

    @Override
    public CompoundTag serializeEntity(Entity location) {
        return null;
    }

    @Override
    public Entity deserializeEntity(CompoundTag s, Location newPosition) {
        return null;
    }

    @Override
    public boolean supportsCustomHeight() {
        return supportsCustomHeight;
    }

    @Override
    public Object getBiomeBaseFromId(int id) {
        return null;
    }

    @Override
    public int getMinHeight(World world) {
        return supportsCustomHeight ? world.getMinHeight() : 0;
    }

    @Override
    public boolean supportsCustomBiomes() {
        return false;
    }

    @Override
    public int getTrueBiomeBaseId(Object biomeBase) {
        return 0;
    }

    @Override
    public Object getTrueBiomeBase(Location location) {
        return null;
    }

    @Override
    public String getTrueBiomeBaseKey(Location location) {
        return null;
    }

    @Override
    public Object getCustomBiomeBaseFor(String mckey) {
        return null;
    }

    @Override
    public Object getCustomBiomeBaseHolderFor(String mckey) {
        return null;
    }

    @Override
    public int getBiomeBaseIdForKey(String key) {
        return 0;
    }

    @Override
    public String getKeyForBiomeBase(Object biomeBase) {
        return null;
    }

    public Object getBiomeBase(World world, Biome biome) {
        return null;
    }

    @Override
    public Object getBiomeBase(Object registry, Biome biome) {
        return null;
    }

    @Override
    public KList<Biome> getBiomes() {
        return new KList<>(Biome.values()).qdel(Biome.CUSTOM);
    }

    @Override
    public boolean isBukkit() {
        return true;
    }

    @Override
    public int getBiomeId(Biome biome) {
        return biome.ordinal();
    }

    @Override
    public MCABiomeContainer newBiomeContainer(int min, int max) {
        Iris.error("Cannot use the custom biome data! Iris is incapable of using MCA generation on this version of minecraft!");

        return null;
    }

    @Override
    public MCABiomeContainer newBiomeContainer(int min, int max, int[] v) {
        Iris.error("Cannot use the custom biome data! Iris is incapable of using MCA generation on this version of minecraft!");

        return null;
    }

    @Override
    public int countCustomBiomes() {
        return 0;
    }

    @Override
    public void forceBiomeInto(int x, int y, int z, Object somethingVeryDirty, ChunkGenerator.BiomeGrid chunk) {

    }

    @Override
    public Vector3d getBoundingbox(org.bukkit.entity.EntityType entity) {
        return null;
    }

    @Override
    public MCAPaletteAccess createPalette() {
        Iris.error("Cannot use the global data palette! Iris is incapable of using MCA generation on this version of minecraft!");
        return null;
    }

    public void injectBukkit() {
        try {
            Iris.info("Injecting Bukkit");
            new ByteBuddy()
                    .redefine(WorldCreator.class)
                    .visit(Advice.to(WorldCreatorAdvice.class).on(ElementMatchers.isConstructor().and(ElementMatchers.takesArguments(String.class))))
                    .make()
                    .load(WorldCreator.class.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
            Iris.info("Injected Bukkit Successfully!");
        } catch (Exception e) {
            Iris.info(C.RED + "Failed to Inject Bukkit!");
            e.printStackTrace();
            Iris.reportError(e);
        }

    }

    @Override
    public IPackRepository getPackRepository() {
        return new PackRepository1X();
    }

    private static class WorldCreatorAdvice {
        @Advice.OnMethodEnter
        static void enter(@Advice.Argument(0) String name) {
            File isIrisWorld = new File(name, "iris");
            boolean isFromIris = false;
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            for (StackTraceElement stack : stackTrace) {
                if (stack.getClassName().contains("Iris")) {
                    isFromIris = true;
                    break;
                }
            }
            if (!isFromIris) {
                Preconditions.checkArgument(!isIrisWorld.exists(), "Only Iris can load Iris Worlds!");
            }
        }
    }
}
