package art.arcane.iris.util.common.misc;

public final class RegenRuntime {
    private static final ThreadLocal<String> RUN_ID = new ThreadLocal<>();

    private RegenRuntime() {
    }

    public static void setRunId(String runId) {
        RUN_ID.set(runId);
    }

    public static String getRunId() {
        return RUN_ID.get();
    }

    public static void clear() {
        RUN_ID.remove();
    }
}
