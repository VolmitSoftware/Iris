package com.volmit.iris.core.safeguard;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;

import java.util.concurrent.atomic.AtomicBoolean;

public class IrisSafeguard {
    private static final AtomicBoolean sfg = new AtomicBoolean(false);
    public static boolean unstablemode = false;
    public static boolean warningmode = false;
    public static boolean stablemode = false;

    public static void IrisSafeguardSystem() {
        Iris.info("Enabled Iris SafeGuard");
        ServerBootSFG.BootCheck();
    }

    public static void splash(boolean early) {
        if (early && (ServerBootSFG.safeguardPassed || IrisSettings.get().getGeneral().DoomsdayAnnihilationSelfDestructMode))
            return;

        if (!sfg.getAndSet(true)) {
            Iris.instance.splash();
            UtilsSFG.splash();
        }
    }

    public static String mode() {
        if (unstablemode) {
            return "unstable";
        } else if (warningmode) {
            return "warning";
        } else {
            return "stable";
        }
    }
}

