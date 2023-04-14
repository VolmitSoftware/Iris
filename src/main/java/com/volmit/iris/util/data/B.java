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

package com.volmit.iris.util.data;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.link.Identifier;
import com.volmit.iris.core.service.ExternalDataSVC;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.scheduling.ChronoLatch;
import it.unimi.dsi.fastutil.ints.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.block.data.type.PointedDripstone;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.bukkit.Material.*;

public class B {
    private static final KMap<String, BlockData> custom = new KMap<>();

    private static final Material AIR_MATERIAL = Material.AIR;
    private static final BlockData AIR = AIR_MATERIAL.createBlockData();
    private static final IntSet foliageCache = buildFoliageCache();
    private static final IntSet deepslateCache = buildDeepslateCache();
    private static final Int2IntMap normal2DeepslateCache = buildNormal2DeepslateCache();
    private static final Int2IntMap deepslate2NormalCache = buildDeepslate2NormalCache();
    private static final IntSet decorantCache = buildDecorantCache();
    private static final IntSet storageCache = buildStorageCache();
    private static final IntSet storageChestCache = buildStorageChestCache();
    private static final IntSet litCache = buildLitCache();
    private static final ChronoLatch clw = new ChronoLatch(1000);

    private static IntSet buildFoliageCache() {
        IntSet b = new IntOpenHashSet();
        Arrays.stream(new Material[]{
                POPPY,
                DANDELION,
                CORNFLOWER,
                SWEET_BERRY_BUSH,
                CRIMSON_ROOTS,
                WARPED_ROOTS,
                NETHER_SPROUTS,
                ALLIUM,
                AZURE_BLUET,
                BLUE_ORCHID,
                OXEYE_DAISY,
                LILY_OF_THE_VALLEY,
                WITHER_ROSE,
                DARK_OAK_SAPLING,
                ACACIA_SAPLING,
                JUNGLE_SAPLING,
                BIRCH_SAPLING,
                SPRUCE_SAPLING,
                OAK_SAPLING,
                ORANGE_TULIP,
                PINK_TULIP,
                RED_TULIP,
                WHITE_TULIP,
                FERN,
                LARGE_FERN,
                GRASS,
                TALL_GRASS
        }).forEach((i) -> b.add(i.ordinal()));

        return IntSets.unmodifiable(b);
    }

    private static IntSet buildDeepslateCache() {
        IntSet b = new IntOpenHashSet();
        Arrays.stream(new Material[]{
                DEEPSLATE,
                DEEPSLATE_BRICKS,
                DEEPSLATE_BRICK_SLAB,
                DEEPSLATE_BRICK_STAIRS,
                DEEPSLATE_BRICK_WALL,
                DEEPSLATE_TILE_SLAB,
                DEEPSLATE_TILES,
                DEEPSLATE_TILE_STAIRS,
                DEEPSLATE_TILE_WALL,
                CRACKED_DEEPSLATE_TILES
        }).forEach((i) -> b.add(i.ordinal()));

        return IntSets.unmodifiable(b);
    }

    private static Int2IntMap buildNormal2DeepslateCache() {
        Int2IntMap b = new Int2IntOpenHashMap();

        b.put(COAL_ORE.ordinal(), DEEPSLATE_COAL_ORE.ordinal());
        b.put(EMERALD_ORE.ordinal(), DEEPSLATE_EMERALD_ORE.ordinal());
        b.put(DIAMOND_ORE.ordinal(), DEEPSLATE_DIAMOND_ORE.ordinal());
        b.put(COPPER_ORE.ordinal(), DEEPSLATE_COPPER_ORE.ordinal());
        b.put(GOLD_ORE.ordinal(), DEEPSLATE_GOLD_ORE.ordinal());
        b.put(IRON_ORE.ordinal(), DEEPSLATE_IRON_ORE.ordinal());
        b.put(LAPIS_ORE.ordinal(), DEEPSLATE_LAPIS_ORE.ordinal());
        b.put(REDSTONE_ORE.ordinal(), DEEPSLATE_REDSTONE_ORE.ordinal());

        return b;
    }

