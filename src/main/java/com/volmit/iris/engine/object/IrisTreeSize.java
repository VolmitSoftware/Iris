package com.volmit.iris.engine.object;

import com.volmit.iris.engine.object.annotations.Desc;

@Desc("Sapling override object picking options")
public enum IrisTreeSize {

    @Desc("Only one sapling")
    ONE,

    @Desc("Two by two area")
    TWO,

    @Desc("Three by three area with center")
    THREE_CENTER,

    @Desc("Three by three any location")
    THREE_ANY,

    @Desc("Four by four")
    FOUR,

    @Desc("Five by five center")
    FIVE_CENTER,

    @Desc("Five by five")
    FIVE_ANY;

    public static boolean isAnySize(IrisTreeSize treeSize){
        return switch (treeSize) {
            case THREE_CENTER, FIVE_CENTER -> false;
            default -> true;
        };
    }
}
