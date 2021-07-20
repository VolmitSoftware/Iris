package com.volmit.iris.engine.object;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.util.collection.KList;
import org.bukkit.Location;
import org.bukkit.Material;

import java.util.Objects;

@Desc("Sapling override object picking options")
public enum IrisTreeSize {

    @Desc("Only one sapling")
    ONE,

    @Desc("Two by two area")
    TWO,

    @Desc("Three by three any location")
    THREE_ANY,

    @Desc("Three by three area with center")
    THREE_CENTER,

    @Desc("Four by four")
    FOUR,

    @Desc("Five by five")
    FIVE_ANY,

    @Desc("Five by five center")
    FIVE_CENTER;

    /**
     * All sizes in this enum
     */
    public static final KList<IrisTreeSize> sizes = new KList<>(ONE, TWO, THREE_ANY, THREE_CENTER, FOUR, FIVE_ANY, FIVE_CENTER);

    /**
     * The best size in this enum
     */
    public static final IrisTreeSize bestSize = FIVE_CENTER;

    /**
     * Whether the position of the any type (not fixed at a center)
     * @param treeSize The treesize to check
     */
    public static boolean isAnyPosition(IrisTreeSize treeSize){
        return switch (treeSize) {
            case ONE, THREE_CENTER, FIVE_CENTER -> false;
            default -> true;
        };
    }

    /**
     * Get the best size to match against from a list of sizes
     * @param sizes The list of sizes
     * @return The best size (highest & center > any)
     */
    public static IrisTreeSize bestSize(KList<IrisTreeSize> sizes){
        if (sizes.contains(FIVE_CENTER)){
            return FIVE_CENTER;
        }
        else if (sizes.contains(FIVE_ANY)){
            return FIVE_ANY;
        }
        else if (sizes.contains(FOUR)){
            return FOUR;
        }
        else if (sizes.contains(THREE_CENTER)){
            return THREE_CENTER;
        }
        else if (sizes.contains(THREE_ANY)){
            return THREE_ANY;
        }
        else if (sizes.contains(TWO)){
            return TWO;
        }
        else if (sizes.contains(ONE)){
            return ONE;
        } else {
            return null;
        }
    }

    /**
     * Find the best size based on a location
     * @param location The location to look from
     * @return The best size
     */
    public static IrisTreeSize getBestSize(Location location){
        return getBestSize(location, sizes.copy());
    }

    /**
     * Find the best valid size based on a location and a list of sizes
     * @param location The location to search from
     * @param sizeList The list of sizes to pick from
     * @return The best valid size
     */
    public static IrisTreeSize getBestSize(Location location, KList<IrisTreeSize> sizeList){
        while (sizeList.isNotEmpty()){

            // Find the best size & remove from list
            IrisTreeSize bestSize = bestSize(sizeList);
            assert bestSize != null;
            sizeList.remove(bestSize);

            // Find the best match
            KList<KList<Location>> best = isSizeValid(bestSize, location);
            if (best != null){
                return bestSize;
            }
        }
        return ONE;

    }

    /**
     * Check if the size at a specific location is valid
     * @param size the IrisTreeSize to check
     * @param location at this location
     * @return A list of locations if any match, or null if not.
     */
    public static KList<KList<Location>> isSizeValid(IrisTreeSize size, Location location) {
        return switch (size){
            case ONE            -> new KList<KList<Location>>(new KList<>(location));
            case TWO            -> loopLocation(location, 2);
            case THREE_ANY      -> loopLocation(location, 3);
            case FOUR           -> loopLocation(location, 4);
            case FIVE_ANY       -> loopLocation(location, 5);
            case THREE_CENTER   -> isCenterMapValid(location, 3);
            case FIVE_CENTER    -> isCenterMapValid(location, 5);
        };
    }

    /**
     * Check a map with
     * @param center this block location as a center
     * @param size with this size map
     * @return A 2d KList of locations or null
     */
    private static KList<KList<Location>> isCenterMapValid(Location center, int size) {
        KList<KList<Location>> locations = getMap(size, center, true);
        return isMapValid(locations, center.getBlock().getType()) ? locations : null;
    }


    /**
     * Loops over all possible squares based on
     * @param center center position
     * @param size a square size
     * @return A list of matching locations, or null.
     */
    private static KList<KList<Location>> loopLocation(Location center, int size){
        Material blockType = center.getBlock().getType();
        KList<KList<Location>> locations;
        for (int i = -size + 1; i <= 0; i++){
            for (int j = -size + 1; j <= 0; j++){
                locations = getMap(size, center.clone().add(i, 0, j));
                if (isMapValid(locations, blockType)){
                    Iris.info("Valid map for size " + size + " with material " + blockType.name() + " with center" + center);
                    Iris.info("Locations: " + locations);
                    return locations;
                }
            }
        }
        return null;
    }

    /**
     * Get if the map is valid, compared to a block material
     * @param map The map to check inside of
     * @param block The block material to check with
     * @return True if it's valid
     */
    private static boolean isMapValid(KList<KList<Location>> map, Material block){
        if (map == null) return false;
        return map.stream().allMatch(row -> row.stream().allMatch(location -> location.getBlock().getType().equals(block)));
    }

    /**
     * Get a map with all blocks in a
     * @param size `size * size` square area
     * @param leftTop starting from the here
     * @return A map with all block locations in the area
     */
    private static KList<KList<Location>> getMap(int size, Location leftTop){
        KList<KList<Location>> locations = new KList<>();
        for (int i = 0; i < size; i++){
            KList<Location> row = new KList<>();
            for (int j = 0; j < size; j++){
                row.add(leftTop.clone().add(i, 0, j));
            }
            locations.add(row);
        }
        return locations;
    }

    /**
     * Get a map with all blocks in a
     * @param size `size * size` square to check, must be odd (returns null if not)
     * @param center from a center
     * @param useCenter boolean toggle to call this function over IrisTreeSize#getMap(size, leftTop)
     * @return A map with all block locations in the area
     */
    private static KList<KList<Location>> getMap(int size, Location center, boolean useCenter){
        if (size % 2 != 1){
            return null;
        }
        return getMap(size, center.clone().add(-(size - 1) / 2d, 0, -(size - 1) / 2d));
    }
}
