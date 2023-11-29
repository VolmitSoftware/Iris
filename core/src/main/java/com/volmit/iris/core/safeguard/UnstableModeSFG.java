package com.volmit.iris.core.safeguard;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.util.format.C;

public class UnstableModeSFG {
 public static void selectMode(){
  if (IrisSafeguard.unstablemode) {
   Iris.safeguard(C.DARK_RED + "Iris is running in Unstable Mode");
   unstable();
  } else {
   stable();
  }
 }
 public static void stable(){
   Iris.safeguard(C.BLUE + "Iris is running Stable");
 }

 public static void unstable() {

  UtilsSFG.printIncompatibleWarnings();

  if (IrisSafeguard.unstablemode) {
   Iris.info("");
   Iris.info(C.DARK_GRAY + "--==<" + C.RED + " IMPORTANT " + C.DARK_GRAY + ">==--");
   Iris.info(C.RED + "Iris is running in unstable mode which may cause the following issues:");
   Iris.info(C.DARK_RED + "Server Issues");
   Iris.info(C.RED + "- Server won't boot");
   Iris.info(C.RED + "- Data Loss");
   Iris.info(C.RED + "- Unexpected behavior.");
   Iris.info(C.RED + "- And More...");
   Iris.info(C.DARK_RED + "World Issues");
   Iris.info(C.RED + "- Worlds can't load due to corruption.");
   Iris.info(C.RED + "- Worlds may slowly corrupt until they can't load.");
   Iris.info(C.RED + "- World data loss.");
   Iris.info(C.RED + "- And More...");
   Iris.info(C.DARK_RED + "ATTENTION: " + C.RED + "While running Iris in unstable mode, you won't be eligible for support.");
   Iris.info(C.DARK_RED + "CAUSE: " + C.RED + UtilsSFG.MSGIncompatibleWarnings());

   if (IrisSettings.get().getGeneral().bootUnstable) {
    Iris.info(C.DARK_RED + "Boot Unstable is set to true, continuing with the startup process.");
   } else {
    Iris.info(C.DARK_RED + "Go to plugins/iris/settings.json and set ignoreUnstable to true if you wish to proceed.");
    while (true) {
     try {
      Thread.sleep(1000);
     } catch (InterruptedException e) {
      // Handle interruption
     }
    }
   }
   Iris.info("");
  }
 }
}
