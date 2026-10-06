package art.arcane.iris.engine.object.loot;

import art.arcane.iris.engine.object.material.IrisBlockData;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.collection.KList;

public interface IObjectLoot {
    KList<IrisBlockData> getFilter();
    KList<PlatformBlockState> getFilter(IrisData manager);
    boolean isExact();
    String getName();
    int getWeight();
}
