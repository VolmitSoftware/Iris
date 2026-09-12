package art.arcane.iris.world.lifecycle;

public final class WorldReplacementBootstrapMarker {
    private static volatile boolean bootstrappedThisProcess;

    private WorldReplacementBootstrapMarker() {
    }

    public static boolean wasBootstrappedThisProcess() {
        return bootstrappedThisProcess;
    }

    public static void markBootstrapped() {
        bootstrappedThisProcess = true;
    }
}
