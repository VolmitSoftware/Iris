package com.volmit.iris.core.command.pregen;

import com.volmit.iris.Iris;
import com.volmit.iris.core.gui.PregeneratorJob;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.plugin.MortarCommand;
import com.volmit.iris.util.plugin.VolmitSender;

public class CommandIrisPregenPause extends MortarCommand {

    public CommandIrisPregenPause() {
        super("pause", "toggle", "t", "continue", "resume", "p", "c", "unpause", "up");
        requiresPermission(Iris.perm);
        setCategory("Pregen");
        setDescription("Toggle an ongoing pregeneration task");
    }

    @Override
    public boolean handle(VolmitSender sender, String[] args) {
        if (PregeneratorJob.pauseResume()) {
            sender.sendMessage("Paused/unpaused pregeneration task, now: " + (PregeneratorJob.isPaused() ? "Paused" : "Running") + ".");
        } else {
            sender.sendMessage("No active pregeneration tasks to pause/unpause.");
        }
        return true;
    }

    @Override
    public void addTabOptions(VolmitSender sender, String[] args, KList<String> list) {

    }

    @Override
    protected String getArgsUsage() {
        return "";
    }
}
