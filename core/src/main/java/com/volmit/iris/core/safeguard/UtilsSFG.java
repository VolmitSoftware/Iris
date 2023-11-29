package com.volmit.iris.core.safeguard;

import com.volmit.iris.Iris;
import com.volmit.iris.util.format.C;

public class UtilsSFG {
 public static void splash(){
  UnstableModeSFG.selectMode();
 }

 public static void printIncompatibleWarnings(){
  // String SupportedIrisVersion = getDescription().getVersion(); //todo Automatic version

  if (ServerBootSFG.safeguardPassed) {
   Iris.safeguard(C.BLUE + "0 Conflicts found");
  } else {
   Iris.safeguard(C.DARK_RED + "" + ServerBootSFG.count + " Conflicts found");

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
}
