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

package art.arcane.iris.modded;


final class WorldCheckMaterials {
    private WorldCheckMaterials() {
    }

    static boolean isCharacteristicMaterial(String structureLabel, String structureKey, String blockKey) {
        if (structureKey == null || blockKey == null || !blockKey.startsWith("minecraft:")) {
            return false;
        }
        String block = blockKey.substring(blockKey.indexOf(':') + 1);
        return switch (structureLabel) {
            case "stronghold" -> isStrongholdMaterial(block);
            case "trial_chambers" -> isTrialChamberMaterial(block);
            case "mansion" -> isWoodConstructionMaterial(block, "dark_oak")
                    || isWoodConstructionMaterial(block, "birch")
                    || isCobblestoneConstructionMaterial(block);
            case "village" -> isVillageMaterial(structureKey.substring(structureKey.indexOf(':') + 1), block);
            case "monument" -> isMonumentMaterial(block);
            default -> false;
        };
    }

    private static boolean isStrongholdMaterial(String block) {
        return block.equals("stone_bricks")
                || block.equals("cracked_stone_bricks")
                || block.equals("mossy_stone_bricks")
                || block.equals("infested_stone_bricks")
                || block.equals("infested_cracked_stone_bricks")
                || block.equals("infested_mossy_stone_bricks")
                || block.equals("stone_brick_stairs")
                || block.equals("stone_brick_slab")
                || block.equals("stone_brick_wall");
    }

    private static boolean isTrialChamberMaterial(String block) {
        return block.contains("tuff_brick")
                || block.equals("polished_tuff")
                || block.equals("chiseled_tuff")
                || block.endsWith("copper_grate")
                || block.equals("trial_spawner")
                || block.equals("vault");
    }

    private static boolean isVillageMaterial(String structure, String block) {
        if (isCobblestoneConstructionMaterial(block)) {
            return true;
        }
        return switch (structure) {
            case "village_plains" -> isWoodConstructionMaterial(block, "oak");
            case "village_desert" -> block.equals("cut_sandstone")
                    || block.equals("smooth_sandstone")
                    || block.equals("cut_sandstone_slab")
                    || block.equals("smooth_sandstone_slab")
                    || block.equals("smooth_sandstone_stairs")
                    || block.equals("sandstone_stairs")
                    || block.equals("sandstone_slab")
                    || block.equals("sandstone_wall");
            case "village_savanna" -> isWoodConstructionMaterial(block, "acacia");
            case "village_snowy", "village_taiga" -> isWoodConstructionMaterial(block, "spruce");
            default -> false;
        };
    }

    private static boolean isWoodConstructionMaterial(String block, String wood) {
        if (block.startsWith(wood)) {
            int suffixOffset = wood.length();
            if (matchesSuffix(block, suffixOffset, "_planks")
                    || matchesSuffix(block, suffixOffset, "_stairs")
                    || matchesSuffix(block, suffixOffset, "_slab")
                    || matchesSuffix(block, suffixOffset, "_fence")
                    || matchesSuffix(block, suffixOffset, "_fence_gate")
                    || matchesSuffix(block, suffixOffset, "_door")
                    || matchesSuffix(block, suffixOffset, "_trapdoor")) {
                return true;
            }
        }
        int strippedOffset = "stripped_".length();
        if (!block.startsWith("stripped_")
                || !block.regionMatches(strippedOffset, wood, 0, wood.length())) {
            return false;
        }
        int suffixOffset = strippedOffset + wood.length();
        return matchesSuffix(block, suffixOffset, "_log")
                || matchesSuffix(block, suffixOffset, "_wood");
    }

    private static boolean isCobblestoneConstructionMaterial(String block) {
        return block.equals("cobblestone")
                || block.equals("cobblestone_stairs")
                || block.equals("cobblestone_slab")
                || block.equals("cobblestone_wall")
                || block.equals("mossy_cobblestone")
                || block.equals("mossy_cobblestone_stairs")
                || block.equals("mossy_cobblestone_slab")
                || block.equals("mossy_cobblestone_wall");
    }

    private static boolean matchesSuffix(String value, int offset, String suffix) {
        return value.length() == offset + suffix.length()
                && value.regionMatches(offset, suffix, 0, suffix.length());
    }

    private static boolean isMonumentMaterial(String block) {
        return block.equals("prismarine")
                || block.equals("prismarine_bricks")
                || block.equals("dark_prismarine")
                || block.equals("sea_lantern");
    }
}
