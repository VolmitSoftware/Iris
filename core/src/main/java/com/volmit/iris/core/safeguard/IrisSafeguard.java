package com.volmit.iris.core.safeguard;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;

public class IrisSafeguard {
    public static boolean unstablemode = false;
    public static boolean warningmode = false;
    public static boolean stablemode = false;

    public static void IrisSafeguardSystem() {
        Iris.info("Enabled Iris SafeGuard");
        ServerBootSFG.BootCheck();
    }

    public static void earlySplash() {
        if (ServerBootSFG.safeguardPassed || IrisSettings.get().getGeneral().DoomsdayAnnihilationSelfDestructMode)
            return;

        Iris.instance.splash();
        UtilsSFG.splash();
    }
}

