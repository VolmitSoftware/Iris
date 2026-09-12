package art.arcane.iris.structure.object;

import art.arcane.iris.generation.block.IrisBlockData;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.collection.KList;

public interface IObjectLoot {
    KList<IrisBlockData> getFilter();
    KList<PlatformBlockState> getFilter(IrisData manager);
    boolean isExact();
    String getName();
    int getWeight();
}
