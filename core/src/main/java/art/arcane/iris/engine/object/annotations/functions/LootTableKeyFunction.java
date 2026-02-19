package art.arcane.iris.engine.object.annotations.functions;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.ListFunction;
import art.arcane.volmlib.util.collection.KList;
import org.bukkit.NamespacedKey;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.LootTables;

import java.util.Arrays;
import java.util.stream.Collectors;

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
        return Arrays.stream(LootTables.values())
                .map(LootTables::getLootTable)
                .map(LootTable::getKey)
                .map(NamespacedKey::toString)
                .collect(Collectors.toCollection(KList::new));
    }
}
