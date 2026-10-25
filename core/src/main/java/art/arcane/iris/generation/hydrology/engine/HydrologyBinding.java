package art.arcane.iris.generation.hydrology.engine;

@FunctionalInterface
public interface HydrologyBinding {
    Scope NO_SCOPE = () -> {
    };
    HydrologyBinding UNBOUND = () -> NO_SCOPE;

    Scope open();

    @FunctionalInterface
    interface Scope extends AutoCloseable {
        @Override
        void close();
    }
}
