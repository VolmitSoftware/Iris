package art.arcane.iris.engine.object.entity.spawn;

import art.arcane.volmlib.util.collection.KMap;
import lombok.EqualsAndHashCode;
import lombok.NonNull;

import java.util.function.ToLongFunction;

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

    /** Forgets the cooldowns that ran out and those of spawners {@code intervalMilliseconds} does not know (a negative interval). */
    public void cleanup(ToLongFunction<String> intervalMilliseconds) {
        cooldowns.values().removeIf(cd -> {
            long interval = intervalMilliseconds.applyAsLong(cd.getSpawner());
            return interval < 0 || cd.canSpawn(interval);
        });
    }

    public boolean isEmpty() {
        return cooldowns.isEmpty();
    }
}
