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

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.modded.api.ModdedCustomContentRegistry;
import art.arcane.iris.modded.api.ModdedBlockData;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.data.UnresolvedKeyLog;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SpeleothemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SpeleothemThickness;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class ModdedBlockResolution {
    private static final Set<Block> FOLIAGE = blockSet(
            "poppy", "dandelion", "cornflower", "sweet_berry_bush", "crimson_roots", "warped_roots",
            "nether_sprouts", "allium", "azure_bluet", "blue_orchid", "oxeye_daisy", "lily_of_the_valley",
            "wither_rose", "dark_oak_sapling", "acacia_sapling", "jungle_sapling", "birch_sapling",
            "spruce_sapling", "oak_sapling", "orange_tulip", "pink_tulip", "red_tulip", "white_tulip",
            "fern", "large_fern", "short_grass", "tall_grass");
    private static final Set<Block> DECORANT = decorantSet();
    private static final Set<Block> LIT = blockSet(
            "glowstone", "amethyst_cluster", "small_amethyst_bud", "medium_amethyst_bud", "large_amethyst_bud",
            "end_rod", "soul_sand", "torch", "redstone_torch", "soul_torch", "redstone_wall_torch", "wall_torch",
            "soul_wall_torch", "lantern", "candle", "jack_o_lantern", "redstone_lamp", "magma_block", "light",
            "shroomlight", "sea_lantern", "soul_lantern", "fire", "soul_fire", "sea_pickle", "brewing_stand",
            "redstone_ore");
    private static final Set<Block> STORAGE = blockSet(
            "chest", "smoker", "trapped_chest", "shulker_box", "white_shulker_box", "orange_shulker_box",
            "magenta_shulker_box", "light_blue_shulker_box", "yellow_shulker_box", "lime_shulker_box",
            "pink_shulker_box", "gray_shulker_box", "light_gray_shulker_box", "cyan_shulker_box",
            "purple_shulker_box", "blue_shulker_box", "brown_shulker_box", "green_shulker_box",
            "red_shulker_box", "black_shulker_box", "barrel", "dispenser", "dropper", "hopper", "furnace",
            "blast_furnace");
    private static final Set<Block> STORAGE_CHEST = storageChestSet();
    private static final Set<Block> DEEPSLATE = blockSet(
            "deepslate", "deepslate_bricks", "deepslate_brick_slab", "deepslate_brick_stairs",
            "deepslate_brick_wall", "deepslate_tile_slab", "deepslate_tiles", "deepslate_tile_stairs",
            "deepslate_tile_wall", "cracked_deepslate_tiles", "deepslate_coal_ore", "deepslate_iron_ore",
            "deepslate_copper_ore", "deepslate_diamond_ore", "deepslate_emerald_ore", "deepslate_gold_ore",
            "deepslate_lapis_ore", "deepslate_redstone_ore");
    private static final Map<Block, Block> NORMAL_TO_DEEPSLATE = oreMap(false);
    private static final Map<Block, Block> DEEPSLATE_TO_NORMAL = oreMap(true);
    private static final Set<Block> FOLIAGE_PLANTABLE_STATE = blockSet(
            "grass_block", "moss_block", "rooted_dirt", "dirt", "coarse_dirt", "podzol");
    private static final Set<Block> FOLIAGE_PLANTABLE_MATERIAL = blockSet(
            "grass_block", "moss_block", "dirt", "tall_grass", "tall_seagrass", "large_fern", "sunflower",
            "peony", "lilac", "rose_bush", "rooted_dirt", "coarse_dirt", "podzol");
    private static final Set<Block> PLACE_ONTO_LEAVES = blockSet(
            "acacia_leaves", "birch_leaves", "dark_oak_leaves", "jungle_leaves", "oak_leaves", "spruce_leaves");
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final UnresolvedKeyLog UNRESOLVED = new UnresolvedKeyLog("Iris modded block resolution", 30_000L);
    private static final int REPORTED_FAILURE_KEYS_MAX = 256;
    private static final Set<String> REPORTED_FAILURE_KEYS = ConcurrentHashMap.newKeySet();

    private ModdedBlockResolution() {
    }

    record Parsed(BlockState state, Map<Property<?>, Comparable<?>> properties, String deferredPlacementKey) {
    }

    private static Set<Block> blockSet(String... ids) {
        Set<Block> blocks = new HashSet<>();
        for (String id : ids) {
            Identifier identifier = Identifier.parse("minecraft:" + id);
            if (BuiltInRegistries.BLOCK.containsKey(identifier)) {
                blocks.add(BuiltInRegistries.BLOCK.getValue(identifier));
            }
        }
        return blocks;
    }

    private static Set<Block> decorantSet() {
        Set<Block> blocks = blockSet(
                "short_grass", "tall_grass", "fern", "large_fern", "cornflower", "sunflower", "chorus_flower",
                "poppy", "dandelion", "oxeye_daisy", "orange_tulip", "pink_tulip", "red_tulip", "white_tulip",
                "lilac", "dead_bush", "sweet_berry_bush", "rose_bush", "wither_rose", "allium", "blue_orchid",
                "lily_of_the_valley", "crimson_fungus", "warped_fungus", "red_mushroom", "brown_mushroom",
                "crimson_roots", "azure_bluet", "cactus", "weeping_vines", "weeping_vines_plant", "warped_roots",
                "nether_sprouts", "twisting_vines", "twisting_vines_plant", "sugar_cane", "wheat", "potatoes",
                "carrots", "beetroots", "nether_wart", "sea_pickle", "seagrass", "tall_seagrass",
                "acacia_button", "birch_button", "crimson_button", "dark_oak_button", "jungle_button",
                "oak_button", "polished_blackstone_button", "spruce_button", "stone_button", "warped_button",
                "torch", "soul_torch", "glow_lichen", "vine", "sculk_vein");
        blocks.addAll(FOLIAGE);
        return blocks;
    }

    private static Set<Block> storageChestSet() {
        Set<Block> blocks = new HashSet<>(STORAGE);
        blocks.remove(Blocks.SMOKER);
        blocks.remove(Blocks.FURNACE);
        blocks.remove(Blocks.BLAST_FURNACE);
        return blocks;
    }

    private static Map<Block, Block> oreMap(boolean deepslateToNormal) {
        String[][] pairs = {
                {"coal_ore", "deepslate_coal_ore"},
                {"emerald_ore", "deepslate_emerald_ore"},
                {"diamond_ore", "deepslate_diamond_ore"},
                {"copper_ore", "deepslate_copper_ore"},
                {"gold_ore", "deepslate_gold_ore"},
                {"iron_ore", "deepslate_iron_ore"},
                {"lapis_ore", "deepslate_lapis_ore"},
                {"redstone_ore", "deepslate_redstone_ore"}
        };
        Map<Block, Block> map = new HashMap<>();
        for (String[] pair : pairs) {
            Block normal = BuiltInRegistries.BLOCK.getValue(Identifier.parse("minecraft:" + pair[0]));
            Block deep = BuiltInRegistries.BLOCK.getValue(Identifier.parse("minecraft:" + pair[1]));
            if (deepslateToNormal) {
                map.put(deep, normal);
            } else {
                map.put(normal, deep);
            }
        }
        return map;
    }

    private static void reportResolveFailure(String key, Throwable error) {
        String failureKey = key == null ? "<null>" : key;
        if (!REPORTED_FAILURE_KEYS.add(failureKey)) {
            return;
        }
        if (REPORTED_FAILURE_KEYS.size() > REPORTED_FAILURE_KEYS_MAX) {
            REPORTED_FAILURE_KEYS.clear();
        }
        IrisLogging.reportError("Iris block data '" + failureKey + "' failed to resolve", error);
    }

    private static void warnUnresolved(String key, String message) {
        if (UNRESOLVED.firstOccurrence(key)) {
            IrisLogging.warn(message);
        }
        String summary = UNRESOLVED.pollSummary();
        if (summary != null) {
            IrisLogging.warn(summary);
        }
    }

    public static BlockState airState() {
        return AIR;
    }

    public static ModdedBlockState getAir() {
        return ModdedBlockState.of(AIR, null);
    }

    public static ModdedBlockState get(String bdxf) {
        Parsed parsed = resolveGet(bdxf);
        return stateFrom(parsed);
    }

    public static ModdedBlockState getNoCompat(String bdxf) {
        Parsed parsed = resolveNoCompat(bdxf);
        return stateFrom(parsed);
    }

    public static ModdedBlockState getOrNull(String bdxf) {
        return getOrNull(bdxf, false);
    }

    public static ModdedBlockState getOrNull(String bdxf, boolean warn) {
        Parsed parsed = resolveOrNull(bdxf, warn);
        return parsed == null ? null : stateFrom(parsed);
    }

    private static ModdedBlockState stateFrom(Parsed parsed) {
        return parsed.deferredPlacementKey() == null
                ? ModdedBlockState.of(parsed.state(), parsed.properties())
                : ModdedBlockState.deferred(parsed.state(), parsed.properties(), parsed.deferredPlacementKey());
    }

    static Parsed resolveGet(String bdxf) {
        // Mirrors the Bukkit path: an unknown key warns (rate limited) and falls back to air, instead of
        // resolving to air with no output at all.
        return resolveNoCompat(bdxf);
    }

    static Parsed resolveNoCompat(String bdxf) {
        Parsed parsed = resolveOrNull(bdxf, true);
        if (parsed != null) {
            return parsed;
        }
        return new Parsed(AIR, null, null);
    }

    /**
     * Resolves a key, returning null when nothing claims it. Never substitutes air - {@link #resolveNoCompat(String)}
     * owns the air fallback.
     */
    static Parsed resolveOrNull(String bdxf, boolean warn) {
        try {
            String bd = bdxf.trim();

            if (bd.startsWith("minecraft:cauldron[level=")) {
                bd = bd.replaceAll("\\Q:cauldron[\\E", ":water_cauldron[");
            }

            if (bd.equals("minecraft:grass_path")) {
                return new Parsed(Blocks.DIRT_PATH.defaultBlockState(), null, null);
            }

            Parsed bdx = parseBlockData(bd, warn);

            if (bdx == null) {
                ModdedBlockData provided = ModdedCustomContentRegistry.resolveBlock(bd);
                if (provided != null) {
                    return new Parsed(provided.state(), null, provided.deferredPlacement() ? bd : null);
                }
                if (warn) {
                    warnUnresolved(bd, "Unknown Block Data '" + bd + "'");
                }
                return null;
            }

            return bdx;
        } catch (Throwable e) {
            reportResolveFailure(bdxf, e);
            if (warn) {
                warnUnresolved(bdxf, "Unknown Block Data '" + bdxf + "'");
            }
        }

        return null;
    }

    public static ModdedBlockState strictParse(String key) {
        Parsed parsed = parseStrict(key);
        return stateFrom(parsed);
    }

    private static Parsed parseStrict(String key) {
        StringReader reader = new StringReader(key);
        BlockStateParser.BlockResult result;
        try {
            result = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, reader, false);
        } catch (CommandSyntaxException e) {
            throw new IllegalArgumentException("Could not parse data: " + key, e);
        }
        if (reader.canRead()) {
            throw new IllegalArgumentException("Could not parse remainder: " + reader.getRemaining());
        }
        return new Parsed(result.blockState(), result.properties(), null);
    }

    private static Parsed createBlockData(String s, boolean warn) {
        try {
            return parseStrict(s);
        } catch (IllegalArgumentException e) {
            if (s.contains("[")) {
                String base = s.split("\\Q[\\E")[0];
                Parsed stripped = createBlockData(base, warn);
                if (stripped != null && warn) {
                    // Dedup on the base block key, not the full state string. UnresolvedKeyLog interns every key it
                    // is handed into a set it never trims, and a rejected property is usually rejected for every
                    // value and every combination a pack uses - keying on the state string would intern one entry per
                    // distinct state (16 levels x 6 facings x ...) for a single authoring mistake.
                    warnUnresolved("props:" + base,
                            "Block '" + base + "' rejected state '" + propertySection(s) + "'; using its default state");
                }
                return stripped;
            }
        }

        if (warn) {
            warnUnresolved(s, "Can't find block data for " + s);
        }
        return null;
    }

    private static String propertySection(String key) {
        int open = key.indexOf('[');
        if (open < 0) {
            return "";
        }
        int close = key.indexOf(']', open);
        return close < 0 ? key.substring(open + 1) : key.substring(open + 1, close);
    }

    private static Parsed materialBlockData(String ix) {
        if (ix.contains("[") || ix.contains(":")) {
            return null;
        }
        Identifier identifier = Identifier.tryParse("minecraft:" + ix.toLowerCase(Locale.ROOT));
        if (identifier == null || !BuiltInRegistries.BLOCK.containsKey(identifier)) {
            return null;
        }
        return new Parsed(BuiltInRegistries.BLOCK.getValue(identifier).defaultBlockState(), null, null);
    }

    private static Parsed parseBlockData(String ix, boolean warn) {
        try {
            Parsed bx = createBlockData(ix.toLowerCase(), warn);

            if (bx == null) {
                bx = createBlockData("minecraft:" + ix.toLowerCase(), warn);
            }

            if (bx == null) {
                bx = materialBlockData(ix);
            }

            if (bx == null) {
                if (warn) {
                    warnUnresolved(ix, "Unknown Block Data: " + ix);
                }
                return null;
            }

            if (bx.state().getBlock() instanceof LeavesBlock) {
                BlockState mutated = bx.state().setValue(LeavesBlock.PERSISTENT, shouldPreventLeafDecay());
                bx = new Parsed(mutated, bx.properties(), bx.deferredPlacementKey());
            }

            return bx;
        } catch (Throwable e) {
            String block = ix.contains(":") ? ix.split(":")[1].toLowerCase() : ix.toLowerCase();
            String state = block.contains("[") ? block.split("\\Q[\\E")[1].split("\\Q]\\E")[0] : "";
            Map<String, String> stateMap = new HashMap<>();
            if (!state.equals("")) {
                Arrays.stream(state.split(",")).forEach((String s) -> stateMap.put(s.split("=")[0], s.split("=")[1]));
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
            for (String key : stateMap.keySet()) {
                createBlockData(block + "[" + key + "=" + stateMap.get(key) + "]", false);
                newStates.put(key, stateMap.get(key));
            }

            String joined = newStates.entrySet().stream()
                    .map((Map.Entry<String, String> entry) -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining(","));
            if (!joined.equals("")) {
                joined = "[" + joined + "]";
            }
            String newBlock = block + joined;
            IrisLogging.debug("Converting " + ix + " to " + newBlock);

            try {
                return createBlockData(newBlock, false);
            } catch (Throwable e1) {
                IrisLogging.reportError(e1);
            }

            return null;
        }
    }

    private static boolean shouldPreventLeafDecay() {
        return IrisSettings.get().getGenerator().isPreventLeafDecay();
    }

    public static BlockState toDeepSlateOre(BlockState block, BlockState ore) {
        Block key = ore.getBlock();

        if (isDeepSlate(block)) {
            Block mapped = NORMAL_TO_DEEPSLATE.get(key);
            if (mapped != null) {
                return mapped.defaultBlockState();
            }
        } else {
            Block mapped = DEEPSLATE_TO_NORMAL.get(key);
            if (mapped != null) {
                return mapped.defaultBlockState();
            }
        }

        return ore;
    }

    public static boolean isDeepSlate(BlockState state) {
        return DEEPSLATE.contains(state.getBlock());
    }

    public static boolean isOre(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().endsWith("_ore");
    }

    public static boolean isAir(BlockState state) {
        if (state == null) {
            return true;
        }
        Block block = state.getBlock();
        return block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR;
    }

    public static boolean isSolid(BlockState state) {
        if (state == null) {
            return false;
        }
        return isSolidMaterial(state.getBlock());
    }

    private static boolean isSolidMaterial(Block block) {
        return block.defaultBlockState().blocksMotion();
    }

    public static boolean isOccluding(BlockState state) {
        return state.getBlock().defaultBlockState().isRedstoneConductor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    public static boolean isFluid(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.WATER || block == Blocks.LAVA;
    }

    public static boolean isWater(BlockState state) {
        return state.getBlock() == Blocks.WATER;
    }

    public static boolean isWaterLogged(BlockState state) {
        return state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED);
    }

    public static boolean isLit(BlockState state) {
        return LIT.contains(state.getBlock());
    }

    public static boolean isUpdatable(BlockState state) {
        return isStorage(state)
                || (state.getBlock() instanceof SpeleothemBlock
                && state.getValue(SpeleothemBlock.THICKNESS) == SpeleothemThickness.TIP);
    }

    public static boolean isFoliage(BlockState state) {
        return FOLIAGE.contains(state.getBlock());
    }

    public static boolean isFoliagePlantable(BlockState state) {
        return FOLIAGE_PLANTABLE_STATE.contains(state.getBlock());
    }

    public static boolean isDecorant(BlockState state) {
        return DECORANT.contains(state.getBlock());
    }

    public static boolean isStorage(BlockState state) {
        return STORAGE.contains(state.getBlock());
    }

    public static boolean isStorageChest(BlockState state) {
        return STORAGE_CHEST.contains(state.getBlock());
    }

    public static boolean isVineBlock(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.VINE || block == Blocks.SCULK_VEIN || block == Blocks.GLOW_LICHEN;
    }

    public static boolean hasTileEntity(BlockState state) {
        return state.getBlock().defaultBlockState().hasBlockEntity();
    }

    public static boolean canPlaceOnto(Block mat, Block onto) {
        if (mat == Blocks.SUGAR_CANE) {
            return onto == Blocks.SUGAR_CANE || onto == Blocks.GRASS_BLOCK || onto == Blocks.DIRT
                    || onto == Blocks.COARSE_DIRT || onto == Blocks.PODZOL || onto == Blocks.MYCELIUM
                    || onto == Blocks.ROOTED_DIRT
                    || onto == Blocks.MOSS_BLOCK || onto == Blocks.PALE_MOSS_BLOCK || onto == Blocks.MUD
                    || onto == Blocks.MUDDY_MANGROVE_ROOTS || onto == Blocks.SAND || onto == Blocks.RED_SAND
                    || onto == Blocks.SUSPICIOUS_SAND;
        }

        if (mat == Blocks.CACTUS) {
            return onto == Blocks.CACTUS || onto == Blocks.SAND || onto == Blocks.RED_SAND;
        }

        if ((onto == Blocks.CRIMSON_NYLIUM || onto == Blocks.WARPED_NYLIUM)
                && (mat == Blocks.CRIMSON_FUNGUS || mat == Blocks.CRIMSON_ROOTS
                || mat == Blocks.WARPED_FUNGUS || mat == Blocks.WARPED_ROOTS)) {
            return true;
        }

        if (FOLIAGE.contains(mat)) {
            if (!FOLIAGE_PLANTABLE_MATERIAL.contains(onto)) {
                return false;
            }
        }

        if (onto == Blocks.AIR || onto == Blocks.CAVE_AIR || onto == Blocks.VOID_AIR) {
            return false;
        }

        if (onto == Blocks.GRASS_BLOCK && mat == Blocks.DEAD_BUSH) {
            return false;
        }

        if (onto == Blocks.DIRT_PATH) {
            if (!isSolidMaterial(mat)) {
                return false;
            }
        }

        if (PLACE_ONTO_LEAVES.contains(onto)) {
            return isSolidMaterial(mat);
        }

        return true;
    }
}
