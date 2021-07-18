package com.volmit.iris.engine.object;

import com.volmit.iris.core.TreeManager;
import com.volmit.iris.engine.object.annotations.*;
import com.volmit.iris.util.collection.KList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.TreeType;

@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Desc("Tree replace options")
@Data
public class IrisTree {

    @Required
    @Desc("The types of trees overwritten")
    @ArrayType(min = 1, type = IrisTreeType.class)
    private KList<IrisTreeType> treeTypes;

    @RegistryListObject
    @Required
    @ArrayType(min = 1, type = String.class)
    @Desc("List of objects to replace trees with")
    private KList<String> objects = new KList<>();

    @Desc("The size of the square of saplings this applies to (2 means a 2 * 2 sapling area)")
    @MinNumber(1)
    @MaxNumber(TreeManager.maxSaplingPlane)
    private int size = 1;
}