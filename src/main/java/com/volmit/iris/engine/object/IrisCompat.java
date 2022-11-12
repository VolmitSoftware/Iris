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

package com.volmit.iris.engine.object;

import com.google.gson.Gson;
import com.volmit.iris.Iris;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.data.B;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.json.JSONObject;
import com.volmit.iris.util.scheduling.J;
import lombok.Data;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.io.File;
import java.io.IOException;

@Data
public class IrisCompat {
    private KList<IrisCompatabilityBlockFilter> blockFilters;
    private KList<IrisCompatabilityItemFilter> itemFilters;

    public IrisCompat() {
        blockFilters = getDefaultBlockCompatabilityFilters();
        itemFilters = getDefaultItemCompatabilityFilters();
    }

    public static IrisCompat configured(File f) {
        IrisCompat def = new IrisCompat();
        String defa = new JSONObject(new Gson().toJson(def)).toString(4);
        J.attemptAsync(() -> IO.writeAll(new File(f.getParentFile(), "compat.default.json"), defa));


        if (!f.exists()) {
            J.a(() -> {
                try {
                    IO.writeAll(f, defa);
                } catch (IOException e) {
                    Iris.error("Failed to writeNodeData to compat file");
                    Iris.reportError(e);
                }
            });
        } else {
            // If the file doesn't exist, no additional mappings are present outside default
            // so we shouldn't try getting them
            try {
                IrisCompat rea = new Gson().fromJson(IO.readAll(f), IrisCompat.class);

                for (IrisCompatabilityBlockFilter i : rea.getBlockFilters()) {
                    def.getBlockFilters().add(i);
                }

                for (IrisCompatabilityItemFilter i : rea.getItemFilters()) {
                    def.getItemFilters().add(i);
                }
            } catch (Throwable e) {
                e.printStackTrace();
                Iris.reportError(e);
            }
        }

        return def;
    }

    private static KList<IrisCompatabilityItemFilter> getDefaultItemCompatabilityFilters() {
        KList<IrisCompatabilityItemFilter> filters = new KList<>();

        // Below 1.16
        filters.add(new IrisCompatabilityItemFilter("NETHERITE_HELMET", "DIAMOND_HELMET"));
        filters.add(new IrisCompatabilityItemFilter("NETHERITE_CHESTPLATE", "DIAMOND_CHESTPLATE"));
        filters.add(new IrisCompatabilityItemFilter("NETHERITE_BOOTS", "DIAMOND_BOOTS"));
        filters.add(new IrisCompatabilityItemFilter("NETHERITE_LEGGINGS", "DIAMOND_LEGGINGS"));
        filters.add(new IrisCompatabilityItemFilter("MUSIC_DISC_PIGSTEP", "MUSIC_DISC_FAR"));
        filters.add(new IrisCompatabilityItemFilter("NETHERITE_SWORD", "DIAMOND_SWORD"));
        filters.add(new IrisCompatabilityItemFilter("NETHERITE_AXE", "DIAMOND_AXE"));
        filters.add(new IrisCompatabilityItemFilter("NETHERITE_PICKAXE", "DIAMOND_PICKAXE"));
        filters.add(new IrisCompatabilityItemFilter("NETHERITE_HOE", "DIAMOND_HOE"));
        filters.add(new IrisCompatabilityItemFilter("NETHERITE_SHOVEL", "DIAMOND_SHOVEL"));
        filters.add(new IrisCompatabilityItemFilter("NETHERITE_INGOT", "DIAMOND"));
        filters.add(new IrisCompatabilityItemFilter("PIGLIN_BANNER_PATTERN", "PORKCHOP"));
        filters.add(new IrisCompatabilityItemFilter("NETHERITE_SCRAP", "GOLD_INGOT"));
        filters.add(new IrisCompatabilityItemFilter("WARPED_FUNGUS_ON_A_STICK", "CARROT_ON_A_STICK"));

        // Below 1.15
        filters.add(new IrisCompatabilityItemFilter("HONEY_BOTTLE", "GLASS_BOTTLE"));
        filters.add(new IrisCompatabilityItemFilter("HONEYCOMB", "GLASS"));

        // Below 1.14
        filters.add(new IrisCompatabilityItemFilter("SWEET_BERRIES", "APPLE"));
        filters.add(new IrisCompatabilityItemFilter("SUSPICIOUS_STEW", "MUSHROOM_STEW"));
        filters.add(new IrisCompatabilityItemFilter("BLACK_DYE", "INK_SAC"));
        filters.add(new IrisCompatabilityItemFilter("WHITE_DYE", "BONE_MEAL"));
        filters.add(new IrisCompatabilityItemFilter("BROWN_DYE", "COCOA_BEANS"));
        filters.add(new IrisCompatabilityItemFilter("BLUE_DYE", "LAPIS_LAZULI"));
        filters.add(new IrisCompatabilityItemFilter("CROSSBOW", "BOW"));
        filters.add(new IrisCompatabilityItemFilter("FLOWER_BANNER_PATTERN", "CORNFLOWER"));
        filters.add(new IrisCompatabilityItemFilter("SKULL_BANNER_PATTERN", "BONE"));
        filters.add(new IrisCompatabilityItemFilter("GLOBE_BANNER_PATTERN", "WHEAT_SEEDS"));
        filters.add(new IrisCompatabilityItemFilter("MOJANG_BANNER_PATTERN", "DIRT"));
        filters.add(new IrisCompatabilityItemFilter("CREEPER_BANNER_PATTERN", "CREEPER_HEAD"));

        return filters;
    }

