/*
 *  Iris is a World Generator for Minecraft Bukkit Servers
 *  Copyright (c) 2024 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.volmit.iris.core.safeguard;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.core.nms.INMS;
import com.volmit.iris.core.safeguard.handler.onCommandWarning;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.misc.getHardware;

import static org.bukkit.Bukkit.getServer;

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
        acceptUnstable = IrisSettings.get().getSafeguard().ignoreBootMode;
        getServer().getPluginManager().registerEvents(new onCommandWarning(), Iris.instance);
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

