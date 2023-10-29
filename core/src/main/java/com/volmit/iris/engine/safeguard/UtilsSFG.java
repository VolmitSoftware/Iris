package com.volmit.iris.engine.safeguard;

import com.volmit.iris.Iris;
import com.volmit.iris.util.format.C;

import java.util.ArrayList;
import java.util.List;

import static com.volmit.iris.engine.safeguard.IrisSafeguard.unstablemode;
import static com.volmit.iris.engine.safeguard.ServerBootSFG.*;

public class UtilsSFG {
 public static void UnstableMode(){
  if (unstablemode) {
   Iris.safeguard(C.DARK_RED + "Iris is running in Unstable Mode");
  } else {
   Iris.safeguard(C.BLUE + "Iris is running Stable");
  }
 }
 public static void SupportedServerSoftware(){
  if (!passedserversoftware) {
   Iris.safeguard(C.DARK_RED + "Server is running unsupported server software");
   Iris.safeguard(C.RED + "Supported: Purpur, Pufferfish, Paper, Spigot, Bukkit");
  }
 }
 public static void printIncompatiblePluginWarnings(){
  // String SupportedIrisVersion = getDescription().getVersion(); //todo Automatic version

  if (safeguardPassed) {
   Iris.safeguard(C.BLUE + "0 Conflicts found");
  } else {
   Iris.safeguard(C.DARK_RED + "" + count + " Conflicts found");
   unstablemode = true;

   if (multiverse) {
    Iris.safeguard(C.RED + "Multiverse");
    Iris.safeguard(C.RED + "- The plugin Multiverse is not compatible with the server.");
    Iris.safeguard(C.RED + "- If you want to have a world manager, consider using PhantomWorlds or MyWorlds instead.");
   }
   if (dynmap) {
    Iris.safeguard(C.RED + "Dynmap");
    Iris.safeguard(C.RED + "- The plugin Dynmap is not compatible with the server.");
    Iris.safeguard(C.RED + "- If you want to have a map plugin like Dynmap, consider Bluemap or LiveAtlas.");
   }
   if (terraform || stratos) {
    Iris.safeguard(C.YELLOW + "Terraform Generator / Stratos");
    Iris.safeguard(C.YELLOW + "- Iris is not compatible with other worldgen plugins.");
   }
   if (unsuportedversion) {
    Iris.safeguard(C.RED + "Server Version");
    Iris.safeguard(C.RED + "- Iris only supports 1.19.2 > 1.20.2");
   }
   if (!passedserversoftware) {
    Iris.safeguard(C.RED + "Unsupported Server Software");
    Iris.safeguard(C.RED + "- Please consider using Paper or Purpur instead.");

   }
  }
 }

 public static String MSGIncompatibleWarnings(){
  StringBuilder stringBuilder = new StringBuilder();

  List<String> incompatibleList = new ArrayList<>();

  if (multiverse) {
   String incompatibility1 = "Multiverse";
   stringBuilder.append(incompatibility1).append(", ");
   incompatibleList.add(incompatibility1);
  }
  if(dynmap) {
   String incompatibility2 = "Dynmap";
   stringBuilder.append(incompatibility2).append(", ");
   incompatibleList.add(incompatibility2);
  }
  if (terraform) {
   String incompatibility3 = "Terraform";
   stringBuilder.append(incompatibility3).append(", ");
   incompatibleList.add(incompatibility3);
  }
  if(stratos){
   String incompatibility4 = "Stratos";
   stringBuilder.append(incompatibility4).append(", ");
   incompatibleList.add(incompatibility4);

  }
  if(unsuportedversion){
   String incompatibility5 = "Server Version";
   stringBuilder.append(incompatibility5).append(", ");
   incompatibleList.add(incompatibility5);

  }
  if(!passedserversoftware){
   String incompatibility6 = "Server Software";
   stringBuilder.append(incompatibility6).append(", ");
   incompatibleList.add(incompatibility6);

  }

  String MSGIncompatiblePlugins = stringBuilder.toString();
  return MSGIncompatiblePlugins;

 }
}