    private static KList<IrisCompatabilityBlockFilter> getDefaultBlockCompatabilityFilters() {
        KList<IrisCompatabilityBlockFilter> filters = new KList<>();

        // Below 1.16
        filters.add(new IrisCompatabilityBlockFilter("WEEPING_VINES", "NETHER_FENCE"));
        filters.add(new IrisCompatabilityBlockFilter("WEEPING_VINES_PLANT", "NETHER_FENCE"));
        filters.add(new IrisCompatabilityBlockFilter("WARPED_WART_BLOCK", "NETHER_WART_BLOCK"));
        filters.add(new IrisCompatabilityBlockFilter("TWISTING_VINES", "BAMBOO"));
        filters.add(new IrisCompatabilityBlockFilter("TWISTING_VINES_PLANT", "BAMBOO"));
        filters.add(new IrisCompatabilityBlockFilter("TARGET", "COBBLESTONE"));
        filters.add(new IrisCompatabilityBlockFilter("SOUL_SOIL", "SOULSAND"));
        filters.add(new IrisCompatabilityBlockFilter("SOUL_TORCH", "TORCH"));
        filters.add(new IrisCompatabilityBlockFilter("SOUL_LANTERN", "LANTERN"));
        filters.add(new IrisCompatabilityBlockFilter("SOUL_FIRE", "FIRE"));
        filters.add(new IrisCompatabilityBlockFilter("SOUL_CAMPFIRE", "CAMPFIRE"));
        filters.add(new IrisCompatabilityBlockFilter("SHROOMLIGHT", "GLOWSTONE"));
        filters.add(new IrisCompatabilityBlockFilter("RESPAWN_ANCHOR", "OBSIDIAN"));
        filters.add(new IrisCompatabilityBlockFilter("NETHER_SPROUTS", "RED_MUSHROOM"));
        filters.add(new IrisCompatabilityBlockFilter("NETHER_GOLD_ORE", "GOLD_ORE"));
        filters.add(new IrisCompatabilityBlockFilter("LODESTONE", "STONE"));
        filters.add(new IrisCompatabilityBlockFilter("STRIPPED_WARPED_HYPHAE", "BROWN_MUSHROOM_BLOCK"));
        filters.add(new IrisCompatabilityBlockFilter("STRIPPED_CRIMSON_HYPHAE", "RED_MUSHROOM_BLOCK"));
        filters.add(new IrisCompatabilityBlockFilter("WARPED_HYPHAE", "MUSHROOM_STEM"));
        filters.add(new IrisCompatabilityBlockFilter("CRIMSON_HYPHAE", "RED_MUSHROOM_BLOCK"));
        filters.add(new IrisCompatabilityBlockFilter("GILDED_BLACKSTONE", "COBBLESTONE"));
        filters.add(new IrisCompatabilityBlockFilter("CRYING_OBSIDIAN", "OBSIDIAN"));
        filters.add(new IrisCompatabilityBlockFilter("STRIPPED_WARPED_STEM", "MUSHROOM_STEM"));
        filters.add(new IrisCompatabilityBlockFilter("STRIPPED_CRIMSON_STEM", "MUSHROOM_STEM"));
        filters.add(new IrisCompatabilityBlockFilter("WARPED_STEM", "MUSHROOM_STEM"));
        filters.add(new IrisCompatabilityBlockFilter("CRIMSON_STEM", "MUSHROOM_STEM"));
        filters.add(new IrisCompatabilityBlockFilter("CRIMSON_ROOTS", "RED_MUSHROOM"));
        filters.add(new IrisCompatabilityBlockFilter("WARPED_ROOTS", "BROWN_MUSHROOM"));
        filters.add(new IrisCompatabilityBlockFilter("CRIMSON_PLANKS", "OAK_PLANKS"));
        filters.add(new IrisCompatabilityBlockFilter("WARPED_PLANKS", "OAK_PLANKS"));
        filters.add(new IrisCompatabilityBlockFilter("WARPED_NYLIUM", "MYCELIUM"));
        filters.add(new IrisCompatabilityBlockFilter("CRIMSON_NYLIUM", "MYCELIUM"));
        filters.add(new IrisCompatabilityBlockFilter("WARPED_FUNGUS", "BROWN_MUSHROOM"));
        filters.add(new IrisCompatabilityBlockFilter("CRIMSON_FUNGUS", "RED_MUSHROOM"));
        filters.add(new IrisCompatabilityBlockFilter("CRACKED_NETHER_BRICKS", "NETHER_BRICKS"));
        filters.add(new IrisCompatabilityBlockFilter("CHISELED_NETHER_BRICKS", "NETHER_BRICKS"));
        filters.add(new IrisCompatabilityBlockFilter("NETHER_FENCE", "LEGACY_NETHER_FENCE"));
        filters.add(new IrisCompatabilityBlockFilter("CHAIN", "IRON_BARS"));
        filters.add(new IrisCompatabilityBlockFilter("NETHERITE_BLOCK", "QUARTZ_BLOCK"));
        filters.add(new IrisCompatabilityBlockFilter("BLACKSTONE", "COBBLESTONE"));
        filters.add(new IrisCompatabilityBlockFilter("BASALT", "STONE"));
        filters.add(new IrisCompatabilityBlockFilter("ANCIENT_DEBRIS", "NETHERRACK"));
        filters.add(new IrisCompatabilityBlockFilter("NETHERRACK", "LEGACY_NETHERRACK"));

        // Below 1.15
        filters.add(new IrisCompatabilityBlockFilter("HONEY_BLOCK", "OAK_LEAVES"));
        filters.add(new IrisCompatabilityBlockFilter("BEEHIVE", "OAK_LEAVES"));
        filters.add(new IrisCompatabilityBlockFilter("BEE_NEST", "OAK_LEAVES"));

        // Below 1.14
        filters.add(new IrisCompatabilityBlockFilter("GRANITE_WALL", "COBBLESTONE_WALL"));
        filters.add(new IrisCompatabilityBlockFilter("BLUE_ICE", "PACKED_ICE"));
        filters.add(new IrisCompatabilityBlockFilter("DIORITE_WALL", "COBBLESTONE_WALL"));
        filters.add(new IrisCompatabilityBlockFilter("ANDESITE_WALL", "COBBLESTONE_WALL"));
        filters.add(new IrisCompatabilityBlockFilter("SWEET_BERRY_BUSH", "GRASS"));
        filters.add(new IrisCompatabilityBlockFilter("STONECUTTER", "CRAFTING_TABLE"));
        filters.add(new IrisCompatabilityBlockFilter("SANDSTONE_STAIRS", "LEGACY_SANDSTONE_STAIRS"));
        filters.add(new IrisCompatabilityBlockFilter("SMOOTH_SANDSTONE_STAIRS", "LEGACY_SANDSTONE_STAIRS"));
        filters.add(new IrisCompatabilityBlockFilter("MOSSY_COBBLESTONE_STAIRS", "COBBLESTONE_STAIRS"));
        filters.add(new IrisCompatabilityBlockFilter("MOSSY_STONE_BRICK_STAIRS", "STONE_BRICK_STAIRS"));
        filters.add(new IrisCompatabilityBlockFilter("POLISHED_GRANITE_STAIRS", "COBBLESTONE_STAIRS"));
        filters.add(new IrisCompatabilityBlockFilter("GRANITE_STAIRS", "COBBLESTONE_STAIRS"));
        filters.add(new IrisCompatabilityBlockFilter("POLISHED_DIORITE_STAIRS", "COBBLESTONE_STAIRS"));
        filters.add(new IrisCompatabilityBlockFilter("DIORITE_STAIRS", "COBBLESTONE_STAIRS"));
        filters.add(new IrisCompatabilityBlockFilter("POLISHED_ANDESITE_STAIRS", "COBBLESTONE_STAIRS"));
        filters.add(new IrisCompatabilityBlockFilter("ANDESITE_STAIRS", "COBBLESTONE_STAIRS"));
        filters.add(new IrisCompatabilityBlockFilter("STONE_STAIRS", "COBBLESTONE_STAIRS"));
        filters.add(new IrisCompatabilityBlockFilter("END_STONE_BRICK_STAIRS", "LEGACY_SANDSTONE_STAIRS"));
        filters.add(new IrisCompatabilityBlockFilter("NETHER_BRICK_STAIRS", "LEGACY_NETHER_BRICK_STAIRS"));
        filters.add(new IrisCompatabilityBlockFilter("RED_NETHER_BRICK_STAIRS", "NETHER_BRICK_STAIRS"));
        filters.add(new IrisCompatabilityBlockFilter("SMOOTH_QUARTZ_STAIRS", "LEGACY_QUARTZ_STAIRS"));
        filters.add(new IrisCompatabilityBlockFilter("QUARTZ_STAIRS", "LEGACY_QUARTZ_STAIRS"));
        filters.add(new IrisCompatabilityBlockFilter("RED_SANDSTONE_STAIRS", "LEGACY_RED_SANDSTONE_STAIRS"));
        filters.add(new IrisCompatabilityBlockFilter("SMOOTH_RED_SANDSTONE_STAIRS", "LEGACY_RED_SANDSTONE_STAIRS"));
        filters.add(new IrisCompatabilityBlockFilter("STONE_SLAB", "SMOOTH_STONE_SLAB"));
        filters.add(new IrisCompatabilityBlockFilter("SMOKER", "FURNACE"));
        filters.add(new IrisCompatabilityBlockFilter("SMITHING_TABLE", "CRAFTING_TABLE"));
        filters.add(new IrisCompatabilityBlockFilter("END_STONE_BRICK_SLAB", "SANDSTONE_SLAB"));
        filters.add(new IrisCompatabilityBlockFilter("RED_NETHER_BRICK_SLAB", "NETHER_BRICK_SLAB"));
        filters.add(new IrisCompatabilityBlockFilter("SMOOTH_QUARTZ_SLAB", "QUARTZ_SLAB"));
        filters.add(new IrisCompatabilityBlockFilter("CUT_SANDSTONE_SLAB", "SANDSTONE_SLAB"));
        filters.add(new IrisCompatabilityBlockFilter("CUT_RED_SANDSTONE_SLAB", "RED_SANDSTONE_SLAB"));
        filters.add(new IrisCompatabilityBlockFilter("SMOOTH_RED_SANDSTONE_SLAB", "RED_SANDSTONE_SLAB"));
        filters.add(new IrisCompatabilityBlockFilter("SMOOTH_SANDSTONE_SLAB", "SANDSTONE_SLAB"));
        filters.add(new IrisCompatabilityBlockFilter("MOSSY_COBBLESTONE_SLAB", "COBBLESTONE_SLAB"));
        filters.add(new IrisCompatabilityBlockFilter("MOSSY_STONE_BRICK_SLAB", "STONE_BRICK_SLAB"));
        filters.add(new IrisCompatabilityBlockFilter("STONE_SLAB", "SMOOTH_STONE_SLAB"));
        filters.add(new IrisCompatabilityBlockFilter("ANDESITE_SLAB", "COBBLESTONE_SLAB"));
        filters.add(new IrisCompatabilityBlockFilter("ANDESITE_SLAB", "COBBLESTONE_SLAB"));
        filters.add(new IrisCompatabilityBlockFilter("DIORITE_SLAB", "COBBLESTONE_SLAB"));
        filters.add(new IrisCompatabilityBlockFilter("GRANITE_SLAB", "COBBLESTONE_SLAB"));
        filters.add(new IrisCompatabilityBlockFilter("POLISHED_ANDESITE_SLAB", "SMOOTH_STONE_SLAB"));
        filters.add(new IrisCompatabilityBlockFilter("POLISHED_DIORITE_SLAB", "SMOOTH_STONE_SLAB"));
        filters.add(new IrisCompatabilityBlockFilter("POLISHED_GRANITE_SLAB", "SMOOTH_STONE_SLAB"));
        filters.add(new IrisCompatabilityBlockFilter("WARPED_WALL_SIGN", "LEGACY_WALL_SIGN"));
        filters.add(new IrisCompatabilityBlockFilter("WARPED_SIGN", "LEGACY_SIGN_POST"));
        filters.add(new IrisCompatabilityBlockFilter("SPRUCE_WALL_SIGN", "LEGACY_WALL_SIGN"));
        filters.add(new IrisCompatabilityBlockFilter("SPRUCE_SIGN", "LEGACY_SIGN_POST"));
        filters.add(new IrisCompatabilityBlockFilter("OAK_WALL_SIGN", "LEGACY_WALL_SIGN"));
        filters.add(new IrisCompatabilityBlockFilter("OAK_SIGN", "LEGACY_SIGN_POST"));
        filters.add(new IrisCompatabilityBlockFilter("JUNGLE_WALL_SIGN", "LEGACY_WALL_SIGN"));
        filters.add(new IrisCompatabilityBlockFilter("JUNGLE_SIGN", "LEGACY_SIGN_POST"));
        filters.add(new IrisCompatabilityBlockFilter("DARK_OAK_WALL_SIGN", "LEGACY_WALL_SIGN"));
        filters.add(new IrisCompatabilityBlockFilter("DARK_OAK_SIGN", "LEGACY_SIGN_POST"));
        filters.add(new IrisCompatabilityBlockFilter("CRIMSON_WALL_SIGN", "LEGACY_WALL_SIGN"));
        filters.add(new IrisCompatabilityBlockFilter("CRIMSON_SIGN", "LEGACY_SIGN_POST"));
        filters.add(new IrisCompatabilityBlockFilter("BIRCH_WALL_SIGN", "LEGACY_WALL_SIGN"));
        filters.add(new IrisCompatabilityBlockFilter("BIRCH_SIGN", "LEGACY_SIGN_POST"));
        filters.add(new IrisCompatabilityBlockFilter("ACACIA_WALL_SIGN", "LEGACY_WALL_SIGN"));
        filters.add(new IrisCompatabilityBlockFilter("ACACIA_SIGN", "LEGACY_SIGN_POST"));
        filters.add(new IrisCompatabilityBlockFilter("SCAFFOLDING", "BIRCH_FENCE"));
        filters.add(new IrisCompatabilityBlockFilter("LOOM", "LOOM"));
        filters.add(new IrisCompatabilityBlockFilter("LECTERN", "BOOKSHELF"));
        filters.add(new IrisCompatabilityBlockFilter("LANTERN", "REDSTONE_LAMP"));
        filters.add(new IrisCompatabilityBlockFilter("JIGSAW", "AIR"));
        filters.add(new IrisCompatabilityBlockFilter("GRINDSTONE", "COBBLESTONE"));
        filters.add(new IrisCompatabilityBlockFilter("FLETCHING_TABLE", "CRAFTING_TABLE"));
        filters.add(new IrisCompatabilityBlockFilter("COMPOSTER", "CHEST"));
        filters.add(new IrisCompatabilityBlockFilter("CARTOGRAPHY_TABLE", "CRAFTING_TABLE"));
        filters.add(new IrisCompatabilityBlockFilter("CAMPFIRE", "DARK_OAK_SLAB"));
        filters.add(new IrisCompatabilityBlockFilter("BLAST_FURNACE", "FURNACE"));
        filters.add(new IrisCompatabilityBlockFilter("BELL", "REDSTONE_LAMP"));
        filters.add(new IrisCompatabilityBlockFilter("minecraft:barrel[facing=south]", "minecraft:hay_bale[axis=z]", true));
        filters.add(new IrisCompatabilityBlockFilter("minecraft:barrel[facing=north]", "minecraft:hay_bale[axis=z]", true));
        filters.add(new IrisCompatabilityBlockFilter("minecraft:barrel[facing=east]", "minecraft:hay_bale[axis=x]", true));
        filters.add(new IrisCompatabilityBlockFilter("minecraft:barrel[facing=west]", "minecraft:hay_bale[axis=x]", true));
        filters.add(new IrisCompatabilityBlockFilter("minecraft:barrel[facing=up]", "minecraft:hay_bale[axis=y]", true));
        filters.add(new IrisCompatabilityBlockFilter("minecraft:barrel[facing=down]", "minecraft:hay_bale[axis=y]", true));
        filters.add(new IrisCompatabilityBlockFilter("BAMBOO", "BIRCH_FENCE"));
        filters.add(new IrisCompatabilityBlockFilter("BAMBOO_SAPLING", "BIRCH_SAPLING"));
        filters.add(new IrisCompatabilityBlockFilter("POTTED_BAMBOO", "POTTED_BIRCH_SAPLING"));

        return filters;
    }

