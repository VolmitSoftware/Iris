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

package art.arcane.iris.pack.validation;

import art.arcane.iris.platform.bukkit.BukkitBlockResolution;

import art.arcane.iris.spi.IrisLogging;
import com.google.gson.Gson;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.json.JSONObject;
import art.arcane.iris.world.task.J;
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
                    IrisLogging.error("Failed to writeNodeData to compat file");
                    IrisLogging.reportError(e);
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
                IrisLogging.reportError(e);
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
        for (LegacyBlockRenames.Rename rename : LegacyBlockRenames.defaults()) {
            filters.add(new IrisCompatabilityBlockFilter(rename.when(), rename.supplement(), rename.exact()));
        }
        return filters;
    }

    public BlockData getBlock(String n) {
        String buf = n;
        int err = 16;

        BlockData tx = BukkitBlockResolution.getOrNull(buf, false);

        if (tx != null) {
            return tx;
        }

        searching:
        while (true) {
            if (err-- <= 0) {
                IrisLogging.warnOnce("compat-block:" + n, "Can't find block data for " + n + "; using STONE.");
                return BukkitBlockResolution.getNoCompat("STONE");
            }
            String m = buf;
            if (m.contains("[")) {
                m = m.split("\\Q[\\E")[0];
            }
            if (m.contains(":")) {
                m = m.split("\\Q:\\E", 2)[1];
            }

            for (IrisCompatabilityBlockFilter i : blockFilters) {
                if (i.getWhen().equalsIgnoreCase(i.isExact() ? buf : m)) {
                    BlockData b = i.getReplace();

                    if (b != null) {
                        return b;
                    }

                    buf = i.getSupplement();
                    continue searching;
                }
            }

            IrisLogging.warnOnce("compat-block:" + n, "Can't find block data for " + n + "; using STONE.");
            return BukkitBlockResolution.getNoCompat("STONE");
        }
    }

    public Material getItem(String n) {
        String buf = n;
        int err = 16;
        Material txf = BukkitBlockResolution.getMaterialOrNull(buf);

        if (txf != null) {
            return txf;
        }

        int nomore = 64;

        searching:
        while (true) {
            if (nomore < 0) {
                return BukkitBlockResolution.getMaterial("STONE");
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
        BlockData tx = BukkitBlockResolution.getOrNull(buf, false);

        if (tx != null) {
            return tx.getMaterial();
        }
        nomore = 64;

        searching:
        while (true) {
            if (nomore < 0) {
                return BukkitBlockResolution.getMaterial("STONE");
            }

            nomore--;

            if (err-- <= 0) {
                return BukkitBlockResolution.getMaterial("STONE");
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

            return BukkitBlockResolution.getMaterial("STONE");
        }
    }
}
