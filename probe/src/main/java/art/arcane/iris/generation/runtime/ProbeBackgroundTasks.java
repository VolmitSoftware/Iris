package art.arcane.iris.generation.runtime;

public final class ProbeBackgroundTasks {
    private ProbeBackgroundTasks() {
    }

    public static void await(Engine engine) {
        if (!(engine instanceof IrisEngine iris)) {
            throw new IllegalArgumentException("Probe background barrier requires an Iris engine");
        }
        await(iris.backgroundTasks);
    }

    static void await(EngineBackgroundTasks tasks) {
        tasks.drainBackgroundTasks("probe completion").requireComplete("probe completion");
    }
}
