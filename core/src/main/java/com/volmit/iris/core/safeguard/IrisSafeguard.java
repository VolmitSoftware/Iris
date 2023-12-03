package com.volmit.iris.core.safeguard;

import com.volmit.iris.Iris;

public class IrisSafeguard {
    public static boolean unstablemode = false;
    public static boolean stablemode = false;
    public static void IrisSafeguardSystem() {
        Iris.info("Enabled Iris SafeGuard");
        ServerBootSFG.BootCheck();
    }
}

