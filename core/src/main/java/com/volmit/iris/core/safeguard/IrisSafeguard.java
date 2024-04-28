package com.volmit.iris.core.safeguard;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.misc.getHardware;

public class IrisSafeguard {
    public static IrisSafeguard instance;
    public boolean acceptUnstable = false;
    public boolean unstablemode = false;
    public boolean warningmode = false;
    public boolean stablemode = false;

    public static void InitializeSafeguard() {
        instance = new IrisSafeguard();
    }

    public void IrisSafeguardSystem() {
        acceptUnstable = IrisSettings.get().getGeneral().ignoreBootMode;
        Iris.info("Enabled Iris SafeGuard");
        ServerBootSFG.BootCheck();
    }

    public void earlySplash() {
        String padd = Form.repeat(" ", 8);
        String padd2 = Form.repeat(" ", 4);
        String[] info = new String[]{"", "", "", "", "", padd2 + C.RED + " Iris", padd2 + C.GRAY + " by " + C.DARK_RED + "Volmit Software", padd2 + C.GRAY + " v" + C.RED + Iris.instance.getDescription().getVersion()};
        String[] splashunstable = {
                padd + C.GRAY + "   @@@@@@@@@@@@@@" + C.DARK_GRAY + "@@@",
                padd + C.GRAY + " @@&&&&&&&&&" + C.DARK_GRAY + "&&&&&&" + C.RED + "   .(((()))).                     ",
                padd + C.GRAY + "@@@&&&&&&&&" + C.DARK_GRAY + "&&&&&" + C.RED + "  .((((((())))))).                  ",
                padd + C.GRAY + "@@@&&&&&" + C.DARK_GRAY + "&&&&&&&" + C.RED + "  ((((((((()))))))))               " + C.GRAY + " @",
                padd + C.GRAY + "@@@&&&&" + C.DARK_GRAY + "@@@@@&" + C.RED + "    ((((((((-)))))))))              " + C.GRAY + " @@",
                padd + C.GRAY + "@@@&&" + C.RED + "            ((((((({ }))))))))           " + C.GRAY + " &&@@@",
                padd + C.GRAY + "@@" + C.RED + "               ((((((((-)))))))))    " + C.DARK_GRAY + "&@@@@@" + C.GRAY + "&&&&@@@",
                padd + C.GRAY + "@" + C.RED + "                ((((((((()))))))))  " + C.DARK_GRAY + "&&&&&" + C.GRAY + "&&&&&&&@@@",
                padd + C.GRAY + "" + C.RED + "                  '((((((()))))))'  " + C.DARK_GRAY + "&&&&&" + C.GRAY + "&&&&&&&&@@@",
                padd + C.GRAY + "" + C.RED + "                     '(((())))'   " + C.DARK_GRAY + "&&&&&&&&" + C.GRAY + "&&&&&&&@@",
                padd + C.GRAY + "                               " + C.DARK_GRAY + "@@@" + C.GRAY + "@@@@@@@@@@@@@@"
        };

        for (int i = 0; i < info.length; i++) {
            splashunstable[i] += info[i];
        }
        Iris.info("Java: " + Iris.instance.getJava());
        if (!Iris.instance.getServer().getVersion().contains("Purpur")) {
            if (Iris.instance.getServer().getVersion().contains("Spigot") && Iris.instance.getServer().getVersion().contains("Bukkit")) {
                Iris.info(C.RED + " Iris requires paper or above to function properly..");
            } else {
                Iris.info(C.YELLOW + "Purpur is recommended to use with iris.");
            }
        }
        if (getHardware.getProcessMemory() < 5999) {
            Iris.warn("6GB+ Ram is recommended");
            Iris.warn("Process Memory: " + getHardware.getProcessMemory() + " MB");
        }
        Iris.info("Custom Biomes: " + INMS.get().countCustomBiomes());
        Iris.info("\n\n " + new KList<>(splashunstable).toString("\n") + "\n");
        UtilsSFG.splash();

    }
}

