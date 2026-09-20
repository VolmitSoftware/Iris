package art.arcane.iris.structure.object;

import art.arcane.iris.generation.block.IrisBlockData;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.volmlib.nativelib.terrain.NativeBlockState;
import art.arcane.volmlib.util.collection.KList;

public interface IObjectLoot {
    KList<IrisBlockData> getFilter();
    KList<NativeBlockState> getFilter(IrisData manager);
    boolean isExact();
    String getName();
    int getWeight();
}
