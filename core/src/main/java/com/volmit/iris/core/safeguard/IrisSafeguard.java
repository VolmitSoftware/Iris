package com.volmit.iris.core.safeguard;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import io.papermc.lib.PaperLib;

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

    public static void suggestPaper() {
        PaperLib.suggestPaper(Iris.instance);
    }

    public static KMap<String, Object> asContext() {
        KMap<String, Object> m = new KMap<>();
        m.put("diskSpace", !ServerBootSFG.hasEnoughDiskSpace);
        m.put("javaVersion", !ServerBootSFG.isCorrectJDK);
        m.put("jre", ServerBootSFG.isJRE);
        m.put("missingAgent", ServerBootSFG.missingAgent);
        m.put("missingDimensionTypes", ServerBootSFG.missingDimensionTypes);
        m.put("failedInjection", ServerBootSFG.failedInjection);
        m.put("unsupportedVersion", ServerBootSFG.unsuportedversion);
        m.put("serverSoftware", !ServerBootSFG.passedserversoftware);
        KList<String> incompatiblePlugins = new KList<>();
        ServerBootSFG.incompatibilities.forEach((plugin, present) -> {
            if (present) incompatiblePlugins.add(plugin);
        });
        m.put("plugins", incompatiblePlugins);
        return m;
    }
}

