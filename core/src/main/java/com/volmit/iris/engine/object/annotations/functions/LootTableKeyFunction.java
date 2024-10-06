package com.volmit.iris.engine.object.annotations.functions;

import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.framework.ListFunction;
import com.volmit.iris.util.collection.KList;
import org.bukkit.NamespacedKey;
import org.bukkit.loot.LootTables;

import java.util.Arrays;
import java.util.stream.Collectors;

public class LootTableKeyFunction implements ListFunction<IrisData, KList<String>> {
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
        return Arrays.stream(LootTables.values())
                .map(LootTables::getKey)
                .map(NamespacedKey::toString)
                .collect(Collectors.toCollection(KList::new));
    }
}
