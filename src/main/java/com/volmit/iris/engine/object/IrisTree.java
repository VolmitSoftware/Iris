package com.volmit.iris.engine.object;

import com.volmit.iris.engine.object.annotations.ArrayType;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.Required;
import com.volmit.iris.util.collection.KList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.TreeType;

@Accessors(chain = true)
@AllArgsConstructor
@NoArgsConstructor
@Desc("Tree replace options for this object placer")
@Data
public class IrisTree {

    @Required
    @Desc("The types of trees overwritten by this object")
    @ArrayType(min = 1, type = TreeType.class)
    private KList<TreeType> treeTypes;

    @Desc("If enabled, overrides any TreeType")
    private boolean anyTree = false;

    @Required
    @Desc("The size of the square of saplings this applies to (2 means a 2 * 2 sapling area)")
    @ArrayType(min = 1, type = IrisTreeSize.class)
    private KList<IrisTreeSize> sizes = new KList<>();

    @Desc("If enabled, overrides trees of any size")
    private boolean anySize;
}