    private static Int2IntMap buildDeepslate2NormalCache() {
        Int2IntMap b = new Int2IntOpenHashMap();

        b.put(DEEPSLATE_COAL_ORE.ordinal(), COAL_ORE.ordinal());
        b.put(DEEPSLATE_EMERALD_ORE.ordinal(), EMERALD_ORE.ordinal());
        b.put(DEEPSLATE_DIAMOND_ORE.ordinal(), DIAMOND_ORE.ordinal());
        b.put(DEEPSLATE_COPPER_ORE.ordinal(), COPPER_ORE.ordinal());
        b.put(DEEPSLATE_GOLD_ORE.ordinal(), GOLD_ORE.ordinal());
        b.put(DEEPSLATE_IRON_ORE.ordinal(), IRON_ORE.ordinal());
        b.put(DEEPSLATE_LAPIS_ORE.ordinal(), LAPIS_ORE.ordinal());
        b.put(DEEPSLATE_REDSTONE_ORE.ordinal(), REDSTONE_ORE.ordinal());

        return b;
    }

    private static IntSet buildDecorantCache() {
        IntSet b = new IntOpenHashSet();
        Arrays.stream(new Material[]{
                GRASS,
                TALL_GRASS,
                FERN,
                LARGE_FERN,
                CORNFLOWER,
                SUNFLOWER,
                CHORUS_FLOWER,
                POPPY,
                DANDELION,
                OXEYE_DAISY,
                ORANGE_TULIP,
                PINK_TULIP,
                RED_TULIP,
                WHITE_TULIP,
                LILAC,
                DEAD_BUSH,
                SWEET_BERRY_BUSH,
                ROSE_BUSH,
                WITHER_ROSE,
                ALLIUM,
                BLUE_ORCHID,
                LILY_OF_THE_VALLEY,
                CRIMSON_FUNGUS,
                WARPED_FUNGUS,
                RED_MUSHROOM,
                BROWN_MUSHROOM,
                CRIMSON_ROOTS,
                AZURE_BLUET,
                WEEPING_VINES,
                WEEPING_VINES_PLANT,
                WARPED_ROOTS,
                NETHER_SPROUTS,
                TWISTING_VINES,
                TWISTING_VINES_PLANT,
                SUGAR_CANE,
                WHEAT,
                POTATOES,
                CARROTS,
                BEETROOTS,
                NETHER_WART,
                SEA_PICKLE,
                SEAGRASS,
                ACACIA_BUTTON,
                BIRCH_BUTTON,
                CRIMSON_BUTTON,
                DARK_OAK_BUTTON,
                JUNGLE_BUTTON,
                OAK_BUTTON,
                POLISHED_BLACKSTONE_BUTTON,
                SPRUCE_BUTTON,
                STONE_BUTTON,
                WARPED_BUTTON,
                TORCH,
                SOUL_TORCH,
                GLOW_LICHEN,
                VINE,
                SCULK_VEIN
        }).forEach((i) -> b.add(i.ordinal()));
        b.addAll(foliageCache);

        return IntSets.unmodifiable(b);
    }

    private static IntSet buildLitCache() {
        IntSet b = new IntOpenHashSet();
        Arrays.stream(new Material[]{
                GLOWSTONE,
                AMETHYST_CLUSTER,
                SMALL_AMETHYST_BUD,
                MEDIUM_AMETHYST_BUD,
                LARGE_AMETHYST_BUD,
                END_ROD,
                SOUL_SAND,
                TORCH,
                REDSTONE_TORCH,
                SOUL_TORCH,
                REDSTONE_WALL_TORCH,
                WALL_TORCH,
                SOUL_WALL_TORCH,
                LANTERN,
                CANDLE,
                JACK_O_LANTERN,
                REDSTONE_LAMP,
                MAGMA_BLOCK,
                LIGHT,
                SHROOMLIGHT,
                SEA_LANTERN,
                SOUL_LANTERN,
                FIRE,
                SOUL_FIRE,
                SEA_PICKLE,
                BREWING_STAND,
                REDSTONE_ORE,
        }).forEach((i) -> b.add(i.ordinal()));

        return IntSets.unmodifiable(b);
    }

