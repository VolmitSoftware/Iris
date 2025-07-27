package com.volmit.iris.util.agent;

import java.lang.instrument.Instrumentation;

public class Installer {
    private static volatile Instrumentation instrumentation;

    public static Instrumentation getInstrumentation() {
        Instrumentation instrumentation = Installer.instrumentation;
        if (instrumentation == null) {
            throw new IllegalStateException("The agent is not loaded or this method is not called via the system class loader");
        }
        return instrumentation;
    }

    public static void premain(String arguments, Instrumentation instrumentation) {
        doMain(instrumentation);
    }

    public static void agentmain(String arguments, Instrumentation instrumentation) {
        doMain(instrumentation);
    }

    private static synchronized void doMain(Instrumentation instrumentation) {
        if (Installer.instrumentation != null)
            return;
        Installer.instrumentation = instrumentation;
    }
}