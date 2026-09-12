package art.arcane.iris.world.history;

public final class SavedBiomeUnavailableException extends IllegalStateException {
    private final boolean loading;

    public SavedBiomeUnavailableException(String message, boolean loading) {
        super(message);
        this.loading = loading;
    }

    public SavedBiomeUnavailableException(String message, Throwable cause) {
        super(message, cause);
        loading = false;
    }

    public boolean isLoading() {
        return loading;
    }
}