    private static IntSet buildStorageCache() {
        IntSet b = new IntOpenHashSet();
        Arrays.stream(new Material[]{
                CHEST,
                SMOKER,
                TRAPPED_CHEST,
                SHULKER_BOX,
                WHITE_SHULKER_BOX,
                ORANGE_SHULKER_BOX,
                MAGENTA_SHULKER_BOX,
                LIGHT_BLUE_SHULKER_BOX,
                YELLOW_SHULKER_BOX,
                LIME_SHULKER_BOX,
                PINK_SHULKER_BOX,
                GRAY_SHULKER_BOX,
                LIGHT_GRAY_SHULKER_BOX,
                CYAN_SHULKER_BOX,
                PURPLE_SHULKER_BOX,
                BLUE_SHULKER_BOX,
                BROWN_SHULKER_BOX,
                GREEN_SHULKER_BOX,
                RED_SHULKER_BOX,
                BLACK_SHULKER_BOX,
                BARREL,
                DISPENSER,
                DROPPER,
                HOPPER,
                FURNACE,
                BLAST_FURNACE
        }).forEach((i) -> b.add(i.ordinal()));

        return IntSets.unmodifiable(b);
    }

    public static BlockData toDeepSlateOre(BlockData block, BlockData ore) {
        int key = ore.getMaterial().ordinal();

        if (isDeepSlate(block)) {
            if (normal2DeepslateCache.containsKey(key)) {
                return Material.values()[normal2DeepslateCache.get(key)].createBlockData();
            }
        } else {
            if (deepslate2NormalCache.containsKey(key)) {
                return Material.values()[deepslate2NormalCache.get(key)].createBlockData();
            }
        }

        return ore;
    }

    public static boolean isDeepSlate(BlockData blockData) {
        return deepslateCache.contains(blockData.getMaterial().ordinal());
    }

    public static boolean isOre(BlockData blockData) {
        return blockData.getMaterial().name().endsWith("_ORE");
    }

    private static IntSet buildStorageChestCache() {
        IntSet b = new IntOpenHashSet(storageCache);
        b.remove(SMOKER.ordinal());
        b.remove(FURNACE.ordinal());
        b.remove(BLAST_FURNACE.ordinal());

        return IntSets.unmodifiable(b);
    }

    public static boolean canPlaceOnto(Material mat, Material onto) {
        if ((onto.equals(CRIMSON_NYLIUM) || onto.equals(WARPED_NYLIUM)) &&
                (mat.equals(CRIMSON_FUNGUS) || mat.equals(CRIMSON_ROOTS) || mat.equals(WARPED_FUNGUS) || mat.equals(WARPED_ROOTS))) {
            return true;
        }

        if (isFoliage(mat)) {
            if (!isFoliagePlantable(onto)) {
                return false;
            }
        }

        if (onto.equals(Material.AIR) ||
                onto.equals(B.getMaterial("CAVE_AIR"))
                || onto.equals(B.getMaterial("VOID_AIR"))) {
            return false;
        }

        if (onto.equals(Material.GRASS_BLOCK) && mat.equals(Material.DEAD_BUSH)) {
            return false;
        }

        if (onto.equals(Material.DIRT_PATH)) {
            if (!mat.isSolid()) {
                return false;
            }
        }

        if (onto.equals(Material.ACACIA_LEAVES)
                || onto.equals(Material.BIRCH_LEAVES)
                || onto.equals(Material.DARK_OAK_LEAVES)
                || onto.equals(Material.JUNGLE_LEAVES)
                || onto.equals(Material.OAK_LEAVES)
                || onto.equals(Material.SPRUCE_LEAVES)) {
            return mat.isSolid();
        }

        return true;
    }

    public static boolean isFoliagePlantable(BlockData d) {
        return d.getMaterial().equals(Material.GRASS_BLOCK)
                || d.getMaterial().equals(Material.ROOTED_DIRT)
                || d.getMaterial().equals(Material.DIRT)
                || d.getMaterial().equals(Material.COARSE_DIRT)
                || d.getMaterial().equals(Material.PODZOL);
    }

    public static boolean isFoliagePlantable(Material d) {
        return d.equals(Material.GRASS_BLOCK)
                || d.equals(Material.DIRT)
                || d.equals(TALL_GRASS)
                || d.equals(TALL_SEAGRASS)
                || d.equals(LARGE_FERN)
                || d.equals(SUNFLOWER)
                || d.equals(PEONY)
                || d.equals(LILAC)
                || d.equals(ROSE_BUSH)
                || d.equals(Material.ROOTED_DIRT)
                || d.equals(Material.COARSE_DIRT)
                || d.equals(Material.PODZOL);
    }

