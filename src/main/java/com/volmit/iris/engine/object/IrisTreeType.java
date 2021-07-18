package com.volmit.iris.engine.object;

import com.volmit.iris.engine.object.annotations.Desc;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.TreeType;

@Accessors(chain = true)
@NoArgsConstructor
@Desc("Tree Types")
public enum IrisTreeType {

    @Desc("Oak tree (BIG_TREE, TREE)")
    OAK,

    @Desc("Spruce tree (MEGA_REDWOOD, REDWOOD, SWAMP, TALL_REDWOOD)")
    SPRUCE,

    @Desc("Birch tree (BIRCH, TALL_BIRCH)")
    BIRCH,

    @Desc("Jungle tree (JUNGLE, SMALL_JUNGLE)")
    JUNGLE,

    @Desc("Big red mushroom; short and fat")
    RED_MUSHROOM,

    @Desc("Big brown mushroom; tall and umbrella-like")
    BROWN_MUSHROOM,

    @Desc("Acacia tree")
    ACACIA,

    @Desc("Dark Oak tree")
    DARK_OAK,

    @Desc("Large crimson fungus native to the nether")
    CRIMSON_FUNGUS,

    @Desc("Large warped fungus native to the nether")
    WARPED_FUNGUS,

    @Desc("Tree with large roots which grows above lush caves")
    AZALEA,

    @Desc("The fallback type for all other non-supported growth events")
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