    public BlockData getBlock(String n) {
        String buf = n;
        int err = 16;

        BlockData tx = B.getOrNull(buf);

        if (tx != null) {
            return tx;
        }

        searching:
        while (true) {
            if (err-- <= 0) {
                return B.get("STONE");
            }

            for (IrisCompatabilityBlockFilter i : blockFilters) {
                if (i.getWhen().equalsIgnoreCase(buf)) {
                    BlockData b = i.getReplace();

                    if (b != null) {
                        return b;
                    }

                    buf = i.getSupplement();
                    continue searching;
                }
            }

            return B.get("STONE");
        }
    }

    public Material getItem(String n) {
        String buf = n;
        int err = 16;
        Material txf = B.getMaterialOrNull(buf);

        if (txf != null) {
            return txf;
        }

        int nomore = 64;

        searching:
        while (true) {
            if (nomore < 0) {
                return B.getMaterial("STONE");
            }

            nomore--;
            if (err-- <= 0) {
                break;
            }

            for (IrisCompatabilityItemFilter i : itemFilters) {
                if (i.getWhen().equalsIgnoreCase(buf)) {
                    Material b = i.getReplace();

                    if (b != null) {
                        return b;
                    }

                    buf = i.getSupplement();
                    continue searching;
                }
            }

            break;
        }

        buf = n;
        BlockData tx = B.getOrNull(buf);

        if (tx != null) {
            return tx.getMaterial();
        }
        nomore = 64;

        searching:
        while (true) {
            if (nomore < 0) {
                return B.getMaterial("STONE");
            }

            nomore--;

            if (err-- <= 0) {
                return B.getMaterial("STONE");
            }

            for (IrisCompatabilityBlockFilter i : blockFilters) {
                if (i.getWhen().equalsIgnoreCase(buf)) {
                    BlockData b = i.getReplace();

                    if (b != null) {
                        return b.getMaterial();
                    }

                    buf = i.getSupplement();
                    continue searching;
                }
            }

            return B.getMaterial("STONE");
        }
    }
}
