package art.arcane.iris.generation.runtime;

import java.lang.ref.WeakReference;

/**
 * Identifies an engine in pack-object cache keys without keeping it alive. Pack objects outlive
 * worlds and sit in thread-local scratch on pooled threads, so a strong engine there pins a closed world.
 */
public final class EngineRef {
    private static final EngineRef NONE = new EngineRef(null);
    private final WeakReference<Engine> engine;
    private final int identity;

    EngineRef(Engine engine) {
        this.engine = new WeakReference<>(engine);
        this.identity = System.identityHashCode(engine);
    }

    public static EngineRef of(Engine engine) {
        if (engine == null) {
            return NONE;
        }
        if (engine instanceof IrisEngine irisEngine) {
            return irisEngine.getCacheRef();
        }
        return new EngineRef(engine);
    }

    public boolean refersTo(Engine candidate) {
        if (candidate == null) {
            return this == NONE;
        }
        return this != NONE && engine.refersTo(candidate);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof EngineRef other) || this == NONE || other == NONE || identity != other.identity) {
            return false;
        }
        Engine live = engine.get();
        return live != null && other.engine.refersTo(live);
    }

    @Override
    public int hashCode() {
        return identity;
    }
}
