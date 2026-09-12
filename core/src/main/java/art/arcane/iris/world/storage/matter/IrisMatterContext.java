package art.arcane.iris.world.storage.matter;

import art.arcane.iris.pack.loading.IrisData;

import java.util.Objects;

public final class IrisMatterContext {
    private static final ThreadLocal<IrisData> DATA = new ThreadLocal<>();

    private IrisMatterContext() {
    }

    public static Scope open(IrisData data) {
        Thread owner = Thread.currentThread();
        IrisData previous = DATA.get();
        IrisData installed = Objects.requireNonNull(data);
        DATA.set(installed);
        return new Scope(owner, previous, installed);
    }

    public static IrisData require() {
        IrisData data = DATA.get();
        if (data == null) {
            throw new IllegalStateException("No Iris matter data context is bound to thread " + Thread.currentThread().getName() + ".");
        }
        return data;
    }

    public static final class Scope implements AutoCloseable {
        private final Thread owner;
        private final IrisData previous;
        private final IrisData installed;
        private boolean closed;

        private Scope(Thread owner, IrisData previous, IrisData installed) {
            this.owner = owner;
            this.previous = previous;
            this.installed = installed;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            if (Thread.currentThread() != owner) {
                throw new IllegalStateException("Iris matter data scope closed from a different thread.");
            }
            if (DATA.get() != installed) {
                throw new IllegalStateException("Iris matter data scopes must close in LIFO order.");
            }
            if (previous == null) {
                DATA.remove();
            } else {
                DATA.set(previous);
            }
            closed = true;
        }
    }
}
