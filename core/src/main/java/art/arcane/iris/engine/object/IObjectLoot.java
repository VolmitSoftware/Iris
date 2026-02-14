package art.arcane.iris.engine.object;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.volmlib.util.collection.KList;
import org.bukkit.block.data.BlockData;

public interface IObjectLoot {
    KList<IrisBlockData> getFilter();
    KList<BlockData> getFilter(IrisData manager);
    boolean isExact();
    String getName();
    int getWeight();
}
