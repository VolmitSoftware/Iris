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

package com.volmit.iris.util.data;

import com.volmit.iris.Iris;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.collection.KSet;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class B {
    private static final Material AIR_MATERIAL = Material.AIR;
    private static final BlockData AIR = AIR_MATERIAL.createBlockData();
    private static final KSet<String> nullBlockDataCache = new KSet<>();
    private static final KSet<String> nullMaterialCache = new KSet<>();
    private static final KMap<Material, Boolean> solidCache = new KMap<>();
    private static final KMap<Material, Boolean> updatableCache = new KMap<>();
    private static final KMap<Material, Boolean> foliageCache = new KMap<>();
    private static final KMap<Material, Boolean> litCache = new KMap<>();
    private static final KMap<Material, Boolean> decorantCache = new KMap<>();
    private static final KMap<Material, Boolean> storageCache = new KMap<>();
    private static final KMap<Material, Boolean> storageChestCache = new KMap<>();
    private static final KMap<String, BlockData> blockDataCache = new KMap<>();
    private static final KMap<String, Material> materialCache = new KMap<>();

    public static boolean isWater(BlockData b) {
        return b.getMaterial().equals(Material.WATER);
    }

    public static BlockData getAir() {
        return AIR;
    }

    public static Material getMaterial(String bdx) {
        Material mat = getMaterialOrNull(bdx);

        if (mat != null) {
            return mat;
        }

        return AIR_MATERIAL;
    }

    public static Material getMaterialOrNull(String bdxx) {
        String bx = bdxx.trim().toUpperCase();

        if (nullMaterialCache.contains(bx)) {
            return null;
        }

        Material mat = materialCache.get(bx);

        if (mat != null) {
            return mat;
        }

        try {
            Material mm = Material.valueOf(bx);
            materialCache.put(bx, mm);
            return mm;
        } catch (Throwable e) {
            Iris.reportError(e);
            nullMaterialCache.add(bx);
            return null;
        }
    }

    public static boolean isSolid(BlockData mat) {
        return isSolid(mat.getMaterial());
    }

    public static boolean isSolid(Material mat) {
        Boolean solid = solidCache.get(mat);

        if (solid != null) {
            return solid;
        }

        solid = mat.isSolid();
        solidCache.put(mat, solid);

        return solid;
    }

    public static BlockData getOrNull(String bdxf) {
        try {
            String bd = bdxf.trim();
            BlockData bdx = parseBlockData(bd);

            if (bdx == null) {
                Iris.warn("Unknown Block Data '" + bd + "'");
                return AIR;
            }

            return bdx;
        } catch (Throwable e) {
            Iris.reportError(e);
            Iris.warn("Unknown Block Data '" + bdxf + "'");
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

    private static BlockData parseBlockDataOrNull(String ix) {
        if (nullBlockDataCache.contains(ix)) {
            return null;
        }

        try {
            BlockData bb = blockDataCache.get(ix);

            if (bb != null) {
                return bb;
            }
            BlockData bx = null;

            if (ix.startsWith("oraxen:") && Iris.linkOraxen.supported()) {
                bx = Iris.linkOraxen.getBlockDataFor(ix.split("\\Q:\\E")[1]);
            }

            if (bx == null) {
                bx = Bukkit.createBlockData(ix);
            }

            if (bx instanceof Leaves) {
                ((Leaves) bx).setPersistent(true);
            }

            blockDataCache.put(ix, bx);
            return bx;
        } catch (Exception e) {
            //Iris.reportError(e);
            Iris.debug("Failed to load block \"" + ix + "\"");

            String block = ix.contains(":") ? ix.split(":")[1].toLowerCase() : ix.toLowerCase();
            String state = block.contains("[") ? block.split("\\[")[1].split("\\]")[0] : "";
            Map<String, String> stateMap = new HashMap<>();
            if (!state.equals("")) {
                Arrays.stream(state.split(",")).forEach(s -> {
                    stateMap.put(s.split("=")[0], s.split("=")[1]);
                });
            }
            block = block.split("\\[")[0];

            switch (block) {
                case "cauldron" -> block = "water_cauldron"; //Would fail to load if it has a level parameter
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
                    Bukkit.createBlockData(newState);

                    //If we get to here, the state is okay so we can use it
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
                BlockData bd = Bukkit.createBlockData(newBlock);
                blockDataCache.put(ix, bd);
                return bd;
            } catch (Throwable e1) {
                Iris.reportError(e1);
            }

            nullBlockDataCache.add(ix);
            return null;
        }
    }

    private static BlockData parseBlockData(String ix) {
        BlockData bd = parseBlockDataOrNull(ix);

        if (bd != null) {
            return bd;
        }

        Iris.warn("Unknown Block Data: " + ix);

        return AIR;
    }

    public static boolean isStorage(BlockData mat) {
        Material mm = mat.getMaterial();
        Boolean f = storageCache.get(mm);

        if (f != null) {
            return f;
        }

        f = mm.equals(B.getMaterial("CHEST"))
                || mm.equals(B.getMaterial("TRAPPED_CHEST"))
                || mm.equals(B.getMaterial("SHULKER_BOX"))
                || mm.equals(B.getMaterial("WHITE_SHULKER_BOX"))
                || mm.equals(B.getMaterial("ORANGE_SHULKER_BOX"))
                || mm.equals(B.getMaterial("MAGENTA_SHULKER_BOX"))
                || mm.equals(B.getMaterial("LIGHT_BLUE_SHULKER_BOX"))
                || mm.equals(B.getMaterial("YELLOW_SHULKER_BOX"))
                || mm.equals(B.getMaterial("LIME_SHULKER_BOX"))
                || mm.equals(B.getMaterial("PINK_SHULKER_BOX"))
                || mm.equals(B.getMaterial("GRAY_SHULKER_BOX"))
                || mm.equals(B.getMaterial("LIGHT_GRAY_SHULKER_BOX"))
                || mm.equals(B.getMaterial("CYAN_SHULKER_BOX"))
                || mm.equals(B.getMaterial("PURPLE_SHULKER_BOX"))
                || mm.equals(B.getMaterial("BLUE_SHULKER_BOX"))
                || mm.equals(B.getMaterial("BROWN_SHULKER_BOX"))
                || mm.equals(B.getMaterial("GREEN_SHULKER_BOX"))
                || mm.equals(B.getMaterial("RED_SHULKER_BOX"))
                || mm.equals(B.getMaterial("BLACK_SHULKER_BOX"))
                || mm.equals(B.getMaterial("BARREL"))
                || mm.equals(B.getMaterial("DISPENSER"))
                || mm.equals(B.getMaterial("DROPPER"))
                || mm.equals(B.getMaterial("HOPPER"))
                || mm.equals(B.getMaterial("FURNACE"))
                || mm.equals(B.getMaterial("BLAST_FURNACE"))
                || mm.equals(B.getMaterial("SMOKER"));
        storageCache.put(mm, f);
        return f;
    }

    public static boolean isStorageChest(BlockData mat) {
        if (!isStorage(mat)) {
            return false;
        }

        Material mm = mat.getMaterial();
        Boolean f = storageChestCache.get(mm);

        if (f != null) {
            return f;
        }

        f = mm.equals(B.getMaterial("CHEST"))
                || mm.equals(B.getMaterial("TRAPPED_CHEST"))
                || mm.equals(B.getMaterial("SHULKER_BOX"))
                || mm.equals(B.getMaterial("WHITE_SHULKER_BOX"))
                || mm.equals(B.getMaterial("ORANGE_SHULKER_BOX"))
                || mm.equals(B.getMaterial("MAGENTA_SHULKER_BOX"))
                || mm.equals(B.getMaterial("LIGHT_BLUE_SHULKER_BOX"))
                || mm.equals(B.getMaterial("YELLOW_SHULKER_BOX"))
                || mm.equals(B.getMaterial("LIME_SHULKER_BOX"))
                || mm.equals(B.getMaterial("PINK_SHULKER_BOX"))
                || mm.equals(B.getMaterial("GRAY_SHULKER_BOX"))
                || mm.equals(B.getMaterial("LIGHT_GRAY_SHULKER_BOX"))
                || mm.equals(B.getMaterial("CYAN_SHULKER_BOX"))
                || mm.equals(B.getMaterial("PURPLE_SHULKER_BOX"))
                || mm.equals(B.getMaterial("BLUE_SHULKER_BOX"))
                || mm.equals(B.getMaterial("BROWN_SHULKER_BOX"))
                || mm.equals(B.getMaterial("GREEN_SHULKER_BOX"))
                || mm.equals(B.getMaterial("RED_SHULKER_BOX"))
                || mm.equals(B.getMaterial("BLACK_SHULKER_BOX"))
                || mm.equals(B.getMaterial("BARREL"))
                || mm.equals(B.getMaterial("DISPENSER"))
                || mm.equals(B.getMaterial("DROPPER"))
                || mm.equals(B.getMaterial("HOPPER"));
        storageChestCache.put(mm, f);
        return f;
    }

    public static boolean isLit(BlockData mat) {
        Material mm = mat.getMaterial();
        Boolean f = litCache.get(mm);

        if (f != null) {
            return f;
        }

        f = mm.equals(B.getMaterial("GLOWSTONE"))
                || mm.equals(B.getMaterial("END_ROD"))
                || mm.equals(B.getMaterial("SOUL_SAND"))
                || mm.equals(B.getMaterial("TORCH"))
                || mm.equals(Material.REDSTONE_TORCH)
                || mm.equals(B.getMaterial("SOUL_TORCH"))
                || mm.equals(Material.REDSTONE_WALL_TORCH)
                || mm.equals(Material.WALL_TORCH)
                || mm.equals(B.getMaterial("SOUL_WALL_TORCH"))
                || mm.equals(B.getMaterial("LANTERN"))
                || mm.equals(Material.JACK_O_LANTERN)
                || mm.equals(Material.REDSTONE_LAMP)
                || mm.equals(Material.MAGMA_BLOCK)
                || mm.equals(B.getMaterial("SHROOMLIGHT"))
                || mm.equals(B.getMaterial("SEA_LANTERN"))
                || mm.equals(B.getMaterial("SOUL_LANTERN"))
                || mm.equals(Material.FIRE)
                || mm.equals(B.getMaterial("SOUL_FIRE"))
                || mm.equals(B.getMaterial("SEA_PICKLE"))
                || mm.equals(Material.BREWING_STAND)
                || mm.equals(Material.REDSTONE_ORE);
        litCache.put(mm, f);
        return f;
    }

    public static boolean isUpdatable(BlockData mat) {
        Boolean u = updatableCache.get(mat.getMaterial());

        if (u != null) {
            return u;
        }

        u = isLit(mat) || isStorage(mat);
        updatableCache.put(mat.getMaterial(), u);
        return u;
    }

    public static boolean isFoliage(Material d) {
        return isFoliage(d.createBlockData());
    }

    public static boolean isFoliage(BlockData d) {
        Boolean f = foliageCache.get(d.getMaterial());
        if (f != null) {
            return f;
        }

        if (isFluid(d) || isAir(d) || isSolid(d)) {
            foliageCache.put(d.getMaterial(), false);
            return false;
        }

        Material mat = d.getMaterial();
        f = mat.equals(Material.POPPY)
                || mat.equals(Material.DANDELION)
                || mat.equals(B.getMaterial("CORNFLOWER"))
                || mat.equals(B.getMaterial("SWEET_BERRY_BUSH"))
                || mat.equals(B.getMaterial("CRIMSON_ROOTS"))
                || mat.equals(B.getMaterial("WARPED_ROOTS"))
                || mat.equals(B.getMaterial("NETHER_SPROUTS"))
                || mat.equals(B.getMaterial("ALLIUM"))
                || mat.equals(B.getMaterial("AZURE_BLUET"))
                || mat.equals(B.getMaterial("BLUE_ORCHID"))
                || mat.equals(B.getMaterial("POPPY"))
                || mat.equals(B.getMaterial("DANDELION"))
                || mat.equals(B.getMaterial("OXEYE_DAISY"))
                || mat.equals(B.getMaterial("LILY_OF_THE_VALLEY"))
                || mat.equals(B.getMaterial("WITHER_ROSE"))
                || mat.equals(Material.DARK_OAK_SAPLING)
                || mat.equals(Material.ACACIA_SAPLING)
                || mat.equals(Material.JUNGLE_SAPLING)
                || mat.equals(Material.BIRCH_SAPLING)
                || mat.equals(Material.SPRUCE_SAPLING)
                || mat.equals(Material.OAK_SAPLING)
                || mat.equals(Material.ORANGE_TULIP)
                || mat.equals(Material.PINK_TULIP)
                || mat.equals(Material.RED_TULIP)
                || mat.equals(Material.WHITE_TULIP)
                || mat.equals(Material.FERN)
                || mat.equals(Material.LARGE_FERN)
                || mat.equals(Material.GRASS)
                || mat.equals(Material.TALL_GRASS);
        foliageCache.put(d.getMaterial(), f);
        return f;
    }

    public static boolean canPlaceOnto(Material mat, Material onto) {
        String key = mat.name() + "" + onto.name();

        if (isFoliage(mat)) {
            if (!isFoliagePlantable(onto)) {
                return false;
            }
        }

        if (onto.equals(Material.AIR) || onto.equals(B.getMaterial("CAVE_AIR")) || onto.equals(B.getMaterial("VOID_AIR"))) {
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

    public static boolean isDecorant(BlockData m) {
        Material mm = m.getMaterial();
        Boolean f = decorantCache.get(mm);

        if (f != null) {
            return f;
        }

        f = mm.equals(Material.GRASS)
                || mm.equals(Material.TALL_GRASS)
                || mm.equals(Material.FERN)
                || mm.equals(Material.LARGE_FERN)
                || mm.equals(B.getMaterial("CORNFLOWER"))
                || mm.equals(Material.SUNFLOWER)
                || mm.equals(Material.CHORUS_FLOWER)
                || mm.equals(Material.POPPY)
                || mm.equals(Material.DANDELION)
                || mm.equals(Material.OXEYE_DAISY)
                || mm.equals(Material.ORANGE_TULIP)
                || mm.equals(Material.PINK_TULIP)
                || mm.equals(Material.RED_TULIP)
                || mm.equals(Material.WHITE_TULIP)
                || mm.equals(Material.LILAC)
                || mm.equals(Material.DEAD_BUSH)
                || mm.equals(B.getMaterial("SWEET_BERRY_BUSH"))
                || mm.equals(Material.ROSE_BUSH)
                || mm.equals(B.getMaterial("WITHER_ROSE"))
                || mm.equals(Material.ALLIUM)
                || mm.equals(Material.BLUE_ORCHID)
                || mm.equals(B.getMaterial("LILY_OF_THE_VALLEY"))
                || mm.equals(B.getMaterial("CRIMSON_FUNGUS"))
                || mm.equals(B.getMaterial("WARPED_FUNGUS"))
                || mm.equals(Material.RED_MUSHROOM)
                || mm.equals(Material.BROWN_MUSHROOM)
                || mm.equals(B.getMaterial("CRIMSON_ROOTS"))
                || mm.equals(B.getMaterial("AZURE_BLUET"))
                || mm.equals(B.getMaterial("WEEPING_VINES"))
                || mm.equals(B.getMaterial("WEEPING_VINES_PLANT"))
                || mm.equals(B.getMaterial("WARPED_ROOTS"))
                || mm.equals(B.getMaterial("NETHER_SPROUTS"))
                || mm.equals(B.getMaterial("TWISTING_VINES"))
                || mm.equals(B.getMaterial("TWISTING_VINES_PLANT"))
                || mm.equals(Material.SUGAR_CANE)
                || mm.equals(Material.WHEAT)
                || mm.equals(Material.POTATOES)
                || mm.equals(Material.CARROTS)
                || mm.equals(Material.BEETROOTS)
                || mm.equals(Material.NETHER_WART)
                || mm.equals(B.getMaterial("SEA_PICKLE"))
                || mm.equals(B.getMaterial("SEAGRASS"))
                || mm.equals(B.getMaterial("ACACIA_BUTTON"))
                || mm.equals(B.getMaterial("BIRCH_BUTTON"))
                || mm.equals(B.getMaterial("CRIMSON_BUTTON"))
                || mm.equals(B.getMaterial("DARK_OAK_BUTTON"))
                || mm.equals(B.getMaterial("JUNGLE_BUTTON"))
                || mm.equals(B.getMaterial("OAK_BUTTON"))
                || mm.equals(B.getMaterial("POLISHED_BLACKSTONE_BUTTON"))
                || mm.equals(B.getMaterial("SPRUCE_BUTTON"))
                || mm.equals(B.getMaterial("STONE_BUTTON"))
                || mm.equals(B.getMaterial("WARPED_BUTTON"))
                || mm.equals(Material.TORCH)
                || mm.equals(B.getMaterial("SOUL_TORCH"));
        decorantCache.put(mm, f);
        return f;
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
                || d.equals(Material.ROOTED_DIRT)
                || d.equals(Material.COARSE_DIRT)
                || d.equals(Material.PODZOL);
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


    public static String[] getBlockTypes() {
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

        try
        {
            for (String i : Iris.linkOraxen.getItemTypes()) {
                bt.add("oraxen:" + i);
            }
        }

        catch(Throwable e)
        {
            e.printStackTrace();
        }

        return bt.toArray(new String[0]);
    }

    public static String[] getItemTypes() {
        KList<String> bt = new KList<>();

        for (Material i : Material.values()) {
            String v = i.name().toLowerCase().trim();
            bt.add(v);
        }

        return bt.toArray(new String[0]);
    }
}
