package com.volmit.iris.core.decrees;

import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisSettings;
import com.volmit.iris.util.decree.DecreeExecutor;

public interface DecreeStudioExtension extends DecreeExecutor {

    /**
     * @return true if server GUIs are not enabled
     */
    default boolean noGUI() {
        if (!IrisSettings.get().isUseServerLaunchedGuis()){
            error("You must have server launched GUIs enabled in the settings!");
            return true;
        }
        return false;
    }

    /**
     * @return true if no studio is open or the player is not in one
     */
    default boolean noStudio(){
        if (!sender().isPlayer()){
            error("Players only (this is a config error. Ask support to add DecreeOrigin.PLAYER to the command you tried to run)");
            return true;
        }
        if (!Iris.proj.isProjectOpen()){
            error("No studio world is open!");
            return true;
        }
        if (!engine().isStudio()){
            error("You must be in a studio world!");
            return true;
        }
        return false;
    }
}
