package com.volmit.iris.object;

import com.volmit.iris.Iris;
import com.volmit.iris.manager.IrisDataManager;
import com.volmit.iris.scaffold.cache.AtomicCache;
import com.volmit.iris.util.ArrayType;
import com.volmit.iris.util.Desc;
import com.volmit.iris.util.DontObfuscate;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.RegistryListLoot;
import com.volmit.iris.util.Required;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.block.data.BlockData;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Represents loot within this object or jigsaw piece")
@Data
public class IrisObjectLoot {

    @DontObfuscate
    @ArrayType(min = 1, type = IrisBlockData.class)
    @Desc("The list of blocks this loot table should apply to")
    private KList<IrisBlockData> filter = new KList<>();

    @Desc("Exactly match the block data or not")
    @DontObfuscate
    private boolean exact = false;

    @DontObfuscate
    @Desc("The loot table name")
    @Required
    @RegistryListLoot
    private String name;

    @DontObfuscate
    @Desc("The weight of this loot table being chosen")
    private int weight = 1;

    private final transient AtomicCache<KList<BlockData>> filterCache = new AtomicCache<>();

    public KList<BlockData> getFilter(IrisDataManager rdata)
    {
        return filterCache.aquire(() ->
        {
            KList<BlockData> b = new KList<>();

            for(IrisBlockData i : filter)
            {
                BlockData bx = i.getBlockData(rdata);

                if(bx != null)
                {
                    b.add(bx);
                }
            }

            return b;
        });
    }

    public boolean matchesFilter(IrisDataManager manager, BlockData data) {
        for (BlockData filterData : getFilter(manager)) {
            if (filterData.matches(data)) return true;
        }
        return false;
    }
}
