package art.arcane.iris.engine.object.annotations.functions;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.ListFunction;
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
