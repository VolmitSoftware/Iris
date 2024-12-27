package com.volmit.iris.engine.object;

import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.util.collection.KList;
import org.bukkit.block.data.BlockData;

public interface IObjectLoot {
    KList<IrisBlockData> getFilter();
    KList<BlockData> getFilter(IrisData manager);
    boolean isExact();
    String getName();
    int getWeight();
}
