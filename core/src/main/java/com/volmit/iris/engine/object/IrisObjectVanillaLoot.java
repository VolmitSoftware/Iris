package com.volmit.iris.engine.object;

import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.data.cache.AtomicCache;
import com.volmit.iris.engine.object.annotations.*;
import com.volmit.iris.engine.object.annotations.functions.LootTableKeyFunction;
import com.volmit.iris.util.collection.KList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.block.data.BlockData;

@Snippet("object-vanilla-loot")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents vanilla loot within this object or jigsaw piece")
@Data
public class IrisObjectVanillaLoot {
    private final transient AtomicCache<KList<BlockData>> filterCache = new AtomicCache<>();
    @ArrayType(min = 1, type = IrisBlockData.class)
    @Desc("The list of blocks this loot table should apply to")
    private KList<IrisBlockData> filter = new KList<>();
    @Desc("Exactly match the block data or not")
    private boolean exact = false;
    @Desc("The vanilla loot table key")
    @Required
    @RegistryListFunction(LootTableKeyFunction.class)
    private String name;
    @Desc("The weight of this loot table being chosen")
    private int weight = 1;

    public KList<BlockData> getFilter(IrisData rdata) {
        return filterCache.aquire(() ->
        {
            KList<BlockData> b = new KList<>();

            for (IrisBlockData i : filter) {
                BlockData bx = i.getBlockData(rdata);

                if (bx != null) {
                    b.add(bx);
                }
            }

            return b;
        });
    }
}
