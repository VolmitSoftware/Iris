package art.arcane.iris.util.data;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.data.BSupport;
import art.arcane.iris.Iris;
import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.link.Identifier;
import art.arcane.iris.core.link.data.DataType;
import art.arcane.iris.core.nms.INMS;
import art.arcane.iris.core.nms.container.BlockProperty;
import art.arcane.iris.core.service.ExternalDataSVC;
import art.arcane.iris.util.data.registry.Materials;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.List;
import java.util.Optional;

public class B {
    private static final BSupportImpl BASE = new BSupportImpl();

    private static final class BSupportImpl extends BSupport<BlockProperty> {
        @Override
        protected void warn(String message) {
            Iris.warn(message);
        }

        @Override
        protected void debug(String message) {
            Iris.debug(message);
        }

        @Override
        protected void reportError(Throwable throwable) {
            Iris.reportError(throwable);
        }

        @Override
        protected void error(String message) {
            Iris.error(message);
        }

        @Override
        protected Material shortGrassMaterial() {
            return Materials.GRASS;
        }

        @Override
        protected void appendExtraFoliageMaterials(IntSet foliage) {
            // Iris has no extra foliage materials beyond Bukkit Material.
        }

        @Override
        protected boolean includeLitInUpdatable() {
            return false;
        }

        @Override
        protected BlockData resolveCompatBlock(String bdxf) {
            if (bdxf.contains(":")) {
                if (bdxf.startsWith("minecraft:")) {
                    return Iris.compat.getBlock(bdxf);
                }
                return null;
            }
            return Iris.compat.getBlock(bdxf);
        }

        @Override
        protected BlockData resolveExternalBlockData(String ix) {
            if (ix.startsWith("minecraft:") || !ix.contains(":")) {
                return null;
            }

            Identifier key = Identifier.fromString(ix);
            Optional<BlockData> bd = Iris.service(ExternalDataSVC.class).getBlockData(key);
            debug("Loading block data " + key);
            return bd.orElse(null);
        }

        @Override
        protected boolean shouldPreventLeafDecay() {
            return IrisSettings.get().getGenerator().isPreventLeafDecay();
        }

        @Override
        protected void appendExternalBlockTypes(KList<String> blockTypes) {
            for (Identifier id : Iris.service(ExternalDataSVC.class).getAllIdentifiers(DataType.BLOCK)) {
                blockTypes.add(id.toString());
            }
        }

        @Override
        protected void appendExternalItemTypes(KList<String> itemTypes) {
            for (Identifier id : Iris.service(ExternalDataSVC.class).getAllIdentifiers(DataType.ITEM)) {
                itemTypes.add(id.toString());
            }
        }

        @Override
        protected KMap<List<String>, List<BlockProperty>> loadExternalBlockStates() {
            KMap<List<BlockProperty>, List<String>> flipped = new KMap<>();
            INMS.get().getBlockProperties().forEach((k, v) -> {
                flipped.computeIfAbsent(v, $ -> new KList<>()).add(k.getKey().toString());
            });

            var emptyStates = flipped.computeIfAbsent(new KList<>(0), $ -> new KList<>());
            for (var pair : Iris.service(ExternalDataSVC.class).getAllBlockProperties()) {
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

    public synchronized static String[] getBlockTypes() {
        return BASE.getBlockTypes();
    }

    public synchronized static KMap<List<String>, List<BlockProperty>> getBlockStates() {
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
