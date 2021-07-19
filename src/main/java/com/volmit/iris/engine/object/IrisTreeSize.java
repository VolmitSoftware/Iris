package com.volmit.iris.engine.object;

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

    public static IrisTreeSize getBestSize(Location location){
        KList<IrisTreeSize> sizes = new KList<>(ONE, TWO, THREE_ANY, THREE_CENTER, FOUR, FIVE_ANY, FIVE_CENTER);
        while (sizes.isNotEmpty()){

            // Find the best size & remove from list
            IrisTreeSize bestSize = bestSize(sizes);
            assert bestSize != null;
            sizes.remove(bestSize);

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
        switch (size){
            case ONE -> {
                return new KList<KList<Location>>(new KList<>(location));
            }
            case TWO -> {
                return loopLocation(location, 2, location.getBlock().getType());
            }
            case THREE_ANY -> {
                return loopLocation(location, 3, location.getBlock().getType());
            }
            case THREE_CENTER -> {
                KList<KList<Location>> locations = getMap(3, location, true);
                if (locations == null) {
                    return null;
                }
                return isMapValid(locations, location.getBlock().getType()) ? locations : null;
            }
            case FOUR -> {
                return loopLocation(location, 4, location.getBlock().getType());
            }
            case FIVE_ANY -> {
                return loopLocation(location, 5, location.getBlock().getType());
            }
            case FIVE_CENTER -> {
                KList<KList<Location>> locations = getMap(5, location, true);
                if (locations == null) {
                    return null;
                }
                return isMapValid(locations, location.getBlock().getType()) ? locations : null;
            }
            default -> {
                return null;
            }
        }
    }

    /**
     * Loops over all possible squares based on
     * @param location top left position
     * @param size a square size
     * @param blockType and a type of a block
     * @return A list of matching locations, or null.
     */
    private static KList<KList<Location>> loopLocation(Location location, int size, Material blockType){
        Location leftTop = location.add(-size + 1, 0, -size + 1);
        KList<KList<Location>> locations;
        for (int i = -size + 1; i <= 0; i++){
            for (int j = -size + 1; j <= 0; j++){
                locations = getMap(size, leftTop.add(i, 0, j));
                if (isMapValid(locations, blockType)){
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
                row.add(leftTop.add(i, 0, j));
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
        return getMap(size, center.add(-(size - 1) / 2d, 0, -(size - 1) / 2d));
    }
}
