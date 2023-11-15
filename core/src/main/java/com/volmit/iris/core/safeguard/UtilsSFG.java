package com.volmit.iris.core.safeguard;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.util.format.C;

public class UtilsSFG {
 public static void UnstableMode(){
  if (IrisSafeguard.unstablemode) {
   Iris.safeguard(C.DARK_RED + "Iris is running in Unstable Mode");
  } else {
   Iris.safeguard(C.BLUE + "Iris is running Stable");
  }
 }
 public static void SupportedServerSoftware(){
  if (!ServerBootSFG.passedserversoftware) {
   Iris.safeguard(C.DARK_RED + "Server is running unsupported server software");
   Iris.safeguard(C.RED + "Supported: Purpur, Pufferfish, Paper, Spigot, Bukkit");
  }
 }
 public static void printIncompatibleWarnings(){
  // String SupportedIrisVersion = getDescription().getVersion(); //todo Automatic version

  if (ServerBootSFG.safeguardPassed) {
   Iris.safeguard(C.BLUE + "0 Conflicts found");
  } else {
   Iris.safeguard(C.DARK_RED + "" + ServerBootSFG.count + " Conflicts found");
   IrisSafeguard.unstablemode = true;

   if (ServerBootSFG.incompatiblePlugins.get("Multiverse-Core")) {
    Iris.safeguard(C.RED + "Multiverse");
    Iris.safeguard(C.RED + "- The plugin Multiverse is not compatible with the server.");
    Iris.safeguard(C.RED + "- If you want to have a world manager, consider using PhantomWorlds or MyWorlds instead.");
   }
   if (ServerBootSFG.incompatiblePlugins.get("Dynmap")) {
    Iris.safeguard(C.RED + "Dynmap");
    Iris.safeguard(C.RED + "- The plugin Dynmap is not compatible with the server.");
    Iris.safeguard(C.RED + "- If you want to have a map plugin like Dynmap, consider Bluemap.");
   }
   if (ServerBootSFG.incompatiblePlugins.get("TerraformGenerator") || ServerBootSFG.incompatiblePlugins.get("Stratos")) {
    Iris.safeguard(C.YELLOW + "Terraform Generator / Stratos");
    Iris.safeguard(C.YELLOW + "- Iris is not compatible with other worldgen plugins.");
   }
   if (ServerBootSFG.unsuportedversion) {
    Iris.safeguard(C.RED + "Server Version");
    Iris.safeguard(C.RED + "- Iris only supports 1.19.2 > 1.20.2");
   }
   if (!ServerBootSFG.passedserversoftware) {
    Iris.safeguard(C.RED + "Unsupported Server Software");
    Iris.safeguard(C.RED + "- Please consider using Paper or Purpur instead.");

   }
  }
 }

 public static String MSGIncompatibleWarnings() {
  return ServerBootSFG.allIncompatiblePlugins;
 }


 public static void unstablePrompt() {
  if (IrisSafeguard.unstablemode) {
   Iris.info("");
   Iris.info(C.DARK_GRAY + "--==<" + C.RED + " IMPORTANT " + C.DARK_GRAY + ">==--");
   Iris.info(C.RED + "Iris is running in unstable mode what may cause the following issues.");
   Iris.info(C.DARK_RED + "Server Issues");
   Iris.info(C.RED + "- Server wont boot");
   Iris.info(C.RED + "- Data Loss");
   Iris.info(C.RED + "- Unexpected behavior.");
   Iris.info(C.RED + "- And More..");
   Iris.info(C.DARK_RED + "World Issues");
   Iris.info(C.RED + "- Worlds cant load due to corruption..");
   Iris.info(C.RED + "- Worlds may slowly corrupt till they wont be able to load.");
   Iris.info(C.RED + "- World data loss.");
   Iris.info(C.RED + "- And More..");
   Iris.info(C.DARK_RED + "ATTENTION:" + C.RED + " While running iris in unstable mode you wont be eligible for support.");
   Iris.info(C.DARK_RED + "CAUSE: " + C.RED + MSGIncompatibleWarnings());
   Iris.info("");
   if (IrisSettings.get().getGeneral().bootUnstable) {
    Iris.info(C.DARK_RED + "Boot Unstable is set to true, continuing with the startup process.");
   }

   if (!IrisSettings.get().getGeneral().isBootUnstable()) {
    Iris.info(C.DARK_RED + "Go to plugins/iris/settings.json and set ignoreUnstable to true if you wish to proceed.");
    while (true) {
     try {
      Thread.sleep(1000);
     } catch (InterruptedException e) {
      // No
     }
    }
   }
   Iris.info("");
  }
 }
}
