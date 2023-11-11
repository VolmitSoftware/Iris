package com.volmit.iris.core.safeguard;

import com.volmit.iris.Iris;

public class IrisSafeguard {
 // more planned and WIP
    public static boolean unstablemode = false;
    public static void IrisSafeguardSystem() {
        Iris.info("Enabled Iris SafeGuard");
        ServerBootSFG.BootCheck();
    }
}

