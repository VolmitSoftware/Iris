package art.arcane.iris.pack.schema.annotation.functions;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.generation.runtime.ListFunction;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.volmlib.util.collection.KList;

public class LootTableKeyFunction implements ListFunction<KList<String>> {
    @Override
    public String key() {
        return "loot-table-key";
    }

    @Override
    public String fancyName() {
        return "LootTable Key";
    }

    @Override
    public KList<String> apply(IrisData data) {
        return new KList<>(IrisPlatforms.get().registries().lootTableKeys());
    }
}
