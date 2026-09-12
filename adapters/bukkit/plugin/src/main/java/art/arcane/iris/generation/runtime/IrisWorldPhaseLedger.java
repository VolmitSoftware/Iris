package art.arcane.iris.generation.runtime;

import art.arcane.iris.platform.bukkit.api.IrisApiEventSVC;

import art.arcane.iris.api.world.IrisWorldPhase;
import org.bukkit.World;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class IrisWorldPhaseLedger {
    @FunctionalInterface
    interface Dispatch {
        void fire(World world, IrisWorldPhase phase);
    }

    private final Set<UUID> announced = ConcurrentHashMap.newKeySet();
    private final Dispatch dispatch;

    IrisWorldPhaseLedger() {
        this(IrisApiEventSVC::fireWorldPhase);
    }

    IrisWorldPhaseLedger(Dispatch dispatch) {
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
    }

    void ready(World world) {
        UUID identity = identityOf(world);
        if (identity == null || !announced.add(identity)) {
            return;
        }

        dispatch.fire(world, IrisWorldPhase.ENGINE_READY);
    }

    void closing(World world) {
        UUID identity = identityOf(world);
        if (identity == null || !announced.remove(identity)) {
            return;
        }

        dispatch.fire(world, IrisWorldPhase.ENGINE_CLOSING);
    }

    private static UUID identityOf(World world) {
        return world == null ? null : world.getUID();
    }
}
