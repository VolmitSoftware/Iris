package com.volmit.iris.core.command.pregen;

import com.volmit.iris.Iris;
import com.volmit.iris.core.gui.PregeneratorJob;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.plugin.MortarCommand;
import com.volmit.iris.util.plugin.VolmitSender;

public class CommandIrisPregenToggle extends MortarCommand {

    public CommandIrisPregenToggle() {
        super("toggle", "t", "pause", "continue", "p", "c");
        requiresPermission(Iris.perm);
        setCategory("Pregen");
        setDescription("Toggle an ongoing pregeneration task");
    }

    @Override
    public boolean handle(VolmitSender sender, String[] args) {
        if (PregeneratorJob.pauseResume()){
            sender.sendMessage("Toggled pregeneration task, now: " + (PregeneratorJob.isPaused() ? "Paused" : "Running"));
        } else {
            sender.sendMessage("No active pregeneration tasks to toggle");
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
