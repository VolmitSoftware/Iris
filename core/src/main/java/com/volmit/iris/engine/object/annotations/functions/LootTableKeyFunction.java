package com.volmit.iris.engine.object.annotations.functions;

import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.framework.ListFunction;
import com.volmit.iris.util.collection.KList;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.LootTables;

import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

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
        return StreamSupport.stream(Registry.LOOT_TABLES.spliterator(), false)
                .map(LootTables::getLootTable)
                .map(LootTable::getKey)
                .map(NamespacedKey::toString)
                .collect(Collectors.toCollection(KList::new));
    }
}