    public static boolean isWater(BlockData b) {
        return b.getMaterial().equals(Material.WATER);
    }

    public static BlockData getAir() {
        return AIR;
    }

    public static Material getMaterialOrNull(String bdx) {
        try {
            return Material.valueOf(bdx.trim().toUpperCase());
        } catch (Throwable e) {
            Iris.reportError(e);
            if (clw.flip()) {
                Iris.warn("Unknown Material: " + bdx);
            }
            return null;
        }
    }

    public static Material getMaterial(String bdx) {
        Material m = getMaterialOrNull(bdx);

        if (m == null) {
            return AIR_MATERIAL;
        }

        return m;
    }

    public static boolean isSolid(BlockData mat) {
        if (mat == null)
            return false;
        return mat.getMaterial().isSolid();
    }

    public static BlockData getOrNull(String bdxf) {
        try {
            String bd = bdxf.trim();

            if (!custom.isEmpty() && custom.containsKey(bd)) {
                return custom.get(bd);
            }

            if (bd.startsWith("minecraft:cauldron[level=")) {
                bd = bd.replaceAll("\\Q:cauldron[\\E", ":water_cauldron[");
            }

            if (bd.equals("minecraft:grass_path")) {
                return DIRT_PATH.createBlockData();
            }

            BlockData bdx = parseBlockData(bd);

            if (bdx == null) {
                if (clw.flip()) {
                    Iris.warn("Unknown Block Data '" + bd + "'");
                }
                return AIR;
            }

            return bdx;
        } catch (Throwable e) {
            Iris.reportError(e);

            if (clw.flip()) {
                Iris.warn("Unknown Block Data '" + bdxf + "'");
            }
        }

        return null;
    }

    public static BlockData get(String bdxf) {
        BlockData bd = getOrNull(bdxf);

        if (bd != null) {
            return bd;
        }

        return AIR;
    }

    private static synchronized BlockData createBlockData(String s) {
        try {
            return Bukkit.createBlockData(s);
        } catch (IllegalArgumentException e) {
            if (s.contains("[")) {
                return createBlockData(s.split("\\Q[\\E")[0]);
            }
        }

        Iris.error("Can't find block data for " + s);
        return null;
    }

    private static BlockData parseBlockData(String ix) {
        try {
            BlockData bx = null;

            if (!ix.startsWith("minecraft:") && ix.contains(":")) {
                Identifier key = Identifier.fromString(ix);
                Optional<BlockData> bd = Iris.service(ExternalDataSVC.class).getBlockData(key);
                Iris.info("Loading block data " + key);
                if (bd.isPresent())
                    bx = bd.get();
            }

            if (bx == null) {
                try {
                    bx = createBlockData(ix.toLowerCase());
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }

            if (bx == null) {
                try {
                    bx = createBlockData("minecraft:" + ix.toLowerCase());
                } catch (Throwable e) {

                }
            }

            if (bx == null) {
                try {
                    bx = Material.valueOf(ix.toUpperCase()).createBlockData();
                } catch (Throwable e) {

                }
            }

            if (bx == null) {
                return null;
            }

            if (bx instanceof Leaves && IrisSettings.get().getGenerator().isPreventLeafDecay()) {
                ((Leaves) bx).setPersistent(true);
            } else if (bx instanceof Leaves) {
                ((Leaves) bx).setPersistent(false);
            }

            return bx;
        } catch (Throwable e) {
            if (clw.flip()) {
                Iris.warn("Unknown Block Data: " + ix);
            }

            String block = ix.contains(":") ? ix.split(":")[1].toLowerCase() : ix.toLowerCase();
            String state = block.contains("[") ? block.split("\\Q[\\E")[1].split("\\Q]\\E")[0] : "";
            Map<String, String> stateMap = new HashMap<>();
            if (!state.equals("")) {
                Arrays.stream(state.split(",")).forEach(s -> stateMap.put(s.split("=")[0], s.split("=")[1]));
            }
            block = block.split("\\Q[\\E")[0];

            switch (block) {
                case "cauldron" -> block = "water_cauldron";
                case "grass_path" -> block = "dirt_path";
                case "concrete" -> block = "white_concrete";
                case "wool" -> block = "white_wool";
                case "beetroots" -> {
                    if (stateMap.containsKey("age")) {
                        String updated = stateMap.get("age");
                        switch (updated) {
                            case "7" -> updated = "3";
                            case "3", "4", "5" -> updated = "2";
                            case "1", "2" -> updated = "1";
                        }
                        stateMap.put("age", updated);
                    }
                }
            }

            Map<String, String> newStates = new HashMap<>();
            for (String key : stateMap.keySet()) { //Iterate through every state and check if its valid
                try {
                    String newState = block + "[" + key + "=" + stateMap.get(key) + "]";
                    createBlockData(newState);
                    newStates.put(key, stateMap.get(key));

                } catch (IllegalArgumentException ignored) {
                }
            }

            //Combine all the "good" states again
            state = newStates.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).collect(Collectors.joining(","));
            if (!state.equals("")) state = "[" + state + "]";
            String newBlock = block + state;
            Iris.debug("Converting " + ix + " to " + newBlock);

            try {
                return createBlockData(newBlock);
            } catch (Throwable e1) {
                Iris.reportError(e1);
            }

            return null;
        }
    }

