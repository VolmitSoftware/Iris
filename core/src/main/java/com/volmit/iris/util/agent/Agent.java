package com.volmit.iris.util.agent;

import com.volmit.iris.Iris;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class Agent {
    private static final String NAME = "com.volmit.iris.util.agent.Installer";
    public static final File AGENT_JAR = new File(Iris.instance.getDataFolder(), "agent.jar");

    public static ClassReloadingStrategy installed() {
        return ClassReloadingStrategy.of(getInstrumentation());
    }

    public static Instrumentation getInstrumentation() {
        Instrumentation instrumentation = doGetInstrumentation();
        if (instrumentation == null) throw new IllegalStateException("The agent is not initialized or unavailable");
        return instrumentation;
    }

    public static boolean install() {
        if (doGetInstrumentation() != null)
            return true;
        try {
            Files.copy(Iris.instance.getResource("agent.jar"), AGENT_JAR.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Iris.info("Installing Java Agent...");
            ByteBuddyAgent.attach(AGENT_JAR, ByteBuddyAgent.ProcessProvider.ForCurrentVm.INSTANCE);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return doGetInstrumentation() != null;
    }

    private static Instrumentation doGetInstrumentation() {
        try {
            return (Instrumentation) Class.forName(NAME, true, ClassLoader.getSystemClassLoader()).getMethod("getInstrumentation").invoke(null);
        } catch (Exception ex) {
            return null;
        }
    }
}
