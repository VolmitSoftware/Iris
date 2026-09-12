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

package art.arcane.iris.platform.bukkit;

import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.integration.Identifier;
import art.arcane.iris.integration.data.DataType;
import art.arcane.iris.platform.bukkit.nms.INMS;
import art.arcane.iris.platform.bukkit.nms.container.BlockProperty;
import art.arcane.iris.integration.ExternalDataSVC;
import art.arcane.iris.pack.validation.IrisCompat;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.platform.bukkit.registry.Materials;
import art.arcane.iris.platform.reflect.KeyedType;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.data.BSupport;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class BukkitBlockResolution {
    private static final BSupportImpl BASE = new BSupportImpl();

    private BukkitBlockResolution() {
    }

    private static final class BSupportImpl extends BSupport<BlockProperty> {
        @Override
        protected void warn(String message) {
            IrisLogging.warn(message);
        }

        @Override
        protected void debug(String message) {
            IrisLogging.debug(message);
        }

        @Override
        protected void reportError(Throwable throwable) {
            IrisLogging.reportError(throwable);
        }

        @Override
        protected void error(String message) {
            IrisLogging.error(message);
        }

        @Override
        protected Material shortGrassMaterial() {
            return Materials.GRASS;
        }

        @Override
        protected void appendExtraFoliageMaterials(IntSet foliage) {
        }

        @Override
        protected boolean includeLitInUpdatable() {
            return false;
        }

        @Override
        protected BlockData resolveCompatBlock(String bdxf) {
            if (bdxf.contains(":")) {
                if (bdxf.startsWith("minecraft:")) {
                    return IrisServices.get(IrisCompat.class).getBlock(bdxf);
                }
                return null;
            }
            return IrisServices.get(IrisCompat.class).getBlock(bdxf);
        }

        @Override
        protected BlockData resolveExternalBlockData(String ix) {
            if (ix.startsWith("minecraft:") || !ix.contains(":")) {
                return null;
            }

            Identifier key = Identifier.fromString(ix);
            Optional<BlockData> bd = IrisServices.get(ExternalDataSVC.class).getBlockData(key);
            debug("Loading block data " + key);
            return bd.orElse(null);
        }

        @Override
        protected boolean shouldPreventLeafDecay() {
            return IrisSettings.get().getGenerator().isPreventLeafDecay();
        }

        @Override
        protected void appendExternalBlockTypes(KList<String> blockTypes) {
            for (Identifier id : IrisServices.get(ExternalDataSVC.class).getAllIdentifiers(DataType.BLOCK)) {
                blockTypes.add(id.toString());
            }
        }

        @Override
        protected void appendExternalItemTypes(KList<String> itemTypes) {
            for (Identifier id : IrisServices.get(ExternalDataSVC.class).getAllIdentifiers(DataType.ITEM)) {
                itemTypes.add(id.toString());
            }
        }

        @Override
        protected KMap<List<String>, List<BlockProperty>> loadExternalBlockStates() {
            KMap<List<BlockProperty>, List<String>> flipped = new KMap<>();
            INMS.get().getBlockProperties().forEach((k, v) -> {
                NamespacedKey key = KeyedType.getKey(k);
                String serialized = key == null ? k.name().toLowerCase(Locale.ROOT) : key.toString();
                flipped.computeIfAbsent(v, $ -> new KList<>()).add(serialized);
            });

            var emptyStates = flipped.computeIfAbsent(new KList<>(0), $ -> new KList<>());
            for (var pair : IrisServices.get(ExternalDataSVC.class).getAllBlockProperties()) {
                if (pair.getB().isEmpty()) {
                    emptyStates.add(pair.getA().toString());
                } else {
                    flipped.computeIfAbsent(pair.getB(), $ -> new KList<>()).add(pair.getA().toString());
                }
            }

            KMap<List<String>, List<BlockProperty>> states = new KMap<>();
            flipped.forEach((k, v) -> {
                var old = states.put(v, k);
                if (old != null) {
                    error("Duplicate block state: " + v + " (" + old + " and " + k + ")");
                }
            });

            return states;
        }
    }

    public static BlockData toDeepSlateOre(BlockData block, BlockData ore) {
        return BASE.toDeepSlateOre(block, ore);
    }

    public static boolean isDeepSlate(BlockData blockData) {
        return BASE.isDeepSlate(blockData);
    }

    public static boolean isOre(BlockData blockData) {
        return BASE.isOre(blockData);
    }

    public static boolean canPlaceOnto(Material mat, Material onto) {
        return BASE.canPlaceOnto(mat, onto);
    }

    public static boolean isFoliagePlantable(BlockData d) {
        return BASE.isFoliagePlantable(d);
    }

    public static boolean isFoliagePlantable(Material d) {
        return BASE.isFoliagePlantable(d);
    }

    public static boolean isWater(BlockData b) {
        return BASE.isWater(b);
    }

    public static BlockData getAir() {
        return BASE.getAir();
    }

    public static Material getMaterialOrNull(String bdx) {
        return BASE.getMaterialOrNull(bdx);
    }

    public static Material getMaterial(String bdx) {
        return BASE.getMaterial(bdx);
    }

    public static boolean isSolid(BlockData mat) {
        return BASE.isSolid(mat);
    }

    public static BlockData getOrNull(String bdxf) {
        return BASE.getOrNull(bdxf);
    }

    public static BlockData getOrNull(String bdxf, boolean warn) {
        return BASE.getOrNull(bdxf, warn);
    }

    /**
     * Strict lookup: null when nothing claims the key, never an air substitute. Unlike {@link #getOrNull(String)} this
     * never reaches the {@link art.arcane.iris.pack.validation.IrisCompat} legacy rewrite table, which is a Bukkit-only
     * layer and must stay off the generation path.
     */
    public static BlockData resolveOrNull(String bdxf) {
        return BASE.resolveOrNull(bdxf, false);
    }

    public static BlockData resolveOrNull(String bdxf, boolean warn) {
        return BASE.resolveOrNull(bdxf, warn);
    }

    public static BlockData getNoCompat(String bdxf) {
        return BASE.getNoCompat(bdxf);
    }

    public static BlockData get(String bdxf) {
        return BASE.get(bdxf);
    }

    public static boolean isStorage(BlockData mat) {
        return BASE.isStorage(mat);
    }

    public static boolean isStorageChest(BlockData mat) {
        return BASE.isStorageChest(mat);
    }

    public static boolean isLit(BlockData mat) {
        return BASE.isLit(mat);
    }

    public static boolean isUpdatable(BlockData mat) {
        return BASE.isUpdatable(mat);
    }

    public static boolean isFoliage(Material d) {
        return BASE.isFoliage(d);
    }

    public static boolean isFoliage(BlockData d) {
        return BASE.isFoliage(d);
    }

    public static boolean isDecorant(BlockData m) {
        return BASE.isDecorant(m);
    }

    public static KList<BlockData> get(KList<String> find) {
        return BASE.get(find);
    }

    public static boolean isFluid(BlockData d) {
        return BASE.isFluid(d);
    }

    public static boolean isAirOrFluid(BlockData d) {
        return BASE.isAirOrFluid(d);
    }

    public static boolean isAir(BlockData d) {
        return BASE.isAir(d);
    }

    public static synchronized String[] getBlockTypes() {
        return BASE.getBlockTypes();
    }

    public static synchronized KMap<List<String>, List<BlockProperty>> getBlockStates() {
        return BASE.getBlockStates();
    }

    public static String[] getItemTypes() {
        return BASE.getItemTypes();
    }

    public static boolean isWaterLogged(BlockData b) {
        return BASE.isWaterLogged(b);
    }

    public static void registerCustomBlockData(String namespace, String key, BlockData blockData) {
        BASE.registerCustomBlockData(namespace, key, blockData);
    }

    public static boolean isVineBlock(BlockData data) {
        return BASE.isVineBlock(data);
    }
}
