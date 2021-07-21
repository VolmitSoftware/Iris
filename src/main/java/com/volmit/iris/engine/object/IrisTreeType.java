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

    @Desc("Any tree type (all will match, including mushrooms & nether trees")
    ANY,

    @Desc("The fallback type for all other non-supported growth events")
    NONE;

    public static IrisTreeType fromTreeType(TreeType type){
        return switch(type){
            case BIG_TREE, TREE -> IrisTreeType.OAK;
            case MEGA_REDWOOD, REDWOOD, SWAMP, TALL_REDWOOD -> IrisTreeType.SPRUCE;
            case BIRCH, TALL_BIRCH -> IrisTreeType.BIRCH;
            case JUNGLE, SMALL_JUNGLE -> IrisTreeType.JUNGLE;
            case RED_MUSHROOM -> IrisTreeType.RED_MUSHROOM;
            case BROWN_MUSHROOM -> IrisTreeType.BROWN_MUSHROOM;
            case ACACIA -> IrisTreeType.ACACIA;
            case DARK_OAK -> IrisTreeType.DARK_OAK;
            case CRIMSON_FUNGUS -> IrisTreeType.CRIMSON_FUNGUS;
            case WARPED_FUNGUS -> IrisTreeType.WARPED_FUNGUS;
            case AZALEA -> IrisTreeType.AZALEA;
            //case COCOA_TREE, CHORUS_PLANT, JUNGLE_BUSH -> IrisSaplingType.NONE;
            default -> IrisTreeType.NONE;
        };
    }
}
