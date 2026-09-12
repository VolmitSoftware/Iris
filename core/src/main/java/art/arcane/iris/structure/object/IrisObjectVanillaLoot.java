package art.arcane.iris.structure.object;

import art.arcane.iris.generation.block.IrisBlockData;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.cache.AtomicCache;
import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.MinNumber;
import art.arcane.iris.pack.schema.annotation.RegistryListFunction;
import art.arcane.iris.pack.schema.annotation.Required;
import art.arcane.iris.pack.schema.annotation.Snippet;
import art.arcane.iris.pack.schema.annotation.functions.LootTableKeyFunction;
import art.arcane.volmlib.util.collection.KList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import art.arcane.iris.spi.PlatformBlockState;
import lombok.experimental.Accessors;

@Snippet("object-vanilla-loot")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Description("Represents vanilla loot within this object")
@Data
public class IrisObjectVanillaLoot implements IObjectLoot {
    private final transient AtomicCache<KList<PlatformBlockState>> filterCache = new AtomicCache<>();
    @ArrayType(min = 1, type = IrisBlockData.class)
    @Description("The list of blocks this loot table should apply to")
    private KList<IrisBlockData> filter = new KList<>();
    @Description("Exactly match the block data or not")
    private boolean exact = false;
    @Description("The vanilla loot table key")
    @Required
    @RegistryListFunction(LootTableKeyFunction.class)
    private String name;
    @Description("The weight of this loot table being chosen")
    @MinNumber(1)
    private int weight = 1;

    public KList<PlatformBlockState> getFilter(IrisData rdata) {
        return filterCache.aquire(() ->
        {
            KList<PlatformBlockState> b = new KList<>();

            for (IrisBlockData i : filter) {
                PlatformBlockState bx = i.getBlockData(rdata);

                if (bx != null) {
                    b.add(bx);
                }
            }

            return b;
        });
    }
}
