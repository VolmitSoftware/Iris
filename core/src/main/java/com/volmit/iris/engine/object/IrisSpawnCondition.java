package com.volmit.iris.engine.object;

import com.volmit.iris.Iris;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;
import java.util.function.BooleanSupplier;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Conditions for a spawner to be triggered")
@Data
public class IrisSpawnCondition {
    private static final NamespacedKey CATEGORY_KEY = new NamespacedKey(Iris.instance, "spawn_category");

    private SpawnCategory category = SpawnCategory.AMBIENT;
    private int maxEntities = 60;

    public boolean check(KMap<UUID, KMap<String, Boolean>> cache, KList<Entity> entities) {
        int entityCount = 0;
        for (Entity entity : entities) {
            var map = cache.computeIfAbsent(entity.getUniqueId(), k -> new KMap<>());
            if (check(map, "category_" + category.name(), () -> checkCategory(entity, category)) && ++entityCount >= maxEntities)
                return false;
        }
        return true;
    }

    public void apply(Entity entity) {
        var pdc = entity.getPersistentDataContainer();
        pdc.set(CATEGORY_KEY, PersistentDataType.STRING, category.name());
    }

    private static boolean check(KMap<String, Boolean> cache, String key, BooleanSupplier predicate) {
        return cache.computeIfAbsent(key, k -> predicate.getAsBoolean()) == Boolean.TRUE;
    }

    private static boolean checkCategory(Entity entity, SpawnCategory category) {
        if (entity.getSpawnCategory() == category)
            return true;

        var pdc = entity.getPersistentDataContainer();
        if (!pdc.has(CATEGORY_KEY, PersistentDataType.STRING))
            return false;
        return category.name().equals(pdc.get(CATEGORY_KEY, PersistentDataType.STRING));
    }
}
