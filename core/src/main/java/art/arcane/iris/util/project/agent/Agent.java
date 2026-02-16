package art.arcane.iris.util.agent;

import art.arcane.iris.Iris;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;

import java.io.File;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class Agent {
    private static final String NAME = "art.arcane.iris.util.agent.Installer";
    public static final File AGENT_JAR = new File(Iris.instance.getDataFolder(), "agent.jar");

    public static ClassReloadingStrategy installed() {
        return ClassReloadingStrategy.of(getInstrumentation());
    }

    public static boolean isInstalled() {
        return doGetInstrumentation() != null;
    }

    public static Instrumentation getInstrumentation() {
        Instrumentation instrumentation = doGetInstrumentation();
        if (instrumentation == null) throw new IllegalStateException("The agent is not initialized or unavailable");
        return instrumentation;
    }

    public static boolean install() {
        if (isInstalled())
            return true;

        if (!ensureAgentJar())
            return false;

        try {
            Iris.info("Installing Java Agent...");
            Iris.info("Note: JVM [Attach Listener/ERROR] [STDERR] warning lines during this step are expected and not Iris errors.");
            ByteBuddyAgent.attach(AGENT_JAR, ByteBuddyAgent.ProcessProvider.ForCurrentVm.INSTANCE);
        } catch (Throwable e) {
            Iris.error("Failed to install Java Agent: " + e.getMessage());
            Iris.reportError(e);
        }
        return doGetInstrumentation() != null;
    }

    private static boolean ensureAgentJar() {
        File parent = AGENT_JAR.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
            Iris.error("Failed to create Iris plugin data folder for Java agent: " + parent.getAbsolutePath());
            return false;
        }

        try (InputStream in = openBundledAgentJar()) {
            if (in == null) {
                if (AGENT_JAR.isFile() && AGENT_JAR.length() > 0) {
                    Iris.warn("Bundled agent.jar not found in Iris plugin jar. Reusing existing " + AGENT_JAR.getAbsolutePath());
                    return true;
                }

                Iris.error("Bundled agent.jar was not found in Iris plugin jar. Rebuild/deploy Iris with embedded agent.jar.");
                return false;
            }

            Files.copy(in, AGENT_JAR.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Throwable e) {
            Iris.error("Failed to prepare Java agent jar: " + e.getMessage());
            Iris.reportError(e);
            return false;
        }
    }

    private static InputStream openBundledAgentJar() {
        InputStream stream = Iris.instance.getResource("agent.jar");
        if (stream != null) {
            return stream;
        }

        return Agent.class.getClassLoader().getResourceAsStream("agent.jar");
    }

    private static Instrumentation doGetInstrumentation() {
        try {
            return (Instrumentation) Class.forName(NAME, true, ClassLoader.getSystemClassLoader()).getMethod("getInstrumentation").invoke(null);
        } catch (Exception ex) {
            return null;
        }
    }
}
