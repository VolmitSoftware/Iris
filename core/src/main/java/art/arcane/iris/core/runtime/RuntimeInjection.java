package art.arcane.iris.core.runtime;

import art.arcane.iris.core.IrisStartupValidation;
import art.arcane.iris.core.nms.INMS;
import art.arcane.iris.core.nms.v1X.NMSBinding1X;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.util.project.agent.Agent;

public final class RuntimeInjection {
    private static final Object LOCK = new Object();

    private static volatile Outcome outcome;

    private RuntimeInjection() {
    }

    public enum Failure {
        NONE,
        NO_BINDING,
        AGENT,
        INJECTION
    }

    public record Outcome(boolean installed, Failure failure, String lockReason) {
    }

    public static Outcome install() {
        Outcome current = outcome;
        if (current != null) {
            return current;
        }

        synchronized (LOCK) {
            if (outcome != null) {
                return outcome;
            }
            Outcome resolved = attemptInstall();
            outcome = resolved;
            return resolved;
        }
    }

    public static void installIfDeferred() {
        Outcome resolved = install();
        if (resolved.installed()) {
            return;
        }

        IrisLogging.error("Iris runtime injection is unavailable: " + resolved.lockReason());
        IrisStartupValidation.markRuntimeInvalid(resolved.lockReason());
    }

    public static boolean isInstalled() {
        Outcome current = outcome;
        return current != null && current.installed();
    }

    public static void reset() {
        synchronized (LOCK) {
            outcome = null;
        }
    }

    static String agentLockReason() {
        return "Iris Java agent is unavailable. Add -javaagent:" + Agent.AGENT_JAR.getPath()
                + " to the JVM arguments before -jar and restart the server.";
    }

    private static Outcome attemptInstall() {
        if (!INMS.isBound() || INMS.get() instanceof NMSBinding1X) {
            return new Outcome(false, Failure.NO_BINDING,
                    "Iris cannot use this server's NMS runtime. Resolve the Server Version or NMS Disabled error above and restart the server.");
        }

        if (!Agent.install()) {
            return new Outcome(false, Failure.AGENT, agentLockReason());
        }

        if (!INMS.get().injectBukkit()) {
            return new Outcome(false, Failure.INJECTION,
                    "Iris runtime injection failed. Resolve the startup errors and restart the server.");
        }

        return new Outcome(true, Failure.NONE, null);
    }
}
