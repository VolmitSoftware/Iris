/*
 * Iris is a World Generator for Minecraft Servers
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

package art.arcane.iris.generation.biome;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Direct vanilla biome tag membership, generated from the MC 26.2 built-in datapack
 * (data/minecraft/tags/worldgen/biome). Used to give Iris custom biomes the tag membership of the vanilla
 * biome they derive from, so tag selectors written by vanilla and by mods (#minecraft:is_overworld and
 * friends) resolve against Iris terrain.
 *
 * <p>Only DIRECT membership is listed. Vanilla's derived tags reference other tags rather than repeating
 * biomes (#minecraft:is_ocean includes #minecraft:is_deep_ocean, water_on_map_outlines includes
 * #minecraft:is_river, and so on), so adding a custom biome to the direct tags carries it into the derived
 * tags for free.
 *
 * <p>The has_structure/* tags are deliberately absent. Iris resolves native structure placement through the
 * biome's structure derivative, never through the custom biome, so injecting custom biomes there would place
 * a structure twice.
 *
 * <p>{@code stronghold_biased_to} is absent for the same reason, even though it is not a has_structure tag.
 * It is a structure-placement input: {@code worldgen/structure_set/strongholds.json} reads it as
 * {@code preferred_biomes} when it rings strongholds around spawn. Inheriting it would enter every Iris biome
 * derived from a tagged vanilla biome into that ring - which is the entire overworld surface of a typical pack -
 * and let vanilla place strongholds where the pack's own structure configuration did not ask for them.
 *
 * <p>An unknown key (a mod biome, a datapack biome, or a vanilla biome added after 26.2) contributes nothing;
 * the pack author's explicit tags still apply.
 */
public final class IrisVanillaBiomeTags {
    private static final String TAG_NAMESPACE = "minecraft:";
    private static final Map<String, List<String>> DIRECT_TAGS = new HashMap<>(96);

    private IrisVanillaBiomeTags() {
    }

    /**
     * Tags the given vanilla biome key belongs to directly. Never null; empty for unknown keys. Keys are
     * returned fully namespaced and lowercase.
     */
    public static List<String> tagsFor(String biomeKey) {
        if (biomeKey == null || biomeKey.isBlank()) {
            return List.of();
        }
        String normalized = biomeKey.trim().toLowerCase(Locale.ROOT);
        if (normalized.indexOf(':') < 0) {
            normalized = TAG_NAMESPACE + normalized;
        }
        List<String> tags = DIRECT_TAGS.get(normalized);
        return tags == null ? List.of() : tags;
    }

    static int knownBiomeCount() {
        return DIRECT_TAGS.size();
    }

    private static void put(String biomeKey, String... tagPaths) {
        String[] namespaced = new String[tagPaths.length];
        for (int i = 0; i < tagPaths.length; i++) {
            namespaced[i] = TAG_NAMESPACE + tagPaths[i];
        }
        DIRECT_TAGS.put(biomeKey, List.of(namespaced));
    }

