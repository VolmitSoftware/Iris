package com.volmit.iris.manager.command;

import com.volmit.iris.Iris;
import com.volmit.iris.scaffold.IrisWorlds;
import com.volmit.iris.util.Command;
import com.volmit.iris.util.KList;
import com.volmit.iris.util.MortarCommand;
import com.volmit.iris.util.MortarSender;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;

public class CommandLocate extends MortarCommand
{
    @Command
    private CommandIrisStudioGoto got0;

    public CommandLocate()
    {
        super("locate");
        requiresPermission(Iris.perm);
    }

    @Override
    public boolean handle(MortarSender sender, String[] args)
    {
        if(sender.isPlayer()) {
            Player p = sender.player();
            World world = p.getWorld();
            if (!IrisWorlds.isIrisWorld(world)) {
                String cmd = "locate";
                for (int i = 0; i < args.length; i++){
                    cmd.concat(" " + args[i]);
                }
                Bukkit.dispatchCommand(sender, cmd);
                return true;
            }
            sender.sendMessage("You are in an Iris world and the /locate command is currently disabled in those.");
        } else {
            sender.sendMessage("Players only");
        }
        return true;
    }

    @Override
    public void addTabOptions(MortarSender sender, String[] args, KList<String> list) {

    }

    @Override
    protected String getArgsUsage()
    {
        return "";
    }
}