    public static boolean isStorage(BlockData mat) {
        return storageCache.contains(mat.getMaterial().ordinal());
    }

    public static boolean isStorageChest(BlockData mat) {
        return storageChestCache.contains(mat.getMaterial().ordinal());
    }

    public static boolean isLit(BlockData mat) {
        return litCache.contains(mat.getMaterial().ordinal());
    }

    public static boolean isUpdatable(BlockData mat) {
        return isLit(mat)
                || isStorage(mat)
                || (mat instanceof PointedDripstone
                && ((PointedDripstone) mat).getThickness().equals(PointedDripstone.Thickness.TIP));
    }

    public static boolean isFoliage(Material d) {
        return foliageCache.contains(d.ordinal());
    }

    public static boolean isFoliage(BlockData d) {
        return isFoliage(d.getMaterial());
    }

    public static boolean isDecorant(BlockData m) {
        return decorantCache.contains(m.getMaterial().ordinal());
    }

    public static KList<BlockData> get(KList<String> find) {
        KList<BlockData> b = new KList<>();

        for (String i : find) {
            BlockData bd = get(i);

            if (bd != null) {
                b.add(bd);
            }
        }

        return b;
    }

    public static boolean isFluid(BlockData d) {
        return d.getMaterial().equals(Material.WATER) || d.getMaterial().equals(Material.LAVA);
    }

    public static boolean isAirOrFluid(BlockData d) {
        return isAir(d) || isFluid(d);
    }

    public static boolean isAir(BlockData d) {
        if (d == null) {
            return true;
        }

        return d.getMaterial().equals(Material.AIR) || d.getMaterial().equals(Material.CAVE_AIR) || d.getMaterial().equals(Material.VOID_AIR);
    }


    public synchronized static String[] getBlockTypes() {
        KList<String> bt = new KList<>();

        for (Material i : Material.values()) {
            if (i.isBlock()) {
                String v = i.createBlockData().getAsString(true);

                if (v.contains("[")) {
                    v = v.split("\\Q[\\E")[0];
                }

                bt.add(v);
            }
        }

        for (Identifier id : Iris.service(ExternalDataSVC.class).getAllBlockIdentifiers())
            bt.add(id.toString());
        bt.addAll(custom.k());

        return bt.toArray(new String[0]);
    }

    public static String[] getItemTypes() {
        KList<String> bt = new KList<>();

        for (Material i : Material.values()) {
            String v = i.name().toLowerCase().trim();
            bt.add(v);
        }

        for (Identifier id : Iris.service(ExternalDataSVC.class).getAllItemIdentifiers())
            bt.add(id.toString());

        return bt.toArray(new String[0]);
    }

    public static boolean isWaterLogged(BlockData b) {
        return (b instanceof Waterlogged) && ((Waterlogged) b).isWaterlogged();
    }

    public static void registerCustomBlockData(String namespace, String key, BlockData blockData) {
        custom.put(namespace + ":" + key, blockData);
    }

    public static boolean isVineBlock(BlockData data) {
        return switch (data.getMaterial()) {
            case VINE, SCULK_VEIN, GLOW_LICHEN -> true;
            default -> false;
        };
    }
}
