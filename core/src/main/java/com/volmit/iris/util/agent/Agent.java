package com.volmit.iris.util.agent;

import com.volmit.iris.Iris;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;

import java.lang.instrument.Instrumentation;

public class Agent {
    private static final String NAME = "com.volmit.iris.util.agent.Installer";

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
            Iris.info("Installing Java Agent...");
            ByteBuddyAgent.attach(Iris.instance.getJarFile(), ByteBuddyAgent.ProcessProvider.ForCurrentVm.INSTANCE);
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