    static {
        put("minecraft:badlands", "is_badlands", "is_overworld");
        put("minecraft:bamboo_jungle", "is_jungle", "is_overworld");
        put("minecraft:basalt_deltas", "is_nether");
        put("minecraft:beach", "is_beach", "is_overworld");
        put("minecraft:birch_forest", "is_forest", "is_overworld");
        put("minecraft:cherry_grove", "is_mountain", "is_overworld");
        put("minecraft:cold_ocean", "is_ocean", "is_overworld", "spawns_cold_variant_farm_animals");
        put("minecraft:crimson_forest", "is_nether");
        put("minecraft:dark_forest", "is_forest", "is_overworld");
        put("minecraft:deep_cold_ocean", "is_deep_ocean", "is_overworld", "spawns_cold_variant_farm_animals");
        put("minecraft:deep_dark",
                "is_overworld", "mineshaft_blocking", "spawns_cold_variant_farm_animals", "spawns_cold_variant_frogs");
        put("minecraft:deep_frozen_ocean",
                "is_deep_ocean", "is_overworld", "polar_bears_spawn_on_alternate_blocks", "spawns_cold_variant_farm_animals",
                "spawns_cold_variant_frogs");
        put("minecraft:deep_lukewarm_ocean", "is_deep_ocean", "is_overworld", "spawns_warm_variant_farm_animals");
        put("minecraft:deep_ocean", "is_deep_ocean", "is_overworld");
        put("minecraft:desert",
                "is_overworld", "spawns_gold_rabbits", "spawns_warm_variant_farm_animals", "spawns_warm_variant_frogs");
        put("minecraft:dripstone_caves", "is_overworld");
        put("minecraft:end_barrens", "is_end");
        put("minecraft:end_highlands", "is_end");
        put("minecraft:end_midlands", "is_end");
        put("minecraft:eroded_badlands", "is_badlands", "is_overworld");
        put("minecraft:flower_forest", "is_forest", "is_overworld");
        put("minecraft:forest", "is_forest", "is_overworld");
        put("minecraft:frozen_ocean",
                "is_ocean", "is_overworld", "polar_bears_spawn_on_alternate_blocks", "spawns_cold_variant_farm_animals",
                "spawns_cold_variant_frogs", "spawns_snow_foxes", "spawns_white_rabbits");
        put("minecraft:frozen_peaks",
                "is_mountain", "is_overworld", "spawns_cold_variant_farm_animals", "spawns_cold_variant_frogs",
                "spawns_snow_foxes", "spawns_white_rabbits");
        put("minecraft:frozen_river",
                "is_overworld", "is_river", "spawns_cold_variant_farm_animals", "spawns_cold_variant_frogs",
                "spawns_snow_foxes", "spawns_white_rabbits");
        put("minecraft:grove",
                "is_forest", "is_overworld", "spawns_cold_variant_farm_animals", "spawns_cold_variant_frogs",
                "spawns_snow_foxes", "spawns_white_rabbits");
        put("minecraft:ice_spikes",
                "is_overworld", "spawns_cold_variant_farm_animals", "spawns_cold_variant_frogs", "spawns_snow_foxes",
                "spawns_white_rabbits");
        put("minecraft:jagged_peaks",
                "is_mountain", "is_overworld", "spawns_cold_variant_farm_animals", "spawns_cold_variant_frogs",
                "spawns_snow_foxes", "spawns_white_rabbits");
        put("minecraft:jungle", "is_jungle", "is_overworld");
        put("minecraft:lukewarm_ocean", "is_ocean", "is_overworld", "spawns_warm_variant_farm_animals");
        put("minecraft:lush_caves", "allows_tropical_fish_spawns_at_any_height", "is_overworld");
        put("minecraft:mangrove_swamp",
                "allows_surface_slime_spawns", "is_overworld", "spawns_warm_variant_farm_animals",
                "spawns_warm_variant_frogs", "water_on_map_outlines");
        put("minecraft:meadow", "is_mountain", "is_overworld");
        put("minecraft:mushroom_fields", "is_overworld", "without_zombie_sieges");
        put("minecraft:nether_wastes", "is_nether");
        put("minecraft:ocean", "is_ocean", "is_overworld");
        put("minecraft:old_growth_birch_forest", "is_forest", "is_overworld");
        put("minecraft:old_growth_pine_taiga",
                "is_overworld", "is_taiga", "spawns_cold_variant_farm_animals");
        put("minecraft:old_growth_spruce_taiga",
                "is_overworld", "is_taiga", "spawns_cold_variant_farm_animals");
        put("minecraft:pale_garden", "is_forest", "is_overworld");
        put("minecraft:plains", "is_overworld");
        put("minecraft:river", "is_overworld", "is_river");
        put("minecraft:savanna", "is_overworld", "is_savanna");
        put("minecraft:savanna_plateau", "is_overworld", "is_savanna");
        put("minecraft:small_end_islands", "is_end");
        put("minecraft:snowy_beach",
                "is_beach", "is_overworld", "spawns_cold_variant_farm_animals", "spawns_cold_variant_frogs",
                "spawns_snow_foxes", "spawns_white_rabbits");
        put("minecraft:snowy_plains",
                "is_overworld", "spawns_cold_variant_farm_animals", "spawns_cold_variant_frogs", "spawns_snow_foxes",
                "spawns_white_rabbits");
        put("minecraft:snowy_slopes",
                "is_mountain", "is_overworld", "spawns_cold_variant_farm_animals", "spawns_cold_variant_frogs",
                "spawns_snow_foxes", "spawns_white_rabbits");
        put("minecraft:snowy_taiga",
                "is_overworld", "is_taiga", "spawns_cold_variant_farm_animals", "spawns_cold_variant_frogs",
                "spawns_snow_foxes", "spawns_white_rabbits");
        put("minecraft:soul_sand_valley", "is_nether");
        put("minecraft:sparse_jungle", "is_jungle", "is_overworld");
        put("minecraft:stony_peaks", "is_mountain", "is_overworld", "spawns_cold_variant_farm_animals");
        put("minecraft:stony_shore", "is_overworld");
        put("minecraft:sulfur_caves", "is_overworld");
        put("minecraft:sunflower_plains", "is_overworld");
        put("minecraft:swamp", "allows_surface_slime_spawns", "is_overworld", "water_on_map_outlines");
        put("minecraft:taiga", "is_overworld", "is_taiga", "spawns_cold_variant_farm_animals");
        put("minecraft:the_end", "is_end");
        put("minecraft:the_void", "without_wandering_trader_spawns");
        put("minecraft:warm_ocean",
                "is_ocean", "is_overworld", "produces_corals_from_bonemeal", "spawns_coral_variant_zombie_nautilus",
                "spawns_warm_variant_farm_animals", "spawns_warm_variant_frogs");
        put("minecraft:warped_forest", "is_nether");
        put("minecraft:windswept_forest",
                "is_hill", "is_overworld", "spawns_cold_variant_farm_animals");
        put("minecraft:windswept_gravelly_hills",
                "is_hill", "is_overworld", "spawns_cold_variant_farm_animals");
        put("minecraft:windswept_hills", "is_hill", "is_overworld", "spawns_cold_variant_farm_animals");
        put("minecraft:windswept_savanna", "is_overworld", "is_savanna");
        put("minecraft:wooded_badlands", "is_badlands", "is_overworld");
    }
}
