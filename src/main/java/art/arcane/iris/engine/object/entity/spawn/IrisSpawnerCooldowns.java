package art.arcane.iris.engine.object.entity.spawn;

import art.arcane.iris.builtin.spawn.Spawner;
import art.arcane.iris.engine.spawn.SpawnResources;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.volmlib.util.collection.KMap;
import lombok.EqualsAndHashCode;
import lombok.NonNull;

@EqualsAndHashCode
public class IrisSpawnerCooldowns {
    private final KMap<String, IrisEngineSpawnerCooldown> cooldowns = new KMap<>();

    public IrisEngineSpawnerCooldown getCooldown(@NonNull String loadKey) {
        return cooldowns.computeIfAbsent(loadKey, k -> {
            IrisEngineSpawnerCooldown cd = new IrisEngineSpawnerCooldown();
            cd.setSpawner(loadKey);
            return cd;
        });
    }

    public void cleanup(Engine engine) {
        cooldowns.values().removeIf(cd -> {
            Spawner spawner = SpawnResources.spawner(engine.getData(), cd.getSpawner());
            return spawner == null || cd.canSpawn(spawner.maximumRate().interval());
        });
    }

    public boolean isEmpty() {
        return cooldowns.isEmpty();
    }
}
