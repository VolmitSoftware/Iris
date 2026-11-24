package art.arcane.iris.generation.cache;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * A per-instance thread local whose owner can drop every thread's value when it closes.
 * A plain ThreadLocal leaves its values in pooled threads that outlive the owner, and with them
 * whatever they reference, until each thread happens to clean the stale entry.
 */
public final class ReleasableThreadLocal<T> {
    private final Supplier<? extends T> factory;
    private final Set<Slot<T>> slots = ConcurrentHashMap.newKeySet();
    private final ThreadLocal<Slot<T>> local = ThreadLocal.withInitial(this::registerSlot);

    public ReleasableThreadLocal(Supplier<? extends T> factory) {
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    public T get() {
        Slot<T> slot = local.get();
        T value = slot.value;
        if (value == null) {
            value = factory.get();
            slot.value = value;
        }
        return value;
    }

    public void releaseAll() {
        for (Slot<T> slot : slots) {
            slot.value = null;
        }
    }

    private Slot<T> registerSlot() {
        Slot<T> slot = new Slot<>();
        slots.add(slot);
        return slot;
    }

    private static final class Slot<T> {
        private T value;
    }
}
