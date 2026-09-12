/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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
package art.arcane.iris.pack.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The legacy block rename table: keys Minecraft renamed or removed, and the key to try instead. Pure data with no
 * platform types so the {@link ContentGate} applies it identically on Bukkit and the modded loaders; the Bukkit
 * {@code IrisCompat} filter list is built from the same entries.
 * <p>
 * A {@code when} without {@code exact} matches the bare block name (namespace and properties stripped, case
 * insensitive); an exact entry matches the whole normalized state. Supplements can chain (the first entry whose
 * supplement exists wins) and the table contains cycles, so callers bound the number of hops.
 */
public final class LegacyBlockRenames {
    public static final int MAX_HOPS = 16;

    public record Rename(String when, String supplement, boolean exact) {
        public Rename(String when, String supplement) {
            this(when, supplement, false);
        }
    }

    private static final List<Rename> DEFAULTS = defaults0();
    private static final Map<String, String> BY_NAME = byName(DEFAULTS);
    private static final Map<String, String> BY_STATE = byState(DEFAULTS);

    private LegacyBlockRenames() {
    }

    /** Every entry in table order. */
    public static List<Rename> defaults() {
        return DEFAULTS;
    }

    /**
     * The supplement for a normalized state ({@code namespace:name[props]}, lowercase), or null when no entry applies.
     * Exact entries are consulted first, then the bare name.
     */
    public static String supplementFor(String normalizedState) {
        if (normalizedState == null) {
            return null;
        }
        String exact = BY_STATE.get(normalizedState);
        if (exact != null) {
            return exact;
        }
        String base = ContentGate.baseKey(normalizedState);
        int namespace = base.indexOf(':');
        return BY_NAME.get(namespace < 0 ? base : base.substring(namespace + 1));
    }

