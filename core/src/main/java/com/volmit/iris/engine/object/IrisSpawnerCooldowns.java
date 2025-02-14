package com.volmit.iris.engine.object;

import com.volmit.iris.engine.framework.Engine;
import com.volmit.iris.util.collection.KMap;
import lombok.EqualsAndHashCode;
import lombok.NonNull;

@EqualsAndHashCode
public class IrisSpawnerCooldowns {
    private final KMap<String, IrisEngineSpawnerCooldown> cooldowns = new KMap<>();

    public IrisEngineSpawnerCooldown getCooldown(@NonNull IrisSpawner spawner) {
        return getCooldown(spawner.getLoadKey());
    }

    public IrisEngineSpawnerCooldown getCooldown(@NonNull String loadKey) {
        return cooldowns.computeIfAbsent(loadKey, k -> {
            IrisEngineSpawnerCooldown cd = new IrisEngineSpawnerCooldown();
            cd.setSpawner(loadKey);
            return cd;
        });
    }

    public void cleanup(Engine engine) {
        cooldowns.values().removeIf(cd -> {
            IrisSpawner sp = engine.getData().getSpawnerLoader().load(cd.getSpawner());
            return sp == null || cd.canSpawn(sp.getMaximumRate());
        });
    }

    public boolean isEmpty() {
        return cooldowns.isEmpty();
    }
}
