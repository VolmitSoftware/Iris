package com.volmit.iris.engine.object;

import org.bukkit.TreeType;

public enum IrisTreeType {

    /**
     * Oak tree (BIG_TREE, TREE)
     */
    OAK,
    /**
     * Spruce tree (MEGA_REDWOOD, REDWOOD, SWAMP, TALL_REDWOOD)
     */
    SPRUCE,
    /**
     * Birch tree (BIRCH, TALL_BIRCH)
     */
    BIRCH,
    /**
     * Jungle tree (JUNGLE, SMALL_JUNGLE)
     */
    JUNGLE,
    /**
     * Big red mushroom; short and fat
     */
    RED_MUSHROOM,
    /**
     * Big brown mushroom; tall and umbrella-like
     */
    BROWN_MUSHROOM,
    /**
     * Acacia tree
     */
    ACACIA,
    /**
     * Dark Oak tree
     */
    DARK_OAK,
    /**
     * Large crimson fungus native to the nether
     */
    CRIMSON_FUNGUS,
    /**
     * Large warped fungus native to the nether
     */
    WARPED_FUNGUS,
    /**
     * Tree with large roots which grows above lush caves
     */
    AZALEA,
    /**
     * The fallback type for all other non-supported growth events
     */
    NONE;

    public static IrisTreeType fromTreeType(TreeType type){
        IrisTreeType irisType;
        switch(type){
            case BIG_TREE, TREE -> irisType = IrisTreeType.OAK;
            case MEGA_REDWOOD, REDWOOD, SWAMP, TALL_REDWOOD -> irisType = IrisTreeType.SPRUCE;
            case BIRCH, TALL_BIRCH -> irisType = IrisTreeType.BIRCH;
            case JUNGLE, SMALL_JUNGLE -> irisType = IrisTreeType.JUNGLE;
            case RED_MUSHROOM -> irisType = IrisTreeType.RED_MUSHROOM;
            case BROWN_MUSHROOM -> irisType = IrisTreeType.BROWN_MUSHROOM;
            case ACACIA -> irisType = IrisTreeType.ACACIA;
            case DARK_OAK -> irisType = IrisTreeType.DARK_OAK;
            case CRIMSON_FUNGUS -> irisType = IrisTreeType.CRIMSON_FUNGUS;
            case WARPED_FUNGUS -> irisType = IrisTreeType.WARPED_FUNGUS;
            case AZALEA -> irisType = IrisTreeType.AZALEA;
            //case COCOA_TREE, CHORUS_PLANT, JUNGLE_BUSH -> irisType = IrisSaplingType.NONE;
            default -> irisType = IrisTreeType.NONE;
        }
        return irisType;
    }
}