    private static Map<String, String> byName(List<Rename> renames) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Rename rename : renames) {
            if (!rename.exact()) {
                map.putIfAbsent(rename.when().toLowerCase(Locale.ROOT), rename.supplement());
            }
        }
        return Collections.unmodifiableMap(map);
    }

    private static Map<String, String> byState(List<Rename> renames) {
        Map<String, String> map = new LinkedHashMap<>();
        for (Rename rename : renames) {
            if (rename.exact()) {
                String state = ContentGate.normalizeState(rename.when());
                if (state != null) {
                    map.putIfAbsent(state, rename.supplement());
                }
            }
        }
        return Collections.unmodifiableMap(map);
    }

    private static List<Rename> defaults0() {
        List<Rename> filters = new ArrayList<>();

        filters.add(new Rename("CHAIN", "IRON_CHAIN"));
        filters.add(new Rename("GRASS", "SHORT_GRASS"));
        filters.add(new Rename("SHORT_GRASS", "GRASS"));

        // Below 1.16
        filters.add(new Rename("WEEPING_VINES", "NETHER_FENCE"));
        filters.add(new Rename("WEEPING_VINES_PLANT", "NETHER_FENCE"));
        filters.add(new Rename("WARPED_WART_BLOCK", "NETHER_WART_BLOCK"));
        filters.add(new Rename("TWISTING_VINES", "BAMBOO"));
        filters.add(new Rename("TWISTING_VINES_PLANT", "BAMBOO"));
        filters.add(new Rename("TARGET", "COBBLESTONE"));
        filters.add(new Rename("SOUL_SOIL", "SOULSAND"));
        filters.add(new Rename("SOUL_TORCH", "TORCH"));
        filters.add(new Rename("SOUL_LANTERN", "LANTERN"));
        filters.add(new Rename("SOUL_FIRE", "FIRE"));
        filters.add(new Rename("SOUL_CAMPFIRE", "CAMPFIRE"));
        filters.add(new Rename("SHROOMLIGHT", "GLOWSTONE"));
        filters.add(new Rename("RESPAWN_ANCHOR", "OBSIDIAN"));
        filters.add(new Rename("NETHER_SPROUTS", "RED_MUSHROOM"));
        filters.add(new Rename("NETHER_GOLD_ORE", "GOLD_ORE"));
        filters.add(new Rename("LODESTONE", "STONE"));
        filters.add(new Rename("STRIPPED_WARPED_HYPHAE", "BROWN_MUSHROOM_BLOCK"));
        filters.add(new Rename("STRIPPED_CRIMSON_HYPHAE", "RED_MUSHROOM_BLOCK"));
        filters.add(new Rename("WARPED_HYPHAE", "MUSHROOM_STEM"));
        filters.add(new Rename("CRIMSON_HYPHAE", "RED_MUSHROOM_BLOCK"));
        filters.add(new Rename("GILDED_BLACKSTONE", "COBBLESTONE"));
        filters.add(new Rename("CRYING_OBSIDIAN", "OBSIDIAN"));
        filters.add(new Rename("STRIPPED_WARPED_STEM", "MUSHROOM_STEM"));
        filters.add(new Rename("STRIPPED_CRIMSON_STEM", "MUSHROOM_STEM"));
        filters.add(new Rename("WARPED_STEM", "MUSHROOM_STEM"));
        filters.add(new Rename("CRIMSON_STEM", "MUSHROOM_STEM"));
        filters.add(new Rename("CRIMSON_ROOTS", "RED_MUSHROOM"));
        filters.add(new Rename("WARPED_ROOTS", "BROWN_MUSHROOM"));
        filters.add(new Rename("CRIMSON_PLANKS", "OAK_PLANKS"));
        filters.add(new Rename("WARPED_PLANKS", "OAK_PLANKS"));
        filters.add(new Rename("WARPED_NYLIUM", "MYCELIUM"));
        filters.add(new Rename("CRIMSON_NYLIUM", "MYCELIUM"));
        filters.add(new Rename("WARPED_FUNGUS", "BROWN_MUSHROOM"));
        filters.add(new Rename("CRIMSON_FUNGUS", "RED_MUSHROOM"));
        filters.add(new Rename("CRACKED_NETHER_BRICKS", "NETHER_BRICKS"));
        filters.add(new Rename("CHISELED_NETHER_BRICKS", "NETHER_BRICKS"));
        filters.add(new Rename("NETHER_FENCE", "LEGACY_NETHER_FENCE"));
        filters.add(new Rename("IRON_CHAIN", "IRON_BARS"));
        filters.add(new Rename("NETHERITE_BLOCK", "QUARTZ_BLOCK"));
        filters.add(new Rename("BLACKSTONE", "COBBLESTONE"));
        filters.add(new Rename("BASALT", "STONE"));
        filters.add(new Rename("ANCIENT_DEBRIS", "NETHERRACK"));
        filters.add(new Rename("NETHERRACK", "LEGACY_NETHERRACK"));

        // Below 1.15
        filters.add(new Rename("HONEY_BLOCK", "OAK_LEAVES"));
        filters.add(new Rename("BEEHIVE", "OAK_LEAVES"));
        filters.add(new Rename("BEE_NEST", "OAK_LEAVES"));

        // Below 1.14
        filters.add(new Rename("GRANITE_WALL", "COBBLESTONE_WALL"));
        filters.add(new Rename("BLUE_ICE", "PACKED_ICE"));
        filters.add(new Rename("DIORITE_WALL", "COBBLESTONE_WALL"));
        filters.add(new Rename("ANDESITE_WALL", "COBBLESTONE_WALL"));
        filters.add(new Rename("SWEET_BERRY_BUSH", "GRASS"));
        filters.add(new Rename("STONECUTTER", "CRAFTING_TABLE"));
        filters.add(new Rename("SANDSTONE_STAIRS", "LEGACY_SANDSTONE_STAIRS"));
        filters.add(new Rename("SMOOTH_SANDSTONE_STAIRS", "LEGACY_SANDSTONE_STAIRS"));
        filters.add(new Rename("MOSSY_COBBLESTONE_STAIRS", "COBBLESTONE_STAIRS"));
        filters.add(new Rename("MOSSY_STONE_BRICK_STAIRS", "STONE_BRICK_STAIRS"));
        filters.add(new Rename("POLISHED_GRANITE_STAIRS", "COBBLESTONE_STAIRS"));
        filters.add(new Rename("GRANITE_STAIRS", "COBBLESTONE_STAIRS"));
        filters.add(new Rename("POLISHED_DIORITE_STAIRS", "COBBLESTONE_STAIRS"));
        filters.add(new Rename("DIORITE_STAIRS", "COBBLESTONE_STAIRS"));
        filters.add(new Rename("POLISHED_ANDESITE_STAIRS", "COBBLESTONE_STAIRS"));
        filters.add(new Rename("ANDESITE_STAIRS", "COBBLESTONE_STAIRS"));
        filters.add(new Rename("STONE_STAIRS", "COBBLESTONE_STAIRS"));
        filters.add(new Rename("END_STONE_BRICK_STAIRS", "LEGACY_SANDSTONE_STAIRS"));
        filters.add(new Rename("NETHER_BRICK_STAIRS", "LEGACY_NETHER_BRICK_STAIRS"));
        filters.add(new Rename("RED_NETHER_BRICK_STAIRS", "NETHER_BRICK_STAIRS"));
        filters.add(new Rename("SMOOTH_QUARTZ_STAIRS", "LEGACY_QUARTZ_STAIRS"));
        filters.add(new Rename("QUARTZ_STAIRS", "LEGACY_QUARTZ_STAIRS"));
        filters.add(new Rename("RED_SANDSTONE_STAIRS", "LEGACY_RED_SANDSTONE_STAIRS"));
        filters.add(new Rename("SMOOTH_RED_SANDSTONE_STAIRS", "LEGACY_RED_SANDSTONE_STAIRS"));
        filters.add(new Rename("STONE_SLAB", "SMOOTH_STONE_SLAB"));
        filters.add(new Rename("SMOKER", "FURNACE"));
        filters.add(new Rename("SMITHING_TABLE", "CRAFTING_TABLE"));
        filters.add(new Rename("END_STONE_BRICK_SLAB", "SANDSTONE_SLAB"));
        filters.add(new Rename("RED_NETHER_BRICK_SLAB", "NETHER_BRICK_SLAB"));
        filters.add(new Rename("SMOOTH_QUARTZ_SLAB", "QUARTZ_SLAB"));
        filters.add(new Rename("CUT_SANDSTONE_SLAB", "SANDSTONE_SLAB"));
        filters.add(new Rename("CUT_RED_SANDSTONE_SLAB", "RED_SANDSTONE_SLAB"));
        filters.add(new Rename("SMOOTH_RED_SANDSTONE_SLAB", "RED_SANDSTONE_SLAB"));
        filters.add(new Rename("SMOOTH_SANDSTONE_SLAB", "SANDSTONE_SLAB"));
        filters.add(new Rename("MOSSY_COBBLESTONE_SLAB", "COBBLESTONE_SLAB"));
        filters.add(new Rename("MOSSY_STONE_BRICK_SLAB", "STONE_BRICK_SLAB"));
        filters.add(new Rename("STONE_SLAB", "SMOOTH_STONE_SLAB"));
        filters.add(new Rename("ANDESITE_SLAB", "COBBLESTONE_SLAB"));
        filters.add(new Rename("ANDESITE_SLAB", "COBBLESTONE_SLAB"));
        filters.add(new Rename("DIORITE_SLAB", "COBBLESTONE_SLAB"));
        filters.add(new Rename("GRANITE_SLAB", "COBBLESTONE_SLAB"));
        filters.add(new Rename("POLISHED_ANDESITE_SLAB", "SMOOTH_STONE_SLAB"));
        filters.add(new Rename("POLISHED_DIORITE_SLAB", "SMOOTH_STONE_SLAB"));
        filters.add(new Rename("POLISHED_GRANITE_SLAB", "SMOOTH_STONE_SLAB"));
        filters.add(new Rename("WARPED_WALL_SIGN", "LEGACY_WALL_SIGN"));
        filters.add(new Rename("WARPED_SIGN", "LEGACY_SIGN_POST"));
        filters.add(new Rename("SPRUCE_WALL_SIGN", "LEGACY_WALL_SIGN"));
        filters.add(new Rename("SPRUCE_SIGN", "LEGACY_SIGN_POST"));
        filters.add(new Rename("OAK_WALL_SIGN", "LEGACY_WALL_SIGN"));
        filters.add(new Rename("OAK_SIGN", "LEGACY_SIGN_POST"));
        filters.add(new Rename("JUNGLE_WALL_SIGN", "LEGACY_WALL_SIGN"));
        filters.add(new Rename("JUNGLE_SIGN", "LEGACY_SIGN_POST"));
        filters.add(new Rename("DARK_OAK_WALL_SIGN", "LEGACY_WALL_SIGN"));
        filters.add(new Rename("DARK_OAK_SIGN", "LEGACY_SIGN_POST"));
        filters.add(new Rename("CRIMSON_WALL_SIGN", "LEGACY_WALL_SIGN"));
        filters.add(new Rename("CRIMSON_SIGN", "LEGACY_SIGN_POST"));
        filters.add(new Rename("BIRCH_WALL_SIGN", "LEGACY_WALL_SIGN"));
        filters.add(new Rename("BIRCH_SIGN", "LEGACY_SIGN_POST"));
        filters.add(new Rename("ACACIA_WALL_SIGN", "LEGACY_WALL_SIGN"));
        filters.add(new Rename("ACACIA_SIGN", "LEGACY_SIGN_POST"));
        filters.add(new Rename("SCAFFOLDING", "BIRCH_FENCE"));
        filters.add(new Rename("LECTERN", "BOOKSHELF"));
        filters.add(new Rename("LANTERN", "REDSTONE_LAMP"));
        filters.add(new Rename("JIGSAW", "AIR"));
        filters.add(new Rename("GRINDSTONE", "COBBLESTONE"));
        filters.add(new Rename("FLETCHING_TABLE", "CRAFTING_TABLE"));
        filters.add(new Rename("COMPOSTER", "CHEST"));
        filters.add(new Rename("CARTOGRAPHY_TABLE", "CRAFTING_TABLE"));
        filters.add(new Rename("CAMPFIRE", "DARK_OAK_SLAB"));
        filters.add(new Rename("BLAST_FURNACE", "FURNACE"));
        filters.add(new Rename("BELL", "REDSTONE_LAMP"));
        filters.add(new Rename("minecraft:barrel[facing=south]", "minecraft:hay_bale[axis=z]", true));
        filters.add(new Rename("minecraft:barrel[facing=north]", "minecraft:hay_bale[axis=z]", true));
        filters.add(new Rename("minecraft:barrel[facing=east]", "minecraft:hay_bale[axis=x]", true));
        filters.add(new Rename("minecraft:barrel[facing=west]", "minecraft:hay_bale[axis=x]", true));
        filters.add(new Rename("minecraft:barrel[facing=up]", "minecraft:hay_bale[axis=y]", true));
        filters.add(new Rename("minecraft:barrel[facing=down]", "minecraft:hay_bale[axis=y]", true));
        filters.add(new Rename("BAMBOO", "BIRCH_FENCE"));
        filters.add(new Rename("BAMBOO_SAPLING", "BIRCH_SAPLING"));
        filters.add(new Rename("POTTED_BAMBOO", "POTTED_BIRCH_SAPLING"));

        return Collections.unmodifiableList(filters);
    }
}
